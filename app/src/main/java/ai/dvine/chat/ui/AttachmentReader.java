package ai.dvine.chat.ui;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * Turns a picked file into an attachment: a photo is shrunk (1568 px on its long side, the most vision models use)
 * and kept in the app's files; a text or code file (up to 300 kB) is read as text; anything else (a PDF, a video, a
 * sound) is attached by its name and kind, since its contents can't be read on the phone yet. Blocking; call off the
 * main thread.
 */
public final class AttachmentReader {
    static final int MAX_SIDE = 1568, MAX_TEXT = 300_000;

    private AttachmentReader() {
    }

    public static ComposerAttachments.Item read(Context c, Uri uri) throws IOException {
        return read(c, uri, MAX_SIDE);
    }

    /** As above, with the long side of a photo in pixels (Settings › Advanced). */
    public static ComposerAttachments.Item read(Context c, Uri uri, int side) throws IOException {
        String name = name(c, uri);
        String mime = c.getContentResolver().getType(uri);
        String lower = name.toLowerCase(Locale.US);
        if ((mime != null && mime.startsWith("image/")) || lower.matches(".*\\.(jpe?g|png|webp|gif|heic|bmp)$")) {
            return image(c, uri, name, side);
        }
        boolean texty = (mime != null && (mime.startsWith("text/") || mime.contains("json") || mime.contains("xml")
                || mime.contains("javascript"))) || lower.matches(".*\\.(txt|md|csv|json|xml|html?|css|js|ts|tsx|jsx|java|kt|py|rb|go|rs|c|h|cpp|cs|php|swift|sh|ya?ml|toml|ini|gradle|sql|log)$");
        if (texty) {
            byte[] bytes = readAll(c, uri, MAX_TEXT + 1);
            String text = new String(bytes, 0, Math.min(bytes.length, MAX_TEXT), StandardCharsets.UTF_8);
            if (bytes.length > MAX_TEXT) {
                text += "\n…(the file goes on; only the first 300 kB is attached)";
            }
            return new ComposerAttachments.Item("text", name, null, text);
        }
        String kind = mime == null ? "a file" : mime.startsWith("video/") ? "a video" : mime.startsWith("audio/") ? "a sound recording"
                : mime.contains("pdf") ? "a PDF" : "a file (" + mime + ")";
        return new ComposerAttachments.Item("file", name, null,
                "The user attached " + kind + " named " + name + ". Its contents can't be read on the phone yet, so ask them to paste the part that matters.");
    }

    private static ComposerAttachments.Item image(Context c, Uri uri, String name, int side) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) {
            sample *= 2;
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            bmp = BitmapFactory.decodeStream(in, null, o);
        }
        if (bmp == null) {
            throw new IOException("the picture couldn't be read");
        }
        float scale = Math.min(1f, side / (float) Math.max(bmp.getWidth(), bmp.getHeight()));
        if (scale < 1f) {
            bmp = Bitmap.createScaledBitmap(bmp, Math.round(bmp.getWidth() * scale), Math.round(bmp.getHeight() * scale), true);
        }
        File dir = new File(c.getFilesDir(), "attachments");
        dir.mkdirs();
        File out = new File(dir, UUID.randomUUID() + ".jpg");
        try (FileOutputStream o2 = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, o2);
        }
        return new ComposerAttachments.Item("image", name, out.getAbsolutePath(), null);
    }

    private static String name(Context c, Uri uri) {
        try (Cursor cursor = c.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int i = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0 && cursor.getString(i) != null) {
                    return cursor.getString(i);
                }
            }
        } catch (RuntimeException ignored) {
            // no name: a plain one
        }
        String last = uri.getLastPathSegment();
        return last == null ? "file" : last;
    }

    private static byte[] readAll(Context c, Uri uri, int max) throws IOException {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("the file couldn't be opened");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while (out.size() < max && (n = in.read(b)) > 0) {
                out.write(b, 0, n);
            }
            return out.toByteArray();
        }
    }
}
