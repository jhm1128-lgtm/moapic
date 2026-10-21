package app.moapic.gallery;

import android.net.Uri;

public final class Photo {
    public final Uri uri;
    public final String name, folder, kind, fingerprint;
    public final long added;
    public boolean favorite;
    public boolean searchable;
    public String tags = "", text = "";
    public Photo(Uri uri, String name, String folder, long added, long modified, long size) {
        this.uri = uri; this.name = name; this.folder = folder; this.added = added;
        this.kind = MediaClassifier.classify(name, folder);
        this.fingerprint = modified + ":" + size + ":search3";
    }
    public boolean matches(String query) {
        return TextSearch.matches(name + " " + tags + " " + text, query);
    }
}
