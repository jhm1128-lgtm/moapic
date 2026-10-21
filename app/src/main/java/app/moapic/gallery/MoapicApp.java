package app.moapic.gallery;

import android.app.Application;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public final class MoapicApp extends Application {
    public MetadataStore metadata;
    public MediaRepository media;
    public final ExecutorService indexWorker = Executors.newSingleThreadExecutor();
    public volatile boolean indexing;
    public volatile String indexStatus = "이미지 속 글자도 찾아보세요";
    @Override public void onCreate() { super.onCreate(); metadata = new MetadataStore(this); media = new MediaRepository(this); }
    public synchronized boolean index(List<Photo> photos, Runnable changed) {
        if (indexing) return false;
        indexing = true;
        indexWorker.execute(() -> {
            int done = 0, errors = 0;
            try (TextRecognizer korean = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
                 TextRecognizer latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)) {
                for (Photo photo : photos) {
                    if (!metadata.indexed(photo)) {
                        try {
                            InputImage image = InputImage.fromFilePath(this, photo.uri);
                            String text = Tasks.await(korean.process(image)).getText() + "\n" + Tasks.await(latin.process(image)).getText();
                            metadata.saveText(photo, text);
                        } catch (Exception e) { errors++; }
                    }
                    done++; indexStatus = "글자 읽는 중 · " + done + " / " + photos.size();
                }
                indexStatus = errors == 0 ? "글자 검색 준비 완료 · " + done + "장" : "완료 · 읽지 못한 " + errors + "장은 다시 시도해 주세요";
            } catch (Exception e) { indexStatus = "글자를 읽지 못했어요. 다시 시도해 주세요"; }
            finally { indexing = false; changed.run(); }
        }); return true;
    }
}
