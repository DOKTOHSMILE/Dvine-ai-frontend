package ai.ibiko.linux;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns program output into what a terminal would show: colors, a line rewritten with {@code \\r},
 * and a cleared screen. {@link #write} returns only the new tail, or null when the whole screen changed.
 */
public final class TermScreen {
    private static final int[] COLOR = new int[] {
            0xFF555555, 0xFFCC5555, 0xFF55AA55, 0xFFCCCC55,
            0xFF5555CC, 0xFFCC55CC, 0xFF55CCCC, 0xFFE6E6E6,
            0xFF888888, 0xFFFF6666, 0xFF66FF66, 0xFFFFFF66,
            0xFF6699FF, 0xFFFF66FF, 0xFF66FFFF, 0xFFFFFFFF
    };

    private final StringBuilder text = new StringBuilder();
    private final StringBuilder ink = new StringBuilder();
    private final StringBuilder pending = new StringBuilder();
    private int cursor;
    private int lineStart;
    private int fg = 7;
    private boolean bold;

    public static final class Piece {
        public final String text;
        public final int color;
        public final boolean bold;

        Piece(String text, int color, boolean bold) {
            this.text = text;
            this.color = color;
            this.bold = bold;
        }
    }

    public String plain() {
        return text.toString();
    }

    public void clear() {
        text.setLength(0);
        ink.setLength(0);
        pending.setLength(0);
        cursor = 0;
        lineStart = 0;
        fg = 7;
        bold = false;
    }

    public List<Piece> pieces() {
        return piecesFrom(0);
    }

    /** New pieces at the end, or null when earlier text changed and the whole screen should be drawn. */
    public List<Piece> write(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return new ArrayList<Piece>();
        }
        if (pending.length() > 0) {
            chunk = pending.toString() + chunk;
            pending.setLength(0);
        }
        int cut = openEscape(chunk);
        if (cut >= 0) {
            pending.append(chunk.substring(cut));
            chunk = chunk.substring(0, cut);
        }
        boolean rewrite = false;
        List<Piece> added = new ArrayList<Piece>();
        StringBuilder run = new StringBuilder();
        int runInk = mark();
        for (int i = 0; i < chunk.length(); i++) {
            char c = chunk.charAt(i);
            if (c == '\u001b') {
                int end = escapeEnd(chunk, i);
                if (end < 0) {
                    pending.append(chunk.substring(i));
                    break;
                }
                flush(run, runInk, added, rewrite);
                run.setLength(0);
                apply(chunk.substring(i, end + 1));
                if (chunk.charAt(end) == 'J' || chunk.charAt(end) == 'K') {
                    rewrite = true;
                }
                runInk = mark();
                i = end;
                continue;
            }
            if (c == '\r' || c == '\n' || c == '\b' || c == 7) {
                flush(run, runInk, added, rewrite);
                run.setLength(0);
                runInk = mark();
            }
            if (c == '\r') {
                cursor = lineStart;
                rewrite = true;
            } else if (c == '\n') {
                int nl = indexOf('\n', cursor);
                if (nl < 0) {
                    text.append('\n');
                    ink.append((char) mark());
                    cursor = text.length();
                    if (!rewrite) {
                        added.add(new Piece("\n", COLOR[mark() & 15], false));
                    }
                } else {
                    cursor = nl + 1;
                    rewrite = true;
                }
                lineStart = cursor;
            } else if (c == '\b') {
                if (cursor > lineStart) {
                    cursor--;
                    rewrite = true;
                }
            } else if (c == 7 || c == '\u0000') {
                // bell, ignored
            } else {
                int code = mark();
                if (cursor < text.length() && text.charAt(cursor) != '\n') {
                    text.setCharAt(cursor, c);
                    ink.setCharAt(cursor, (char) code);
                    cursor++;
                    rewrite = true;
                } else {
                    text.insert(cursor, c);
                    ink.insert(cursor, (char) code);
                    cursor++;
                    if (code != runInk && run.length() > 0) {
                        flush(run, runInk, added, rewrite);
                        run.setLength(0);
                    }
                    runInk = code;
                    run.append(c);
                }
            }
        }
        flush(run, runInk, added, rewrite);
        return rewrite ? null : added;
    }

    private void flush(StringBuilder run, int runInk, List<Piece> added, boolean rewrite) {
        if (rewrite || run.length() == 0) {
            return;
        }
        int color = COLOR[Math.max(0, Math.min(15, runInk))];
        added.add(new Piece(run.toString(), color, runInk >= 8));
    }

    private List<Piece> piecesFrom(int start) {
        List<Piece> out = new ArrayList<Piece>();
        if (start >= text.length()) {
            return out;
        }
        StringBuilder run = new StringBuilder();
        int inkNow = ink.charAt(start);
        for (int i = start; i < text.length(); i++) {
            int code = ink.charAt(i);
            if (code != inkNow) {
                out.add(new Piece(run.toString(), COLOR[inkNow & 15], inkNow >= 8));
                run.setLength(0);
                inkNow = code;
            }
            run.append(text.charAt(i));
        }
        if (run.length() > 0) {
            out.add(new Piece(run.toString(), COLOR[inkNow & 15], inkNow >= 8));
        }
        return out;
    }

    private int mark() {
        int n = fg;
        if (bold && n < 8) {
            n += 8;
        }
        return n;
    }

    /** Where an unfinished escape starts, or -1. */
    private static int openEscape(String chunk) {
        int at = chunk.lastIndexOf('\u001b');
        if (at < 0) {
            return -1;
        }
        return escapeEnd(chunk, at) < 0 ? at : -1;
    }

    private static int escapeEnd(String chunk, int at) {
        if (at + 1 >= chunk.length()) {
            return -1;
        }
        if (chunk.charAt(at + 1) != '[') {
            return at + 1;
        }
        for (int j = at + 2; j < chunk.length(); j++) {
            char c = chunk.charAt(j);
            if (c >= '@' && c <= '~') {
                return j;
            }
        }
        return -1;
    }

    private void apply(String seq) {
        if (seq.length() < 3 || seq.charAt(seq.length() - 1) != 'm' && seq.charAt(seq.length() - 1) != 'J'
                && seq.charAt(seq.length() - 1) != 'K' && seq.charAt(seq.length() - 1) != 'H') {
            return;
        }
        char cmd = seq.charAt(seq.length() - 1);
        String args = seq.substring(2, seq.length() - 1);
        if (cmd == 'J') {
            if (args.isEmpty() || args.indexOf('2') >= 0 || args.indexOf('3') >= 0) {
                text.setLength(0);
                ink.setLength(0);
                cursor = 0;
                lineStart = 0;
            }
            return;
        }
        if (cmd == 'K') {
            int end = indexOf('\n', cursor);
            if (end < 0) {
                end = text.length();
            }
            text.delete(cursor, end);
            ink.delete(cursor, end);
            return;
        }
        if (cmd == 'H') {
            cursor = 0;
            lineStart = 0;
            return;
        }
        if (args.isEmpty()) {
            fg = 7;
            bold = false;
            return;
        }
        String[] parts = args.split(";");
        for (String part : parts) {
            int n = 0;
            try {
                n = part.isEmpty() ? 0 : Integer.parseInt(part);
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (n == 0) {
                fg = 7;
                bold = false;
            } else if (n == 1) {
                bold = true;
            } else if (n == 22) {
                bold = false;
            } else if (n >= 30 && n <= 37) {
                fg = n - 30;
            } else if (n >= 90 && n <= 97) {
                fg = n - 90 + 8;
            } else if (n == 39) {
                fg = 7;
            }
        }
    }

    private int indexOf(char c, int from) {
        for (int i = from; i < text.length(); i++) {
            if (text.charAt(i) == c) {
                return i;
            }
        }
        return -1;
    }
}
