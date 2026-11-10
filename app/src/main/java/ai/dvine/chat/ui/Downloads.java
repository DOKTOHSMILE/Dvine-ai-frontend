package ai.dvine.chat.ui;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.IOException;
import java.io.OutputStream;

/** Saves a file the user asked for into Downloads/DVINE, where the Files app and other apps find it. */
public final class Downloads {
    private Downloads() {
    }

    /** Writes it; returns where it is ("Downloads/DVINE/bicycle.glb") and its address for sharing. */
    public static Uri save(Context c, String name, String mime, byte[] bytes) throws IOException {
        ContentValues v = new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME, name);
        v.put(MediaStore.Downloads.MIME_TYPE, mime);
        v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Ibiko");
        Uri uri = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (uri == null) {
            throw new IOException("there's no Downloads folder");
        }
        try (OutputStream o = c.getContentResolver().openOutputStream(uri)) {
            if (o == null) {
                throw new IOException("the file couldn't be written");
            }
            o.write(bytes);
        }
        return uri;
    }
}
