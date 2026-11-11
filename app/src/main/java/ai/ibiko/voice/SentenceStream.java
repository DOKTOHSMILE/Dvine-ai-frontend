package ai.ibiko.voice;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuts a reply into sentences while the model is still writing it, so the voice can start on the first sentence
 * instead of waiting for the whole answer. Code blocks are left to the screen (they aren't read out), and markdown,
 * links and action tags are cleaned so they aren't spoken. Pure Java.
 */
public final class SentenceStream {
    /** Below this many characters a sentence waits to join the next one, so the voice doesn't stutter "Sure." alone… */
    static final int SHORT = 24;
    private final StringBuilder buf = new StringBuilder();
    private final StringBuilder held = new StringBuilder();
    private boolean inCode, first = true, saidCode;

    /** A whole reply as the sentences to read aloud (code left out, markdown cleaned), for Listen. */
    public static List<String> split(String text) {
        SentenceStream s = new SentenceStream();
        List<String> out = new ArrayList<String>(s.add(text));
        out.addAll(s.finish());
        List<String> spoken = new ArrayList<String>();
        for (String t : out) {
            if (t != null && !t.trim().isEmpty()) {
                spoken.add(t.trim());
            }
        }
        return spoken;
    }

    /** Adds what the model just wrote; returns the sentences now complete (often none). */
    public List<String> add(String piece) {
        List<String> out = new ArrayList<String>();
        if (piece == null || piece.isEmpty() || "\u0001".equals(piece)) {
            return out;
        }
        buf.append(piece);
        cut(out, false);
        return out;
    }

    /** The model has finished: whatever is left. */
    public List<String> finish() {
        List<String> out = new ArrayList<String>();
        cut(out, true);
        String rest = inCode ? "" : buf.toString();
        buf.setLength(0);
        emit(out, rest, true);
        if (held.length() > 0) {
            String h = held.toString();
            held.setLength(0);
            out.add(h);
        }
        return out;
    }

    private void cut(List<String> out, boolean end) {
        while (true) {
            int fence = buf.indexOf("```");
            if (inCode) {
                if (fence < 0) {
                    // keep only a tail that could be the start of the closing fence
                    if (buf.length() > 2) {
                        buf.delete(0, buf.length() - 2);
                    }
                    return;
                }
                buf.delete(0, fence + 3);
                inCode = false;
                continue;
            }
            int stop = boundary(fence < 0 ? buf.length() : fence);
            if (stop >= 0) {
                String s = buf.substring(0, stop);
                buf.delete(0, stop);
                emit(out, s, false);
                continue;
            }
            if (fence >= 0) {
                String before = buf.substring(0, fence);
                buf.delete(0, fence + 3);
                emit(out, before, false);
                inCode = true;
                if (!saidCode) {
                    saidCode = true;
                    emit(out, "The code is on the screen.", false);
                }
                continue;
            }
            return;
        }
    }

    /** The end of the first whole sentence in buf[0, limit): after . ! ? : followed by a space, or a new line. */
    private int boundary(int limit) {
        for (int i = 0; i < limit; i++) {
            char c = buf.charAt(i);
            if (c == '\n') {
                return i + 1;
            }
            if ((c == '.' || c == '!' || c == '?') && i + 1 < limit && Character.isWhitespace(buf.charAt(i + 1))) {
                // not "e.g." or "Dr." or a list number "1." at the start of a line
                int w = i - 1;
                while (w >= 0 && Character.isLetterOrDigit(buf.charAt(w))) {
                    w--;
                }
                String word = buf.substring(w + 1, i).toLowerCase(java.util.Locale.US);
                boolean listNumber = word.matches("\\d+") && (w < 0 || buf.charAt(w) == '\n');
                if (c == '.' && (listNumber || word.matches("e\\.g|i\\.e|mr|mrs|ms|dr|st|vs|etc|no") || word.length() == 1 && i > 0
                        && Character.isUpperCase(buf.charAt(i - 1)))) {
                    continue;
                }
                return i + 2;
            }
        }
        return -1;
    }

    private void emit(List<String> out, String raw, boolean end) {
        String s = clean(raw);
        if (!s.isEmpty()) {
            if (held.length() > 0) {
                held.append(' ');
            }
            held.append(s);
        }
        // the very first sentence goes at once (that is what makes the voice start fast); later short ones wait
        if (held.length() > 0 && (first || held.length() >= SHORT || end)) {
            out.add(held.toString());
            held.setLength(0);
            first = false;
        }
    }

    /**
     * What the voice should read: the words only. No markdown (** __ ` # > - |), no tags (<final>, <b>), no quotes,
     * brackets or other symbols read out as "less than" or "quote"; links as their words, arrows as "to".
     */
    public static String clean(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\[\\[[^\\]]{0,24}\\]\\]", "")
                .replaceAll("!?\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                .replaceAll("https?://\\S+", "the link")
                .replaceAll("`([^`]*)`", "$1")
                .replaceAll("</?[A-Za-z][\\w:-]*[^<>]*>", " ")
                .replaceAll("(?m)^\\s*(#{1,6}|[-*+>]|\\d+[.)])\\s+", "")
                .replaceAll("\\s*(->|=>|\\u2192)\\s*", " to ")
                .replaceAll("[*_~#|`<>\"\\u201C\\u201D\\u00AB\\u00BB{}\\[\\]\\\\^=]+", " ")
                .replaceAll("(?<=\\s|^)'+|'+(?=\\s|$)", "")
                .replaceAll("\\s+([,.!?;:])", "$1")
                .replaceAll("\\s+", " ")
                .trim();
        return t.matches("[\\p{Punct}\\s]*") ? "" : t;
    }

    /**
     * What a voice can say without stumbling: {@link #clean} and then no code (fenced blocks, function codes like
     * \u00A7IM, lines that are mostly symbols), no file paths (the file's name instead), no emoji. A sentence with
     * nothing sayable left is skipped (""), not read as noise.
     */
    public static String speakable(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("(?s)```.*?(```|$)", " ")
                .replaceAll("[\u00A7\u27E6][A-Z]{2}\\b[^\\n]*", " ")
                .replaceAll("\\[\\[[A-Z]{2}\\]\\][^\\n]*", " ")
                .replaceAll("(?<![\\w@])(?:[\\w.-]+/)+([\\w-]+)(?:\\.\\w{1,6})?\\b", "$1")
                .replaceAll("\\b([\\w-]+)\\.(java|kt|py|js|ts|md|json|xml|gradle|txt|sh|html|css|yml|yaml|png|jpg|apk|zip)\\b", "$1")
                .replaceAll("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}]", " ")
                .replaceAll("\\b[0-9a-f]{12,}\\b", " ");
        t = clean(t);
        int letters = 0;
        for (int i = 0; i < t.length(); i++) {
            if (Character.isLetter(t.charAt(i)) || t.charAt(i) == ' ') {
                letters++;
            }
        }
        return t.isEmpty() || letters < t.length() * 0.6 ? "" : t; // mostly symbols or numbers: nothing to say
    }
}
