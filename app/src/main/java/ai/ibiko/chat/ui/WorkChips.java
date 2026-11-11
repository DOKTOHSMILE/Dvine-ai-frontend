package ai.ibiko.chat.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import ai.ibiko.brain.MiniJson;
import ai.ibiko.chat.WorkLog;
import ai.ibiko.connect.GitHubGit;

/**
 * The pills under a reply, as in Claude Code: "Diff +12 −3" (the lines that reply changed in the cloned repositories;
 * tap for them, green for added and red for removed), "Pull requests · 1" (the ones it opened; tap to open one on
 * GitHub) and "Artifacts · 2" (the files it made, such as Ivie's exported models; tap to open, hold to share). Each
 * reply that did work has its own {@link Run}, kept on the phone with the chat, so the pills stay under that reply.
 */
public final class WorkChips {
    /** One file a reply changed: its path, its lines added and removed, and the changed lines themselves. */
    public static final class Change {
        public final String path, kind;
        public final int added, removed;
        public final List<String> lines;

        public Change(String path, String kind, int added, int removed, List<String> lines) {
            this.path = path;
            this.kind = kind;
            this.added = added;
            this.removed = removed;
            this.lines = lines;
        }
    }

    /** What one reply produced. */
    public static final class Run {
        public final List<Change> changes = new ArrayList<Change>();
        public final List<WorkLog.Entry> pullRequests = new ArrayList<WorkLog.Entry>();
        public final List<WorkLog.Entry> artifacts = new ArrayList<WorkLog.Entry>();

        public boolean isEmpty() {
            return changes.isEmpty() && pullRequests.isEmpty() && artifacts.isEmpty();
        }
    }

    /** Lines of a file's changes kept with the chat (a longer diff keeps its start). */
    static final int KEEP_LINES = 3000;

    private final Activity activity;
    private final HorizontalScrollView scroll;
    private final LinearLayout row;
    private Run run = new Run();

    public WorkChips(Activity activity, HorizontalScrollView scroll, LinearLayout row) {
        this.activity = activity;
        this.scroll = scroll;
        this.row = row;
        Ui.init(activity);
    }

    // ------------------------------------------------------------ one reply's work, kept with the chat

    /**
     * What a reply produced: the changes to the files it {@code changed} inside cloned repositories (relative to
     * {@code workspace}), and the pull requests and artifacts logged since {@code since}. Kept in files/work under
     * an id, which is returned; null when the reply produced nothing. Call it off the main thread (it reads files).
     */
    public static String capture(Context c, File workspace, List<String> changed, long since) {
        Run r = new Run();
        java.util.Set<String> wanted = new java.util.HashSet<String>(changed == null ? new ArrayList<String>() : changed);
        java.util.Set<String> repos = new java.util.LinkedHashSet<String>();
        for (String p : wanted) {
            if (p.contains("/") && new File(workspace, p.substring(0, p.indexOf('/')) + "/" + GitHubGit.RECORD).isFile()) {
                repos.add(p.substring(0, p.indexOf('/')));
            }
        }
        for (String repo : repos) {
            for (GitHubGit.FileDiff d : GitHubGit.diff(new File(workspace, repo))) {
                String path = repo + "/" + d.path;
                if (wanted.contains(path)) {
                    List<String> lines = d.diff.lines.size() > KEEP_LINES ? d.diff.lines.subList(0, KEEP_LINES) : d.diff.lines;
                    r.changes.add(new Change(path, d.kind, d.diff.added, d.diff.removed, new ArrayList<String>(lines)));
                }
            }
        }
        WorkLog log = new WorkLog(c);
        for (WorkLog.Entry e : log.pullRequests()) {
            if (e.at >= since) {
                r.pullRequests.add(e);
            }
        }
        for (WorkLog.Entry e : log.artifacts()) {
            if (e.at >= since) {
                r.artifacts.add(e);
            }
        }
        if (r.isEmpty()) {
            return null;
        }
        String id = java.util.UUID.randomUUID().toString();
        try {
            File dir = new File(c.getFilesDir(), "work");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return null;
            }
            java.nio.file.Files.write(new File(dir, id + ".json").toPath(), write(r).getBytes(StandardCharsets.UTF_8));
            return id;
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** A reply's work kept under {@code id}; empty when it is gone. */
    public static Run load(Context c, String id) {
        try {
            File f = new File(new File(c.getFilesDir(), "work"), id.replaceAll("[^A-Za-z0-9-]", "") + ".json");
            return f.isFile() ? read(new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)) : new Run();
        } catch (java.io.IOException | RuntimeException e) {
            return new Run();
        }
    }

    static String write(Run r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        List<Object> changes = new ArrayList<Object>();
        for (Change ch : r.changes) {
            Map<String, Object> x = new LinkedHashMap<String, Object>();
            x.put("path", ch.path);
            x.put("kind", ch.kind);
            x.put("added", (double) ch.added);
            x.put("removed", (double) ch.removed);
            x.put("lines", new ArrayList<Object>(ch.lines));
            changes.add(x);
        }
        m.put("changes", changes);
        m.put("prs", entries(r.pullRequests));
        m.put("artifacts", entries(r.artifacts));
        return MiniJson.write(m);
    }

    static Run read(String json) {
        Run r = new Run();
        Map<String, Object> m = MiniJson.obj(MiniJson.parse(json));
        for (Object o : MiniJson.list(m.get("changes"))) {
            Map<String, Object> x = MiniJson.obj(o);
            List<String> lines = new ArrayList<String>();
            for (Object l : MiniJson.list(x.get("lines"))) {
                lines.add(MiniJson.str(l, ""));
            }
            r.changes.add(new Change(MiniJson.str(x.get("path"), ""), MiniJson.str(x.get("kind"), "modified"),
                    (int) MiniJson.num(x.get("added"), 0), (int) MiniJson.num(x.get("removed"), 0), lines));
        }
        r.pullRequests.addAll(readEntries(MiniJson.list(m.get("prs"))));
        r.artifacts.addAll(readEntries(MiniJson.list(m.get("artifacts"))));
        return r;
    }

    private static List<Object> entries(List<WorkLog.Entry> list) {
        List<Object> out = new ArrayList<Object>();
        for (WorkLog.Entry e : list) {
            Map<String, Object> x = new LinkedHashMap<String, Object>();
            x.put("title", e.title);
            x.put("address", e.address);
            x.put("detail", e.detail);
            x.put("mime", e.mime);
            x.put("at", (double) e.at);
            out.add(x);
        }
        return out;
    }

    private static List<WorkLog.Entry> readEntries(List<Object> list) {
        List<WorkLog.Entry> out = new ArrayList<WorkLog.Entry>();
        for (Object o : list) {
            Map<String, Object> x = MiniJson.obj(o);
            out.add(new WorkLog.Entry(MiniJson.str(x.get("title"), ""), MiniJson.str(x.get("address"), ""),
                    MiniJson.str(x.get("detail"), ""), MiniJson.str(x.get("mime"), ""), (long) MiniJson.num(x.get("at"), 0)));
        }
        return out;
    }

    // ------------------------------------------------------------ the pills

    /** Shows a reply's pills (the strip hides when it produced nothing). */
    public void show(Run r) {
        run = r == null ? new Run() : r;
        row.removeAllViews();
        int added = 0, removed = 0;
        for (Change d : run.changes) {
            added += d.added;
            removed += d.removed;
        }
        if (!run.changes.isEmpty()) {
            LinearLayout chip = chip();
            chip.addView(Ui.text(activity, "Diff  ", 14, Ui.TEXT, false));
            chip.addView(Ui.text(activity, "+" + added, 14, 0xFF3FB950, false));
            chip.addView(Ui.text(activity, " \u2212" + removed, 14, 0xFFF85149, false));
            chip.setOnClickListener(v -> showDiff());
            add(chip);
        }
        if (!run.pullRequests.isEmpty()) {
            LinearLayout chip = chip();
            chip.addView(Ui.text(activity, "Pull requests \u00B7 " + run.pullRequests.size(), 14, Ui.TEXT, false));
            chip.setOnClickListener(v -> showList("Pull requests", run.pullRequests));
            add(chip);
        }
        if (!run.artifacts.isEmpty()) {
            LinearLayout chip = chip();
            chip.addView(Ui.text(activity, "Artifacts \u00B7 " + run.artifacts.size(), 14, Ui.TEXT, false));
            chip.setOnClickListener(v -> showList("Artifacts", run.artifacts));
            add(chip);
        }
        scroll.setVisibility(row.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private LinearLayout chip() {
        LinearLayout c = new LinearLayout(activity);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setBackground(Ui.round(activity, Ui.SURFACE, Ui.LINE, 18));
        c.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 8), Ui.dp(activity, 14), Ui.dp(activity, 8));
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
        android.widget.ImageView close = Ui.icon(activity, ai.ibiko.R.drawable.ic_close, Ui.TEXT, 20);
        close.setContentDescription("Close");
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
        for (Change d : run.changes) {
            LinearLayout head = new LinearLayout(activity);
            head.setPadding(0, Ui.dp(activity, 14), 0, Ui.dp(activity, 6));
            TextView name = Ui.text(activity, d.path + (d.kind.equals("modified") ? "" : "  (" + d.kind + ")"), 14, Ui.TEXT, true);
            head.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            head.addView(Ui.text(activity, "+" + d.added, 13, 0xFF3FB950, false));
            head.addView(Ui.text(activity, "  −" + d.removed, 13, 0xFFF85149, false));
            list.addView(head);
            HorizontalScrollView wide = new HorizontalScrollView(activity);
            LinearLayout code = new LinearLayout(activity);
            code.setOrientation(LinearLayout.VERTICAL);
            code.setBackground(Ui.round(activity, Ui.SURFACE, Ui.LINE, 10));
            code.setPadding(0, Ui.dp(activity, 6), Ui.dp(activity, 12), Ui.dp(activity, 6));
            for (String l : d.lines) {
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
