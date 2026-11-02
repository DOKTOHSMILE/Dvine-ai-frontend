package ai.dvine.chat.ui;

import android.app.Activity;
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
 * Usage, laid out like Claude's: how full the brain's context window was on the last reply (the conversation, the
 * instructions, the new message, the reply, what was free), today's requests and tokens, which brain answers, and
 * what is stored on the phone. Counts the API reported are marked as measured; the rest are estimates and say so.
 */
public final class UsageActivity extends Activity {
    private LinearLayout body;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
            ctx.addView(legend(0xFF2C2C30, "Free space", Math.max(0, window - used), window), Ui.wide(this, 10));
            divider(ctx);
            ctx.addView(row("Answered by", c.brain), Ui.wide(this, 10));
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
        today.addView(Ui.text(this, "Resets at midnight. API spending and limits are on your provider's own dashboard.", 13, Ui.SUB, false),
                Ui.wide(this, 12));
        body.addView(today, Ui.wide(this, 8));

        // ---- brain
        section("Brain");
        LinearLayout brain = card();
        String answers = active.api() ? active.title + ": " + store.model(active) + (store.connected(active) ? "" : " (not tested yet)")
                : "On this phone: " + ai.dvine.llm.ModelSpec.WEIGHTS;
        brain.addView(row("Chosen", answers));
        brain.addView(row("On this phone", phoneState(m)), Ui.wide(this, 8));
        brain.addView(row("Context window", tokens(active.api() ? store.window(active) : LocalModel.LOCAL_WINDOW) + " tokens"), Ui.wide(this, 8));
        TextView change = Ui.text(this, "Change the brain", 15, Ui.ACCENT, false);
        change.setPadding(0, Ui.dp(this, 14), 0, 0);
        change.setOnClickListener(v -> startActivity(new android.content.Intent(this, ProvidersActivity.class)));
        brain.addView(change);
        body.addView(brain, Ui.wide(this, 8));

        // ---- storage
        section("On this phone");
        LinearLayout disk = card();
        disk.addView(row("Brain file", m.installed() ? bytes(m.file().length()) : "not downloaded"));
        disk.addView(row("Chats", bytes(size(new File(getFilesDir(), "chats.json")))), Ui.wide(this, 8));
        disk.addView(row("Saved drawings", bytes(size(new File(getFilesDir(), "drawings")))), Ui.wide(this, 8));
        disk.addView(row("Everything else", bytes(Math.max(0, size(getFilesDir()) - size(new File(getFilesDir(), "drawings"))
                - size(new File(getFilesDir(), "chats.json")) - (m.installed() ? m.file().length() : 0)))), Ui.wide(this, 8));
        body.addView(disk, Ui.wide(this, 8));
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
        v.setBackgroundColor(0xFF3A3A40);
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
