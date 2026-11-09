package ai.dvine.chat.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.ScaleGestureDetector;
import android.view.inputmethod.EditorInfo;
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

import ai.dvine.linux.LinuxRoot;
import ai.dvine.linux.ProotSession;

/**
 * A Linux terminal inside the app: Alpine, with {@code pkg} for installing programs, extra keys
 * (ESC, CTRL, arrows) and pinch-to-zoom. Termux is not opened.
 */
public final class TerminalActivity extends AppCompatActivity {
    private static final String WELCOME = "Welcome to DVINE Linux.\n"
            + "\n"
            + "Alpine Linux is running inside this app.\n"
            + "Termux is not opened. Packages install here.\n"
            + "\n"
            + "Working with packages:\n"
            + " - Search:    pkg search <query>\n"
            + " - Install:   pkg install <package>\n"
            + " - Upgrade:   pkg upgrade\n"
            + "\n"
            + "For example:  pkg install python3 git openssh\n"
            + "\n"
            + "Pinch with two fingers to zoom.\n"
            + "CTRL, then C, stops a running command.\n"
            + "\n";

    private TextView transcript;
    private EditText input;
    private ScrollView scroll;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<String> history = new ArrayList<String>();
    private int historyAt = 0;
    private ProotSession session;
    private volatile boolean closed;
    private boolean ctrl;
    private boolean alt;
    private boolean editing;
    private float textSp = 13f;
    private TextView ctrlKey;
    private TextView altKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        textSp = getPreferences(MODE_PRIVATE).getFloat("text_sp", 13f);
        int pad = Ui.dp(this, 10);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFF000000);
        page.setPadding(pad, Ui.dp(this, 8), pad, pad);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView close = Ui.text(this, "✕", 20, 0xFFE8E8E8, false);
        close.setContentDescription("Close");
        close.setOnClickListener(v -> finish());
        head.addView(close);
        TextView title = Ui.text(this, "Linux", 16, 0xFFE8E8E8, true);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView clear = Ui.text(this, "Clear", 14, Ui.ACCENT, true);
        clear.setOnClickListener(v -> transcript.setText(""));
        head.addView(clear);
        page.addView(head);

        scroll = new ZoomScroll(this);
        scroll.setFillViewport(true);
        HorizontalScrollView wide = new HorizontalScrollView(this);
        wide.setFillViewport(true);
        transcript = Ui.text(this, "", textSp, 0xFFE6E6E6, false);
        transcript.setTypeface(android.graphics.Typeface.MONOSPACE);
        transcript.setTextIsSelectable(true);
        wide.addView(transcript);
        scroll.addView(wide);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        page.addView(keys("ESC", "/", "-", "HOME", "↑", "END", "PGUP"));
        page.addView(keys("TAB", "CTRL", "ALT", "←", "↓", "→", "PGDN"));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 4), 0, 0);
        TextView prompt = Ui.text(this, "$", 15, 0xFFB8F5C8, true);
        prompt.setTypeface(android.graphics.Typeface.MONOSPACE);
        prompt.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 8), 0);
        row.addView(prompt);
        input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(0xFFE6E6E6);
        input.setHintTextColor(0xFF6E6E78);
        input.setHint("command");
        input.setBackgroundColor(0xFF000000);
        input.setTypeface(android.graphics.Typeface.MONOSPACE);
        input.setTextSize(textSp);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_GO);
        input.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        input.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE
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
            }
        });
        row.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        page.addView(row);
        setContentView(page);
        Ui.fitInsets(page);
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
                        input.setTextSize(textSp);
                        getPreferences(MODE_PRIVATE).edit().putFloat("text_sp", textSp).apply();
                        return true;
                    }
                });
        private final GestureDetector taps = new GestureDetector(TerminalActivity.this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(android.view.MotionEvent e) {
                        textSp = 13f;
                        transcript.setTextSize(textSp);
                        input.setTextSize(textSp);
                        getPreferences(MODE_PRIVATE).edit().putFloat("text_sp", textSp).apply();
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
    }

    private LinearLayout keys(String... labels) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, Ui.dp(this, 4), 0, 0);
        for (String label : labels) {
            TextView key = Ui.text(this, label, 11, 0xFFD0D0D0, true);
            key.setGravity(Gravity.CENTER);
            key.setBackgroundColor(0xFF1A1A1A);
            key.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            key.setOnClickListener(v -> onKey(label, key));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.leftMargin = Ui.dp(this, 2);
            lp.rightMargin = Ui.dp(this, 2);
            row.addView(key, lp);
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
        if ("CTRL".equals(label)) {
            ctrl = !ctrl;
            key.setTextColor(ctrl ? 0xFF101010 : 0xFFD0D0D0);
            key.setBackgroundColor(ctrl ? Ui.ACCENT : 0xFF1A1A1A);
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
            input.setText("");
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
        if ("↑".equals(label)) {
            recall(-1);
            return;
        }
        if ("↓".equals(label)) {
            recall(1);
            return;
        }
        if ("←".equals(label)) {
            nudge(-1);
            return;
        }
        if ("→".equals(label)) {
            nudge(1);
            return;
        }
        if ("PGUP".equals(label)) {
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
        key.setTextColor(on ? 0xFF101010 : 0xFFD0D0D0);
        key.setBackgroundColor(on ? Ui.ACCENT : 0xFF1A1A1A);
    }

    private void modified(char ch) {
        if (ctrl) {
            ctrl = false;
            paintMod(ctrlKey, false);
            if (ch == 'c' || ch == 'C') {
                if (session != null) {
                    session.interrupt();
                }
                append("^C\n");
            } else if (ch == 'd' || ch == 'D') {
                runLine();
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
    }

    private void boot() {
        append("Preparing Linux…\n");
        worker.execute(() -> {
            try {
                LinuxRoot.install(this, line -> runOnUiThread(() -> append(line + "\n")));
                if (closed) {
                    return;
                }
                ProotSession opened = ProotSession.open(this, LinuxRoot.root(this));
                opened.pump(chunk -> runOnUiThread(() -> {
                    if (chunk == null) {
                        session = null;
                        append("\n[session ended]\n");
                        return;
                    }
                    append(chunk);
                }));
                session = opened;
                runOnUiThread(() -> append(WELCOME));
            } catch (Exception e) {
                String why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                runOnUiThread(() -> append("Linux did not start: " + why + "\n"));
            }
        });
    }

    private void runLine() {
        String line = input.getText().toString();
        if (session == null || !session.alive()) {
            append("Linux is not running yet.\n");
            return;
        }
        editing = true;
        input.setText("");
        editing = false;
        if (!line.trim().isEmpty()) {
            history.add(line);
            historyAt = history.size();
        }
        append("$ " + line + "\n");
        try {
            session.send(line);
        } catch (Exception e) {
            append("Couldn't send that: " + e.getMessage() + "\n");
        }
    }

    private void append(String more) {
        if (more == null || more.isEmpty()) {
            return;
        }
        transcript.append(more);
        if (transcript.length() > 180000) {
            CharSequence rest = transcript.getText().subSequence(transcript.length() - 140000, transcript.length());
            transcript.setText(rest);
        }
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        closed = true;
        if (session != null) {
            session.close();
        }
        worker.shutdownNow();
        super.onDestroy();
    }
}
