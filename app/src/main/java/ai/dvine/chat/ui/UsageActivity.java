package ai.dvine.chat.ui;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;

import ai.dvine.llm.LocalModel;
import ai.dvine.llm.ModelHub;
import ai.dvine.llm.ProviderStore;
import ai.dvine.llm.Providers;
import ai.dvine.llm.UsageLog;

/**
 * Usage, laid out like Claude's: how full the model's context window was on the last reply (the conversation, the
 * instructions, the new message, the reply, what was free), today's requests and tokens, which model answers, and
 * what is stored on the phone. Counts the API reported are marked as measured; the rest are estimates and say so.
 */
public final class UsageActivity extends AppCompatActivity {
    private LinearLayout body;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        int pad = Ui.dp(this, 16);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(pad, Ui.dp(this, 20), pad, Ui.dp(this, 8));
        TextView close = Ui.text(this, "✕", 22, Ui.TEXT, false);
        close.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        close.setOnClickListener(v -> finish());
        head.addView(close);
        TextView title = Ui.text(this, "Usage", 19, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView refresh = Ui.text(this, "↻", 24, Ui.TEXT, false);
        refresh.setPadding(Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2));
        refresh.setOnClickListener(v -> fill());
        head.addView(refresh);
        root.addView(head);

        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, 0, pad, Ui.dp(this, 32));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        Ui.fitInsets(root);
        fill();
    }

    private void fill() {
        body.removeAllViews();
        ModelHub hub = ModelHub.get(this);
        LocalModel m = hub.model;
        UsageLog log = m.usage;
        long now = System.currentTimeMillis();
        ProviderStore store = new ProviderStore(this);
        Providers active = store.active();

        // ---- context window
        section("Context");
        LinearLayout ctx = card();
        UsageLog.Call c = log.last();
        int window = c != null ? c.window : m.window();
        int used = c == null ? 0 : Math.min(window, c.used());
        LinearLayout top = new LinearLayout(this);
        top.addView(Ui.text(this, "Context window", 18, Ui.TEXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(Ui.text(this, percent(used, window) + " used", 15, Ui.SUB, false));
        ctx.addView(top);
        int[] parts = c == null ? new int[0] : new int[]{c.conversation, c.system, c.message, c.reply};
        int[] colours = {Ui.BLUE, Ui.ORANGE, Ui.GREEN, Ui.AMBER};
        ctx.addView(bar(parts, colours, window), Ui.wide(this, 14));
        ctx.addView(Ui.text(this, c == null ? "Nothing asked yet. Send a message and refresh."
                : tokens(used) + " of " + tokens(window) + " tokens" + (c.measured ? " (counted by the API)" : " (estimated)"), 13, Ui.SUB, false),
                Ui.wide(this, 10));
        if (c != null) {
            divider(ctx);
            String[] names = {"Messages", "System prompt", "This message", "Reply"};
            for (int i = 0; i < names.length; i++) {
                ctx.addView(legend(colours[i], names[i], parts[i], window), Ui.wide(this, 10));
            }
            ctx.addView(legend(Ui.TRACK, "Free space", Math.max(0, window - used), window), Ui.wide(this, 10));
            divider(ctx);
            ctx.addView(row("Answered by", c.brain + ", " + WorkloadSheet.ago(now - c.at)), Ui.wide(this, 10));
            ctx.addView(row("Speed", c.speed() <= 0 ? "-" : String.format(Locale.US, "%.1f tokens a second", c.speed())), Ui.wide(this, 8));
        }
        body.addView(ctx, Ui.wide(this, 8));

        // ---- today
        section("Today");
        LinearLayout today = card();
        today.addView(row("Requests", String.valueOf(log.callsToday(now))));
        today.addView(row("Tokens sent", tokens(log.inToday(now))), Ui.wide(this, 8));
        today.addView(row("Tokens written", tokens(log.outToday(now))), Ui.wide(this, 8));
        float speed = log.speedToday(now);
        today.addView(row("Average speed", speed <= 0 ? "-" : String.format(Locale.US, "%.1f tokens a second", speed)), Ui.wide(this, 8));
        today.addView(row("Failed replies", String.valueOf(log.failedToday(now))), Ui.wide(this, 8));
        today.addView(row("Ibiko 1 answers", String.valueOf(log.ownToday(now))), Ui.wide(this, 8));
        today.addView(row("  from its memory", String.valueOf(log.ownMemoryToday(now))), Ui.wide(this, 6));
        today.addView(row("  researched", String.valueOf(log.ownResearchToday(now))), Ui.wide(this, 6));
        today.addView(Ui.text(this, "Resets at midnight. API spending and limits are on your provider's own dashboard.", 13, Ui.SUB, false),
                Ui.wide(this, 12));
        body.addView(today, Ui.wide(this, 8));

        // ---- model
        section("Model");
        LinearLayout brain = card();
        // the brain the pilot picked in the model menu: DVINE 1, the offline model, or the API models
        String pick = getSharedPreferences("dvine", MODE_PRIVATE).getString("brain_pick", "api");
        java.util.List<Providers> apis = store.usable();
        boolean api = "api".equals(pick) && !apis.isEmpty();
        String answers = "dvine".equals(pick) ? "Ibiko 1 \u00B7 0.1 (Ibiko's own)"
                : api ? apis.get(0).title + ": " + store.model(apis.get(0)) + (store.connected(apis.get(0)) ? "" : " (not tested yet)")
                        + (apis.size() > 1 ? " +" + (apis.size() - 1) + " backup" : "")
                : "On this device: " + hub.model.spec.weights;
        brain.addView(row("Chosen", answers));
        brain.addView(row("Offline model", phoneState(m)), Ui.wide(this, 8));
        brain.addView(row("Context window", "dvine".equals(pick) ? "none (answers from memory and research)"
                : tokens(api ? store.window(apis.get(0)) : hub.model.localWindow()) + " tokens"), Ui.wide(this, 8));
        TextView change = Ui.text(this, "Change the model", 15, Ui.ACCENT, false);
        change.setPadding(0, Ui.dp(this, 14), 0, 0);
        change.setOnClickListener(v -> startActivity(new android.content.Intent(this, ProvidersActivity.class)));
        brain.addView(change);
        body.addView(brain, Ui.wide(this, 8));

        // ---- learning
        section("Learning");
        LinearLayout learn = card();
        final android.content.SharedPreferences prefs = getSharedPreferences("dvine", MODE_PRIVATE);
        final ai.dvine.learn.LearningLog ll = new ai.dvine.learn.LearningLog(new File(getFilesDir(), "learning"));
        LinearLayout sw = new LinearLayout(this);
        sw.setGravity(Gravity.CENTER_VERTICAL);
        sw.addView(Ui.text(this, "Learn from the API models", 16, Ui.TEXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
        android.widget.Switch toggle = new android.widget.Switch(this);
        toggle.setChecked(learningOn(prefs));
        toggle.setTrackTintList(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{Ui.CORAL, Ui.FIELD}));
        toggle.setOnCheckedChangeListener((b, on) -> {
            prefs.edit().putBoolean("learn_on", on).apply();
            m.learning = on ? ll : null;
        });
        sw.addView(toggle);
        learn.addView(sw);
        learn.addView(row("Answers kept", String.valueOf(ll.count())), Ui.wide(this, 8));
        learn.addView(row("Size", bytes(ll.bytes())), Ui.wide(this, 8));
        learn.addView(Ui.text(this, "Each request, reply and Ibiko step from your API models is kept on this device, never sent anywhere."
                + " Ibiko's own model uses the closest ones as examples when it answers offline, and Export saves them"
                + " as a training file to fine-tune Ibiko's model. Check your provider's terms before training a model on its replies.",
                13, Ui.SUB, false), Ui.wide(this, 12));
        LinearLayout buttons = new LinearLayout(this);
        TextView export = Ui.text(this, "Export", 15, Ui.ACCENT, false);
        export.setPadding(0, Ui.dp(this, 14), Ui.dp(this, 28), 0);
        export.setOnClickListener(v -> exportLearning(ll));
        buttons.addView(export);
        TextView clear = Ui.text(this, "Delete", 15, Ui.ACCENT, false);
        clear.setPadding(0, Ui.dp(this, 14), 0, 0);
        clear.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setMessage("Delete everything Ibiko has learned from the API models?")
                .setPositiveButton("Delete", (d, w) -> {
                    ll.clear();
                    fill();
                })
                .setNegativeButton("Cancel", null)
                .show());
        buttons.addView(clear);
        learn.addView(buttons);
        body.addView(learn, Ui.wide(this, 8));

        // ---- storage
        section("On this device");
        LinearLayout disk = card();
        disk.addView(row("Model file", m.installed() ? bytes(m.file().length()) : "not downloaded"));
        disk.addView(row("Chats", bytes(size(new File(getFilesDir(), "chats.json")))), Ui.wide(this, 8));
        disk.addView(row("Saved drawings", bytes(size(new File(getFilesDir(), "drawings")))), Ui.wide(this, 8));
        disk.addView(row("Everything else", bytes(Math.max(0, size(getFilesDir()) - size(new File(getFilesDir(), "drawings"))
                - size(new File(getFilesDir(), "chats.json")) - (m.installed() ? m.file().length() : 0)))), Ui.wide(this, 8));
        body.addView(disk, Ui.wide(this, 8));
    }

    /** Whether DVINE learns from the API models (on unless the user turned it off). */
    public static boolean learningOn(android.content.SharedPreferences prefs) {
        return prefs.getBoolean("learn_on", true);
    }

    /** Saves the training file to Downloads/DVINE. */
    private void exportLearning(ai.dvine.learn.LearningLog ll) {
        if (ll.count() == 0) {
            android.widget.Toast.makeText(this, "Nothing learned yet.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        String name = "dvine-training-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new java.util.Date()) + ".jsonl";
        android.content.ContentValues v = new android.content.ContentValues();
        v.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name);
        v.put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/jsonl");
        v.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/Ibiko");
        String said;
        try {
            android.net.Uri uri = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) {
                throw new java.io.IOException("no Downloads folder");
            }
            int n;
            try (java.io.OutputStream o = getContentResolver().openOutputStream(uri)) {
                n = ll.exportForTraining(o);
            }
            said = "Saved " + n + " examples to Downloads/Ibiko/" + name;
        } catch (java.io.IOException | RuntimeException e) {
            said = "Couldn't export: " + e.getMessage();
        }
        android.widget.Toast.makeText(this, said, android.widget.Toast.LENGTH_LONG).show();
    }

    private static String phoneState(LocalModel m) {
        switch (m.state()) {
            case READY: return "running";
            case INSTALLED: return "installed";
            case LOADING: return "starting";
            case DOWNLOADING: return "downloading";
            case UNSUPPORTED: return "can't run here";
            case FAILED: return "couldn't start";
            default: return "not downloaded";
        }
    }

    private void section(String s) {
        TextView t = Ui.text(this, s, 15, Ui.SUB, false);
        t.setPadding(Ui.dp(this, 6), Ui.dp(this, 20), 0, Ui.dp(this, 4));
        body.addView(t);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.round(this, Ui.SURFACE, 0, 20));
        int p = Ui.dp(this, 18);
        c.setPadding(p, p, p, p);
        return c;
    }

    private void divider(LinearLayout c) {
        View v = new View(this);
        v.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 1));
        lp.topMargin = Ui.dp(this, 14);
        c.addView(v, lp);
    }

    /** The window as a bar: each part its colour, the rest the track. */
    private View bar(int[] parts, int[] colours, int window) {
        LinearLayout b = new LinearLayout(this);
        b.setBackground(Ui.round(this, Ui.TRACK, 0, 5));
        b.setClipToOutline(true);
        int total = 0;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] <= 0) {
                continue;
            }
            View seg = new View(this);
            seg.setBackgroundColor(colours[i]);
            b.addView(seg, new LinearLayout.LayoutParams(0, Ui.dp(this, 10), Math.max(0.004f, parts[i] / (float) window)));
            total += parts[i];
        }
        View free = new View(this);
        b.addView(free, new LinearLayout.LayoutParams(0, Ui.dp(this, 10), Math.max(0f, 1f - total / (float) window)));
        return b;
    }

    private View legend(int colour, String name, int value, int window) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView dot = Ui.text(this, "●", 14, colour, false);
        dot.setPadding(0, 0, Ui.dp(this, 10), 0);
        r.addView(dot);
        r.addView(Ui.text(this, name, 16, Ui.TEXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(Ui.text(this, tokens(value), 16, Ui.TEXT, false));
        TextView pct = Ui.text(this, percent(value, window), 16, Ui.TEXT, false);
        pct.setGravity(Gravity.END);
        r.addView(pct, new LinearLayout.LayoutParams(Ui.dp(this, 76), -2));
        return r;
    }

    private View row(String name, String value) {
        LinearLayout r = new LinearLayout(this);
        r.addView(Ui.text(this, name, 16, Ui.TEXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView v = Ui.text(this, value, 15, Ui.SUB, false);
        v.setGravity(Gravity.END);
        r.addView(v, new LinearLayout.LayoutParams(0, -2, 1.3f));
        return r;
    }

    static String tokens(int n) {
        if (n >= 1_000_000) {
            return String.format(Locale.US, "%.1fM", n / 1_000_000f).replace(".0M", "M");
        }
        return n >= 1000 ? String.format(Locale.US, "%.1fk", n / 1000f) : String.valueOf(n);
    }

    static String percent(int part, int whole) {
        return whole <= 0 ? "0%" : String.format(Locale.US, "%.1f%%", 100f * part / whole).replace(".0%", "%");
    }

    private static String bytes(long b) {
        return b >= 1_000_000_000 ? String.format(Locale.US, "%.2f GB", b / 1e9) : b >= 1_000_000 ? String.format(Locale.US, "%.1f MB", b / 1e6)
                : b >= 1000 ? String.format(Locale.US, "%.0f kB", b / 1e3) : b + " bytes";
    }

    private static long size(File f) {
        if (f == null || !f.exists()) {
            return 0;
        }
        if (f.isFile()) {
            return f.length();
        }
        long t = 0;
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                t += size(k);
            }
        }
        return t;
    }
}
