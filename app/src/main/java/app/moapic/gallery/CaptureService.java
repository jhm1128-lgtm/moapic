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

/** One user-consented session for a screen, selected region, or manually scrolled page. */
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
    private CaptureSelectionOverlay selection;
    private ScrollCaptureControls controls;
    private String mode="screen";
    private Rect scrollRegion;
    private Bitmap selectionFrame;
    private ScrollCollector collector;
    private boolean savingLong;
    private boolean firstFrameTaken;
    private int frameRequest;

    @Override public void onCreate() {
        super.onCreate();
        countdown = new CountdownOverlay(this);
        selection=new CaptureSelectionOverlay(this);controls=new ScrollCaptureControls(this);
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL, "화면 캡처", NotificationManager.IMPORTANCE_LOW));
        thread = new HandlerThread("moapic-capture"); thread.start(); worker = new Handler(thread.getLooper());
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "cancel".equals(intent.getAction())) { worker.post(this::finishCapture); return START_NOT_STICKY; }
        if (running) return START_NOT_STICKY;
        running = true;
        mode=intent==null?"screen":intent.getStringExtra("mode");if(mode==null||!mode.equals("area")&&!mode.equals("long"))mode="screen";
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
                            if(scrollRegion!=null){saveLongAndFinish();return;}
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
                worker.postDelayed(()->{if(!finished){if(collector!=null&&collector.count()>0)saveLongAndFinish();else finishCapture();}},300000);
                worker.postDelayed(() -> {
                    if (!firstFrameTaken && !finished) { announce("캡처하지 못했어요. 화면 공유 상태를 확인해 주세요."); finishCapture(); }
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
                        worker.postDelayed(CaptureService.this::requestFreshFrame,160);
                    });
                }
            }
        };
        tick.run();
    }
    private void requestFreshFrame(){
        if(finished||display==null)return;
        try{
            if(latestFrame!=null){latestFrame.close();latestFrame=null;}
            ImageReader old=reader;reader=createReader();saved=false;armed=true;display.setSurface(reader.getSurface());if(old!=null)old.close();
            int request=++frameRequest;
            worker.postDelayed(()->{if(!finished&&request==frameRequest&&armed&&!saved){armed=false;if(collector!=null){toast("화면을 받지 못했어요. 화면 추가를 다시 눌러 주세요.");showScrollControls();}else{announce("화면을 받지 못했어요. 다시 시도해 주세요.");finishCapture();}}},5000);
        }catch(Exception e){announce("캡처하지 못했어요. 다시 시도해 주세요.");finishCapture();}
    }
    private ImageReader createReader() {
        ImageReader next = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
        next.setOnImageAvailableListener(source -> {
            if(source!=reader||finished)return;
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
                saved = true;armed=false;firstFrameTaken=true;
                Image.Plane plane = image.getPlanes()[0];
                int stride = plane.getPixelStride(); int paddedWidth = plane.getRowStride() / stride;
                Bitmap padded = Bitmap.createBitmap(paddedWidth, image.getHeight(), Bitmap.Config.ARGB_8888);
                plane.getBuffer().rewind(); padded.copyPixelsFromBuffer(plane.getBuffer());
                Bitmap bitmap = Bitmap.createBitmap(padded, 0, 0, image.getWidth(), image.getHeight());
                if(bitmap!=padded)padded.recycle();
                if(mode.equals("screen")){
                    try{save(bitmap);announce("스크린샷을 모아픽에 저장했어요");}finally{bitmap.recycle();}finishCapture();
                }else if(scrollRegion==null){
                    selectionFrame=bitmap;
                    main.post(()->{if(!running)return;selection.show(bitmap,mode.equals("long"),rect->{selection.hide();worker.post(()->chooseRegion(rect));},()->{selection.hide();worker.post(this::finishCapture);});});
                }else{
                    try{addScrollFrame(bitmap);}finally{bitmap.recycle();}
                }
        } catch (Exception|OutOfMemoryError error) { announce("사진을 처리하지 못했어요. 저장 공간을 확인하고 다시 시도해 주세요.");finishCapture(); }
        finally { image.close(); }
    }
    private void chooseRegion(Rect region){
        if(finished||selectionFrame==null)return;Bitmap frame=selectionFrame;selectionFrame=null;
        try{
            if(mode.equals("area")){Bitmap crop=Bitmap.createBitmap(frame,region.left,region.top,region.width(),region.height());try{save(crop);announce("선택한 부분을 원본 화질로 저장했어요");}finally{if(crop!=frame)crop.recycle();}finishCapture();}
            else{scrollRegion=new Rect(region);collector=new ScrollCollector(this);addScrollFrame(frame);}
        }catch(Exception|OutOfMemoryError e){announce("선택한 부분을 저장하지 못했어요. 다시 시도해 주세요.");finishCapture();}
        finally{frame.recycle();}
    }
    private void addScrollFrame(Bitmap frame){
        Bitmap crop=null;
        try{
            if(scrollRegion.right>frame.getWidth()||scrollRegion.bottom>frame.getHeight())throw new java.io.IOException("화면 크기가 바뀌었어요. 회전하지 않고 캡처해 주세요.");
            crop=Bitmap.createBitmap(frame,scrollRegion.left,scrollRegion.top,scrollRegion.width(),scrollRegion.height());int rows=collector.add(crop);
            toast(rows==0?"같은 화면이에요. 반 화면 정도 아래로 내려 주세요.":collector.count()+"장 연결됨 · 아래로 스크롤한 뒤 화면 추가");
        }catch(Exception|OutOfMemoryError e){toast(e instanceof java.io.IOException?e.getMessage():"화면을 추가하지 못했어요. 다시 시도해 주세요.");}
        finally{if(crop!=null&&crop!=frame)crop.recycle();showScrollControls();}
    }
    private void showScrollControls(){
        if(finished)return;int count=collector==null?0:collector.count();
        main.post(()->{if(!running)return;controls.show(count,()->{controls.hide();worker.postDelayed(this::requestFreshFrame,160);},()->{controls.hide();worker.post(this::saveLongAndFinish);},()->{controls.hide();worker.post(this::finishCapture);});});
    }
    private void saveLongAndFinish(){
        if(finished||savingLong)return;savingLong=true;main.post(()->{controls.hide();selection.hide();});
        try{if(collector!=null&&collector.count()>0){toast("긴 사진을 저장하고 있어요…");collector.save();announce("긴 페이지를 한 장의 사진으로 저장했어요");}else announce("추가한 화면이 없어 긴 캡처를 종료했어요");}
        catch(Exception|OutOfMemoryError e){announce("긴 사진을 저장하지 못했어요. 저장 공간을 확인해 주세요.");}
        finally{finishCapture();}
    }
    private void toast(String text){main.post(()->android.widget.Toast.makeText(this,text,android.widget.Toast.LENGTH_LONG).show());}
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
        Notification.Builder b = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_capture)
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
        Bitmap pending=selectionFrame;selectionFrame=null;
        main.post(() -> {countdown.hide();selection.hide();controls.hide();if(pending!=null&&!pending.isRecycled())pending.recycle();});
        if(collector!=null){collector.close();collector=null;}
        worker.removeCallbacksAndMessages(null);
        if (display != null) { display.release(); display = null; }
        if (latestFrame != null) { latestFrame.close(); latestFrame = null; }
        if (reader != null) { reader.close(); reader = null; }
        if (projection != null) { MediaProjection p = projection; projection = null; p.stop(); }
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    @Override public void onDestroy() {
        running = false;
        main.removeCallbacksAndMessages(null);countdown.hide();selection.hide();controls.hide();
        worker.post(() -> { finishCapture(); thread.quitSafely(); }); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
