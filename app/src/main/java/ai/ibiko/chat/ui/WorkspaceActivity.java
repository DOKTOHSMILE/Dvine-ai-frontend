package ai.ibiko.chat.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;

/**
 * Ibiko's workspace on this phone (cloned repositories, projects, the sandbox), as folders and files: a folder opens,
 * a text file shows in the code viewer, Back goes up a level, and holding one deletes it after asking. The same
 * folder Ibiko works in, so what it made is here to look at.
 */
public final class WorkspaceActivity extends AppCompatActivity {
    /** The chat (conversation id) whose folder this shows. */
    public static final String EXTRA_CHAT = "chat";

    /** The chat's own folder; without a chat named, the chat open now in the chat book. */
    static File chatFolder(android.content.Context c, String chat) {
        if (chat == null) {
            ai.ibiko.chat.ChatBook book = ai.ibiko.chat.ChatBook.open(new File(c.getFilesDir(), "chats.json"));
            ai.ibiko.chat.ChatBook.Section s = book.current();
            chat = s == null ? "chat" : s.group != null ? s.group : s.id;
        }
        return ai.ibiko.main.ChatSpace.of(c.getFilesDir(), chat);
    }

    /** Files above this size aren't opened in the viewer. */
    static final long MOST_BYTES = BigText.MOST_BYTES;
    private File root, here;
    private LinearLayout body;
    private TextView where;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        // only the chat open now: its own folder (repos, memory, images), never another chat's
        root = chatFolder(this, getIntent().getStringExtra(EXTRA_CHAT));
        here = root;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Ui.BG);
        int pad = Ui.dp(this, 16);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(pad, Ui.dp(this, 20), pad, Ui.dp(this, 4));
        TextView close = Ui.text(this, "✕", 22, Ui.TEXT, false);
        close.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        close.setContentDescription("Close");
        close.setOnClickListener(v -> finish());
        head.addView(close);
        TextView title = Ui.text(this, "Workspace", 19, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(new android.view.View(this), new LinearLayout.LayoutParams(Ui.dp(this, 34), 1));
        page.addView(head);
        where = Ui.text(this, "", 13, Ui.SUB, false);
        where.setPadding(pad + Ui.dp(this, 6), 0, pad, Ui.dp(this, 8));
        page.addView(where);
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, 0, pad, Ui.dp(this, 32));
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
        Ui.fitInsets(page);
        fill();
    }

    @Override
    public void onBackPressed() {
        if (!here.equals(root) && here.getParentFile() != null) {
            here = here.getParentFile(); // up a level first
            fill();
            return;
        }
        super.onBackPressed();
    }

    private void fill() {
        body.removeAllViews();
        String rel = root.toURI().relativize(here.toURI()).getPath();
        where.setText(rel.isEmpty() ? "Ibiko's files on this device" : rel);
        File[] kids = here.listFiles();
        if (!here.equals(root)) {
            body.addView(row("..", "Up a level", ai.ibiko.R.drawable.ic_folder, v -> {
                here = here.getParentFile();
                fill();
            }, null));
        }
        if (kids == null || kids.length == 0) {
            TextView none = Ui.text(this, here.equals(root)
                    ? "Nothing here yet. Ask Ibiko to clone a repository or build something, and its files appear here."
                    : "This folder is empty.", 14, Ui.SUB, false);
            none.setPadding(Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 6), 0);
            body.addView(none);
            return;
        }
        Arrays.sort(kids, (a, b) -> a.isDirectory() != b.isDirectory() ? (a.isDirectory() ? -1 : 1)
                : a.getName().compareToIgnoreCase(b.getName()));
        for (final File f : kids) {
            if (f.getName().equals(".git")) {
                continue; // git's own records: not something to browse
            }
            if (f.isDirectory()) {
                String[] in = f.list();
                body.addView(row(f.getName(), (in == null ? 0 : in.length) + " items", ai.ibiko.R.drawable.ic_folder, v -> {
                    here = f;
                    fill();
                }, f));
            } else {
                body.addView(row(f.getName(), size(f.length()), ai.ibiko.R.drawable.ic_file, v -> open(f), f));
            }
        }
    }

    private void open(File f) {
        if (f.length() > MOST_BYTES) {
            android.widget.Toast.makeText(this, "Too big to show here (" + size(f.length()) + ").", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        // only the path travels: the viewer reads the file itself, off the main thread (a big file in the Intent crashed)
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        CodeViewActivity.openFile(this, f, dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.US) : "");
    }

    private LinearLayout row(String name, String under, int icon, android.view.View.OnClickListener tap, final File deletable) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.dp(this, 52));
        r.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        android.util.TypedValue ripple = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        r.setBackgroundResource(ripple.resourceId);
        ImageView i = new ImageView(this);
        i.setImageResource(icon);
        r.addView(i, new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(Ui.dp(this, 14), 0, 0, 0);
        words.addView(Ui.text(this, name, 15, Ui.TEXT, false));
        words.addView(Ui.text(this, under, 12, Ui.SUB, false));
        r.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        r.setContentDescription(name + ", " + under);
        r.setOnClickListener(tap);
        if (deletable != null) {
            r.setOnLongClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setMessage("Delete " + deletable.getName() + (deletable.isDirectory() ? " and everything in it" : "") + "?")
                        .setPositiveButton("Delete", (d, w) -> {
                            delete(deletable);
                            fill();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            });
        }
        return r;
    }

    private static void delete(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                delete(k);
            }
        }
        f.delete();
    }

    static String size(long bytes) {
        return bytes < 1024 ? bytes + " B" : bytes < 1024 * 1024 ? (bytes / 1024) + " KB"
                : String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
    }
}
