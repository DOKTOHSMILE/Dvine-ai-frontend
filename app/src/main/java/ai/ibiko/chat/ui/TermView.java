package ai.ibiko.chat.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import ai.ibiko.linux.TermEmulator;
import ai.ibiko.linux.TermKeys;
import ai.ibiko.linux.TermSession;

/**
 * The terminal's screen: the grid of characters with their colors and the cursor, drawn as a terminal draws them.
 * Every key goes straight to the program in the terminal. A drag scrolls back through earlier lines, a pinch changes
 * the text size (the program is told the new number of rows and columns).
 */
public final class TermView extends View {
    /** CTRL and ALT from the key bar: held for the next key only. */
    public interface Modifiers {
        boolean ctrl();

        boolean alt();

        void used();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint();
    private TermSession session;
    private Modifiers mods;
    private float textPx;
    private float cellW, cellH, baseline;
    /** Lines scrolled back from the bottom. */
    private int scrolled;
    private float dragLeft;
    private final char[] one = new char[2];
    private Runnable onTextSize;
    private Runnable onLongPress;

    /** A long press on the screen (copy and paste). */
    public void onLongPress(Runnable r) {
        onLongPress = r;
    }

    private final ScaleGestureDetector pinch;
    private final GestureDetector touch;

    public TermView(Context c, float textSp) {
        super(c);
        setFocusable(true);
        setFocusableInTouchMode(true);
        paint.setTypeface(Typeface.MONOSPACE);
        setTextSp(textSp);
        pinch = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float sp = Math.max(7f, Math.min(30f, textSp() * d.getScaleFactor()));
                setTextSp(sp);
                fit();
                if (onTextSize != null) {
                    onTextSize.run();
                }
                return true;
            }
        });
        touch = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                dragLeft = 0;
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (selecting) {
                    endSelection(); // a tap elsewhere ends the selection, as Termux does
                    return true;
                }
                showKeyboard();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                selectWordAt(e.getX(), e.getY());
            }

            @Override
            public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                if (session == null || cellH <= 0) {
                    return false;
                }
                dragLeft += dy;
                int lines = (int) (dragLeft / cellH);
                if (lines == 0) {
                    return true;
                }
                dragLeft -= lines * cellH;
                TermEmulator s = session.screen();
                if (s.altScreen()) {
                    // a full-screen program scrolls itself: the drag is sent as arrow keys
                    String key = TermKeys.key(lines > 0 ? TermKeys.DOWN : TermKeys.UP, false, false, s.appCursorKeys());
                    for (int i = 0; i < Math.min(40, Math.abs(lines)); i++) {
                        session.write(key);
                    }
                } else {
                    scrolled = Math.max(0, Math.min(s.scrollback(), scrolled - lines));
                    invalidate();
                }
                return true;
            }
        });
    }

    public void attach(TermSession s, Modifiers m) {
        session = s;
        mods = m;
        fit();
        invalidate();
    }

    public void onTextSize(Runnable r) {
        onTextSize = r;
    }

    public float textSp() {
        return textPx / getResources().getDisplayMetrics().scaledDensity;
    }

    public void setTextSp(float sp) {
        textPx = sp * getResources().getDisplayMetrics().scaledDensity;
        paint.setTextSize(textPx);
        Paint.FontMetrics m = paint.getFontMetrics();
        cellW = paint.measureText("M");
        // every printable ASCII character as wide as "M": then a run is one drawText (true for the monospace font)
        monospaced = Math.abs(paint.measureText("i") - cellW) < 0.01f && Math.abs(paint.measureText("W") - cellW) < 0.01f
                && Math.abs(paint.measureText("iiiiiiiiii") - cellW * 10) < 0.05f;
        cellH = (float) Math.ceil(m.descent - m.ascent);
        baseline = -m.ascent;
    }

    /** Scrolls back (positive) or forward by screens. */
    public void page(int screens) {
        if (session == null) {
            return;
        }
        TermEmulator s = session.screen();
        scrolled = Math.max(0, Math.min(s.scrollback(), scrolled + screens * Math.max(1, s.rows() - 1)));
        invalidate();
    }

    /** New output or a key: back to the live screen. */
    public void toBottom() {
        if (scrolled != 0) {
            scrolled = 0;
            invalidate();
        }
    }

    public void showKeyboard() {
        requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fit();
    }

    /** Tells the program how many columns and rows fit. */
    private void fit() {
        if (session == null || cellW <= 0 || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        int cols = Math.max(8, (int) ((getWidth() - getPaddingLeft() - getPaddingRight()) / cellW));
        int rows = Math.max(4, (int) ((getHeight() - getPaddingTop() - getPaddingBottom()) / cellH));
        session.resize(cols, rows);
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (selecting && handleTouch(e)) {
            return true;
        }
        pinch.onTouchEvent(e);
        if (!pinch.isInProgress() && e.getPointerCount() == 1) {
            touch.onTouchEvent(e);
        }
        return true;
    }

    /** One row's run of characters, reused frame to frame. */
    private char[] run = new char[256];
    /** The font gives every ASCII character the cell's width, so a run can be drawn in one call. */
    private boolean monospaced = true;

    private static int fgOf(TermEmulator.Row row, int x) {
        int attr = row.attr[x];
        boolean reverse = (attr & TermEmulator.REVERSE) != 0;
        int fg = TermEmulator.argb(reverse ? row.bg[x] : row.fg[x], !reverse);
        if ((attr & TermEmulator.BOLD) != 0 && !reverse && row.fg[x] < 8) {
            fg = TermEmulator.argb(row.fg[x] + 8, true); // bold brightens the eight plain colors
        }
        if ((attr & TermEmulator.DIM) != 0) {
            fg = fg & 0xFF000000 | (fg >> 1 & 0x7F7F7F) + 0x202020;
        }
        return fg;
    }

    private static int bgOf(TermEmulator.Row row, int x) {
        boolean reverse = (row.attr[x] & TermEmulator.REVERSE) != 0;
        return TermEmulator.argb(reverse ? row.fg[x] : row.bg[x], reverse);
    }

    private void style(int fg, int attr) {
        paint.setColor(fg);
        paint.setFakeBoldText((attr & TermEmulator.BOLD) != 0);
        paint.setTextSkewX((attr & TermEmulator.ITALIC) != 0 ? -0.2f : 0f);
        paint.setUnderlineText((attr & TermEmulator.UNDERLINE) != 0);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xFF000000);
        if (session == null) {
            return;
        }
        TermEmulator s = session.screen();
        synchronized (s) {
            int rows = s.rows(), cols = s.cols();
            int kept = s.scrollback();
            int back = Math.min(scrolled, kept);
            float left = getPaddingLeft(), topPx = getPaddingTop();
            for (int y = 0; y < rows; y++) {
                TermEmulator.Row row = s.line(kept - back + y);
                float lineTop = topPx + y * cellH;
                int n = Math.min(cols, row.ch.length);
                // drawn in runs: neighbouring cells of one colour and style are one rectangle and one drawText, not
                // one call each (a full screen was thousands of calls a frame, every time output came in)
                int x = 0;
                while (x < n) {
                    int attr = row.attr[x];
                    if ((attr & TermEmulator.WIDE_TAIL) != 0) {
                        x++;
                        continue;
                    }
                    int fg = fgOf(row, x), bg = bgOf(row, x);
                    int ch = row.ch[x];
                    boolean wide = x + 1 < n && (row.attr[x + 1] & TermEmulator.WIDE_TAIL) != 0;
                    float cellLeft = left + x * cellW;
                    if (wide || ch > 0x7E) {
                        // a wide or non-ASCII character: on its own, at its cell
                        if (bg != 0xFF000000) {
                            fill.setColor(bg);
                            canvas.drawRect(cellLeft, lineTop, cellLeft + (wide ? 2 : 1) * cellW, lineTop + cellH, fill);
                        }
                        if (ch != 0 && (attr & TermEmulator.HIDDEN) == 0) {
                            style(fg, attr);
                            int len = Character.toChars(ch, one, 0);
                            canvas.drawText(one, 0, len, cellLeft, lineTop + baseline, paint);
                        }
                        x += wide ? 2 : 1;
                        continue;
                    }
                    int end = x + 1;
                    while (end < n && row.attr[end] == attr && row.ch[end] <= 0x7E && fgOf(row, end) == fg && bgOf(row, end) == bg
                            && (end + 1 >= n || (row.attr[end + 1] & TermEmulator.WIDE_TAIL) == 0)) {
                        end++;
                    }
                    if (bg != 0xFF000000) {
                        fill.setColor(bg);
                        canvas.drawRect(cellLeft, lineTop, left + end * cellW, lineTop + cellH, fill);
                    }
                    if ((attr & TermEmulator.HIDDEN) == 0) {
                        if (run.length < end - x) {
                            run = new char[end - x];
                        }
                        int count = 0;
                        boolean any = false;
                        for (int i = x; i < end; i++) {
                            char c = row.ch[i] == 0 ? ' ' : (char) row.ch[i];
                            run[count++] = c;
                            any |= c != ' ';
                        }
                        if (any) {
                            style(fg, attr);
                            if (monospaced) {
                                canvas.drawText(run, 0, count, cellLeft, lineTop + baseline, paint);
                            } else {
                                for (int i = 0; i < count; i++) {
                                    if (run[i] != ' ') {
                                        canvas.drawText(run, i, 1, cellLeft + i * cellW, lineTop + baseline, paint);
                                    }
                                }
                            }
                        }
                    }
                    x = end;
                }
            }
            if (selecting) {
                fill.setColor(SELECTION);
                int[] a = selFirst(), b = selLast();
                for (int line = a[0]; line <= b[0]; line++) {
                    int y = line - (kept - back);
                    if (y < 0 || y >= rows) {
                        continue;
                    }
                    int from = line == a[0] ? a[1] : 0, to = line == b[0] ? b[1] : cols - 1;
                    canvas.drawRect(left + from * cellW, topPx + y * cellH, left + (to + 1) * cellW, topPx + (y + 1) * cellH, fill);
                }
                drawHandle(canvas, a, kept - back, true);
                drawHandle(canvas, b, kept - back, false);
            }
            if (back == 0 && s.cursorVisible()) {
                int cx = s.cursorX(), cy = s.cursorY();
                float x = left + cx * cellW, y = topPx + cy * cellH;
                fill.setColor(hasFocus() ? 0xFFE6E6E6 : 0xFF808080);
                canvas.drawRect(x, y, x + cellW, y + cellH, fill);
                TermEmulator.Row row = s.row(cy);
                int ch = cx < row.ch.length ? row.ch[cx] : ' ';
                if (ch != ' ' && ch != 0) {
                    paint.setColor(0xFF000000);
                    paint.setFakeBoldText(false);
                    paint.setTextSkewX(0f);
                    paint.setUnderlineText(false);
                    int len = Character.toChars(ch, one, 0);
                    canvas.drawText(one, 0, len, x, y + baseline, paint);
                }
            }
            if (back > 0) {
                // scrolled back: a thin bar on the right shows where
                float h = getHeight(), total = kept + rows;
                fill.setColor(0x80E6E6E6);
                float barTop = h * (kept - back) / total, barH = Math.max(cellH, h * rows / total);
                canvas.drawRect(getWidth() - 6, barTop, getWidth(), barTop + barH, fill);
            }
        }
    }

    // ------------------------------------------------------------ selecting text (as Termux does)

    static final int SELECTION = 0x665B8CFF;
    static final int HANDLE = 0xFF5B8CFF;
    /** The selection's two ends: {absolute line, column}; absolute lines count the scrollback first. */
    private final int[] selA = new int[2], selB = new int[2];
    private boolean selecting;
    /** Which handle a finger is dragging: 0 none, 1 the start, 2 the end. */
    private int dragging;
    private android.widget.PopupWindow bar;

    public boolean selecting() {
        return selecting;
    }

    private int[] selFirst() {
        return selA[0] < selB[0] || selA[0] == selB[0] && selA[1] <= selB[1] ? selA : selB;
    }

    private int[] selLast() {
        return selFirst() == selA ? selB : selA;
    }

    /** The cell under a point: {absolute line, column}. */
    private int[] cellAt(float x, float y) {
        TermEmulator s = session.screen();
        int kept = s.scrollback(), back = Math.min(scrolled, kept);
        int col = Math.max(0, Math.min(s.cols() - 1, (int) ((x - getPaddingLeft()) / cellW)));
        int row = Math.max(0, Math.min(s.rows() - 1, (int) ((y - getPaddingTop()) / cellH)));
        return new int[] {kept - back + row, col};
    }

    /** A long press: the word under the finger is selected, with handles and the Copy / Paste / More bar. */
    void selectWordAt(float x, float y) {
        if (session == null || cellW <= 0) {
            return;
        }
        int[] at = cellAt(x, y);
        TermEmulator s = session.screen();
        String text;
        synchronized (s) {
            text = s.line(at[0]).text();
        }
        int from = Math.min(at[1], Math.max(0, text.length() - 1)), to = from;
        if (from < text.length() && !Character.isWhitespace(text.charAt(from))) {
            while (from > 0 && !Character.isWhitespace(text.charAt(from - 1))) {
                from--;
            }
            while (to + 1 < text.length() && !Character.isWhitespace(text.charAt(to + 1))) {
                to++;
            }
        }
        selA[0] = at[0];
        selA[1] = from;
        selB[0] = at[0];
        selB[1] = to;
        selecting = true;
        invalidate();
        showBar();
    }

    /** Everything on the screen and in the scrollback. */
    void selectAll() {
        if (session == null) {
            return;
        }
        TermEmulator s = session.screen();
        selA[0] = 0;
        selA[1] = 0;
        selB[0] = s.scrollback() + s.rows() - 1;
        selB[1] = s.cols() - 1;
        selecting = true;
        invalidate();
        showBar();
    }

    /** The selected text, its lines' trailing spaces dropped. */
    public String selectedText() {
        if (!selecting || session == null) {
            return "";
        }
        TermEmulator s = session.screen();
        int[] a = selFirst(), b = selLast();
        StringBuilder out = new StringBuilder();
        synchronized (s) {
            for (int line = a[0]; line <= b[0]; line++) {
                String t = s.line(line).text();
                int from = line == a[0] ? Math.min(a[1], t.length()) : 0;
                int to = line == b[0] ? Math.min(b[1] + 1, t.length()) : t.length();
                out.append(from < to ? t.substring(from, to) : "");
                if (line < b[0]) {
                    out.append('\n');
                }
            }
        }
        return out.toString();
    }

    public void endSelection() {
        selecting = false;
        dragging = 0;
        if (bar != null) {
            bar.dismiss();
            bar = null;
        }
        invalidate();
    }

    private void drawHandle(Canvas canvas, int[] end, int firstShown, boolean start) {
        int y = end[0] - firstShown;
        if (y < 0 || session == null || y >= session.screen().rows()) {
            return;
        }
        float r = 9 * getResources().getDisplayMetrics().density;
        float cx = getPaddingLeft() + (start ? end[1] : end[1] + 1) * cellW;
        float cy = getPaddingTop() + (y + 1) * cellH + r;
        fill.setColor(HANDLE);
        canvas.drawCircle(cx, cy, r, fill);
        canvas.drawRect(start ? cx : cx - r, cy - r, start ? cx + r : cx, cy, fill); // the teardrop's point at the text
    }

    /** A finger on a handle drags that end of the selection. True when the touch was the selection's. */
    private boolean handleTouch(MotionEvent e) {
        float r = 22 * getResources().getDisplayMetrics().density;
        TermEmulator s = session.screen();
        int first = s.scrollback() - Math.min(scrolled, s.scrollback());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                dragging = 0;
                for (int which = 1; which <= 2; which++) {
                    int[] end = which == 1 ? selFirst() : selLast();
                    float hx = getPaddingLeft() + (which == 1 ? end[1] : end[1] + 1) * cellW;
                    float hy = getPaddingTop() + (end[0] - first + 1) * cellH + r / 2;
                    if (Math.hypot(e.getX() - hx, e.getY() - hy) < r) {
                        dragging = which;
                        // the dragged end is always selA from here on
                        int[] other = which == 1 ? selLast() : selFirst();
                        int[] mine = which == 1 ? selFirst() : selLast();
                        int[] o = other.clone(), m = mine.clone();
                        selA[0] = m[0];
                        selA[1] = m[1];
                        selB[0] = o[0];
                        selB[1] = o[1];
                        if (bar != null) {
                            bar.dismiss();
                            bar = null;
                        }
                        return true;
                    }
                }
                return false;
            }
            case MotionEvent.ACTION_MOVE:
                if (dragging == 0) {
                    return false;
                }
                int[] at = cellAt(e.getX(), e.getY() - cellH); // the finger sits under the line it drags
                selA[0] = at[0];
                selA[1] = at[1];
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging == 0) {
                    return false;
                }
                dragging = 0;
                showBar();
                return true;
            default:
                return dragging != 0;
        }
    }

    /** The small floating bar above the selection: Copy, Paste, More. */
    private void showBar() {
        if (bar != null) {
            bar.dismiss();
        }
        android.content.Context c = getContext();
        float d = getResources().getDisplayMetrics().density;
        android.widget.LinearLayout row = new android.widget.LinearLayout(c);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xF2303036);
        bg.setCornerRadius(24 * d);
        row.setBackground(bg);
        row.setElevation(6 * d);
        row.setPadding(Math.round(6 * d), 0, Math.round(6 * d), 0);
        String[] labels = {"Copy", "Paste", "More\u2026"};
        for (int i = 0; i < labels.length; i++) {
            final int which = i;
            android.widget.TextView b = new android.widget.TextView(c);
            b.setText(labels[i]);
            b.setTextColor(0xFFF2F2F7);
            b.setTextSize(15);
            b.setGravity(android.view.Gravity.CENTER);
            b.setPadding(Math.round(16 * d), Math.round(12 * d), Math.round(16 * d), Math.round(12 * d));
            b.setMinHeight(Math.round(44 * d));
            android.util.TypedValue tv = new android.util.TypedValue();
            c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            b.setBackgroundResource(tv.resourceId);
            b.setOnClickListener(v -> barAction(which, v));
            row.addView(b);
        }
        bar = new android.widget.PopupWindow(row, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, false);
        bar.setOutsideTouchable(false);
        row.measure(android.view.View.MeasureSpec.UNSPECIFIED, android.view.View.MeasureSpec.UNSPECIFIED);
        TermEmulator s = session.screen();
        int first = s.scrollback() - Math.min(scrolled, s.scrollback());
        int[] a = selFirst();
        int[] loc = new int[2];
        getLocationInWindow(loc);
        int x = loc[0] + Math.max(0, Math.min(getWidth() - row.getMeasuredWidth(),
                Math.round(getPaddingLeft() + a[1] * cellW - row.getMeasuredWidth() / 2f)));
        int y = loc[1] + Math.round(getPaddingTop() + (a[0] - first) * cellH) - row.getMeasuredHeight() - Math.round(8 * d);
        if (y < loc[1]) {
            y = loc[1] + Math.round(getPaddingTop() + (selLast()[0] - first + 1) * cellH + 28 * d); // no room above: below
        }
        if (getWindowToken() != null) {
            bar.showAtLocation(this, android.view.Gravity.NO_GRAVITY, x, y);
        }
    }

    private void barAction(int which, View anchor) {
        android.content.Context c = getContext();
        android.content.ClipboardManager clip = (android.content.ClipboardManager) c.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (which == 0) {
            if (clip != null) {
                clip.setPrimaryClip(android.content.ClipData.newPlainText("terminal", selectedText()));
            }
            endSelection();
        } else if (which == 1) {
            android.content.ClipData data = clip == null ? null : clip.getPrimaryClip();
            CharSequence t = data == null || data.getItemCount() == 0 ? null : data.getItemAt(0).coerceToText(c);
            endSelection();
            paste(t == null ? "" : t.toString());
        } else {
            android.widget.PopupMenu more = new android.widget.PopupMenu(c, anchor);
            more.getMenu().add(0, 1, 0, "Select all");
            more.getMenu().add(0, 2, 1, "Copy all");
            more.getMenu().add(0, 3, 2, "Share");
            more.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    selectAll();
                } else if (item.getItemId() == 2) {
                    if (clip != null && session != null) {
                        clip.setPrimaryClip(android.content.ClipData.newPlainText("terminal", session.screen().text()));
                    }
                    endSelection();
                } else {
                    String text = selectedText();
                    endSelection();
                    android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND)
                            .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text);
                    c.startActivity(android.content.Intent.createChooser(send, "Share"));
                }
                return true;
            });
            more.show();
        }
    }

    // ------------------------------------------------------------ the keyboard

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo out) {
        // a password-style field with no suggestions: the keyboard sends each letter as it is typed
        out.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        out.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_NONE;
        return new BaseInputConnection(this, true) {
            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                super.commitText(text, newCursorPosition);
                flush();
                return true;
            }

            @Override
            public boolean finishComposingText() {
                super.finishComposingText();
                flush();
                return true;
            }

            @Override
            public boolean deleteSurroundingText(int before, int after) {
                for (int i = 0; i < Math.max(1, before); i++) {
                    send(TermKeys.key(TermKeys.BACKSPACE, false, false, false));
                }
                return super.deleteSurroundingText(before, after);
            }

            private void flush() {
                Editable e = getEditable();
                if (e == null || e.length() == 0) {
                    return;
                }
                String typed = e.toString();
                e.clear();
                type(typed);
            }
        };
    }

    /** Typed or pasted letters, with CTRL or ALT from the key bar applied to them. */
    public void type(String text) {
        boolean ctrl = mods != null && mods.ctrl(), alt = mods != null && mods.alt();
        send(TermKeys.typed(text, ctrl, alt));
        if ((ctrl || alt) && mods != null) {
            mods.used();
        }
    }

    /** A named key ({@link TermKeys#UP}…). */
    public void key(int key) {
        if (session == null) {
            return;
        }
        boolean ctrl = mods != null && mods.ctrl(), alt = mods != null && mods.alt();
        send(TermKeys.key(key, ctrl, alt, session.screen().appCursorKeys()));
        if ((ctrl || alt) && mods != null) {
            mods.used();
        }
    }

    public void paste(String text) {
        if (session != null && text != null && !text.isEmpty()) {
            send(TermKeys.paste(text, session.screen().bracketedPaste()));
        }
    }

    private void send(String bytes) {
        if (session == null || bytes.isEmpty()) {
            return;
        }
        toBottom();
        session.write(bytes);
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (session == null || e.isSystem()) {
            return super.onKeyDown(code, e);
        }
        int named = named(code);
        boolean ctrl = e.isCtrlPressed() || mods != null && mods.ctrl();
        boolean alt = e.isAltPressed() || mods != null && mods.alt();
        String out;
        if (named != 0) {
            out = TermKeys.key(named, ctrl, alt, session.screen().appCursorKeys());
        } else {
            int ch = e.getUnicodeChar(e.getMetaState() & ~(KeyEvent.META_CTRL_MASK | KeyEvent.META_ALT_MASK));
            if (ch == 0 || (ch & android.view.KeyCharacterMap.COMBINING_ACCENT) != 0) {
                return super.onKeyDown(code, e);
            }
            out = TermKeys.typed(new String(Character.toChars(ch)), ctrl, alt);
        }
        send(out);
        if (mods != null && (mods.ctrl() || mods.alt())) {
            mods.used();
        }
        return true;
    }

    private static int named(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                return TermKeys.ENTER;
            case KeyEvent.KEYCODE_DEL:
                return TermKeys.BACKSPACE;
            case KeyEvent.KEYCODE_FORWARD_DEL:
                return TermKeys.DELETE;
            case KeyEvent.KEYCODE_TAB:
                return TermKeys.TAB;
            case KeyEvent.KEYCODE_ESCAPE:
                return TermKeys.ESCAPE;
            case KeyEvent.KEYCODE_DPAD_UP:
                return TermKeys.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return TermKeys.DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return TermKeys.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return TermKeys.RIGHT;
            case KeyEvent.KEYCODE_MOVE_HOME:
                return TermKeys.HOME;
            case KeyEvent.KEYCODE_MOVE_END:
                return TermKeys.END;
            case KeyEvent.KEYCODE_PAGE_UP:
                return TermKeys.PAGE_UP;
            case KeyEvent.KEYCODE_PAGE_DOWN:
                return TermKeys.PAGE_DOWN;
            case KeyEvent.KEYCODE_INSERT:
                return TermKeys.INSERT;
            default:
                if (code >= KeyEvent.KEYCODE_F1 && code <= KeyEvent.KEYCODE_F12) {
                    return TermKeys.F1 + code - KeyEvent.KEYCODE_F1;
                }
                return 0;
        }
    }
}
