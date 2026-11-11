package ai.ibiko.chat.ui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Text too big to travel inside an Intent: Android carries at most about 1 MB in one, and a String counts twice
 * (two bytes a letter), so a 512 KB file crashed the app the moment the code viewer was opened. Anything over
 * {@link #INLINE} goes in a file instead and the screen gets its path; the screen reads it off the main thread, line
 * by line, with limits so a huge or binary file is refused instead of freezing or crashing. Pure Java.
 */
public final class BigText {
    private BigText() {
    }

    /** At most this many characters travel inside an Intent (200 KB on the wire). */
    public static final int INLINE = 100_000;
    /** The most a viewer reads: bigger files are refused with their size. */
    public static final long MOST_BYTES = 16L * 1024 * 1024;
    public static final int MOST_LINES = 400_000;
    /** One displayed line is cut here (a minified file can be a single line of megabytes). */
    public static final int LINE_SHOWN = 3000;

    /** Writes text to a new file in {@code dir} (removing the old ones there) and returns it. */
    public static File park(File dir, String text, String ext) throws IOException {
        dir.mkdirs();
        File[] old = dir.listFiles();
        if (old != null) {
            for (File f : old) {
                if (System.currentTimeMillis() - f.lastModified() > 60_000) {
                    f.delete(); // views opened a while ago are gone by now
                }
            }
        }
        File f = new File(dir, "view-" + System.nanoTime() + "." + (ext == null || ext.isEmpty() ? "txt" : ext.replaceAll("[^A-Za-z0-9]", "")));
        Files.write(f.toPath(), (text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        return f;
    }

    /** A file read as lines for a viewer, or why it can't be shown. */
    public static final class Lines {
        public final List<String> lines = new ArrayList<String>();
        public long chars;
        public boolean cutShort;
        public String refused;
    }

    public static Lines read(File f) {
        Lines out = new Lines();
        if (f == null || !f.isFile()) {
            out.refused = "The file isn't there any more.";
            return out;
        }
        if (f.length() > MOST_BYTES) {
            out.refused = "Too big to show here (" + (f.length() / 1048576) + " MB). Share it to open it in another app.";
            return out;
        }
        try (InputStream raw = new FileInputStream(f)) {
            byte[] head = new byte[4096];
            int n = raw.read(head);
            for (int i = 0; i < n; i++) {
                if (head[i] == 0) {
                    out.refused = "Not a text file.";
                    return out;
                }
            }
        } catch (IOException e) {
            out.refused = "Couldn't read it.";
            return out;
        }
        return read(f, out);
    }

    private static Lines read(File f, Lines out) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8), 65536)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (out.lines.size() >= MOST_LINES) {
                    out.cutShort = true;
                    break;
                }
                out.chars += line.length() + 1;
                out.lines.add(line);
            }
        } catch (IOException e) {
            out.refused = "Couldn't read it.";
        }
        return out;
    }

    /** Lines of text already in memory (a code block). */
    public static Lines of(String text) {
        Lines out = new Lines();
        String t = text == null ? "" : text;
        int start = 0;
        while (start <= t.length()) {
            int nl = t.indexOf('\n', start);
            int end = nl < 0 ? t.length() : nl;
            if (out.lines.size() >= MOST_LINES) {
                out.cutShort = true;
                break;
            }
            out.lines.add(t.substring(start, end));
            if (nl < 0) {
                break;
            }
            start = nl + 1;
        }
        out.chars = t.length();
        return out;
    }

    /** One line as shown: its number, padded, and the text cut at {@link #LINE_SHOWN}. */
    public static String shown(int index, int total, String line) {
        String num = String.valueOf(index + 1);
        int width = String.valueOf(Math.max(1, total)).length();
        StringBuilder b = new StringBuilder(width + 2 + Math.min(line.length(), LINE_SHOWN) + 40);
        for (int i = num.length(); i < width; i++) {
            b.append(' ');
        }
        b.append(num).append("  ");
        if (line.length() > LINE_SHOWN) {
            b.append(line, 0, LINE_SHOWN).append(" … (").append(line.length() - LINE_SHOWN).append(" more characters)");
        } else {
            b.append(line);
        }
        return b.toString();
    }
}
