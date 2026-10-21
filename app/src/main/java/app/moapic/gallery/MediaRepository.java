package app.moapic.gallery;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class MediaRepository {
    private final Context context;
    public MediaRepository(Context context) { this.context = context; }
    public List<Photo> all() {
        List<Photo> photos = new ArrayList<>();
        String[] columns = {"_id", "_display_name", "relative_path", "date_added", "date_modified", "_size"};
        try (Cursor c = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                columns, "is_pending=0", null, "date_added DESC, _id DESC")) {
            if (c != null) while (c.moveToNext()) {
                photos.add(new Photo(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)),
                    c.getString(1) == null ? "이미지" : c.getString(1), c.getString(2) == null ? "" : c.getString(2),
                    c.getLong(3), c.getLong(4), c.getLong(5)));
            }
        }
        return photos;
    }
    public Uri importImage(Uri source) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        String mime = resolver.getType(source);
        if (mime == null || !mime.startsWith("image/")) throw new IOException("이미지 파일만 추가할 수 있어요.");
        String ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        Uri target = createPending(resolver, "Saved_" + System.currentTimeMillis() + "." + (ext == null ? "img" : ext), "Pictures/Moapic/Imports/", mime);
        try {
            try (InputStream in = resolver.openInputStream(source); OutputStream out = resolver.openOutputStream(target)) {
                if (in == null || out == null) throw new IOException("이미지를 열 수 없어요.");
                byte[] buffer = new byte[65536]; int length;
                while ((length = in.read(buffer)) != -1) out.write(buffer, 0, length);
            }
            publish(resolver, target); return target;
        } catch (Exception error) {
            resolver.delete(target, null, null); throw new IOException("이미지를 저장하지 못했어요.", error);
        }
    }
    public static Uri createPending(ContentResolver resolver, String name, String folder, String mime) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name); values.put(MediaStore.Images.Media.MIME_TYPE, mime);
        values.put(MediaStore.Images.Media.RELATIVE_PATH, folder); values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("저장 공간을 확인해 주세요."); return uri;
    }
    public static void publish(ContentResolver resolver, Uri uri) {
        ContentValues values = new ContentValues(); values.put(MediaStore.Images.Media.IS_PENDING, 0);
        resolver.update(uri, values, null, null);
    }
}
