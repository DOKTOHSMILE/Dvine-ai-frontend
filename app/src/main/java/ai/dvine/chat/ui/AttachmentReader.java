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
        return doc(c, uri, name, mime == null ? guess(lower) : mime);
    }

    static final int MAX_FILE = 20 * 1024 * 1024, PDF_PAGES = 6, VIDEO_FRAMES = 4;

    /**
     * Any other file, kept whole so a model that takes files gets the file itself; and, for a model that doesn't,
     * what can be taken out of it on the phone: a PDF's first pages as pictures, a video's frames as pictures, the
     * words of a Word, Excel, PowerPoint, OpenDocument or EPUB file, a zip's listing ({@link ai.dvine.chat.FileText}),
     * kept beside it as ".txt".
     */
    private static ComposerAttachments.Item doc(Context c, Uri uri, String name, String mime) throws IOException {
        File dir = new File(c.getFilesDir(), "attachments");
        dir.mkdirs();
        String id = UUID.randomUUID().toString();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')).replaceAll("[^A-Za-z0-9.]", "") : "";
        File out = new File(dir, id + ext);
        byte[] bytes = readAll(c, uri, MAX_FILE + 1);
        if (bytes.length > MAX_FILE) {
            return new ComposerAttachments.Item("file", name, null, "The user attached " + name
                    + ", over 20 MB, too big to send. Ask them for the part that matters.");
        }
        java.nio.file.Files.write(out.toPath(), bytes);
        java.util.List<String> pages = new java.util.ArrayList<String>();
        String label = label(name, mime);
        if (mime.contains("pdf")) {
            int count = pdfPages(out, dir, id, pages);
            label = "PDF" + (count > 0 ? " \u00B7 " + count + (count == 1 ? " page" : " pages") : "");
        } else if (mime.startsWith("video/")) {
            videoFrames(out, dir, id, pages);
        }
        String text = ai.dvine.chat.FileText.of(name, mime, bytes);
        if (text != null) {
            java.nio.file.Files.write(new File(out.getPath() + ".txt").toPath(), text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return new ComposerAttachments.Item("doc", name, out.getAbsolutePath(), String.join(";", pages) + "\n" + label + "\n" + mime);
    }

    private static int pdfPages(File pdf, File dir, String id, java.util.List<String> pages) {
        try (android.os.ParcelFileDescriptor fd = android.os.ParcelFileDescriptor.open(pdf, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
                android.graphics.pdf.PdfRenderer r = new android.graphics.pdf.PdfRenderer(fd)) {
            int count = r.getPageCount();
            for (int i = 0; i < Math.min(PDF_PAGES, count); i++) {
                try (android.graphics.pdf.PdfRenderer.Page page = r.openPage(i)) {
                    float k = Math.min(2f, 1400f / Math.max(page.getWidth(), page.getHeight()));
                    Bitmap bmp = Bitmap.createBitmap(Math.max(1, Math.round(page.getWidth() * k)), Math.max(1, Math.round(page.getHeight() * k)),
                            Bitmap.Config.ARGB_8888);
                    bmp.eraseColor(0xFFFFFFFF);
                    page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    pages.add(jpeg(bmp, new File(dir, id + "-p" + (i + 1) + ".jpg")));
                }
            }
            return count;
        } catch (IOException | RuntimeException e) {
            return 0; // a protected PDF: it still goes whole to models that take files
        }
    }

    private static void videoFrames(File video, File dir, String id, java.util.List<String> frames) {
        android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
        try {
            r.setDataSource(video.getAbsolutePath());
            String d = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
            long ms = d == null ? 0 : Long.parseLong(d);
            for (int i = 0; i < VIDEO_FRAMES; i++) {
                long at = ms <= 0 ? 0 : ms * (2L * i + 1) / (2L * VIDEO_FRAMES);
                Bitmap f = r.getFrameAtTime(at * 1000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (f != null) {
                    float k = Math.min(1f, 1024f / Math.max(f.getWidth(), f.getHeight()));
                    if (k < 1f) {
                        f = Bitmap.createScaledBitmap(f, Math.round(f.getWidth() * k), Math.round(f.getHeight() * k), true);
                    }
                    frames.add(jpeg(f, new File(dir, id + "-f" + (i + 1) + ".jpg")));
                }
            }
        } catch (IOException | RuntimeException e) {
            // a video the phone can't decode: it still goes whole to models that take files
        } finally {
            try {
                r.release();
            } catch (IOException | RuntimeException ignored) {
                // released
            }
        }
    }

    private static String jpeg(Bitmap bmp, File to) throws IOException {
        try (FileOutputStream o = new FileOutputStream(to)) {
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, o);
        }
        return to.getAbsolutePath();
    }

    /** What kind of file it is, in words ("Word document", "Spreadsheet", "Video"). */
    static String label(String name, String mime) {
        String n = name.toLowerCase(java.util.Locale.US);
        if (mime.startsWith("video/")) return "Video";
        if (mime.startsWith("audio/")) return "Audio";
        if (n.matches(".*\\.(docx?|odt|rtf|pages)$")) return "Document";
        if (n.matches(".*\\.(xlsx?|ods|numbers)$")) return "Spreadsheet";
        if (n.matches(".*\\.(pptx?|odp|key)$")) return "Presentation";
        if (n.matches(".*\\.(zip|jar|apk|aar|7z|rar|tar|gz)$")) return "Archive";
        if (n.endsWith(".epub")) return "Book";
        return mime.isEmpty() || mime.equals("application/octet-stream") ? "File" : mime;
    }

    /** A type from the name when the phone doesn't say. */
    static String guess(String lower) {
        String ext = lower.contains(".") ? lower.substring(lower.lastIndexOf('.') + 1) : "";
        String t = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return t == null ? "application/octet-stream" : t;
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
