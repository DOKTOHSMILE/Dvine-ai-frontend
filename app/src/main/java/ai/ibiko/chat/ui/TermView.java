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
                showKeyboard();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (TermView.this.onLongPress != null) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    TermView.this.onLongPress.run();
                }
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
        pinch.onTouchEvent(e);
        if (!pinch.isInProgress() && e.getPointerCount() == 1) {
            touch.onTouchEvent(e);
        }
        return true;
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
                for (int x = 0; x < n; x++) {
                    int attr = row.attr[x];
                    if ((attr & TermEmulator.WIDE_TAIL) != 0) {
                        continue;
                    }
                    boolean reverse = (attr & TermEmulator.REVERSE) != 0;
                    int fg = TermEmulator.argb(reverse ? row.bg[x] : row.fg[x], !reverse);
                    int bg = TermEmulator.argb(reverse ? row.fg[x] : row.bg[x], reverse);
                    if ((attr & TermEmulator.BOLD) != 0 && !reverse && row.fg[x] < 8) {
                        fg = TermEmulator.argb(row.fg[x] + 8, true); // bold brightens the eight plain colors
                    }
                    if ((attr & TermEmulator.DIM) != 0) {
                        fg = fg & 0xFF000000 | (fg >> 1 & 0x7F7F7F) + 0x202020;
                    }
                    int ch = row.ch[x];
                    boolean wide = x + 1 < n && (row.attr[x + 1] & TermEmulator.WIDE_TAIL) != 0;
                    float cellLeft = left + x * cellW;
                    if (bg != 0xFF000000) {
                        fill.setColor(bg);
                        canvas.drawRect(cellLeft, lineTop, cellLeft + (wide ? 2 : 1) * cellW, lineTop + cellH, fill);
                    }
                    if (ch == ' ' || ch == 0 || (attr & TermEmulator.HIDDEN) != 0) {
                        continue;
                    }
                    paint.setColor(fg);
                    paint.setFakeBoldText((attr & TermEmulator.BOLD) != 0);
                    paint.setTextSkewX((attr & TermEmulator.ITALIC) != 0 ? -0.2f : 0f);
                    paint.setUnderlineText((attr & TermEmulator.UNDERLINE) != 0);
                    int len = Character.toChars(ch, one, 0);
                    canvas.drawText(one, 0, len, cellLeft, lineTop + baseline, paint);
                }
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
