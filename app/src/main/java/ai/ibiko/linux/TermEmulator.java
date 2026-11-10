package ai.ibiko.linux;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * A terminal screen (xterm-256color, the part programs use): a grid of cells with a cursor, colors, a scroll region,
 * the second screen full-screen programs draw on ({@code vi}, {@code nano}, {@code top}, {@code htop}, {@code less}),
 * line-drawing characters, and lines kept above the screen to scroll back to. Program output goes in through
 * {@link #write}; what a program asks the terminal ("where is the cursor?") comes out through {@link #takeReply}.
 * Pure Java. Callers hold this object's lock while reading the grid.
 */
public final class TermEmulator {
    /** A color that means "the default": white text, black background. */
    public static final int DEFAULT = 256;
    /** Set on a color that is red, green and blue in its low 24 bits, not a palette number. */
    public static final int RGB = 0x1000000;

    public static final int BOLD = 1, DIM = 2, ITALIC = 4, UNDERLINE = 8, REVERSE = 16, HIDDEN = 32, WIDE_TAIL = 64;

    /** One line of cells. */
    public static final class Row {
        public final int[] ch;
        public final int[] fg;
        public final int[] bg;
        public final byte[] attr;
        /** True when this line ran on into the next one (no line break when copied). */
        public boolean wrapped;

        Row(int cols) {
            ch = new int[cols];
            fg = new int[cols];
            bg = new int[cols];
            attr = new byte[cols];
            Arrays.fill(ch, ' ');
            Arrays.fill(fg, DEFAULT);
            Arrays.fill(bg, DEFAULT);
        }

        Row sized(int cols) {
            if (cols == ch.length) {
                return this;
            }
            Row r = new Row(cols);
            int n = Math.min(cols, ch.length);
            System.arraycopy(ch, 0, r.ch, 0, n);
            System.arraycopy(fg, 0, r.fg, 0, n);
            System.arraycopy(bg, 0, r.bg, 0, n);
            System.arraycopy(attr, 0, r.attr, 0, n);
            r.wrapped = wrapped && cols >= ch.length;
            return r;
        }

        public String text() {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < ch.length; i++) {
                if ((attr[i] & WIDE_TAIL) == 0) {
                    b.appendCodePoint(ch[i] == 0 ? ' ' : ch[i]);
                }
            }
            int end = b.length();
            while (end > 0 && b.charAt(end - 1) == ' ') {
                end--;
            }
            return b.substring(0, end);
        }
    }

    private int cols, rows;
    private Row[] main, alt, grid;
    /** Kept lines, oldest first, in a ring: {@code backStart} is the oldest. Indexed in constant time for drawing. */
    private final ArrayList<Row> back = new ArrayList<Row>();
    private int backStart;
    private final int keep;
    private int cx, cy;
    private boolean wrapNext;
    private int fg = DEFAULT, bg = DEFAULT;
    private int attr;
    private int top, bottom;
    private boolean originMode, autoWrap = true, insertMode;
    private boolean cursorOn = true, appCursor, appKeypad, bracketedPaste, altOn;
    private boolean[] tabs;
    /** Saved by ESC 7, one for each screen. */
    private final int[][] saved = new int[2][];
    /** Line-drawing set chosen for G0 and G1, and which of the two is in use. */
    private final boolean[] drawing = new boolean[2];
    private int charset;
    private int lastChar = ' ';
    private String title = "";
    private final StringBuilder reply = new StringBuilder();

    // the parser
    private static final int TEXT = 0, ESC = 1, CSI = 2, OSC = 3, OSC_ESC = 4, STRING = 5, STRING_ESC = 6, CHARSET = 7, SKIP1 = 8;
    private int state = TEXT;
    private final StringBuilder seq = new StringBuilder();
    private int charsetSlot;
    private char pendingHigh;

    public TermEmulator(int cols, int rows, int keep) {
        this.keep = Math.max(0, keep);
        this.cols = Math.max(2, cols);
        this.rows = Math.max(2, rows);
        main = blank(this.cols, this.rows);
        alt = blank(this.cols, this.rows);
        grid = main;
        bottom = this.rows - 1;
        tabs = tabStops(this.cols);
    }

    private static Row[] blank(int cols, int rows) {
        Row[] g = new Row[rows];
        for (int i = 0; i < rows; i++) {
            g[i] = new Row(cols);
        }
        return g;
    }

    private static boolean[] tabStops(int cols) {
        boolean[] t = new boolean[cols];
        for (int i = 8; i < cols; i += 8) {
            t[i] = true;
        }
        return t;
    }

    // ------------------------------------------------------------ what the screen shows

    public synchronized int cols() {
        return cols;
    }

    public synchronized int rows() {
        return rows;
    }

    /** A line of the screen, 0 at the top. */
    public synchronized Row row(int y) {
        return grid[y];
    }

    /** How many lines are kept above the screen. */
    public synchronized int scrollback() {
        return altOn ? 0 : back.size();
    }

    /** A line counted from the oldest kept line: the kept lines first, then the screen. */
    public synchronized Row line(int index) {
        int kept = scrollback();
        if (index >= 0 && index < kept) {
            return back.get((backStart + index) % back.size());
        }
        return grid[Math.max(0, Math.min(rows - 1, index - kept))];
    }

    public synchronized int cursorX() {
        return Math.min(cx, cols - 1);
    }

    public synchronized int cursorY() {
        return cy;
    }

    public synchronized boolean cursorVisible() {
        return cursorOn;
    }

    /** A full-screen program is drawing ({@code vi}, {@code top}). */
    public synchronized boolean altScreen() {
        return altOn;
    }

    /** Arrow keys are sent as ESC O A instead of ESC [ A. */
    public synchronized boolean appCursorKeys() {
        return appCursor;
    }

    public synchronized boolean appKeypad() {
        return appKeypad;
    }

    public synchronized boolean bracketedPaste() {
        return bracketedPaste;
    }

    public synchronized String title() {
        return title;
    }

    /** What the terminal answers a program that asked it something, to be written back to the program. */
    public synchronized String takeReply() {
        String r = reply.toString();
        reply.setLength(0);
        return r;
    }

    /** The text on the line the cursor is on, up to the cursor. */
    public synchronized String beforeCursor() {
        Row r = grid[cy];
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(cx, cols); i++) {
            if ((r.attr[i] & WIDE_TAIL) == 0) {
                b.appendCodePoint(r.ch[i] == 0 ? ' ' : r.ch[i]);
            }
        }
        return b.toString();
    }

    /** True when nothing is written after the cursor on its line. */
    public synchronized boolean cursorAtLineEnd() {
        Row r = grid[cy];
        for (int i = Math.min(cx, cols); i < cols; i++) {
            if (r.ch[i] != ' ' && r.ch[i] != 0) {
                return false;
            }
        }
        return true;
    }

    /** Everything kept and on the screen, as plain text. */
    public synchronized String text() {
        StringBuilder b = new StringBuilder();
        int total = scrollback() + rows;
        int last = total - 1;
        while (last >= 0 && line(last).text().isEmpty()) {
            last--;
        }
        for (int i = 0; i <= last; i++) {
            Row r = line(i);
            b.append(r.text());
            if (!r.wrapped) {
                b.append('\n');
            }
        }
        return b.toString();
    }

    /** The screen only, one string a line (for tests and for reading the screen). */
    public synchronized String[] screen() {
        String[] out = new String[rows];
        for (int i = 0; i < rows; i++) {
            out[i] = grid[i].text();
        }
        return out;
    }

    // ------------------------------------------------------------ size

    public synchronized void resize(int newCols, int newRows) {
        newCols = Math.max(2, newCols);
        newRows = Math.max(2, newRows);
        if (newCols == cols && newRows == rows) {
            return;
        }
        main = fit(main, newCols, newRows, !altOn ? cy : -1, true);
        alt = fit(alt, newCols, newRows, altOn ? cy : -1, false);
        grid = altOn ? alt : main;
        cols = newCols;
        rows = newRows;
        cy = Math.max(0, Math.min(rows - 1, cy - shifted));
        shifted = 0;
        cx = Math.min(cx, cols - 1);
        wrapNext = false;
        top = 0;
        bottom = rows - 1;
        tabs = tabStops(cols);
    }

    private int shifted;

    /** A grid at the new size. Lines above a cursor that no longer fits go to the kept lines. */
    private Row[] fit(Row[] old, int newCols, int newRows, int cursor, boolean keepTop) {
        int drop = 0;
        if (old.length > newRows) {
            int used = old.length;
            while (used > newRows && used - 1 > cursor && old[used - 1].text().isEmpty()) {
                used--; // empty lines at the bottom go first
            }
            drop = Math.max(0, used - newRows);
        }
        if (cursor >= 0) {
            shifted = drop;
        }
        Row[] g = new Row[newRows];
        for (int i = 0; i < newRows; i++) {
            int from = i + drop;
            g[i] = from < old.length ? old[from].sized(newCols) : new Row(newCols);
        }
        if (keepTop) {
            for (int i = 0; i < drop; i++) {
                remember(old[i]);
            }
        }
        return g;
    }

    private void remember(Row r) {
        if (keep == 0) {
            return;
        }
        if (back.size() < keep) {
            back.add(r);
        } else {
            back.set(backStart, r); // the oldest line is replaced
            backStart = (backStart + 1) % back.size();
        }
    }

    // ------------------------------------------------------------ output from programs

    public synchronized void write(String text) {
        if (text == null) {
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int cp = c;
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                    cp = Character.toCodePoint(c, text.charAt(++i));
                } else {
                    pendingHigh = c;
                    continue;
                }
            } else if (Character.isLowSurrogate(c) && pendingHigh != 0) {
                cp = Character.toCodePoint(pendingHigh, c);
                pendingHigh = 0;
            }
            take(cp);
        }
    }

    private void take(int c) {
        switch (state) {
            case TEXT:
                text(c);
                return;
            case ESC:
                escape(c);
                return;
            case CSI:
                if (c >= 0x40 && c <= 0x7e) {
                    state = TEXT;
                    csi((char) c, seq.toString());
                } else if (c == 0x1b) {
                    state = ESC;
                } else if (c < 0x20) {
                    control(c);
                } else if (seq.length() < 64) {
                    seq.append((char) c);
                }
                return;
            case OSC:
                if (c == 7) {
                    state = TEXT;
                    osc(seq.toString());
                } else if (c == 0x1b) {
                    state = OSC_ESC;
                } else if (seq.length() < 512) {
                    seq.appendCodePoint(c);
                }
                return;
            case OSC_ESC:
                state = TEXT;
                osc(seq.toString());
                if (c != '\\') {
                    state = ESC;
                    escape(c);
                }
                return;
            case STRING:
                if (c == 0x1b) {
                    state = STRING_ESC;
                } else if (c == 7) {
                    state = TEXT;
                }
                return;
            case STRING_ESC:
                state = c == '\\' ? TEXT : STRING;
                return;
            case CHARSET:
                drawing[charsetSlot] = c == '0';
                state = TEXT;
                return;
            case SKIP1:
            default:
                state = TEXT;
        }
    }

    private void text(int c) {
        if (c < 0x20 || c == 0x7f) {
            control(c);
            return;
        }
        if (drawing[charset] && c >= 0x5f && c <= 0x7e) {
            c = LINES.charAt(c - 0x5f);
        }
        int w = width(c);
        if (w == 0) {
            return; // an accent that joins the letter before it: not drawn on its own
        }
        put(c, w);
    }

    private void control(int c) {
        switch (c) {
            case 7:
                break;
            case 8:
                if (cx >= cols) {
                    cx = cols - 1;
                }
                if (cx > 0) {
                    cx--;
                }
                wrapNext = false;
                break;
            case 9:
                tab();
                break;
            case 10:
            case 11:
            case 12:
                lineFeed();
                break;
            case 13:
                cx = 0;
                wrapNext = false;
                break;
            case 14:
                charset = 1;
                break;
            case 15:
                charset = 0;
                break;
            case 0x1b:
                state = ESC;
                break;
            default:
                break;
        }
    }

    private void tab() {
        int x = Math.min(cx, cols - 1) + 1;
        while (x < cols - 1 && !tabs[x]) {
            x++;
        }
        cx = Math.min(x, cols - 1);
        wrapNext = false;
    }

    private void put(int c, int w) {
        if (wrapNext && autoWrap) {
            grid[cy].wrapped = true;
            cx = 0;
            lineFeed();
        }
        wrapNext = false;
        if (w == 2 && cx == cols - 1) {
            if (autoWrap) {
                grid[cy].wrapped = true;
                cx = 0;
                lineFeed();
            } else {
                return;
            }
        }
        Row r = grid[cy];
        if (insertMode) {
            shiftRight(r, cx, w);
        }
        r.ch[cx] = c;
        r.fg[cx] = fg;
        r.bg[cx] = bg;
        r.attr[cx] = (byte) attr;
        if (w == 2 && cx + 1 < cols) {
            r.ch[cx + 1] = 0;
            r.fg[cx + 1] = fg;
            r.bg[cx + 1] = bg;
            r.attr[cx + 1] = (byte) (attr | WIDE_TAIL);
        }
        lastChar = c;
        if (cx + w >= cols) {
            cx = cols - 1;
            wrapNext = true;
        } else {
            cx += w;
        }
    }

    private void shiftRight(Row r, int at, int n) {
        for (int i = cols - 1; i >= at + n; i--) {
            copyCell(r, i - n, i);
        }
        for (int i = at; i < Math.min(cols, at + n); i++) {
            eraseCell(r, i);
        }
    }

    private void shiftLeft(Row r, int at, int n) {
        for (int i = at; i < cols; i++) {
            if (i + n < cols) {
                copyCell(r, i + n, i);
            } else {
                eraseCell(r, i);
            }
        }
    }

    private static void copyCell(Row r, int from, int to) {
        r.ch[to] = r.ch[from];
        r.fg[to] = r.fg[from];
        r.bg[to] = r.bg[from];
        r.attr[to] = r.attr[from];
    }

    private void eraseCell(Row r, int i) {
        r.ch[i] = ' ';
        r.fg[i] = DEFAULT;
        r.bg[i] = bg; // erased cells take the current background, as xterm does
        r.attr[i] = 0;
    }

    private void lineFeed() {
        wrapNext = false;
        if (cy == bottom) {
            scrollUp(1);
        } else if (cy < rows - 1) {
            cy++;
        }
    }

    private void reverseLineFeed() {
        wrapNext = false;
        if (cy == top) {
            scrollDown(1);
        } else if (cy > 0) {
            cy--;
        }
    }

    private void scrollUp(int n) {
        for (int k = 0; k < n; k++) {
            if (!altOn && top == 0) {
                remember(grid[0]);
            }
            for (int y = top; y < bottom; y++) {
                grid[y] = grid[y + 1];
            }
            grid[bottom] = erased();
        }
    }

    private void scrollDown(int n) {
        for (int k = 0; k < n; k++) {
            for (int y = bottom; y > top; y--) {
                grid[y] = grid[y - 1];
            }
            grid[top] = erased();
        }
    }

    private Row erased() {
        Row r = new Row(cols);
        if (bg != DEFAULT) {
            Arrays.fill(r.bg, bg);
        }
        return r;
    }

    private void escape(int c) {
        state = TEXT;
        switch (c) {
            case '[':
                seq.setLength(0);
                state = CSI;
                break;
            case ']':
                seq.setLength(0);
                state = OSC;
                break;
            case 'P':
            case '^':
            case '_':
            case 'X':
                state = STRING;
                break;
            case '(':
                charsetSlot = 0;
                state = CHARSET;
                break;
            case ')':
                charsetSlot = 1;
                state = CHARSET;
                break;
            case '*':
            case '+':
            case '#':
            case '%':
            case ' ':
                state = SKIP1;
                break;
            case '7':
                saveCursor();
                break;
            case '8':
                restoreCursor();
                break;
            case 'D':
                lineFeed();
                break;
            case 'E':
                cx = 0;
                lineFeed();
                break;
            case 'H':
                if (cx < cols) {
                    tabs[cx] = true;
                }
                break;
            case 'M':
                reverseLineFeed();
                break;
            case 'c':
                reset();
                break;
            case '=':
                appKeypad = true;
                break;
            case '>':
                appKeypad = false;
                break;
            default:
                break;
        }
    }

    private void reset() {
        main = blank(cols, rows);
        alt = blank(cols, rows);
        grid = main;
        altOn = false;
        cx = cy = 0;
        wrapNext = false;
        fg = bg = DEFAULT;
        attr = 0;
        top = 0;
        bottom = rows - 1;
        originMode = insertMode = appCursor = appKeypad = bracketedPaste = false;
        autoWrap = cursorOn = true;
        drawing[0] = drawing[1] = false;
        charset = 0;
        tabs = tabStops(cols);
    }

    private void saveCursor() {
        saved[altOn ? 1 : 0] = new int[] { cx, cy, fg, bg, attr, originMode ? 1 : 0, drawing[0] ? 1 : 0, drawing[1] ? 1 : 0, charset };
    }

    private void restoreCursor() {
        int[] s = saved[altOn ? 1 : 0];
        if (s == null) {
            cx = cy = 0;
            return;
        }
        cx = Math.min(s[0], cols - 1);
        cy = Math.min(s[1], rows - 1);
        fg = s[2];
        bg = s[3];
        attr = s[4];
        originMode = s[5] == 1;
        drawing[0] = s[6] == 1;
        drawing[1] = s[7] == 1;
        charset = s[8];
        wrapNext = false;
    }

    private void osc(String body) {
        int semi = body.indexOf(';');
        if (semi > 0 && (body.startsWith("0;") || body.startsWith("2;"))) {
            title = body.substring(semi + 1);
        }
    }

    private void csi(char cmd, String raw) {
        boolean priv = raw.startsWith("?");
        boolean other = raw.startsWith(">") || raw.startsWith("=") || raw.startsWith("<");
        String body = priv || other ? raw.substring(1) : raw;
        // letters between the numbers and the command ("$", " ", "!") belong to sequences this screen ignores
        boolean inter = false;
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch >= '0' && ch <= '9' || ch == ';' || ch == ':') {
                digits.append(ch == ':' ? ';' : ch);
            } else {
                inter = true;
            }
        }
        int[] p = numbers(digits.toString());
        if (other) {
            if (cmd == 'c' && raw.startsWith(">")) {
                reply.append("\u001b[>0;276;0c");
            }
            return;
        }
        if (inter) {
            if (cmd == 'p' && body.contains("!")) {
                reset(); // soft reset
            }
            return;
        }
        if (priv) {
            if (cmd == 'h' || cmd == 'l') {
                for (int n : p) {
                    mode(n, cmd == 'h');
                }
            } else if (cmd == 'n' && arg(p, 0, 0) == 6) {
                reply.append("\u001b[?").append(cy + 1).append(';').append(cursorX() + 1).append('R');
            }
            return;
        }
        int n = Math.max(1, arg(p, 0, 1));
        Row r = grid[cy];
        switch (cmd) {
            case 'A':
                cy = Math.max(cy >= top ? top : 0, cy - n);
                wrapNext = false;
                break;
            case 'B':
            case 'e':
                cy = Math.min(cy <= bottom ? bottom : rows - 1, cy + n);
                wrapNext = false;
                break;
            case 'C':
            case 'a':
                cx = Math.min(cols - 1, Math.min(cx, cols - 1) + n);
                wrapNext = false;
                break;
            case 'D':
                cx = Math.max(0, Math.min(cx, cols - 1) - n);
                wrapNext = false;
                break;
            case 'E':
                cy = Math.min(cy <= bottom ? bottom : rows - 1, cy + n);
                cx = 0;
                wrapNext = false;
                break;
            case 'F':
                cy = Math.max(cy >= top ? top : 0, cy - n);
                cx = 0;
                wrapNext = false;
                break;
            case 'G':
            case '`':
                cx = Math.min(cols - 1, n - 1);
                wrapNext = false;
                break;
            case 'd':
                moveTo(cx, n - 1);
                break;
            case 'H':
            case 'f':
                moveTo(Math.max(1, arg(p, 1, 1)) - 1, n - 1);
                break;
            case 'I':
                for (int i = 0; i < n; i++) {
                    tab();
                }
                break;
            case 'Z':
                for (int i = 0; i < n; i++) {
                    int x = Math.min(cx, cols - 1) - 1;
                    while (x > 0 && !tabs[x]) {
                        x--;
                    }
                    cx = Math.max(0, x);
                }
                wrapNext = false;
                break;
            case 'J':
                eraseDisplay(arg(p, 0, 0));
                break;
            case 'K': {
                int how = arg(p, 0, 0);
                int from = how == 0 ? Math.min(cx, cols - 1) : 0;
                int to = how == 1 ? Math.min(cx, cols - 1) : cols - 1;
                for (int i = from; i <= to; i++) {
                    eraseCell(r, i);
                }
                if (how != 1) {
                    r.wrapped = false;
                }
                wrapNext = false;
                break;
            }
            case 'L':
                if (cy >= top && cy <= bottom) {
                    int keepTop = top;
                    top = cy;
                    scrollDown(Math.min(n, bottom - cy + 1));
                    top = keepTop;
                    cx = 0;
                }
                break;
            case 'M':
                if (cy >= top && cy <= bottom) {
                    int keepTop = top;
                    boolean wasAlt = altOn;
                    top = cy;
                    altOn = true; // deleted lines are not kept above the screen
                    scrollUp(Math.min(n, bottom - cy + 1));
                    altOn = wasAlt;
                    top = keepTop;
                    cx = 0;
                }
                break;
            case 'P':
                shiftLeft(r, Math.min(cx, cols - 1), Math.min(n, cols));
                break;
            case '@':
                shiftRight(r, Math.min(cx, cols - 1), Math.min(n, cols));
                break;
            case 'X':
                for (int i = Math.min(cx, cols - 1); i < Math.min(cols, cx + n); i++) {
                    eraseCell(r, i);
                }
                break;
            case 'S':
                scrollUp(Math.min(n, rows));
                break;
            case 'T':
                scrollDown(Math.min(n, rows));
                break;
            case 'b':
                for (int i = 0; i < Math.min(n, cols * rows); i++) {
                    put(lastChar, width(lastChar));
                }
                break;
            case 'm':
                colors(p);
                break;
            case 'r': {
                int t = Math.max(1, arg(p, 0, 1)) - 1;
                int b = Math.min(rows, arg(p, 1, rows) <= 0 ? rows : arg(p, 1, rows)) - 1;
                if (t < b) {
                    top = t;
                    bottom = b;
                } else {
                    top = 0;
                    bottom = rows - 1;
                }
                moveTo(0, 0);
                break;
            }
            case 's':
                saveCursor();
                break;
            case 'u':
                restoreCursor();
                break;
            case 'g':
                if (arg(p, 0, 0) == 3) {
                    Arrays.fill(tabs, false);
                } else if (cx < cols) {
                    tabs[cx] = false;
                }
                break;
            case 'h':
            case 'l':
                for (int v : p) {
                    if (v == 4) {
                        insertMode = cmd == 'h';
                    }
                }
                break;
            case 'n':
                if (arg(p, 0, 0) == 6) {
                    reply.append("\u001b[").append(cy - (originMode ? top : 0) + 1).append(';').append(cursorX() + 1).append('R');
                } else if (arg(p, 0, 0) == 5) {
                    reply.append("\u001b[0n");
                }
                break;
            case 'c':
                reply.append("\u001b[?1;2c"); // a VT100 with advanced video
                break;
            case 't':
                if (arg(p, 0, 0) == 18) {
                    reply.append("\u001b[8;").append(rows).append(';').append(cols).append('t');
                }
                break;
            default:
                break;
        }
    }

    private void moveTo(int x, int y) {
        int low = originMode ? top : 0, high = originMode ? bottom : rows - 1;
        cy = Math.max(low, Math.min(high, y + low));
        cx = Math.max(0, Math.min(cols - 1, x));
        wrapNext = false;
    }

    private void eraseDisplay(int how) {
        wrapNext = false;
        if (how == 3) {
            back.clear();
            backStart = 0;
            return;
        }
        int from = how == 0 ? cy : 0, to = how == 1 ? cy : rows - 1;
        for (int y = from; y <= to; y++) {
            int a = how == 0 && y == cy ? Math.min(cx, cols - 1) : 0;
            int b = how == 1 && y == cy ? Math.min(cx, cols - 1) : cols - 1;
            for (int x = a; x <= b; x++) {
                eraseCell(grid[y], x);
            }
            if (b == cols - 1) {
                grid[y].wrapped = false;
            }
        }
    }

    private void mode(int n, boolean on) {
        switch (n) {
            case 1:
                appCursor = on;
                break;
            case 6:
                originMode = on;
                moveTo(0, 0);
                break;
            case 7:
                autoWrap = on;
                break;
            case 25:
                cursorOn = on;
                break;
            case 47:
            case 1047:
                screen(on, false);
                break;
            case 1048:
                if (on) {
                    saveCursor();
                } else {
                    restoreCursor();
                }
                break;
            case 1049:
                screen(on, true);
                break;
            case 2004:
                bracketedPaste = on;
                break;
            default:
                break;
        }
    }

    /** The second screen on or off. With {@code cursor}, the first screen's cursor is kept and put back. */
    private void screen(boolean on, boolean cursor) {
        if (on == altOn) {
            return;
        }
        if (on) {
            if (cursor) {
                saveCursor();
            }
            alt = blank(cols, rows);
            grid = alt;
            altOn = true;
        } else {
            grid = main;
            altOn = false;
            if (cursor) {
                restoreCursor();
            }
        }
        top = 0;
        bottom = rows - 1;
        wrapNext = false;
    }

    private void colors(int[] p) {
        if (p.length == 0) {
            fg = bg = DEFAULT;
            attr = 0;
            return;
        }
        for (int i = 0; i < p.length; i++) {
            int n = Math.max(0, p[i]);
            if (n == 0) {
                fg = bg = DEFAULT;
                attr = 0;
            } else if (n == 1) {
                attr |= BOLD;
            } else if (n == 2) {
                attr |= DIM;
            } else if (n == 3) {
                attr |= ITALIC;
            } else if (n == 4) {
                attr |= UNDERLINE;
            } else if (n == 7) {
                attr |= REVERSE;
            } else if (n == 8) {
                attr |= HIDDEN;
            } else if (n == 21 || n == 22) {
                attr &= ~(BOLD | DIM);
            } else if (n == 23) {
                attr &= ~ITALIC;
            } else if (n == 24) {
                attr &= ~UNDERLINE;
            } else if (n == 27) {
                attr &= ~REVERSE;
            } else if (n == 28) {
                attr &= ~HIDDEN;
            } else if (n >= 30 && n <= 37) {
                fg = n - 30;
            } else if (n == 39) {
                fg = DEFAULT;
            } else if (n >= 40 && n <= 47) {
                bg = n - 40;
            } else if (n == 49) {
                bg = DEFAULT;
            } else if (n >= 90 && n <= 97) {
                fg = n - 90 + 8;
            } else if (n >= 100 && n <= 107) {
                bg = n - 100 + 8;
            } else if (n == 38 || n == 48) {
                int color = -1;
                if (i + 2 < p.length && p[i + 1] == 5) {
                    color = Math.max(0, Math.min(255, p[i + 2]));
                    i += 2;
                } else if (i + 4 < p.length && p[i + 1] == 2) {
                    color = RGB | (p[i + 2] & 255) << 16 | (p[i + 3] & 255) << 8 | (p[i + 4] & 255);
                    i += 4;
                } else {
                    break;
                }
                if (n == 38) {
                    fg = color;
                } else {
                    bg = color;
                }
            }
        }
    }

    private static int[] numbers(String s) {
        if (s.isEmpty()) {
            return new int[0];
        }
        String[] parts = s.split(";", -1);
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = parts[i].isEmpty() ? -1 : Math.min(99999, Integer.parseInt(parts[i]));
            } catch (NumberFormatException e) {
                out[i] = -1;
            }
        }
        return out;
    }

    private static int arg(int[] p, int i, int fallback) {
        return i < p.length && p[i] >= 0 ? p[i] : fallback;
    }

    /** DEC line drawing: what the characters from _ to ~ become. */
    private static final String LINES = " ◆▒␉␌␍␊°±␤␋┘┐┌└┼⎺⎻─⎼⎽├┤┴┬│≤≥π≠£·";

    /** How many cells a character takes: 0 for a joining accent, 2 for wide East Asian characters and emoji. */
    static int width(int c) {
        if (c < 0x300) {
            return 1;
        }
        if (c >= 0x300 && c <= 0x36f || c >= 0x200b && c <= 0x200f || c >= 0xfe00 && c <= 0xfe0f || c == 0xfeff
                || c >= 0x1ab0 && c <= 0x1aff || c >= 0x20d0 && c <= 0x20ff || c >= 0xe0100 && c <= 0xe01ef) {
            return 0;
        }
        if (c >= 0x1100 && c <= 0x115f || c >= 0x2e80 && c <= 0xa4cf && c != 0x303f || c >= 0xac00 && c <= 0xd7a3
                || c >= 0xf900 && c <= 0xfaff || c >= 0xfe30 && c <= 0xfe6f || c >= 0xff00 && c <= 0xff60
                || c >= 0xffe0 && c <= 0xffe6 || c >= 0x1f300 && c <= 0x1f64f || c >= 0x1f900 && c <= 0x1f9ff
                || c >= 0x20000 && c <= 0x3fffd) {
            return 2;
        }
        return 1;
    }

    private static final int[] BASE = {
            0xFF000000, 0xFFCD3131, 0xFF0DBC79, 0xFFE5E510, 0xFF2472C8, 0xFFBC3FBC, 0xFF11A8CD, 0xFFE5E5E5,
            0xFF666666, 0xFFF14C4C, 0xFF23D18B, 0xFFF5F543, 0xFF3B8EEA, 0xFFD670D6, 0xFF29B8DB, 0xFFFFFFFF };

    /** The color to paint: {@code color} is a palette number, {@link #DEFAULT}, or {@link #RGB} with the three bytes. */
    public static int argb(int color, boolean foreground) {
        if (color == DEFAULT) {
            return foreground ? 0xFFE6E6E6 : 0xFF000000;
        }
        if ((color & RGB) != 0) {
            return 0xFF000000 | color & 0xFFFFFF;
        }
        if (color < 16) {
            return BASE[color];
        }
        if (color < 232) {
            int n = color - 16;
            int r = n / 36, g = n / 6 % 6, b = n % 6;
            return 0xFF000000 | level(r) << 16 | level(g) << 8 | level(b);
        }
        int v = 8 + (color - 232) * 10;
        return 0xFF000000 | v << 16 | v << 8 | v;
    }

    private static int level(int n) {
        return n == 0 ? 0 : 55 + n * 40;
    }
}
