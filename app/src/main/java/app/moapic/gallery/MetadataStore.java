package app.moapic.gallery;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public final class MetadataStore extends SQLiteOpenHelper {
    public MetadataStore(Context context) { super(context, "moapic.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE photos (uri TEXT PRIMARY KEY, favorite INTEGER NOT NULL DEFAULT 0, tags TEXT NOT NULL DEFAULT '', text TEXT NOT NULL DEFAULT '', fingerprint TEXT NOT NULL DEFAULT '')");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
    public void hydrate(Photo photo) {
        try (Cursor c = getReadableDatabase().query("photos", null, "uri=?", new String[]{photo.uri.toString()}, null, null, null)) {
            if (c.moveToFirst()) {
                photo.favorite = c.getInt(c.getColumnIndexOrThrow("favorite")) != 0;
                photo.tags = c.getString(c.getColumnIndexOrThrow("tags"));
                photo.searchable = photo.fingerprint.equals(c.getString(c.getColumnIndexOrThrow("fingerprint")));
                if (photo.searchable)
                    photo.text = c.getString(c.getColumnIndexOrThrow("text"));
            }
        }
    }
    public boolean indexed(Photo p) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM photos WHERE uri=? AND fingerprint=?", new String[]{p.uri.toString(), p.fingerprint})) { return c.moveToFirst(); }
    }
    private void ensure(Photo p) {
        ContentValues v = new ContentValues(); v.put("uri", p.uri.toString());
        getWritableDatabase().insertWithOnConflict("photos", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }
    public void annotate(Photo p) {
        ensure(p); ContentValues v = new ContentValues(); v.put("favorite", p.favorite); v.put("tags", p.tags);
        getWritableDatabase().update("photos", v, "uri=?", new String[]{p.uri.toString()});
    }
    public void saveText(Photo p, String text) {
        ensure(p); ContentValues v = new ContentValues(); v.put("text", text); v.put("fingerprint", p.fingerprint);
        getWritableDatabase().update("photos", v, "uri=?", new String[]{p.uri.toString()});
    }
    public void clear() { getWritableDatabase().delete("photos", null, null); }
    public void remove(String uri) { getWritableDatabase().delete("photos", "uri=?", new String[]{uri}); }
}
