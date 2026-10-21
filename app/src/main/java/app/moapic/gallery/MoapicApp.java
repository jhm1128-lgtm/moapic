package app.moapic.gallery;

import android.app.Application;
import android.os.*;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.*;
import java.util.concurrent.*;

public final class MoapicApp extends Application {
    public MetadataStore metadata;
    public MediaRepository media;
    public final ExecutorService indexWorker = Executors.newSingleThreadExecutor();
    public volatile boolean indexing;
    public volatile String indexStatus = "사진 속 숫자·영문·한글로 검색해 보세요";
    private final Set<String> attempted = new HashSet<>();
    private final Set<Runnable> listeners = new CopyOnWriteArraySet<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int batches;
    @Override public void onCreate() { super.onCreate(); metadata = new MetadataStore(this); media = new MediaRepository(this); ScrollCollector.cleanInterruptedCache(this); }
    public void addSearchListener(Runnable listener) { listeners.add(listener); }
    public void removeSearchListener(Runnable listener) { listeners.remove(listener); }
    private void changed() { main.post(() -> { for (Runnable listener : listeners) listener.run(); }); }
    public synchronized boolean prepareSearch(List<Photo> photos) {
        List<Photo> pending = new ArrayList<>();
        for (Photo p : photos) if (!p.searchable && attempted.add(p.uri + ":" + p.fingerprint)) pending.add(p);
        if (pending.isEmpty()) return false;
        batches++; indexing = true; indexStatus = "사진 속 숫자·영문·한글 검색 준비 중…"; changed();
        indexWorker.execute(() -> {
            int done = 0, errors = 0;
            try (TextRecognizer korean = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
                 TextRecognizer latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)) {
                for (Photo photo : pending) {
                    try { if (!metadata.indexed(photo)) metadata.saveText(photo, OcrScanner.read(this, photo, korean, latin)); }
                    catch (Exception | OutOfMemoryError e) { errors++; }
                    done++; indexStatus = "사진 검색 준비 중 · " + done + " / " + pending.size(); changed();
                }
            } catch (Exception e) { errors = pending.size(); }
            finally { synchronized (this) { batches--; indexing = batches > 0; } }
            indexStatus = indexing ? "새 사진의 검색을 준비 중…" : errors == 0 ? "숫자·영문·한글 검색 준비 완료" : "일부 사진은 검색 준비가 필요해요 · 설정에서 다시 시도";
            changed();
        }); return true;
    }
    public synchronized void retrySearch(List<Photo> photos) { if (indexing) return; attempted.clear(); prepareSearch(photos); }
    public synchronized void resetSearchAttempts() { if (!indexing) attempted.clear(); }
}
