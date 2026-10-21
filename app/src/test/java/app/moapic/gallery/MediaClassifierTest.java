package app.moapic.gallery;

import org.junit.Test;
import static org.junit.Assert.*;

public class MediaClassifierTest {
    @Test public void recognizesSamsungAndPixelScreenshots() {
        assertEquals(MediaClassifier.SCREENSHOT, MediaClassifier.classify("Screenshot_20261021.png", "DCIM/Screenshots/"));
        assertEquals(MediaClassifier.SCREENSHOT, MediaClassifier.classify("image.png", "Pictures/Screenshots/"));
        assertEquals(MediaClassifier.SCREENSHOT, MediaClassifier.classify("스크린샷 1.png", "Pictures/"));
    }
    @Test public void downloadMatchingUsesWholeFolderSegments() {
        assertEquals(MediaClassifier.DOWNLOAD, MediaClassifier.classify("image.jpg", "Download/"));
        assertEquals(MediaClassifier.DOWNLOAD, MediaClassifier.classify("image.jpg", "Pictures/Moapic/Imports/"));
        assertFalse(MediaClassifier.isAutomatic("download-trip.jpg", "DCIM/Camera/"));
        assertFalse(MediaClassifier.isAutomatic("image.jpg", "Pictures/MyDownloads/"));
    }
    @Test public void cameraAndMessengerAreOptIn() {
        assertFalse(MediaClassifier.isAutomatic("IMG_123.jpg", "DCIM/Camera/"));
        assertFalse(MediaClassifier.isAutomatic("image.jpg", "Pictures/KakaoTalk/"));
    }
    @Test public void normalizesCaseAndBackslashes() {
        assertEquals(MediaClassifier.SCREENSHOT, MediaClassifier.classify("photo.png", "PICTURES\\SCREENSHOTS\\"));
        assertEquals(MediaClassifier.OTHER, MediaClassifier.classify(null, null));
    }
}
