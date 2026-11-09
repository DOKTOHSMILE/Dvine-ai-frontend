package ai.dvine.chat.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.ScaleGestureDetector;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ai.dvine.linux.AlpineRun;
import ai.dvine.linux.Guest;
import ai.dvine.linux.LinuxRoot;

/**
 * A normal terminal. The prompt and the cursor are the last line of the session, not a separate box.
 * Alpine runs the commands. Termux is not opened.
 */
public final class TerminalActivity extends AppCompatActivity {
    private static final String WELCOME = "Alpine Linux 3.22.5\n";

    private TextView transcript;
    private EditText input;
    private ScrollView scroll;
    private final StringBuilder log = new StringBuilder();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<String> history = new ArrayList<String>();
    private final Handler blinker = new Handler(Looper.getMainLooper());
    private int historyAt = 0;
    private AlpineRun.Job job;
    private String cwd = "/root";
    private volatile boolean closed;
    private volatile boolean linuxUp;
    private volatile boolean busy;
    /** Follow new lines. Turned off when the user scrolls up to read. */
    private boolean stick = true;
    private boolean moving;
    private boolean cursorOn = true;
    private boolean ctrl;
    private boolean alt;
    private boolean editing;
    private float textSp = 14f;
    private TextView ctrlKey;
    private TextView altKey;
    private final Runnable blink = new Runnable() {
        @Override
        public void run() {
            cursorOn = !cursorOn;
            paint(false);
            blinker.postDelayed(this, 530);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        textSp = getPreferences(MODE_PRIVATE).getFloat("text_sp", 14f);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFF000000);

        scroll = new ZoomScroll(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF000000);
        HorizontalScrollView wide = new HorizontalScrollView(this);
        wide.setFillViewport(true);
        transcript = Ui.text(this, "", textSp, 0xFFE6E6E6, false);
        transcript.setTypeface(android.graphics.Typeface.MONOSPACE);
        transcript.setTextIsSelectable(true);
        int pad = Ui.dp(this, 8);
        transcript.setPadding(pad, pad, pad, pad);
        transcript.setOnClickListener(v -> keyboard());
        transcript.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom != oldBottom) {
                follow();
            }
        });
        wide.addView(transcript);
        scroll.addView(wide);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(0xFF111111);
        bar.addView(keys("ESC", "CTRL", "ALT", "TAB", "/", "-", "HOME", "\u2191", "END", "PGUP"));
        bar.addView(keys("\u2190", "\u2193", "\u2192", "PGDN"));
        page.addView(bar);

        input = new EditText(this);
        input.setSingleLine(true);
        input.setBackgroundColor(0);
        input.setTextColor(0);
        input.setHintTextColor(0);
        input.setCursorVisible(false);
        input.setAlpha(0f);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE || action == EditorInfo.IME_ACTION_GO
                    || action == EditorInfo.IME_ACTION_SEND) {
                runLine();
                return true;
            }
            return false;
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (editing || count != 1 || (!ctrl && !alt)) {
                    return;
                }
                char ch = s.charAt(start);
                editing = true;
                input.getText().delete(start, start + 1);
                editing = false;
                modified(ch);
            }

            @Override
            public void afterTextChanged(Editable s) {
                paint(false);
            }
        });
        page.addView(input, new LinearLayout.LayoutParams(1, 1));
        setContentView(page);
        Ui.fitInsets(page);
        blinker.postDelayed(blink, 530);
        boot();
    }

    /** Sees every touch, including pinches, before the text takes them. */
    private final class ZoomScroll extends ScrollView {
        private final ScaleGestureDetector scale = new ScaleGestureDetector(TerminalActivity.this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        textSp = Math.max(8f, Math.min(28f, textSp * detector.getScaleFactor()));
                        transcript.setTextSize(textSp);
                        getPreferences(MODE_PRIVATE).edit().putFloat("text_sp", textSp).apply();
                        return true;
                    }
                });
        private final GestureDetector taps = new GestureDetector(TerminalActivity.this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(android.view.MotionEvent e) {
                        textSp = 14f;
                        transcript.setTextSize(textSp);
                        getPreferences(MODE_PRIVATE).edit().putFloat("text_sp", textSp).apply();
                        return true;
                    }

                    @Override
                    public boolean onSingleTapUp(android.view.MotionEvent e) {
                        keyboard();
                        return true;
                    }
                });

        ZoomScroll(android.content.Context context) {
            super(context);
        }

        @Override
        public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
            taps.onTouchEvent(ev);
            scale.onTouchEvent(ev);
            return super.dispatchTouchEvent(ev);
        }

        @Override
        protected void onScrollChanged(int left, int top, int oldLeft, int oldTop) {
            super.onScrollChanged(left, top, oldLeft, oldTop);
            if (moving) {
                return;
            }
            android.view.View child = getChildAt(0);
            if (child == null) {
                return;
            }
            int gap = child.getBottom() - (getScrollY() + getHeight());
            stick = gap < Ui.dp(TerminalActivity.this, 64);
        }
    }

    private void keyboard() {
        input.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private LinearLayout keys(String... labels) {
        LinearLayout row = new LinearLayout(this);
        row.setBackgroundColor(0xFF111111);
        for (String label : labels) {
            TextView key = Ui.text(this, label, 12, 0xFFB0B0B0, false);
            key.setGravity(Gravity.CENTER);
            key.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            key.setOnClickListener(v -> onKey(label, key));
            row.addView(key, new LinearLayout.LayoutParams(0, -2, 1f));
            if ("CTRL".equals(label)) {
                ctrlKey = key;
            }
            if ("ALT".equals(label)) {
                altKey = key;
            }
        }
        return row;
    }

    private void onKey(String label, TextView key) {
        keyboard();
        if ("CTRL".equals(label)) {
            ctrl = !ctrl;
            paintMod(key, ctrl);
            if (ctrl && alt) {
                alt = false;
                paintMod(altKey, false);
            }
            return;
        }
        if ("ALT".equals(label)) {
            alt = !alt;
            paintMod(key, alt);
            if (alt && ctrl) {
                ctrl = false;
                paintMod(ctrlKey, false);
            }
            return;
        }
        if ("ESC".equals(label)) {
            editing = true;
            input.setText("");
            editing = false;
            paint(false);
            return;
        }
        if ("HOME".equals(label)) {
            input.setSelection(0);
            return;
        }
        if ("END".equals(label)) {
            input.setSelection(input.getText().length());
            return;
        }
        if ("\u2191".equals(label)) {
            recall(-1);
            return;
        }
        if ("\u2193".equals(label)) {
            recall(1);
            return;
        }
        if ("\u2190".equals(label)) {
            nudge(-1);
            return;
        }
        if ("\u2192".equals(label)) {
            nudge(1);
            return;
        }
        if ("PGUP".equals(label)) {
            stick = false;
            scroll.scrollBy(0, -scroll.getHeight() / 2);
            return;
        }
        if ("PGDN".equals(label)) {
            scroll.scrollBy(0, scroll.getHeight() / 2);
            return;
        }
        if ("TAB".equals(label)) {
            insert("\t");
            return;
        }
        insert(label);
    }

    private void paintMod(TextView key, boolean on) {
        if (key == null) {
            return;
        }
        key.setTextColor(on ? 0xFF111111 : 0xFFB0B0B0);
        key.setBackgroundColor(on ? 0xFFB0B0B0 : 0xFF111111);
    }

    private void modified(char ch) {
        if (ctrl) {
            ctrl = false;
            paintMod(ctrlKey, false);
            if (ch == 'c' || ch == 'C') {
                if (job != null) {
                    job.cancel();
                }
                append("^C\n");
            } else if (ch == 'd' || ch == 'D') {
                runLine();
            } else if (ch == 'u' || ch == 'U') {
                editing = true;
                input.setText("");
                editing = false;
                paint(false);
            }
            return;
        }
        if (alt) {
            alt = false;
            paintMod(altKey, false);
            insert(String.valueOf(ch));
        }
    }

    private void insert(String text) {
        int at = Math.max(input.getSelectionStart(), 0);
        editing = true;
        input.getText().insert(at, text);
        editing = false;
        paint(false);
    }

    private void nudge(int by) {
        int at = input.getSelectionStart();
        if (at < 0) {
            at = input.getText().length();
        }
        int next = Math.max(0, Math.min(input.getText().length(), at + by));
        input.setSelection(next);
    }

    private void recall(int dir) {
        if (history.isEmpty()) {
            return;
        }
        if (dir < 0) {
            if (historyAt <= 0) {
                return;
            }
            historyAt--;
        } else if (historyAt < history.size()) {
            historyAt++;
        }
        editing = true;
        input.setText(historyAt >= history.size() ? "" : history.get(historyAt));
        input.setSelection(input.getText().length());
        editing = false;
        paint(true);
    }

    private String promptText() {
        return "localhost:" + Guest.prompt(cwd) + "# ";
    }

    /** The session, then the line being typed with a cursor. The cursor is not a separate field. */
    private void paint(boolean jump) {
        if (transcript == null) {
            return;
        }
        String typed = input == null || busy ? "" : input.getText().toString();
        String live = busy ? "" : promptText() + typed + (cursorOn ? "\u2588" : "\u2591");
        int y = scroll == null ? 0 : scroll.getScrollY();
        transcript.setText(log.toString() + live);
        if (jump) {
            stick = true;
            follow();
        } else if (scroll != null && !stick) {
            scroll.post(() -> scroll.scrollTo(0, y));
        }
    }

    private void boot() {
        append("Preparing Linux…\n");
        worker.execute(() -> {
            try {
                LinuxRoot.install(this, line -> runOnUiThread(() -> append(line + "\n")));
                if (closed) {
                    return;
                }
                linuxUp = true;
                runOnUiThread(() -> append(WELCOME));
            } catch (Exception e) {
                String why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                runOnUiThread(() -> append("Linux did not start: " + why + "\n"));
            }
        });
    }

    private void runLine() {
        String line = input.getText().toString();
        if (!linuxUp) {
            return;
        }
        if (busy) {
            return;
        }
        editing = true;
        input.setText("");
        editing = false;
        if (!line.trim().isEmpty()) {
            history.add(line);
            historyAt = history.size();
        }
        stick = true;
        append(promptText() + line + "\n");
        String trimmed = line.trim();
        if ("clear".equals(trimmed)) {
            log.setLength(0);
            paint(true);
            return;
        }
        if ("exit".equals(trimmed)) {
            finish();
            return;
        }
        busy = true;
        paint(true);
        final String ran = line;
        final AlpineRun.Job current = new AlpineRun.Job();
        job = current;
        worker.execute(() -> {
            ai.dvine.linux.LinuxEngine.Result result = AlpineRun.run(this, Guest.wrap(cwd, ran),
                    new File(getFilesDir(), "linux"), current, 256 * 1024);
            Guest.Answer answer = Guest.split(result == null ? "" : result.text, cwd);
            runOnUiThread(() -> {
                if (closed) {
                    return;
                }
                cwd = answer.cwd;
                if (!answer.text.isEmpty()) {
                    append(answer.text);
                }
                busy = false;
                if (job == current) {
                    job = null;
                }
                paint(true);
            });
        });
    }

    private void append(String more) {
        if (more == null || more.isEmpty()) {
            return;
        }
        log.append(more);
        if (log.length() > 180000) {
            log.delete(0, log.length() - 140000);
        }
        stick = true;
        paint(true);
    }

    /** scrollTo after layout. fullScroll runs too early and never reaches the new line. */
    private void follow() {
        if (!stick || scroll == null) {
            return;
        }
        scroll.post(() -> {
            android.view.View child = scroll.getChildAt(0);
            if (child == null || !stick) {
                return;
            }
            int y = Math.max(0, child.getBottom() - scroll.getHeight());
            moving = true;
            scroll.scrollTo(0, y);
            moving = false;
            scroll.post(() -> {
                android.view.View again = scroll.getChildAt(0);
                if (again == null || !stick) {
                    return;
                }
                moving = true;
                scroll.scrollTo(0, Math.max(0, again.getBottom() - scroll.getHeight()));
                moving = false;
            });
        });
    }

    @Override
    protected void onDestroy() {
        closed = true;
        blinker.removeCallbacks(blink);
        if (job != null) {
            job.cancel();
        }
        worker.shutdownNow();
        super.onDestroy();
    }
}
