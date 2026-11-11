package ai.ibiko.chat.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import ai.ibiko.R;

/**
 * Full-screen code. It can be copied or shared as a file. It is not executed. Large code comes as a file path
 * ({@link #EXTRA_FILE}), is read off the main thread and is shown as a list that draws only the lines on screen, so
 * a file of any size up to {@link BigText#MOST_BYTES} opens without freezing or crashing the app.
 */
public final class CodeViewActivity extends AppCompatActivity {
    public static final String EXTRA_CODE = "code";
    public static final String EXTRA_LANG = "lang";
    /** The code is in this file (a workspace file, or code too big for an Intent, parked in the cache). */
    public static final String EXTRA_FILE = "file";
    /** Up to this size the code is shown as one selectable text; above, as a list of lines. */
    static final int SELECTABLE_CHARS = 40_000;
    /** The clipboard travels through the same 1 MB channel: more than this is shared, not copied. */
    static final int MOST_COPY = 300_000;

    /** Opens code from a chat message: small code inside the Intent, big code through a file. */
    public static void open(Context c, String code, String lang) {
        Intent i = new Intent(c, CodeViewActivity.class).putExtra(EXTRA_LANG, lang);
        String text = code == null ? "" : code;
        if (text.length() > BigText.INLINE) {
            try {
                i.putExtra(EXTRA_FILE, BigText.park(new File(c.getCacheDir(), "code-view"), text, lang).getPath());
            } catch (java.io.IOException e) {
                Toast.makeText(c, "Couldn't open it: the device is out of space.", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            i.putExtra(EXTRA_CODE, text);
        }
        if (!(c instanceof android.app.Activity)) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // opened from outside a screen
        }
        c.startActivity(i);
    }

    /** Opens a file from the workspace (only its path travels). */
    public static void openFile(Context c, File f, String lang) {
        Intent i = new Intent(c, CodeViewActivity.class).putExtra(EXTRA_FILE, f.getPath()).putExtra(EXTRA_LANG, lang);
        if (!(c instanceof android.app.Activity)) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        c.startActivity(i);
    }

    private String code;
    private File file;
    private String lang;
    private boolean gone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_code_view);
        Ui.fitInsets(((ViewGroup) findViewById(android.R.id.content)).getChildAt(0)); // below the status bar, above the navigation bar
        code = getIntent().getStringExtra(EXTRA_CODE);
        String path = getIntent().getStringExtra(EXTRA_FILE);
        file = path == null ? null : new File(path);
        lang = getIntent().getStringExtra(EXTRA_LANG);
        TextView title = findViewById(R.id.code_lang);
        final TextView body = findViewById(R.id.code_body);
        if (title != null) {
            title.setText(lang == null || lang.isEmpty() ? (file != null ? file.getName() : "code") : lang);
        }
        TextView back = findViewById(R.id.code_back);
        if (back != null) {
            back.setOnClickListener(v -> finish());
        }
        TextView copy = findViewById(R.id.code_copy);
        if (copy != null) {
            copy.setOnClickListener(v -> copyAll());
        }
        TextView share = findViewById(R.id.code_share);
        if (share != null) {
            share.setOnClickListener(v -> shareFile());
        }
        if (body != null) {
            body.setText("Opening…");
        }
        final String small = code;
        final File from = file;
        new Thread(() -> {
            final BigText.Lines read = from != null ? BigText.read(from) : BigText.of(small);
            runOnUiThread(() -> show(read));
        }, "ibiko-code-view").start();
    }

    private void show(BigText.Lines read) {
        if (gone || isFinishing()) {
            return;
        }
        TextView body = findViewById(R.id.code_body);
        if (read.refused != null) {
            if (body != null) {
                body.setText(read.refused);
            }
            return;
        }
        if (read.chars <= SELECTABLE_CHARS && !read.cutShort) {
            // small: one text, selectable, as before
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < read.lines.size(); i++) {
                if (i > 0) {
                    b.append('\n');
                }
                b.append(BigText.shown(i, read.lines.size(), read.lines.get(i)));
            }
            if (body != null) {
                body.setText(b);
            }
            return;
        }
        // big: a list that draws only the lines on screen; a long press copies one line
        View scroll = findViewById(R.id.code_scroll);
        ListView list = findViewById(R.id.code_lines);
        if (scroll != null) {
            scroll.setVisibility(View.GONE);
        }
        if (list == null) {
            return;
        }
        list.setVisibility(View.VISIBLE);
        final java.util.List<String> lines = read.lines;
        final int total = lines.size();
        final int color = body != null ? body.getCurrentTextColor() : 0xFFE0E0E0;
        list.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return total + (read.cutShort ? 1 : 0);
            }

            @Override
            public Object getItem(int position) {
                return position < total ? lines.get(position) : "";
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convert, ViewGroup parent) {
                TextView t = convert instanceof TextView ? (TextView) convert : new TextView(CodeViewActivity.this);
                t.setTypeface(android.graphics.Typeface.MONOSPACE);
                t.setTextSize(13);
                t.setTextColor(color);
                t.setText(position < total ? BigText.shown(position, total, lines.get(position))
                        : "… only the first " + total + " lines are shown. Share the file to read the rest.");
                return t;
            }
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < total) {
                clip(lines.get(position));
                Toast.makeText(this, "Line " + (position + 1) + " copied", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        if (file == null) {
            code = null; // the lines hold it now
        }
        this.lines = lines;
    }

    private java.util.List<String> lines;

    private void copyAll() {
        String text = code;
        if (text == null && lines != null) {
            long size = 0;
            for (String l : lines) {
                size += l.length() + 1;
            }
            if (size > MOST_COPY) {
                Toast.makeText(this, "Too big to copy: use Share, or long-press a line to copy it.", Toast.LENGTH_LONG).show();
                return;
            }
            text = String.join("\n", lines);
        }
        if (text == null && file != null) {
            if (file.length() > MOST_COPY) {
                Toast.makeText(this, "Too big to copy: use Share.", Toast.LENGTH_LONG).show();
                return;
            }
            try {
                text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                return;
            }
        }
        if (text != null && text.length() > MOST_COPY) {
            Toast.makeText(this, "Too big to copy: use Share.", Toast.LENGTH_LONG).show();
            return;
        }
        clip(text);
    }

    private void clip(String text) {
        ClipboardManager clips = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clips != null) {
            try {
                clips.setPrimaryClip(ClipData.newPlainText("ibiko-code", text == null ? "" : text));
            } catch (RuntimeException tooBig) {
                Toast.makeText(this, "Too big to copy: use Share.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void shareFile() {
        try {
            String name = file != null ? file.getName() : "ibiko." + (lang == null || lang.isEmpty() ? "txt" : lang);
            File out = new File(new File(getCacheDir(), "code-share"), name.replaceAll("^view-\\d+", "ibiko"));
            out.getParentFile().mkdirs();
            if (file != null) {
                Files.copy(file.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else {
                String text = code != null ? code : lines == null ? "" : String.join("\n", lines);
                Files.write(out.toPath(), text.getBytes(StandardCharsets.UTF_8));
            }
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(this, "ai.ibiko.files", out);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "Share code"));
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't share it.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        gone = true;
        super.onDestroy();
    }

    static String numbered(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        String[] lines = code.split("\n", -1);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                b.append('\n');
            }
            b.append(i + 1).append("  ").append(lines[i]);
        }
        return b.toString();
    }
}
