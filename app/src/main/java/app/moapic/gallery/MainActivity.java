package app.moapic.gallery;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.*;
import android.util.LruCache;
import android.util.Size;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int PURPLE = 0xFF6554C0, INK = 0xFF262337, MUTED = 0xFF807C91, BG = 0xFFF8F7FC;
    private static final int ACCESS = 20, PICK = 21, CAPTURE = 22, NOTIFY = 23, OVERLAY = 24;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService images = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getAllocationByteCount(); }
    };
    private MoapicApp app;
    private DeleteCoordinator deletion;
    private SharedPreferences prefs;
    private LinearLayout root, tabs, selectionBar;
    private TextView stats, permissionNote, empty, indexNote;
    private Button captureButton;
    private GridView grid;
    private EditText search;
    private PhotoAdapter adapter;
    private List<Photo> all = new ArrayList<>(), library = new ArrayList<>(), visible = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private String filter = "전체";
    private int generation;
    private boolean loaded;
    private boolean pendingCaptureAfterOverlay;
    private AlertDialog settingsDialog;
    private final Runnable reload = this::load;
    private final ContentObserver observer = new ContentObserver(main) {
        @Override public void onChange(boolean selfChange) { main.removeCallbacks(reload); main.postDelayed(reload, 500); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); app = (MoapicApp)getApplication(); prefs = getSharedPreferences("settings", MODE_PRIVATE);
        deletion = new DeleteCoordinator(this, worker, () -> { selected.clear(); cache.evictAll(); load(); });
        deletion.restore(state);
        pendingCaptureAfterOverlay = state != null && state.getBoolean("pendingOverlayCapture");
        buildUi();
        if (state != null) { filter = state.getString("filter", "전체"); search.setText(state.getString("query", "")); }
        getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer);
        handleShared(getIntent());
        if (state == null && Intent.ACTION_MAIN.equals(getIntent().getAction())) showIntro();
    }
    @Override protected void onResume() { super.onResume(); load(); deletion.resume(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("filter", filter); out.putString("query", search.getText().toString());
        deletion.save(out);
        out.putBoolean("pendingOverlayCapture", pendingCaptureAfterOverlay);
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleShared(intent); }
    @Override protected void onDestroy() {
        getContentResolver().unregisterContentObserver(observer); main.removeCallbacksAndMessages(null);
        worker.shutdownNow(); images.shutdownNow(); super.onDestroy();
    }
    private void buildUi() {
        root = column(); root.setFocusableInTouchMode(true); root.setBackgroundColor(BG); setContentView(root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(bars.left + dp(20), bars.top + dp(12), bars.right + dp(20), bars.bottom + dp(10));
            } else view.setPadding(insets.getSystemWindowInsetLeft() + dp(20), insets.getSystemWindowInsetTop() + dp(12),
                insets.getSystemWindowInsetRight() + dp(20), insets.getSystemWindowInsetBottom() + dp(10));
            return insets;
        });
        root.requestApplyInsets();
        LinearLayout brand = row();
        TextView logo = text("▣  모아픽", 23, INK); logo.setTypeface(null, Typeface.BOLD); brand.addView(logo, weight());
        brand.addView(iconButton(UiIcon.SETTINGS, "설정", PURPLE, v -> settings())); root.addView(brand);
        TextView eyebrow = text("YOUR LITTLE COLLECTION", 10, PURPLE); eyebrow.setLetterSpacing(.16f); add(root, eyebrow, 14);
        TextView title = text("이제 간편하게\n모아보아요.", 28, INK); title.setTypeface(null, Typeface.BOLD); title.setLineSpacing(dp(3), 1); add(root, title, 8);
        stats = text("나만의 이미지 보관함", 13, MUTED); add(root, stats, 8);
        LinearLayout actions = row();
        captureButton = iconLabel("화면 캡처", UiIcon.CAPTURE, true, v -> explainCapture());
        actions.addView(captureButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button add = iconLabel("이미지 추가", UiIcon.ADD, false, v -> pickImages());
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, dp(48), 1); ap.leftMargin = dp(8); actions.addView(add, ap); add(root, actions, 18);
        TextView tileTip = text("알림창에 스크린샷 버튼 추가  ›", 12, PURPLE);
        tileTip.setPadding(0, dp(10), 0, dp(10)); tileTip.setOnClickListener(v -> addCaptureTile()); add(root, tileTip, 0);
        search = new EditText(this); search.setSingleLine(); search.setTextSize(14); search.setTextColor(INK);
        search.setHintTextColor(MUTED); search.setHint("⌕  파일명, 태그, 이미지 속 글자 검색");
        search.setBackground(shape(0xFFFFFFFF, 16)); search.setPadding(dp(16), 0, dp(12), 0);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(50)); sp.topMargin = dp(4); root.addView(search, sp);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(); }
            public void afterTextChanged(Editable e) {}
        });
        HorizontalScrollView scroll = new HorizontalScrollView(this); scroll.setHorizontalScrollBarEnabled(false);
        tabs = row(); scroll.addView(tabs); add(root, scroll, 12); drawTabs();
        permissionNote = text("", 12, PURPLE); permissionNote.setPadding(dp(12), dp(10), dp(12), dp(10));
        permissionNote.setBackground(shape(0xFFECE8FA, 12)); permissionNote.setOnClickListener(v -> requestAccess()); add(root, permissionNote, 10);
        LinearLayout indexRow = row(); indexNote = text("이미지 속 글자도 찾아보세요", 11, MUTED); indexRow.addView(indexNote, weight());
        indexRow.addView(iconLabel("글자 읽기", UiIcon.TEXT, false, v -> index())); add(root, indexRow, 4);
        FrameLayout content = new FrameLayout(this); root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        grid = new GridView(this); grid.setNumColumns(3); grid.setHorizontalSpacing(dp(8)); grid.setVerticalSpacing(dp(8));
        grid.setClipToPadding(false); grid.setPadding(0, dp(8), 0, dp(8)); grid.setSelector(android.R.color.transparent);
        adapter = new PhotoAdapter(); grid.setAdapter(adapter); content.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        empty = text("", 14, MUTED); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(20), dp(20), dp(20), dp(20));
        content.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        grid.setOnItemClickListener((parent, view, position, id) -> {
            Photo photo = visible.get(position); if (!selected.isEmpty()) toggle(photo); else detail(photo);
        });
        grid.setOnItemLongClickListener((parent, view, position, id) -> { toggle(visible.get(position)); return true; });
        selectionBar = row(); root.addView(selectionBar); drawSelection();
        TextView footer = text("기기에만 보관  ·  로그인 없이 가볍게", 10, MUTED); footer.setGravity(Gravity.CENTER); add(root, footer, 6);
    }
    private void drawTabs() {
        tabs.removeAllViews();
        for (String label : new String[]{"전체", "스크린샷", "다운로드", "즐겨찾기"}) {
            Button tab = button(label, filter.equals(label), v -> { filter = label; selected.clear(); drawTabs(); applyFilter(); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(38)); p.rightMargin = dp(6); tabs.addView(tab, p);
        }
    }
    private boolean fullAccess() {
        return checkSelfPermission(Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }
    private boolean partialAccess() {
        return Build.VERSION.SDK_INT >= 34 && checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED;
    }
    private void requestAccess() {
        new AlertDialog.Builder(this).setTitle("사진을 모으려면 접근이 필요해요")
            .setMessage("스크린샷과 다운로드 이미지를 자동으로 찾기 위해 사진 접근을 요청해요. ‘모든 사진 허용’을 선택하면 새 이미지도 모아볼 수 있어요. 선택한 사진만 허용해도 사용 가능해요. 원본은 복사하거나 서버로 보내지 않아요.")
            .setPositiveButton("사진 접근 허용", (d, w) -> {
                if (Build.VERSION.SDK_INT >= 34) requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED}, ACCESS);
                else requestPermissions(new String[]{Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE}, ACCESS);
            }).setNeutralButton("기기 설정", (d,w) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))))
            .setNegativeButton("나중에", null).show();
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == NOTIFY) beginCapture(); else load();
    }
    private void load() {
        if (isDestroyed()) return;
        int ticket = ++generation;
        boolean full = fullAccess(), partial = partialAccess();
        permissionNote.setVisibility(full ? View.GONE : View.VISIBLE);
        permissionNote.setText(partial ? "선택한 사진만 볼 수 있어요 · 접근 범위 변경 ›" : "자동으로 모으려면 사진 접근을 허용해 주세요 ›");
        worker.execute(() -> {
            try {
                List<Photo> photos = app.media.all();
                Set<String> folders = prefs.getStringSet("folders", Collections.emptySet());
                for (Photo p : photos) if (included(p, folders)) app.metadata.hydrate(p);
                main.post(() -> { if (!isDestroyed() && ticket == generation) { all = photos; loaded = true; rebuildLibrary(); } });
            } catch (Exception error) {
                main.post(() -> { if (!isDestroyed() && ticket == generation) { all = new ArrayList<>(); loaded = true; rebuildLibrary(); empty.setText("사진을 읽지 못했어요.\n접근 권한을 확인한 후 다시 열어 주세요."); } });
            }
        });
    }
    private void rebuildLibrary() {
        Set<String> folders = prefs.getStringSet("folders", Collections.emptySet());
        library = new ArrayList<>();
        for (Photo p : all) {
            if (included(p, folders)) library.add(p);
        }
        Set<String> available = new HashSet<>(); for (Photo p : library) available.add(p.uri.toString()); selected.retainAll(available);
        stats.setText(getString(R.string.library_count, library.size()));
        indexNote.setText(app.indexStatus); drawTabs(); applyFilter();
    }
    private boolean included(Photo p, Set<String> folders) {
        return MediaClassifier.SCREENSHOT.equals(p.kind) && prefs.getBoolean("screenshots", true)
            || MediaClassifier.DOWNLOAD.equals(p.kind) && prefs.getBoolean("downloads", true)
            || folders.contains(p.folder);
    }
    private void applyFilter() {
        if (adapter == null) return;
        visible = new ArrayList<>(); String query = search.getText().toString().trim();
        for (Photo p : library) {
            boolean matchesTab = filter.equals("전체") || filter.equals(p.kind) || filter.equals("즐겨찾기") && p.favorite;
            if (matchesTab && p.matches(query)) visible.add(p);
        }
        empty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(!loaded ? "이미지를 모으고 있어요…" : !query.isEmpty() ? "검색 결과가 없어요.\n‘글자 읽기’를 누르면 이미지 속 문구도 검색돼요."
            : library.isEmpty() ? "아직 모아둔 이미지가 없어요.\n\n사진 접근을 허용하거나 ‘이미지 추가’를 눌러보세요.\n다른 폴더의 이미지는 설정에서 포함할 수 있어요."
            : "이곳에 모인 이미지가 없어요.\n이미지를 길게 눌러 정리해 보세요.");
        adapter.notifyDataSetChanged(); drawSelection();
    }
    private void toggle(Photo photo) { String id = photo.uri.toString(); if (!selected.add(id)) selected.remove(id); applyFilter(); }
    private List<Photo> selectedPhotos() {
        List<Photo> result = new ArrayList<>(); for (Photo p : library) if (selected.contains(p.uri.toString())) result.add(p); return result;
    }
    private void drawSelection() {
        selectionBar.removeAllViews(); selectionBar.setVisibility(selected.isEmpty() ? View.GONE : View.VISIBLE);
        if (selected.isEmpty()) return;
        selectionBar.addView(text(selected.size() + "장", 13, PURPLE), weight());
        selectionBar.addView(button("★", false, v -> {
            List<Photo> photos = selectedPhotos(); boolean target = photos.stream().anyMatch(p -> !p.favorite);
            for (Photo p : photos) { p.favorite = target; app.metadata.annotate(p); } selected.clear(); applyFilter();
        }));
        selectionBar.addView(button("태그", false, v -> editTags(selectedPhotos())));
        selectionBar.addView(button("공유", false, v -> share(selectedPhotos())));
        selectionBar.addView(iconButton(UiIcon.DELETE, "선택한 사진 삭제", 0xFFBE4A62, v -> deletion.request(selectedPhotos())));
        selectionBar.addView(button("해제", false, v -> { selected.clear(); applyFilter(); }));
    }
    private void index() {
        if (library.isEmpty()) { toast("먼저 이미지를 추가해 주세요."); return; }
        if (app.indexing) { toast(app.indexStatus); indexNote.setText(app.indexStatus); return; }
        WeakReference<MainActivity> activity = new WeakReference<>(this);
        app.index(new ArrayList<>(library), () -> {
            MainActivity current = activity.get();
            if (current != null && !current.isDestroyed()) current.main.post(() -> { if (!current.isDestroyed()) { current.load(); current.toast(current.app.indexStatus); } });
        });
        indexNote.setText("이미지 속 글자를 기기에서 읽고 있어요…");
        toast("사진을 기기에서 읽어요. 앱을 닫으면 중단될 수 있어요.");
    }
    private void folders() {
        TreeSet<String> unique = new TreeSet<>(); for (Photo p : all) unique.add(p.folder);
        String[] paths = unique.toArray(new String[0]);
        if (paths.length == 0) { requestAccess(); return; }
        Set<String> chosen = new HashSet<>(prefs.getStringSet("folders", Collections.emptySet()));
        boolean[] checked = new boolean[paths.length]; for (int i = 0; i < paths.length; i++) checked[i] = chosen.contains(paths[i]);
        new AlertDialog.Builder(this).setTitle("추가로 모을 폴더")
            .setMultiChoiceItems(paths, checked, (d, index, value) -> { if (value) chosen.add(paths[index]); else chosen.remove(paths[index]); })
            .setPositiveButton("적용", (d,w) -> { prefs.edit().putStringSet("folders", chosen).apply(); load(); })
            .setNegativeButton("취소", null).show();
    }
    private void settings() {
        LinearLayout layout = column(); layout.setPadding(dp(24), dp(8), dp(24), dp(12));
        for (String key : new String[]{"screenshots", "downloads"}) {
            CheckBox option = new CheckBox(this); option.setText(key.equals("screenshots") ? "스크린샷 자동 모으기" : "다운로드 자동 모으기");
            option.setTextSize(15); option.setPadding(0, dp(12), 0, dp(12)); option.setChecked(prefs.getBoolean(key, true));
            option.setOnCheckedChangeListener((b, value) -> { prefs.edit().putBoolean(key, value).apply(); load(); }); layout.addView(option);
        }
        add(layout, button("추가로 모을 폴더 선택", false, v -> folders()), 8);
        add(layout, button("사진 접근 범위 변경", false, v -> requestAccess()), 8);
        add(layout, iconLabel("알림창 스크린샷 버튼 추가", UiIcon.CAPTURE, false, v -> addCaptureTile()), 8);
        add(layout, iconLabel("홈 화면 캡처 바로가기", UiIcon.SHORTCUT, false, v -> addCaptureShortcut()), 8);
        add(layout, button("화면 위 카운트다운 표시 설정", false, v -> openCountdownPermission()), 8);
        add(layout, button("시작 화면 다시 보기", false, v -> { if (settingsDialog != null) settingsDialog.dismiss(); showIntro(); }), 8);
        add(layout, text("카카오톡 등 다른 앱에서 저장한 이미지는 해당 폴더를 추가해 주세요. 다운로드 여부는 저장 위치를 기준으로 구분해요.", 12, MUTED), 12);
        add(layout, text("개인정보\n사진·태그·인식한 글자는 기기에 저장됩니다. 계정, 광고, 분석 서버는 사용하지 않아요. 앱을 지우면 태그와 즐겨찾기도 지워집니다. 공유하면 선택한 앱으로 원본이 전달돼요.", 12, MUTED), 16);
        add(layout, button("태그·즐겨찾기·검색 색인 초기화", false, v -> {
            if (app.indexing) { toast("글자 읽기가 끝난 후 초기화해 주세요."); return; }
            new AlertDialog.Builder(this).setTitle("정리 정보를 초기화할까요?").setMessage("태그, 즐겨찾기, 읽어둔 글자를 지워요. 사진 원본은 유지됩니다.")
                .setNegativeButton("취소", null).setPositiveButton("초기화", (d,w) -> { app.metadata.clear(); cache.evictAll(); app.indexStatus = "이미지 속 글자도 찾아보세요"; load(); }).show();
        }), 12);
        add(layout, text("모아픽 0.2.0 · 가볍게 모아보아요", 11, MUTED), 14);
        ScrollView scroll = new ScrollView(this); scroll.addView(layout);
        settingsDialog = new AlertDialog.Builder(this).setTitle("내 보관함 설정").setView(scroll).setPositiveButton("닫기", null).show();
    }
    private void editTags(List<Photo> photos) {
        if (photos.isEmpty()) return;
        EditText input = new EditText(this); input.setHint("예: 여행, 쇼핑, 나중에 보기"); input.setSingleLine(); input.setMaxLines(1);
        if (photos.size() == 1) input.setText(photos.get(0).tags);
        new AlertDialog.Builder(this).setTitle(photos.size() == 1 ? "태그 수정" : photos.size() + "장에 같은 태그 지정")
            .setView(input).setMessage("쉼표로 구분해 주세요. 기존 태그를 입력한 내용으로 바꿉니다.")
            .setNegativeButton("취소", null).setPositiveButton("저장", (d,w) -> {
                for (Photo p : photos) { p.tags = input.getText().toString().trim(); app.metadata.annotate(p); }
                selected.clear(); applyFilter();
            }).show();
    }
    private void share(List<Photo> photos) {
        if (photos.isEmpty()) return;
        Intent intent = new Intent(photos.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE); intent.setType("image/*");
        ArrayList<Uri> uris = new ArrayList<>(); for (Photo p : photos) uris.add(p.uri);
        if (uris.size() == 1) intent.putExtra(Intent.EXTRA_STREAM, uris.get(0)); else intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        ClipData clip = ClipData.newUri(getContentResolver(), "이미지", uris.get(0)); for (int i=1; i<uris.size(); i++) clip.addItem(new ClipData.Item(uris.get(i)));
        intent.setClipData(clip); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(Intent.createChooser(intent, "이미지 공유")); } catch (Exception e) { toast("공유할 수 있는 앱이 없어요."); }
    }
    private void detail(Photo photo) {
        LinearLayout layout = column(); layout.setPadding(dp(12), dp(8), dp(12), dp(8));
        ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        layout.addView(image, new LinearLayout.LayoutParams(-1, dp(330))); bindImage(image, photo, 1400);
        add(layout, text(new SimpleDateFormat("yyyy.MM.dd  HH:mm", Locale.KOREA).format(new Date(photo.added * 1000)) + " · " + photo.kind, 12, MUTED), 10);
        add(layout, text(photo.folder, 11, MUTED), 4);
        if (!photo.tags.isEmpty()) add(layout, text(photo.tags, 13, PURPLE), 10);
        LinearLayout actions = row();
        Button star = button(photo.favorite ? "★ 저장됨" : "☆ 즐겨찾기", false, null);
        star.setOnClickListener(v -> { photo.favorite = !photo.favorite; app.metadata.annotate(photo); star.setText(photo.favorite ? "★ 저장됨" : "☆ 즐겨찾기"); applyFilter(); });
        actions.addView(star, weight()); actions.addView(button("태그", false, v -> editTags(Collections.singletonList(photo))));
        actions.addView(button("공유", false, v -> share(Collections.singletonList(photo)))); add(layout, actions, 12);
        TextView recognized = text(photo.text.isEmpty() ? "‘글자 읽기’를 실행하면 이 이미지의 글자로 검색할 수 있어요." : photo.text, 12, MUTED);
        recognized.setTextIsSelectable(true); add(layout, recognized, 12);
        ScrollView scroll = new ScrollView(this); scroll.addView(layout);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(photo.name).setView(scroll).setPositiveButton("닫기", null).create();
        Button delete = iconLabel("사진 삭제", UiIcon.DELETE, false, v -> { dialog.dismiss(); deletion.request(Collections.singletonList(photo)); });
        delete.setTextColor(0xFFBE4A62); add(layout, delete, 12); dialog.show();
    }
    private void pickImages() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.setType("image/*"); intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); startActivityForResult(intent, PICK);
    }
    private void handleShared(Intent intent) {
        if (intent == null) return;
        ArrayList<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(intent.getAction())) { Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM); if (uri != null) uris.add(uri); }
        else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) { ArrayList<Uri> list = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM); if (list != null) uris.addAll(list); }
        if (!uris.isEmpty()) { intent.setAction(null); importImages(uris); }
    }
    private void importImages(List<Uri> uris) {
        toast("이미지를 추가하고 있어요…");
        worker.execute(() -> {
            int success = 0;
            for (Uri uri : uris) {
                if (uri == null || !"content".equals(uri.getScheme())) continue;
                try { app.media.importImage(uri); success++; } catch (Exception ignored) {}
            }
            int count = success;
            main.post(() -> { if (!isDestroyed()) { toast(count + "장 추가됨" + (count < uris.size() ? " · 일부 이미지를 추가하지 못했어요" : "")); load(); } });
        });
    }
    private void explainCapture() {
        if (CaptureService.running) { toast("이미 화면을 캡처하고 있어요. 잠시 후 다시 시도해 주세요."); return; }
        new AlertDialog.Builder(this).setTitle("다른 화면을 캡처해 모으기")
            .setMessage("화면 공유를 허용하면 모아픽이 뒤로 이동합니다. 7초 안에 캡처할 화면을 열어 주세요. 한 장을 저장한 뒤 화면 공유는 자동으로 종료돼요.\n\n다른 앱으로 이동해 캡처하려면 시스템 창에서 ‘전체 화면’을 선택해 주세요. 보안이 적용된 앱은 캡처되지 않을 수 있어요.")
            .setNegativeButton("취소", null).setPositiveButton("캡처 시작", (d,w) -> {
                if (!Settings.canDrawOverlays(this)) {
                    new AlertDialog.Builder(this).setTitle("카운트다운을 화면에 표시할까요?")
                        .setMessage("다른 앱에서도 7 → 6 → 5처럼 남은 시간을 보려면 ‘다른 앱 위에 표시’를 허용해 주세요. 숫자는 캡처 전에 사라져요. 허용하지 않으면 알림에서 남은 시간을 표시합니다.")
                        .setPositiveButton("표시 허용 설정", (dialog, which) -> { pendingCaptureAfterOverlay = true; openCountdownPermission(); })
                        .setNeutralButton("알림만 사용", (dialog, which) -> prepareCapture()).setNegativeButton("취소", null).show();
                } else prepareCapture();
            }).show();
    }
    private void openCountdownPermission() {
        try { startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), OVERLAY); }
        catch (Exception e) { pendingCaptureAfterOverlay = false; toast("기기 설정에서 모아픽의 ‘다른 앱 위에 표시’를 허용해 주세요."); }
    }
    private void prepareCapture() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFY);
        else beginCapture();
    }
    private void beginCapture() {
        startActivityForResult(((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).createScreenCaptureIntent(), CAPTURE);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == DeleteCoordinator.REQUEST) { deletion.result(result); return; }
        if (request == OVERLAY) {
            toast(Settings.canDrawOverlays(this) ? "화면 위에 카운트다운을 표시할 수 있어요." : "카운트다운은 캡처 알림에 표시돼요.");
            if (pendingCaptureAfterOverlay) { pendingCaptureAfterOverlay = false; prepareCapture(); }
            return;
        }
        if (result != RESULT_OK || data == null) return;
        if (request == CAPTURE) {
            Intent service = new Intent(this, CaptureService.class).putExtra("code", result).putExtra("data", data);
            startForegroundService(service); moveTaskToBack(true); toast("7초 뒤 한 장을 캡처해요. 원하는 화면으로 이동해 주세요.");
        } else if (request == PICK) {
            ArrayList<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) for (int i=0; i<data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
            else if (data.getData() != null) uris.add(data.getData());
            importImages(uris);
        }
    }
    private void bindImage(ImageView view, Photo photo, int size) {
        String key = photo.uri + ":" + photo.fingerprint + ":" + size; view.setTag(key); view.setImageDrawable(null);
        Bitmap existing = cache.get(key); if (existing != null) { view.setImageBitmap(existing); return; }
        if (images.isShutdown()) return;
        images.execute(() -> {
            try {
                if (!key.equals(view.getTag())) return;
                Bitmap bitmap = getContentResolver().loadThumbnail(photo.uri, new Size(size, size), null);
                cache.put(key, bitmap);
                main.post(() -> { if (!isDestroyed() && key.equals(view.getTag())) view.setImageBitmap(bitmap); });
            } catch (Exception ignored) { /* Revoked access and removed photos are handled by the next refresh. */ }
        });
    }
    private final class PhotoAdapter extends BaseAdapter {
        public int getCount() { return visible.size(); }
        public Object getItem(int i) { return visible.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int position, View recycled, ViewGroup parent) {
            Photo photo = visible.get(position);
            FrameLayout card;
            ImageView image;
            TextView badge;
            if (recycled instanceof FrameLayout) {
                card = (FrameLayout)recycled; image = (ImageView)card.getChildAt(0); badge = (TextView)card.getChildAt(1);
            } else {
                card = new FrameLayout(MainActivity.this); card.setBackground(shape(0xFFEDEAF4, 14)); card.setClipToOutline(true);
                image = new ImageView(MainActivity.this); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                card.addView(image, new FrameLayout.LayoutParams(-1, -1));
                badge = text("", 13, 0xFFFFFFFF); badge.setGravity(Gravity.CENTER);
                badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.TOP | Gravity.END);
                bp.setMargins(dp(6), dp(6), dp(6), dp(6)); card.addView(badge, bp);
            }
            card.setContentDescription(photo.name + ", " + photo.kind + (photo.favorite ? ", 즐겨찾기" : "") + (selected.contains(photo.uri.toString()) ? ", 선택됨" : ""));
            int width = Math.max(dp(72), (grid.getWidth() - dp(16)) / 3);
            card.setLayoutParams(new AbsListView.LayoutParams(-1, (int)(width * 1.35f)));
            bindImage(image, photo, 360);
            badge.setText(selected.contains(photo.uri.toString()) ? "✓" : photo.favorite ? "★" : photo.kind.equals("스크린샷") ? "▣" : "↓");
            badge.setBackground(shape(selected.contains(photo.uri.toString()) ? PURPLE : 0xAA262337, 8));
            return card;
        }
    }
    private int dp(float size) { return Math.round(size * getResources().getDisplayMetrics().density); }
    private void addCaptureTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                getSystemService(StatusBarManager.class).requestAddTileService(new ComponentName(this, CaptureTileService.class), "모아픽 캡처",
                    Icon.createWithResource(this, R.drawable.ic_capture), getMainExecutor(), result -> {
                        if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED || result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED)
                            toast("알림창을 두 번 내려 ‘모아픽 캡처’를 눌러보세요.");
                        else toast("알림창의 버튼 편집에서 ‘모아픽 캡처’를 추가할 수 있어요.");
                    });
            } catch (Exception e) { tileInstructions(); }
        } else tileInstructions();
    }
    private void tileInstructions() {
        new AlertDialog.Builder(this).setTitle("알림창에 캡처 버튼 추가")
            .setMessage("화면 위에서 아래로 두 번 내려 빠른 설정을 열어 주세요. 편집(연필)에서 ‘모아픽 캡처’를 찾아 버튼 목록으로 옮기면 돼요.\n\n버튼을 누르면 시스템 화면 공유 허용을 거쳐 화면 한 장을 저장해요.")
            .setPositiveButton("확인", null).show();
    }
    private void addCaptureShortcut() {
        android.content.pm.ShortcutManager manager = getSystemService(android.content.pm.ShortcutManager.class);
        if (manager == null || !manager.isRequestPinShortcutSupported()) { toast("이 홈 화면에서는 바로가기 추가를 지원하지 않아요."); return; }
        android.content.pm.ShortcutInfo shortcut = new android.content.pm.ShortcutInfo.Builder(this, "capture")
            .setShortLabel("모아픽 캡처").setIcon(Icon.createWithResource(this, R.drawable.ic_launcher))
            .setIntent(new Intent(this, CaptureActivity.class).setAction(Intent.ACTION_VIEW)).build();
        manager.requestPinShortcut(shortcut, null);
    }
    private void showIntro() {
        FrameLayout cover = new FrameLayout(this); cover.setBackgroundColor(BG); cover.setClickable(true);
        LinearLayout presentation = column(); presentation.setGravity(Gravity.CENTER); presentation.setPadding(dp(28), dp(36), dp(28), dp(36));
        TextView name = text("모아픽", 40, PURPLE); name.setTypeface(null, Typeface.BOLD); name.setGravity(Gravity.CENTER); presentation.addView(name);
        TextView small = text("나만의 작은 이미지 보관함", 13, MUTED); small.setGravity(Gravity.CENTER); add(presentation, small, 8);
        ImageView photo = new ImageView(this); photo.setImageResource(R.drawable.splash_photo); photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photo.setContentDescription("사진들을 가지런히 모아 둔 모아픽 타이틀 사진");
        LinearLayout.LayoutParams art = new LinearLayout.LayoutParams(-1, 0, 1); art.topMargin = dp(16); art.bottomMargin = dp(16); presentation.addView(photo, art);
        TextView slogan = text("이제 간편하게 모아보아요", 20, INK); slogan.setTypeface(null, Typeface.BOLD); slogan.setGravity(Gravity.CENTER); add(presentation, slogan, 8);
        add(presentation, button("바로 시작하기", true, v -> dismissIntro(cover)), 24);
        cover.addView(presentation, new FrameLayout.LayoutParams(-1, -1)); addContentView(cover, new ViewGroup.LayoutParams(-1, -1));
        main.postDelayed(() -> { if (!isDestroyed()) dismissIntro(cover); }, 2200);
    }
    private void dismissIntro(FrameLayout cover) {
        if (cover.getParent() == null) return;
        cover.animate().alpha(0f).setDuration(220).withEndAction(() -> { if (cover.getParent() instanceof ViewGroup) ((ViewGroup)cover.getParent()).removeView(cover); }).start();
    }
    private Button iconLabel(String label, int kind, boolean primary, View.OnClickListener click) {
        Button b = button(label, primary, click); UiIcon icon = new UiIcon(kind, primary ? Color.WHITE : PURPLE);
        icon.setBounds(0, 0, dp(23), dp(23)); b.setCompoundDrawables(icon, null, null, null); b.setCompoundDrawablePadding(dp(7)); return b;
    }
    private Button iconButton(int kind, String description, int color, View.OnClickListener click) {
        Button b = button("", false, click); b.setContentDescription(description); UiIcon icon = new UiIcon(kind, color);
        icon.setBounds(0, 0, dp(25), dp(25)); b.setCompoundDrawables(icon, null, null, null);
        b.setPadding(dp(12), dp(10), dp(11), dp(10)); b.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48))); return b;
    }
    private TextView text(String value, int size, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view; }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -2, 1); }
    private void add(LinearLayout parent, View child, int top) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(top); parent.addView(child, p); }
    private GradientDrawable shape(int color, int radius) { GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape; }
    private Button button(String label, boolean primary, View.OnClickListener click) {
        Button button = new Button(this); button.setText(label); button.setTextSize(12); button.setAllCaps(false);
        button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(dp(38)); button.setMinimumHeight(dp(38));
        button.setPadding(dp(13), dp(7), dp(13), dp(7)); button.setTextColor(primary ? 0xFFFFFFFF : PURPLE);
        button.setBackground(shape(primary ? PURPLE : 0xFFEDE9F8, 12)); button.setOnClickListener(click); return button;
    }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
}
