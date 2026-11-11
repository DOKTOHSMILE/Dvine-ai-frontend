package ai.ibiko.chat.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
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

import ai.ibiko.linux.AlpineRun;
import ai.ibiko.linux.CommandWait;
import ai.ibiko.linux.Complete;
import ai.ibiko.linux.Guest;
import ai.ibiko.linux.LinuxHub;
import ai.ibiko.linux.LinuxRoot;
import ai.ibiko.linux.ProotSession;
import ai.ibiko.linux.TermKeys;
import ai.ibiko.linux.TermScreen;
import ai.ibiko.linux.TermSession;

/**
 * Ibiko's Linux terminal. When the phone opens a terminal device this is a full terminal ({@link TermView} on a
 * {@link TermSession}): every key goes to Alpine, and full-screen programs such as vi, nano, top and htop draw as
 * they do anywhere. When it cannot, the older line shell below is used: a prompt line, each command's text shown as
 * it arrives. Alpine runs the commands either way. Termux is not opened.
 */
public final class TerminalActivity extends AppCompatActivity {
    private static final String WELCOME = "Alpine Linux 3.22.5\n";

    private TextView transcript;
    private TextView prompt;
    private EditText input;
    private ScrollView scroll;
    private final StringBuilder log = new StringBuilder();
    private final StringBuilder liveOut = new StringBuilder();
    private final TermScreen screen = new TermScreen();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<String> history = new ArrayList<String>();
    private int historyAt = 0;
    private AlpineRun.Job job;
    private ProotSession live;
    /** The shell process stayed open and answered, so later commands reuse it. */
    private boolean shellReady;
    /** A lasting shell with no terminal device: each command's text is read from a file. */
    private boolean pipe;
    private volatile boolean pipeStop;
    /** True while the lasting shell is still being opened. A command typed then is kept, not dropped. */
    private volatile boolean opening;
    private String held;
    private boolean capturing;
    private boolean foreignRunning;
    private final java.util.ArrayDeque<Foreign> chatQueue = new java.util.ArrayDeque<Foreign>();
    private Foreign chatNow;
    private String running = "";
    private String huntQuery;
    private int huntFrom = -1;
    private int findFrom;
    private String cwd = "/root";
    private volatile boolean closed;
    private volatile boolean linuxUp;
    private volatile boolean busy;
    private long busyAt;
    private final Runnable busyTick = new Runnable() {
        @Override
        public void run() {
            if (!closed && prompt != null && busy) {
                String next = promptText();
                if (!next.contentEquals(prompt.getText())) {
                    prompt.setText(next);
                }
            }
            if (!closed && prompt != null) {
                prompt.postDelayed(this, 1000);
            }
        }
    };
    /** Follow new lines. Turned off when the user scrolls up to read. */
    private boolean stick = true;
    private boolean moving;
    private boolean touching;
    private boolean ctrl;
    private boolean alt;
    private boolean editing;
    private float textSp = DEFAULT_SP;
    /** The text size a terminal opens with: small enough for about 65 columns on a phone, as Termux shows. */
    public static final float DEFAULT_SP = 10f;
    private TextView ctrlKey;
    private TextView altKey;
    /** The full terminal, when the phone opens a terminal device. */
    private TermView term;
    private TermSession full;
    private LinearLayout lineRow;
    private LinearLayout lineKeys;
    private LinearLayout fullKeys;
    private final java.util.function.Function<String, String> chatDoor = this::fromChat;
    private final TermView.Modifiers mods = new TermView.Modifiers() {
        @Override
        public boolean ctrl() {
            return ctrl;
        }

        @Override
        public boolean alt() {
            return alt;
        }

        @Override
        public void used() {
            runOnUiThread(() -> {
                ctrl = false;
                alt = false;
                paintMod(ctrlKey, false);
                paintMod(altKey, false);
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        // a long install or build doesn't stop because the screen went dark
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // a terminal is black edge to edge, with light status and navigation icons, in day and night themes
        getWindow().setStatusBarColor(0xFF000000);
        getWindow().setNavigationBarColor(0xFF000000);
        new androidx.core.view.WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);
        new androidx.core.view.WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView()).setAppearanceLightNavigationBars(false);
        textSp = getPreferences(MODE_PRIVATE).getFloat("text_sp", DEFAULT_SP);
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
        term = new TermView(this, textSp);
        term.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), 0);
        term.setVisibility(android.view.View.GONE);
        term.onTextSize(() -> getPreferences(MODE_PRIVATE).edit().putFloat("text_sp", term.textSp()).apply());
        page.addView(term, new LinearLayout.LayoutParams(-1, 0, 1f));

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
                if (!editing) {
                    huntQuery = null;
                    huntFrom = -1;
                }
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
        lineRow = line;
        page.addView(line);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(0xFF111111);
        lineTop = keys("ESC", "CTRL", "ALT", "TAB", "/", "-", "HOME", "\u2191", "END", "PGUP");
        bar.addView(lineTop);
        lineKeys = keys("\u2190", "\u2193", "\u2192", "PGDN", "FIND", "COPY");
        bar.addView(lineKeys);
        // the standard terminal's extra keys, in its two rows; copy and paste are on a long press of the screen
        fullKeys = new LinearLayout(this);
        fullKeys.setOrientation(LinearLayout.VERTICAL);
        fullKeys.addView(bigKeys("ESC", "/", "\u2015", "HOME", "\u2191", "END", "PGUP"));
        fullKeys.addView(bigKeys("\u21C6", "CTRL", "ALT", "\u2190", "\u2193", "\u2192", "PGDN"));
        fullKeys.setVisibility(android.view.View.GONE);
        bar.addView(fullKeys);
        page.addView(bar);
        setContentView(page);
        Ui.fitInsets(page);
        input.requestFocus();
        loadKept();
        LinuxHub.attach(chatDoor);
        prompt.postDelayed(busyTick, 1000);
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
                        textSp = DEFAULT_SP;
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

    private LinearLayout lineTop;

    /** A row of the standard terminal's extra keys: white labels on black, tall enough for a thumb. */
    /** Keys that repeat while held, as a keyboard's do. */
    static boolean repeats(String label) {
        return "\u2190".equals(label) || "\u2192".equals(label) || "\u2191".equals(label) || "\u2193".equals(label)
                || "PGUP".equals(label) || "PGDN".equals(label);
    }

    /**
     * An extra key: a light tap felt on press and a highlight while held; the arrows and page keys repeat while held
     * (after 400 ms, then every 60 ms), so moving through a long line or a file isn't a tap per step.
     */
    private void pressable(final TextView key, final String label) {
        android.graphics.drawable.GradientDrawable down = new android.graphics.drawable.GradientDrawable();
        down.setColor(0x33FFFFFF);
        down.setCornerRadius(Ui.dp(this, 8));
        final Runnable[] again = new Runnable[1];
        again[0] = () -> {
            onKey(label, key);
            key.postDelayed(again[0], 60);
        };
        key.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.setBackground(down);
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                    if (repeats(label)) {
                        onKey(label, key);
                        v.postDelayed(again[0], 400);
                    }
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                    v.setBackground(null);
                    v.removeCallbacks(again[0]);
                    if (!repeats(label)) {
                        onKey(label, key);
                    }
                    v.performClick();
                    return true;
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.setBackground(null);
                    v.removeCallbacks(again[0]);
                    return true;
                default:
                    return true;
            }
        });
    }

    private LinearLayout bigKeys(String... labels) {
        LinearLayout row = new LinearLayout(this);
        row.setBackgroundColor(0xFF000000);
        for (String label : labels) {
            TextView key = Ui.text(this, label, 15, 0xFFFFFFFF, false);
            key.setGravity(Gravity.CENTER);
            key.setMinHeight(Ui.dp(this, 44));
            pressable(key, label);
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

    private LinearLayout keys(String... labels) {
        LinearLayout row = new LinearLayout(this);
        row.setBackgroundColor(0xFF111111);
        for (String label : labels) {
            TextView key = Ui.text(this, label, 13, 0xFFF2F2F7, false); // bright, as Termux shows them
            key.setGravity(Gravity.CENTER);
            key.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            pressable(key, label);
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

    /** The full terminal takes over the screen. */
    private void enterFull(TermSession session) {
        if (closed) {
            return;
        }
        full = session;
        linuxUp = true;
        scroll.setVisibility(android.view.View.GONE);
        lineRow.setVisibility(android.view.View.GONE);
        lineKeys.setVisibility(android.view.View.GONE);
        lineTop.setVisibility(android.view.View.GONE);
        ((android.view.View) fullKeys.getParent()).setBackgroundColor(0xFF000000);
        fullKeys.setVisibility(android.view.View.VISIBLE);
        term.onLongPress(this::copyOrPaste);
        term.setVisibility(android.view.View.VISIBLE);
        term.attach(session, mods);
        session.onChange(() -> {
            term.postInvalidate();
            if (!session.alive()) {
                runOnUiThread(() -> {
                    if (!closed && full == session) {
                        finish(); // "exit" was typed: the shell ended
                    }
                });
            }
        });
        term.showKeyboard();
    }

    /** A long press on the terminal: copy the session or paste, as a standard terminal offers. */
    private void copyOrPaste() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setItems(new String[] {"Copy all", "Paste"}, (d, which) -> {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard == null || full == null) {
                        return;
                    }
                    if (which == 0) {
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("linux", full.screen().text()));
                        android.widget.Toast.makeText(this, "Copied", android.widget.Toast.LENGTH_SHORT).show();
                    } else {
                        android.content.ClipData clip = clipboard.getPrimaryClip();
                        CharSequence text = clip == null || clip.getItemCount() == 0 ? null : clip.getItemAt(0).coerceToText(this);
                        term.paste(text == null ? "" : text.toString());
                    }
                })
                .show();
    }

    /** A key from the bar, in the full terminal. */
    private void fullKey(String label, TextView key) {
        term.showKeyboard();
        if ("CTRL".equals(label) || "ALT".equals(label)) {
            boolean on = "CTRL".equals(label) ? (ctrl = !ctrl) : (alt = !alt);
            paintMod(key, on);
        } else if ("ESC".equals(label)) {
            term.key(TermKeys.ESCAPE);
        } else if ("TAB".equals(label) || "\u21C6".equals(label)) {
            term.key(TermKeys.TAB);
        } else if ("\u2015".equals(label)) {
            term.type("-");
        } else if ("HOME".equals(label)) {
            term.key(TermKeys.HOME);
        } else if ("END".equals(label)) {
            term.key(TermKeys.END);
        } else if ("\u2191".equals(label)) {
            term.key(TermKeys.UP);
        } else if ("\u2193".equals(label)) {
            term.key(TermKeys.DOWN);
        } else if ("\u2190".equals(label)) {
            term.key(TermKeys.LEFT);
        } else if ("\u2192".equals(label)) {
            term.key(TermKeys.RIGHT);
        } else if ("PGUP".equals(label) || "PGDN".equals(label)) {
            boolean up = "PGUP".equals(label);
            if (full.screen().altScreen()) {
                term.key(up ? TermKeys.PAGE_UP : TermKeys.PAGE_DOWN); // the program's own paging
            } else {
                term.page(up ? 1 : -1);
            }
        } else if ("COPY".equals(label)) {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("linux", full.screen().text()));
                android.widget.Toast.makeText(this, "Copied the session", android.widget.Toast.LENGTH_SHORT).show();
            }
        } else if ("PASTE".equals(label)) {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence text = clip.getItemAt(0).coerceToText(this);
                term.paste(text == null ? "" : text.toString());
            }
        } else {
            term.type(label);
        }
    }

    private void onKey(String label, TextView key) {
        if (full != null) {
            fullKey(label, key);
            return;
        }
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
        if ("COPY".equals(label)) {
            copySession();
            return;
        }
        if ("FIND".equals(label)) {
            findInSession();
            return;
        }
        if ("TAB".equals(label)) {
            complete();
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
                pipeStop = true;
                if (live != null) {
                    live.interrupt();
                }
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
            } else if (ch == 'a' || ch == 'A') {
                input.setSelection(0);
            } else if (ch == 'e' || ch == 'E') {
                input.setSelection(input.getText().length());
            } else if (ch == 'w' || ch == 'W') {
                eraseWord();
            } else if (ch == 'l' || ch == 'L') {
                log.setLength(0);
                screen.clear();
                transcript.setText("");
                keep();
            } else if (ch == 'r' || ch == 'R') {
                historySearch();
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
        return "localhost:" + Guest.prompt(cwd) + "# "
                + (busy ? running + " " + ((System.currentTimeMillis() - busyAt) / 1000) + "s " : "");
    }

    /** The session, then the line being typed with a cursor. The cursor is not a separate field. */
    private void paint(boolean jump) {
        if (transcript == null) {
            return;
        }
        if (prompt != null) {
            String shown = promptText();
            if (!shown.contentEquals(prompt.getText())) {
                prompt.setText(shown);
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
                while (LinuxHub.headlessBusy() && !closed) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                        return;
                    }
                }
                final TermSession runningNow = LinuxHub.running();
                if (runningNow != null) {
                    runOnUiThread(() -> enterFull(runningNow));
                    return;
                }
                ProotSession parked = LinuxHub.unpark();
                if (parked != null && parked.alive()) {
                    runOnUiThread(() -> {
                        if (closed) {
                            LinuxHub.park(parked, true, LinuxRoot.root(TerminalActivity.this));
                            return;
                        }
                        linuxUp = true;
                        startShell(parked);
                        String missed = LinuxHub.takeUnseen();
                        if (!missed.isEmpty()) {
                            append(missed);
                        }
                    });
                    return;
                }
                LinuxRoot.install(this, line -> runOnUiThread(() -> append(line + "\n")));
                if (closed) {
                    return;
                }
                final TermSession opened0 = LinuxHub.terminal(this);
                if (opened0 != null) {
                    runOnUiThread(() -> enterFull(opened0));
                    return;
                }
                if (empty) {
                    runOnUiThread(() -> append(WELCOME));
                }
                runOnUiThread(() -> linuxUp = true);
                opening = true;
                ProotSession opened = null;
                try {
                    opened = ProotSession.open(TerminalActivity.this, LinuxRoot.root(TerminalActivity.this));
                } catch (Exception ignored) {
                    opened = null;
                }
                final ProotSession use = opened;
                runOnUiThread(() -> startShell(use));
            } catch (Exception e) {
                String why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                runOnUiThread(() -> {
                    append("Linux did not start: " + why + "\n");
                    linuxUp = true;
                });
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
        running = word(line);
        huntQuery = null;
        append(promptText() + line + "\n");
        String trimmed = line.trim();
        if ("clear".equals(trimmed)) {
            log.setLength(0);
            liveOut.setLength(0);
            screen.clear();
            transcript.setText("");
            keep();
            return;
        }
        if ("exit".equals(trimmed)) {
            finish();
            return;
        }
        dispatch(line);
    }

    private void dispatch(String line) {
        if (!shellReady && (opening || live != null)) {
            held = line;
            return;
        }
        if (shellReady && live != null && live.alive()) {
            if (pipe) {
                pipeRun(line);
                return;
            }
        }
        oneShot(line);
    }

    private void markBusy() {
        busy = true;
        busyAt = System.currentTimeMillis();
        paint(true);
    }

    /** The shell stayed open without a terminal device, so the command's text is read from its file. */
    private void pipeRun(String line) {
        pipeStop = false;
        markBusy();
        File root = LinuxRoot.root(this);
        File out = new File(root, "root/.ibiko_out");
        File status = new File(root, "root/.ibiko_status");
        File cwdFile = new File(root, "root/.ibiko_cwd");
        out.delete();
        status.delete();
        try {
            live.send(Guest.pipe(line));
        } catch (Exception e) {
            pipe = false;
            shellReady = false;
            busy = false;
            oneShot(line);
            return;
        }
        final long began = System.currentTimeMillis();
        final long budget = CommandWait.ms(line);
        new Thread(() -> {
            long seen = 0;
            long deadline = began + budget;
            boolean ended = false;
            while (!closed && !pipeStop && System.currentTimeMillis() < deadline) {
                try {
                    if (out.isFile()) {
                        java.io.RandomAccessFile raf = new java.io.RandomAccessFile(out, "r");
                        try {
                            if (raf.length() > seen) {
                                raf.seek(seen);
                                byte[] buf = new byte[(int) Math.min(8192, raf.length() - seen)];
                                int n = raf.read(buf);
                                if (n > 0) {
                                    seen += n;
                                    deadline = Math.min(began + CommandWait.LONG_MS,
                                            Math.max(deadline, System.currentTimeMillis() + CommandWait.SHORT_MS));
                                    String more = new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8);
                                    runOnUiThread(() -> {
                                        if (!closed) {
                                            append(more);
                                        }
                                    });
                                }
                            }
                        } finally {
                            raf.close();
                        }
                    }
                    if (status.isFile() && status.length() > 0) {
                        ended = true;
                        break;
                    }
                    if (live == null || !live.alive()) {
                        break;
                    }
                    Thread.sleep(120);
                } catch (Exception ignored) {
                    break;
                }
            }
            String folder = "";
            try {
                if (cwdFile.isFile()) {
                    folder = new String(java.nio.file.Files.readAllBytes(cwdFile.toPath()),
                            java.nio.charset.StandardCharsets.UTF_8).trim();
                }
            } catch (Exception ignored) {
                folder = "";
            }
            final String dir = folder;
            runOnUiThread(() -> {
                if (closed) {
                    return;
                }
                if (dir.startsWith("/")) {
                    cwd = dir;
                }
                int code = 0;
                try {
                    code = Integer.parseInt(readSmall("root/.ibiko_status"));
                } catch (NumberFormatException ignored) {
                    code = pipeStop ? 130 : 0;
                }
                ended(code);
            });
        }, "ibiko-pipe").start();
    }

    private void copySession() {
        if (transcript == null) {
            return;
        }
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("linux", transcript.getText()));
    }

    private static String word(String line) {
        String text = line == null ? "" : line.trim();
        int space = text.indexOf(' ');
        String word = space < 0 ? text : text.substring(0, space);
        return word.length() > 24 ? word.substring(0, 24) : word;
    }

    /** CTRL-R. The words already typed pick an older command. Pressing it again goes further back. */
    private void historySearch() {
        if (history.isEmpty()) {
            return;
        }
        if (huntQuery == null) {
            huntQuery = input.getText().toString().toLowerCase(java.util.Locale.ROOT);
            huntFrom = history.size();
        }
        for (int i = huntFrom - 1; i >= 0; i--) {
            if (huntQuery.isEmpty() || history.get(i).toLowerCase(java.util.Locale.ROOT).contains(huntQuery)) {
                huntFrom = i;
                editing = true;
                input.setText(history.get(i));
                input.setSelection(input.getText().length());
                editing = false;
                return;
            }
        }
        huntFrom = history.size();
    }

    /** FIND. Scrolls to the next place the typed words appear in the session. */
    private void findInSession() {
        if (transcript == null || transcript.getLayout() == null) {
            return;
        }
        String query = input.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        if (query.isEmpty()) {
            return;
        }
        String all = transcript.getText().toString().toLowerCase(java.util.Locale.ROOT);
        int at = all.indexOf(query, findFrom);
        if (at < 0) {
            at = all.indexOf(query);
        }
        if (at < 0) {
            return;
        }
        findFrom = at + query.length();
        int line = transcript.getLayout().getLineForOffset(at);
        stick = false;
        scroll.scrollTo(0, Math.max(0, transcript.getLayout().getLineTop(line)));
    }

    /** One command in its own Linux, used when a lasting shell could not be opened. */
    private void oneShot(String ran) {
        markBusy();
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
                    append(chunk);
                });
            });
            String folder = readSmall("root/.ibiko_cwd");
            runOnUiThread(() -> {
                if (closed) {
                    return;
                }
                if (folder.startsWith("/")) {
                    cwd = folder;
                }
                liveOut.setLength(0);
                if (job == current) {
                    job = null;
                }
                int code = 0;
                try {
                    code = Integer.parseInt(readSmall("root/.ibiko_status"));
                } catch (NumberFormatException ignored) {
                    code = 0;
                }
                ended(code);
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

    private void startShell(ProotSession opened) {
        opening = false;
        if (closed) {
            if (opened != null) {
                opened.close();
            }
            return;
        }
        if (opened == null) {
            linuxUp = true;
            release();
            return;
        }
        if (!opened.tty()) {
            live = opened;
            pipe = true;
            shellReady = true;
            opened.pump(chunk -> runOnUiThread(() -> {
                if (chunk == null) {
                    live = null;
                    shellReady = false;
                    pipe = false;
                    busy = false;
                    linuxUp = true;
                    release();
                    return;
                }
                if (!chunk.isEmpty()) {
                    append(chunk);
                }
            }));
            linuxUp = true;
            release();
            return;
        }
        opened.close(); // a pipe shell is the only kind opened here
        linuxUp = true;
        release();
    }

    /** A command from chat, answered by this shell so a second Linux is not started. */
    private String fromChat(String command) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper() || closed) {
            return null;
        }
        Foreign job = new Foreign(command);
        runOnUiThread(() -> {
            if (closed) {
                job.latch.countDown();
                return;
            }
            chatQueue.add(job);
            if (!busy && !opening) {
                release();
            }
        });
        try {
            job.latch.await(3, java.util.concurrent.TimeUnit.MINUTES);
        } catch (InterruptedException ignored) {
            // the text collected so far is still returned
        }
        return job.out.toString();
    }

    private void ended(int code) {
        if (code != 0) {
            append("[exit " + code + "]\n");
        }
        busy = false;
        paint(stick && !touching);
        keep();
        if (foreignRunning) {
            foreignRunning = false;
            capturing = false;
            if (chatNow != null) {
                chatNow.latch.countDown();
                chatNow = null;
            }
        }
        release();
    }

    private void release() {
        if (busy || opening) {
            return;
        }
        if (chatNow == null && !chatQueue.isEmpty() && (shellReady || live == null)) {
            Foreign next = chatQueue.poll();
            running = word(next.command);
            append(promptText() + next.command + "\n");
            chatNow = next;
            foreignRunning = true;
            capturing = true;
            dispatch(next.command);
            return;
        }
        if (held == null) {
            return;
        }
        String line = held;
        held = null;
        dispatch(line);
    }

    private void complete() {
        int at = input.getSelectionStart();
        Complete.Hit hit = Complete.tab(LinuxRoot.root(this), cwd, input.getText().toString(), at);
        if (hit.line != null && !hit.line.equals(input.getText().toString())) {
            editing = true;
            input.setText(hit.line);
            int caret = Math.max(0, Math.min(hit.line.length(), hit.cursor));
            input.setSelection(caret);
            editing = false;
        }
        if (hit.shown != null && !hit.shown.isEmpty()) {
            append(hit.shown + "\n");
        }
    }

    private void eraseWord() {
        int at = input.getSelectionStart();
        if (at < 0) {
            at = input.getText().length();
        }
        int start = at;
        while (start > 0 && Character.isWhitespace(input.getText().charAt(start - 1))) {
            start--;
        }
        while (start > 0 && !Character.isWhitespace(input.getText().charAt(start - 1))) {
            start--;
        }
        editing = true;
        input.getText().delete(start, at);
        editing = false;
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
                screen.write(text);
                if (transcript != null) {
                    transcript.setText(drawn(screen.pieces()));
                }
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
        }, "ibiko-term-keep").start();
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
        if (capturing && chatNow != null) {
            chatNow.out.append(more);
        }
        if (log.length() > 180000) {
            log.delete(0, log.length() - 140000);
            screen.clear();
            screen.write(log.toString());
            if (transcript != null) {
                transcript.setText(drawn(screen.pieces()));
            }
        } else {
            java.util.List<TermScreen.Piece> added = screen.write(more);
            if (transcript != null) {
                if (added == null) {
                    transcript.setText(drawn(screen.pieces()));
                } else if (!added.isEmpty()) {
                    transcript.append(drawn(added));
                }
            }
        }
        paint(stick && !touching);
    }

    private CharSequence drawn(java.util.List<TermScreen.Piece> pieces) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        if (pieces == null) {
            return out;
        }
        for (TermScreen.Piece piece : pieces) {
            int start = out.length();
            out.append(piece.text);
            out.setSpan(new ForegroundColorSpan(piece.color), start, out.length(),
                    SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (piece.bold) {
                out.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, out.length(),
                        SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return out;
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
        LinuxHub.detach(chatDoor);
        if (full != null) {
            full.onChange(null); // the terminal keeps running; the next time the screen opens it is as it was left
        }
        if (chatNow != null) {
            chatNow.latch.countDown();
        }
        while (!chatQueue.isEmpty()) {
            chatQueue.poll().latch.countDown();
        }
        if (prompt != null) {
            prompt.removeCallbacks(busyTick);
        }
        if (live != null && live.alive()) {
            LinuxHub.park(live, pipe, LinuxRoot.root(this));
            live = null;
        } else if (live != null) {
            live.close();
        }
        if (job != null) {
            job.cancel();
        }
        worker.shutdownNow();
        super.onDestroy();
    }

    /** One command chat asked this shell to run. */
    private static final class Foreign {
        final String command;
        final StringBuilder out = new StringBuilder();
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        Foreign(String command) {
            this.command = command;
        }
    }
}
