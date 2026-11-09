package ai.dvine.chat.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ai.dvine.linux.CommandResult;
import ai.dvine.linux.ShellSession;
import ai.dvine.linux.WorkspaceManager;

/**
 * The shell, typed by hand. Commands run in {@link ShellSession} on a background thread. Termux is not used.
 */
public final class TerminalActivity extends AppCompatActivity {
    private ShellSession session;
    private TextView transcript;
    private TextView where;
    private EditText input;
    private ScrollView scroll;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        File root = WorkspaceManager.ensure(new File(getFilesDir(), "linux"));
        session = new ShellSession(root);
        int pad = Ui.dp(this, 16);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Ui.BG);
        page.setPadding(pad, Ui.dp(this, 12), pad, pad);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView close = Ui.text(this, "✕", 22, Ui.TEXT, false);
        close.setContentDescription("Close");
        close.setOnClickListener(v -> finish());
        head.addView(close);
        TextView title = Ui.text(this, "Shell", 19, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView clear = Ui.text(this, "Clear", 15, Ui.ACCENT, true);
        clear.setOnClickListener(v -> transcript.setText(""));
        head.addView(clear);
        page.addView(head);

        where = Ui.text(this, showWhere(), 13, Ui.SUB, false);
        where.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        page.addView(where);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        transcript = Ui.text(this, "DVINE's shell. python, git and compilers are not bundled.\n", 14, Ui.TEXT, false);
        transcript.setTypeface(android.graphics.Typeface.MONOSPACE);
        scroll.addView(transcript);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 8), 0, 0);
        input = new EditText(this);
        input.setHint("command");
        input.setSingleLine(true);
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(Ui.SUB);
        input.setBackgroundColor(Ui.FIELD);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        input.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEND) {
                runLine();
                return true;
            }
            return false;
        });
        row.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView stop = button("Stop");
        stop.setOnClickListener(v -> session.cancel());
        TextView run = button("Run");
        run.setOnClickListener(v -> runLine());
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-2, -2);
        gap.leftMargin = Ui.dp(this, 8);
        row.addView(stop, gap);
        row.addView(run, gap);
        page.addView(row);
        setContentView(page);
        Ui.fitInsets(page);
    }

    private TextView button(String label) {
        TextView t = Ui.text(this, label, 15, Ui.ON_CORAL, true);
        t.setBackground(Ui.round(this, Ui.ACCENT, 0, 12));
        t.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        return t;
    }

    private void runLine() {
        final String line = input.getText().toString().trim();
        if (line.isEmpty() || busy) {
            return;
        }
        busy = true;
        input.setText("");
        append("$ " + line + "\n");
        worker.execute(() -> {
            CommandResult ran = session.exec(line, null);
            runOnUiThread(() -> {
                append(ran.text() + "\n");
                where.setText(showWhere());
                busy = false;
            });
        });
    }

    private void append(String more) {
        transcript.append(more);
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private String showWhere() {
        String path = session.where();
        String root = new File(getFilesDir(), "linux").getPath();
        if (path.startsWith(root)) {
            String rest = path.substring(root.length());
            return rest.isEmpty() ? "~" : "~" + rest;
        }
        return path;
    }

    @Override
    protected void onDestroy() {
        session.cancel();
        worker.shutdownNow();
        super.onDestroy();
    }
}
