package ai.dvine.agent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Reading and searching the project's own files on the device. A file is never cut off and called whole: a read
 * names the line range it returned and the file's real length, and a search walks the tree itself (not the short
 * file list) so a line can be found without a shell or Termux. Pure Java.
 */
public final class SourceRead {
    /** How much of a file one read hands back. The rest is asked for by line number. */
    public static final int WINDOW = 100_000;
    /** Matches one search will list. */
    public static final int HITS = 500;
    /** A text file larger than this is skipped by search (it is not source). */
    public static final long MAX_BYTES = 8_000_000L;
    /** How many files a search will open. */
    static final int FILES = 8000;

    private SourceRead() {
    }

    /**
     * Lines {@code start}–{@code end} (1-based, inclusive; empty means the whole file, or the first window of it).
     * The result says how long the file really is when this is not all of it.
     */
    public static String read(File file, String start, String end) throws IOException {
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        String[] lines = text.split("\n", -1);
        int from = number(start, 1);
        int to = number(end, lines.length);
        if (from < 1) {
            from = 1;
        }
        if (to < from) {
            to = from;
        }
        if (from > lines.length) {
            return "That file has " + lines.length + " lines; there is no line " + from + ".";
        }
        to = Math.min(to, lines.length);
        StringBuilder body = new StringBuilder();
        int shown = from;
        for (int i = from; i <= to; i++) {
            String row = i + "\t" + lines[i - 1] + "\n";
            if (body.length() + row.length() > WINDOW && i > from) {
                to = i - 1;
                break;
            }
            body.append(row);
            shown = i;
        }
        boolean all = from == 1 && shown == lines.length;
        if (all) {
            return body.toString();
        }
        return "Lines " + from + "–" + shown + " of " + lines.length + " in this file (" + text.length()
                + " characters). This is not the whole file. Read another range with start and end line numbers.\n"
                + body;
    }

    /** Every line in {@code root} (or under {@code folder}) that contains {@code query}. */
    public static String search(File root, String query, String folder) {
        if (query == null || query.isEmpty()) {
            return "No text given.";
        }
        File base = root;
        if (folder != null && !folder.trim().isEmpty() && !folder.trim().equals(".")) {
            base = new File(root, folder.trim());
            if (!base.isDirectory()) {
                return "No such folder: " + folder;
            }
        }
        List<File> files = new ArrayList<File>();
        collect(base, files);
        List<String> hits = new ArrayList<String>();
        int scanned = 0;
        for (File f : files) {
            if (hits.size() >= HITS) {
                break;
            }
            if (!f.isFile() || f.length() > MAX_BYTES || binary(f)) {
                continue;
            }
            scanned++;
            try {
                String[] lines = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n", -1);
                String rel = root.toPath().relativize(f.toPath()).toString();
                for (int i = 0; i < lines.length && hits.size() < HITS; i++) {
                    if (lines[i].contains(query)) {
                        String row = lines[i].trim();
                        if (row.length() > 400) {
                            row = row.substring(0, 400);
                        }
                        hits.add(rel + ":" + (i + 1) + ": " + row);
                    }
                }
            } catch (IOException e) {
                // unreadable: skip
            }
        }
        if (hits.isEmpty()) {
            return "No matches in " + scanned + " files.";
        }
        String more = hits.size() >= HITS ? "\n…(" + HITS + " matches shown; narrow the query or the folder.)" : "";
        return String.join("\n", hits) + more;
    }

    private static void collect(File dir, List<File> out) {
        if (out.size() >= FILES) {
            return;
        }
        File[] kids = dir.isDirectory() ? dir.listFiles() : null;
        if (kids == null) {
            if (dir.isFile()) {
                out.add(dir);
            }
            return;
        }
        java.util.Arrays.sort(kids);
        for (File k : kids) {
            String name = k.getName();
            if (name.equals(".git") || name.equals("node_modules") || name.equals("build") || name.equals(".gradle")
                    || name.equals(".dvine-git.json")) {
                continue;
            }
            if (k.isDirectory()) {
                collect(k, out);
            } else if (out.size() < FILES) {
                out.add(k);
            }
        }
    }

    private static boolean binary(File f) {
        try (java.io.InputStream in = Files.newInputStream(f.toPath())) {
            byte[] head = new byte[8000];
            int n = in.read(head);
            for (int i = 0; i < n; i++) {
                if (head[i] == 0) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    private static int number(String raw, int fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
