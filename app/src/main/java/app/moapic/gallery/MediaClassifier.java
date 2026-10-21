package app.moapic.gallery;

import java.text.Normalizer;
import java.util.Locale;

/** Folder heuristics, deliberately excluding ordinary camera photos. */
public final class MediaClassifier {
    public static final String SCREENSHOT = "스크린샷", DOWNLOAD = "다운로드", OTHER = "선택한 폴더";
    private MediaClassifier() {}
    public static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }
    public static String classify(String name, String path) {
        String n = normalize(name), p = "/" + normalize(path).replace('\\', '/');
        if (p.contains("/screenshots/") || p.contains("/screenshot/") || p.contains("/스크린샷/")
                || n.startsWith("screenshot") || n.startsWith("screen_shot") || n.startsWith("스크린샷"))
            return SCREENSHOT;
        if (p.contains("/download/") || p.contains("/downloads/") || p.contains("/moapic/imports/"))
            return DOWNLOAD;
        return OTHER;
    }
    public static boolean isAutomatic(String name, String path) { return !OTHER.equals(classify(name, path)); }
}
