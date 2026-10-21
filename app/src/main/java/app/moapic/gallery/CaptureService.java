package app.moapic.gallery;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.net.Uri;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.io.OutputStream;

/** One consent, one image, then release the entire projection session. */
public final class CaptureService extends Service {
    public static volatile boolean running;
    private static final String CHANNEL = "screen_capture";
    private HandlerThread thread;
    private Handler worker;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private Image latestFrame;
    private boolean armed, saved, finished;
    private int width, height, density;
    private final Handler main = new Handler(Looper.getMainLooper());
    private CountdownOverlay countdown;

    @Override public void onCreate() {
        super.onCreate();
        countdown = new CountdownOverlay(this);
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL, "화면 캡처", NotificationManager.IMPORTANCE_LOW));
        thread = new HandlerThread("moapic-capture"); thread.start(); worker = new Handler(thread.getLooper());
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "cancel".equals(intent.getAction())) { worker.post(this::finishCapture); return START_NOT_STICKY; }
        if (running) return START_NOT_STICKY;
        running = true;
        int delay = Math.max(1000, Math.min(7000, intent == null ? 7000 : intent.getIntExtra("delay", 7000)));
        startForeground(40, notification(delay < 7000 ? "화면 한 장을 저장하고 있어요" : "7초 뒤 화면 한 장을 저장해요", true), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        if (intent == null || !intent.hasExtra("data")) { stopSelf(); return START_NOT_STICKY; }
        int code = intent.getIntExtra("code", Activity.RESULT_CANCELED);
        Intent data = intent.getParcelableExtra("data");
        worker.post(() -> {
            try {
                projection = getSystemService(MediaProjectionManager.class).getMediaProjection(code, data);
                if (projection == null) throw new IllegalStateException("Projection unavailable");
                projection.registerCallback(new MediaProjection.Callback() {
                    @Override public void onStop() { finishCapture(); }
                    @Override public void onCapturedContentResize(int w, int h) {
                        if (w > 0 && h > 0 && (w != width || h != height) && display != null && !finished) {
                            width = w; height = h;
                            if (latestFrame != null) { latestFrame.close(); latestFrame = null; }
                            ImageReader old = reader; reader = createReader();
                            display.resize(width, height, density); display.setSurface(reader.getSurface());
                            if (old != null) old.close();
                        }
                    }
                }, worker);
                WindowManager wm = getSystemService(WindowManager.class);
                DisplayMetrics metrics = getResources().getDisplayMetrics(); density = metrics.densityDpi;
                if (Build.VERSION.SDK_INT >= 30) { Rect b = wm.getMaximumWindowMetrics().getBounds(); width = b.width(); height = b.height(); }
                else { DisplayMetrics real = new DisplayMetrics(); wm.getDefaultDisplay().getRealMetrics(real); width = real.widthPixels; height = real.heightPixels; }
                reader = createReader();
                display = projection.createVirtualDisplay("Moapic one-shot", width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, worker);
                startCountdown(delay);
                worker.postDelayed(() -> {
                    if (!saved && !finished) { announce("캡처하지 못했어요. 화면 공유 상태를 확인해 주세요."); finishCapture(); }
                }, 15000);
            } catch (Exception error) { announce("화면 캡처를 시작하지 못했어요. 다시 시도해 주세요."); finishCapture(); }
        });
        return START_NOT_STICKY;
    }
    private void startCountdown(int delay) {
        long deadline = SystemClock.uptimeMillis() + delay;
        Runnable tick = new Runnable() {
            @Override public void run() {
                if (finished) return;
                int left = CaptureTiming.secondsRemaining(deadline, SystemClock.uptimeMillis());
                if (left > 0) {
                    try { getSystemService(NotificationManager.class).notify(40, notification("화면 캡처까지 " + left + "초 · 원하는 화면을 열어 주세요", true)); }
                    catch (SecurityException ignored) { /* Capture remains usable if notification permission was declined. */ }
                    main.post(() -> { if (running) countdown.show(left); });
                    worker.postDelayed(this, Math.min(1000, Math.max(1, deadline-SystemClock.uptimeMillis())));
                } else {
                    main.post(() -> {
                        countdown.hide();
                        worker.postDelayed(() -> {
                            if (finished || display == null) return;
                            try {
                                // A fresh surface forces a new frame after the countdown window was removed.
                                if (latestFrame != null) { latestFrame.close(); latestFrame = null; }
                                ImageReader old = reader; reader = createReader(); armed = true;
                                display.setSurface(reader.getSurface()); if (old != null) old.close();
                            } catch (Exception e) { announce("캡처하지 못했어요. 다시 시도해 주세요."); finishCapture(); }
                        }, 160);
                    });
                }
            }
        };
        tick.run();
    }
    private ImageReader createReader() {
        ImageReader next = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
        next.setOnImageAvailableListener(source -> {
            try {
                Image frame = source.acquireLatestImage();
                if (frame == null) return;
                if (latestFrame != null) latestFrame.close();
                latestFrame = frame;
                consumeFrame();
            } catch (Exception error) { announce("화면을 읽지 못했어요. 다시 시도해 주세요."); finishCapture(); }
        }, worker);
        return next;
    }
    private void consumeFrame() {
        if (latestFrame == null || !armed || saved || finished) return;
        Image image = latestFrame; latestFrame = null;
        try {
                saved = true;
                Image.Plane plane = image.getPlanes()[0];
                int stride = plane.getPixelStride(); int paddedWidth = plane.getRowStride() / stride;
                Bitmap padded = Bitmap.createBitmap(paddedWidth, image.getHeight(), Bitmap.Config.ARGB_8888);
                plane.getBuffer().rewind(); padded.copyPixelsFromBuffer(plane.getBuffer());
                Bitmap bitmap = Bitmap.createBitmap(padded, 0, 0, image.getWidth(), image.getHeight());
                try { save(bitmap); } finally { if (bitmap != padded) bitmap.recycle(); padded.recycle(); }
                announce("스크린샷을 모아픽에 저장했어요");
        } catch (Exception error) { announce("저장하지 못했어요. 저장 공간과 화면 공유 상태를 확인해 주세요."); }
        finally { image.close(); finishCapture(); }
    }
    private void save(Bitmap bitmap) throws Exception {
        Uri uri = MediaRepository.createPending(getContentResolver(), "Screenshot_Moapic_" + System.currentTimeMillis() + ".png", "Pictures/Screenshots/Moapic/", "image/png");
        try {
            try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
                if (stream == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new java.io.IOException("Save failed");
            }
            MediaRepository.publish(getContentResolver(), uri);
        } catch (Exception error) { getContentResolver().delete(uri, null, null); throw error; }
    }
    private Notification notification(String message, boolean cancel) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("모아픽").setContentText(message).setContentIntent(open).setOngoing(cancel).setAutoCancel(!cancel).setOnlyAlertOnce(true);
        if (cancel) {
            PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, CaptureService.class).setAction("cancel"), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            b.addAction(new Notification.Action.Builder(null, "취소", stop).build());
        }
        return b.build();
    }
    private void announce(String text) {
        try { getSystemService(NotificationManager.class).notify(41, notification(text, false)); } catch (SecurityException ignored) {}
        new Handler(Looper.getMainLooper()).post(() -> android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_LONG).show());
    }
    private void finishCapture() {
        if (finished) return; finished = true; armed = false;
        main.post(() -> countdown.hide());
        worker.removeCallbacksAndMessages(null);
        if (display != null) { display.release(); display = null; }
        if (latestFrame != null) { latestFrame.close(); latestFrame = null; }
        if (reader != null) { reader.close(); reader = null; }
        if (projection != null) { MediaProjection p = projection; projection = null; p.stop(); }
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    @Override public void onDestroy() {
        running = false;
        main.removeCallbacksAndMessages(null); countdown.hide();
        worker.post(() -> { finishCapture(); thread.quitSafely(); }); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
