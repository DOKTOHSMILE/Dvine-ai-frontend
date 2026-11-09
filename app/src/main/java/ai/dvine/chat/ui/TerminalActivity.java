package ai.dvine.chat.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.ScaleGestureDetector;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
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
    private TextView prompt;
    private EditText input;
    private ScrollView scroll;
    private final StringBuilder log = new StringBuilder();
    private final StringBuilder liveOut = new StringBuilder();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<String> history = new ArrayList<String>();
    private int historyAt = 0;
    private AlpineRun.Job job;
    private String cwd = "/root";
    private volatile boolean closed;
    private volatile boolean linuxUp;
    private volatile boolean busy;
    /** Follow new lines. Turned off when the user scrolls up to read. */
    private boolean stick = true;
    private boolean moving;
    private boolean touching;
    private boolean ctrl;
    private boolean alt;
    private boolean editing;
    private float textSp = 14f;
    private TextView ctrlKey;
    private TextView altKey;

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
        transcript = Ui.text(this, "", textSp, 0xFFE6E6E6, false);
        transcript.setTypeface(android.graphics.Typeface.MONOSPACE);
        transcript.setTextIsSelectable(false);
        transcript.setHorizontallyScrolling(false);
        int pad = Ui.dp(this, 8);
        transcript.setPadding(pad, pad, pad, pad);
        scroll.addView(transcript, new ScrollView.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setBackgroundColor(0xFF000000);
        line.setPadding(pad, 0, pad, Ui.dp(this, 4));
        prompt = Ui.text(this, "localhost:~# ", textSp, 0xFFE6E6E6, false);
        prompt.setTypeface(android.graphics.Typeface.MONOSPACE);
        line.addView(prompt);
        input = new EditText(this);
        input.setSingleLine(true);
        input.setBackgroundColor(0xFF000000);
        input.setTextColor(0xFFE6E6E6);
        input.setHintTextColor(0xFF6E6E78);
        input.setTypeface(android.graphics.Typeface.MONOSPACE);
        input.setTextSize(textSp);
        input.setCursorVisible(true);
        input.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_GO);
        input.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        input.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE
                    || action == EditorInfo.IME_ACTION_SEND) {
                runLine();
                return true;
            }
            return false;
        });
        input.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
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
            }
        });
        line.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        page.addView(line);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(0xFF111111);
        bar.addView(keys("ESC", "CTRL", "ALT", "TAB", "/", "-", "HOME", "\u2191", "END", "PGUP"));
        bar.addView(keys("\u2190", "\u2193", "\u2192", "PGDN"));
        page.addView(bar);
        setContentView(page);
        Ui.fitInsets(page);
        input.requestFocus();
        loadKept();
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
                        if (input != null) {
                            input.setTextSize(textSp);
                        }
                        if (prompt != null) {
                            prompt.setTextSize(textSp);
                        }
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
                        if (input != null) {
                            input.setTextSize(textSp);
                        }
                        if (prompt != null) {
                            prompt.setTextSize(textSp);
                        }
                        getPreferences(MODE_PRIVATE).edit().putFloat("text_sp", textSp).apply();
                        return true;
                    }

                    @Override
                    public boolean onSingleTapUp(android.view.MotionEvent e) {
                        return false;
                    }
                });

        ZoomScroll(android.content.Context context) {
            super(context);
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent ev) {
            int action = ev.getActionMasked();
            if (action == android.view.MotionEvent.ACTION_DOWN || action == android.view.MotionEvent.ACTION_MOVE) {
                touching = true;
            } else if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
                touching = false;
                if (busy) {
                    paint(stick);
                }
            }
            if (ev.getPointerCount() >= 2) {
                scale.onTouchEvent(ev);
                taps.onTouchEvent(ev);
            }
            return super.onTouchEvent(ev);
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
        String extra = busy ? liveOut.toString() : "";
        if (prompt != null) {
            prompt.setText(promptText());
        }
        String next = log.toString() + extra;
        String have = transcript.getText().toString();
        if (!next.equals(have)) {
            if (!have.isEmpty() && next.startsWith(have)) {
                transcript.append(next.substring(have.length()));
            } else {
                transcript.setText(next);
            }
        }
        if (jump && !touching) {
            stick = true;
            follow();
        }
    }

    private void boot() {
        final boolean empty = log.length() == 0;
        worker.execute(() -> {
            try {
                LinuxRoot.install(this, line -> runOnUiThread(() -> append(line + "\n")));
                if (closed) {
                    return;
                }
                linuxUp = true;
                if (empty) {
                    runOnUiThread(() -> append(WELCOME));
                }
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
            liveOut.setLength(0);
            paint(true);
            keep();
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
            AlpineRun.run(this, Guest.wrap(cwd, ran), new File(getFilesDir(), "linux"), current, 256 * 1024, chunk -> {
                if (chunk == null || chunk.isEmpty()) {
                    return;
                }
                runOnUiThread(() -> {
                    if (closed) {
                        return;
                    }
                    liveOut.append(chunk);
                    if (liveOut.length() > 260000) {
                        liveOut.delete(0, liveOut.length() - 200000);
                    }
                    if (!touching) {
                        paint(stick);
                    }
                });
            });
            String folder = readSmall("root/.dvine_cwd");
            runOnUiThread(() -> {
                if (closed) {
                    return;
                }
                if (folder.startsWith("/")) {
                    cwd = folder;
                }
                String shown = liveOut.toString();
                liveOut.setLength(0);
                if (!shown.isEmpty()) {
                    log.append(shown.endsWith("\n") ? shown : shown + "\n");
                    if (log.length() > 180000) {
                        log.delete(0, log.length() - 140000);
                    }
                }
                busy = false;
                if (job == current) {
                    job = null;
                }
                paint(stick && !touching);
                keep();
            });
        });
    }

    private String readSmall(String name) {
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(new File(LinuxRoot.root(this), name).toPath());
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private File kept(String name) {
        return new File(new File(getFilesDir(), "linux"), name);
    }

    /** The session and the recalled commands survive leaving the screen. */
    private void loadKept() {
        try {
            File session = kept("session.txt");
            if (session.isFile()) {
                String text = new String(java.nio.file.Files.readAllBytes(session.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                if (text.length() > 140000) {
                    text = text.substring(text.length() - 140000);
                }
                log.append(text);
                paint(true);
            }
            File past = kept("history.txt");
            if (past.isFile()) {
                String[] lines = new String(java.nio.file.Files.readAllBytes(past.toPath()), java.nio.charset.StandardCharsets.UTF_8).split("\n");
                for (String line : lines) {
                    if (!line.isEmpty()) {
                        history.add(line);
                    }
                }
                historyAt = history.size();
            }
        } catch (Exception ignored) {
            // a missing session just starts empty
        }
    }

    private void keep() {
        final String text = log.toString();
        final java.util.ArrayList<String> past = new java.util.ArrayList<String>(history);
        new Thread(() -> {
            try {
                File dir = new File(getFilesDir(), "linux");
                dir.mkdirs();
                java.nio.file.Files.write(kept("session.txt").toPath(), text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder out = new StringBuilder();
                int from = Math.max(0, past.size() - 200);
                for (int i = from; i < past.size(); i++) {
                    out.append(past.get(i)).append('\n');
                }
                java.nio.file.Files.write(kept("history.txt").toPath(), out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception ignored) {
                // the session still works if it cannot be saved
            }
        }, "dvine-term-keep").start();
    }

    @Override
    protected void onPause() {
        keep();
        super.onPause();
    }

    private void append(String more) {
        if (more == null || more.isEmpty()) {
            return;
        }
        log.append(more);
        if (log.length() > 180000) {
            log.delete(0, log.length() - 140000);
        }
        paint(stick && !touching);
    }

    /** scrollTo after layout. fullScroll runs too early and never reaches the new line. */
    private void follow() {
        if (!stick || touching || scroll == null) {
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
        if (job != null) {
            job.cancel();
        }
        worker.shutdownNow();
        super.onDestroy();
    }
}
