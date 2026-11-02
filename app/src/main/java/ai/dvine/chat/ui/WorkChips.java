package ai.dvine.chat.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ai.dvine.chat.WorkLog;
import ai.dvine.connect.GitHubGit;

/**
 * The chips above the message box, as in Claude Code: "Diff +1193 −26" (what changed in the cloned repositories
 * since they were cloned, pulled or pushed; tap for the changes line by line), "Pull requests · 2" (the ones Ibiko
 * opened; tap to open one on GitHub) and "Artifacts · 1" (the files made, such as Ivie's exported models; tap to
 * open or share). They show on Ibiko's tab, each only when it has something; the diff is worked out off the main thread.
 */
public final class WorkChips {
    private final Activity activity;
    private final HorizontalScrollView scroll;
    private final LinearLayout row;
    private final File workspace;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile List<GitHubGit.FileDiff> diffs = new ArrayList<GitHubGit.FileDiff>();
    private boolean coding;

    public WorkChips(Activity activity, HorizontalScrollView scroll, LinearLayout row, File workspace) {
        this.activity = activity;
        this.scroll = scroll;
        this.row = row;
        this.workspace = workspace;
        Ui.init(activity);
    }

    /** Works the chips out again; {@code coding}: Ibiko's tab, the only one the chips show on. */
    public void refresh(boolean coding) {
        this.coding = coding;
        paint();
        if (!coding) {
            scroll.setVisibility(View.GONE);
            return;
        }
        new Thread(() -> {
            List<GitHubGit.FileDiff> found = new ArrayList<GitHubGit.FileDiff>();
            File[] repos = workspace.listFiles();
            if (repos != null) {
                for (File r : repos) {
                    if (new File(r, GitHubGit.RECORD).isFile()) {
                        for (GitHubGit.FileDiff d : GitHubGit.diff(r)) {
                            found.add(new GitHubGit.FileDiff(r.getName() + "/" + d.path, d.kind, d.diff));
                        }
                    }
                }
            }
            diffs = found;
            main.post(this::paint);
        }, "dvine-diff").start();
    }

    private void paint() {
        if (activity.isFinishing()) {
            return;
        }
        row.removeAllViews();
        WorkLog log = new WorkLog(activity);
        int added = 0, removed = 0;
        for (GitHubGit.FileDiff d : diffs) {
            added += d.diff.added;
            removed += d.diff.removed;
        }
        if (coding && !diffs.isEmpty()) {
            LinearLayout chip = chip();
            chip.addView(Ui.text(activity, "Diff  ", 15, Ui.TEXT, false));
            chip.addView(Ui.text(activity, "+" + added, 15, 0xFF3FB950, false));
            chip.addView(Ui.text(activity, " −" + removed, 15, 0xFFF85149, false));
            chip.setOnClickListener(v -> showDiff());
            add(chip);
        }
        List<WorkLog.Entry> prs = log.pullRequests();
        if (coding && !prs.isEmpty()) {
            LinearLayout chip = chip();
            chip.addView(Ui.text(activity, "Pull requests · " + prs.size(), 15, Ui.TEXT, false));
            chip.setOnClickListener(v -> showList("Pull requests", prs));
            add(chip);
        }
        List<WorkLog.Entry> made = log.artifacts();
        if (coding && !made.isEmpty()) {
            LinearLayout chip = chip();
            chip.addView(Ui.text(activity, "Artifacts · " + made.size(), 15, Ui.TEXT, false));
            chip.setOnClickListener(v -> showList("Artifacts", made));
            add(chip);
        }
        scroll.setVisibility(row.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private LinearLayout chip() {
        LinearLayout c = new LinearLayout(activity);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setBackground(Ui.round(activity, Ui.SURFACE, Ui.LINE, 18));
        c.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 9), Ui.dp(activity, 16), Ui.dp(activity, 9));
        return c;
    }

    private void add(View chip) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(activity, 8);
        row.addView(chip, lp);
    }

    // ------------------------------------------------------------ the sheets

    private Dialog sheet(String title, View content) {
        Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Ui.BG);
        LinearLayout head = new LinearLayout(activity);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 16), Ui.dp(activity, 16), Ui.dp(activity, 8));
        TextView close = Ui.text(activity, "✕", 20, Ui.TEXT, false);
        close.setPadding(0, 0, Ui.dp(activity, 16), 0);
        close.setOnClickListener(v -> d.dismiss());
        head.addView(close);
        head.addView(Ui.text(activity, title, 18, Ui.TEXT, true));
        box.addView(head);
        box.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        d.show();
        return d;
    }

    /** Every changed file: its name and counts, then its changed lines, green for added and red for removed. */
    private void showDiff() {
        ScrollView sv = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(activity, 12), 0, Ui.dp(activity, 12), Ui.dp(activity, 24));
        int shown = 0;
        for (GitHubGit.FileDiff d : diffs) {
            LinearLayout head = new LinearLayout(activity);
            head.setPadding(0, Ui.dp(activity, 14), 0, Ui.dp(activity, 6));
            TextView name = Ui.text(activity, d.path + (d.kind.equals("modified") ? "" : "  (" + d.kind + ")"), 14, Ui.TEXT, true);
            head.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            head.addView(Ui.text(activity, "+" + d.diff.added, 13, 0xFF3FB950, false));
            head.addView(Ui.text(activity, "  −" + d.diff.removed, 13, 0xFFF85149, false));
            list.addView(head);
            HorizontalScrollView wide = new HorizontalScrollView(activity);
            LinearLayout code = new LinearLayout(activity);
            code.setOrientation(LinearLayout.VERTICAL);
            code.setBackground(Ui.round(activity, Ui.SURFACE, Ui.LINE, 10));
            code.setPadding(0, Ui.dp(activity, 6), Ui.dp(activity, 12), Ui.dp(activity, 6));
            for (String l : d.diff.lines) {
                if (shown++ > 3000) {
                    break; // a very long diff: the start of it
                }
                char k = l.isEmpty() ? ' ' : l.charAt(0);
                TextView t = Ui.text(activity, (k == '@' ? "" : k + " ") + l.substring(Math.min(1, l.length())), 12,
                        k == '+' ? 0xFF3FB950 : k == '-' ? 0xFFF85149 : k == '@' ? Ui.SUB : Ui.TEXT, false);
                t.setTypeface(Typeface.MONOSPACE);
                t.setPadding(Ui.dp(activity, 10), 0, 0, 0);
                if (k == '+' || k == '-') {
                    t.setBackgroundColor(k == '+' ? 0x223FB950 : 0x22F85149);
                }
                code.addView(t);
            }
            wide.addView(code);
            list.addView(wide);
        }
        sv.addView(list);
        sheet("Changes", sv);
    }

    /** Pull requests or artifacts: tap to open; artifacts can be shared. */
    private void showList(String title, List<WorkLog.Entry> entries) {
        ScrollView sv = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(activity, 16), 0, Ui.dp(activity, 16), Ui.dp(activity, 24));
        for (final WorkLog.Entry e : entries) {
            LinearLayout r = new LinearLayout(activity);
            r.setOrientation(LinearLayout.VERTICAL);
            r.setBackground(Ui.round(activity, Ui.SURFACE, Ui.LINE, 14));
            r.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12), Ui.dp(activity, 14), Ui.dp(activity, 12));
            r.addView(Ui.text(activity, e.title, 15, Ui.TEXT, true));
            r.addView(Ui.text(activity, e.detail + "  ·  " + android.text.format.DateUtils.getRelativeTimeSpanString(e.at), 13, Ui.SUB, false));
            r.setOnClickListener(v -> open(e, false));
            r.setOnLongClickListener(v -> {
                open(e, true);
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(activity, 10);
            list.addView(r, lp);
        }
        TextView hint = Ui.text(activity, title.equals("Artifacts") ? "Tap to open, hold to share." : "Tap to open on GitHub.", 13, Ui.SUB, false);
        hint.setPadding(0, Ui.dp(activity, 14), 0, 0);
        list.addView(hint);
        sv.addView(list);
        sheet(title, sv);
    }

    private void open(WorkLog.Entry e, boolean share) {
        try {
            Uri uri = Uri.parse(e.address);
            Intent i = share ? new Intent(Intent.ACTION_SEND).setType(e.mime.isEmpty() ? "*/*" : e.mime).putExtra(Intent.EXTRA_STREAM, uri)
                    : new Intent(Intent.ACTION_VIEW).setDataAndType(uri, e.mime.isEmpty() ? null : e.mime);
            if (!share && e.mime.isEmpty()) {
                i = new Intent(Intent.ACTION_VIEW, uri);
            }
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(share ? Intent.createChooser(i, "Share " + e.title) : i);
        } catch (RuntimeException ex) {
            android.widget.Toast.makeText(activity, "No app here opens it. Hold it to share instead.", android.widget.Toast.LENGTH_LONG).show();
        }
    }
}
