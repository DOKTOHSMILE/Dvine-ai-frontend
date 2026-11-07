package ai.dvine.chat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How attached things travel with a message to the model. An image is a marker, {@code [[image:/path/x.jpg]]}, that
 * the API layer turns into a real image for a model that can see ({@code RemoteBrain}); for the phone's own model,
 * and in the remembered conversation, it becomes the words "[an image]". A pasted text or a text file is wrapped in
 * {@code <attachment name="…">…</attachment>}, given as data. Pure Java.
 */
public final class Attachments {
    public static final Pattern IMAGE = Pattern.compile("\\[\\[image:([^\\]]+)\\]\\]");
    /** A document sent as itself to a model that takes files: {@code [[file:path|mime|name|page1.jpg;page2.jpg]]}. */
    public static final Pattern FILE = Pattern.compile("\\[\\[file:([^|\\]]+)\\|([^|\\]]+)\\|([^|\\]]*)\\|([^\\]]*)\\]\\]");
    private static final Pattern BLOCK = Pattern.compile("(?s)<attachment name=\"[^\"]*\">.*?</attachment>");

    private Attachments() {
    }

    public static String image(String path) {
        return "[[image:" + path + "]]";
    }

    /** A document (a PDF) with its pages as pictures, for a model that sees images but takes no files. */
    public static String file(String path, String mime, String name, List<String> pages) {
        return "[[file:" + path + "|" + mime + "|" + name.replace("|", " ").replace("]", ")") + "|" + String.join(";", pages) + "]]";
    }

    /** One attached document: where it is, its type and name, and its pages as pictures. */
    public static final class Doc {
        public final String path, mime, name;
        public final List<String> pages;

        Doc(String path, String mime, String name, List<String> pages) {
            this.path = path;
            this.mime = mime;
            this.name = name;
            this.pages = pages;
        }
    }

    public static List<Doc> files(String message) {
        List<Doc> out = new ArrayList<Doc>();
        Matcher m = FILE.matcher(message == null ? "" : message);
        while (m.find()) {
            List<String> pages = new ArrayList<String>();
            for (String p : m.group(4).split(";")) {
                if (!p.isEmpty()) {
                    pages.add(p);
                }
            }
            out.add(new Doc(m.group(1), m.group(2), m.group(3), pages));
        }
        return out;
    }

    public static String text(String name, String body) {
        return "<attachment name=\"" + name.replace("\"", "'") + "\">\n" + body + "\n</attachment>";
    }

    /** Whether a message carries an attachment. */
    public static boolean has(String message) {
        return message != null && (IMAGE.matcher(message).find() || BLOCK.matcher(message).find() || FILE.matcher(message).find());
    }

    /** The image files a message carries, in order. */
    public static List<String> images(String message) {
        List<String> out = new ArrayList<String>();
        Matcher m = IMAGE.matcher(message == null ? "" : message);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /** The message with its image markers as words, for a model that can't see and for the remembered history. */
    public static String withoutImages(String message) {
        if (message == null) {
            return null;
        }
        return FILE.matcher(IMAGE.matcher(message).replaceAll("[an image]")).replaceAll("[a document: $3]");
    }

    /**
     * The same message for the on-device model, which cannot see pixels. A file's extracted words (the ".txt" kept
     * beside it) are included, capped, so the model reads the document instead of only its name.
     */
    public static String forDevice(String message) {
        return expand(message, 12_000);
    }

    /** A shorter form of {@link #forDevice} kept in the conversation, so a later question still has the file. */
    public static String forMemory(String message) {
        return expand(message, 1_500);
    }

    private static String expand(String message, int cap) {
        if (message == null) {
            return null;
        }
        Matcher files = FILE.matcher(message);
        StringBuffer done = new StringBuffer();
        while (files.find()) {
            String path = files.group(1);
            String mime = files.group(2);
            String name = files.group(3);
            int pages = 0;
            for (String p : files.group(4).split(";")) {
                if (!p.isEmpty()) {
                    pages++;
                }
            }
            StringBuilder note = new StringBuilder();
            note.append("[attached file: ").append(name.isEmpty() ? "file" : name).append(", ").append(mime).append("]");
            String inside = sidecar(path, cap);
            if (inside != null) {
                note.append("\n").append(inside);
            } else if (pages > 0) {
                note.append("\n[the phone saved ").append(pages).append(pages == 1 ? " picture" : " pictures")
                        .append(" of it. This on-device model cannot see pictures, so say so if the words are not here]");
            } else if (mime.startsWith("audio/") || mime.startsWith("video/")) {
                note.append("\n[this on-device model cannot hear or watch it. Ask for the part that matters as text]");
            } else {
                note.append("\n[no text could be read from it]");
            }
            files.appendReplacement(done, Matcher.quoteReplacement(note.toString()));
        }
        files.appendTail(done);
        return IMAGE.matcher(done.toString()).replaceAll("[attached image. This on-device model cannot see pictures]");
    }

    private static String sidecar(String path, int cap) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        File f = new File(path + ".txt");
        if (!f.isFile()) {
            return null;
        }
        try {
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) {
                return null;
            }
            return text.length() > cap ? text.substring(0, cap) + "\n…(the file goes on)" : text;
        } catch (IOException e) {
            return null;
        }
    }

    /** The user's own words, without the attached blocks and markers. */
    public static String words(String message) {
        if (message == null) {
            return "";
        }
        return BLOCK.matcher(FILE.matcher(IMAGE.matcher(message).replaceAll("")).replaceAll("")).replaceAll("").trim();
    }

    /** A pasted block long enough to become an attachment instead of filling the box. */
    public static boolean longPaste(CharSequence inserted) {
        return longPaste(inserted, 1500);
    }

    /** As above, with the length (Settings › Advanced) at which a paste becomes a file. */
    public static boolean longPaste(CharSequence inserted, int chars) {
        if (inserted == null) {
            return false;
        }
        int lines = 1;
        for (int i = 0; i < inserted.length(); i++) {
            if (inserted.charAt(i) == '\n') {
                lines++;
            }
        }
        return inserted.length() >= chars || (inserted.length() >= Math.min(400, chars) && lines >= 25);
    }

    /** "120 lines" or "1 line". */
    public static String lines(String text) {
        int n = text == null || text.isEmpty() ? 0 : text.split("\n", -1).length;
        return n + (n == 1 ? " line" : " lines");
    }
}
