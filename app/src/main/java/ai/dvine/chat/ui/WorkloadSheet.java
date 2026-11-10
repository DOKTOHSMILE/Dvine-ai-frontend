package ai.dvine.chat.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

import ai.dvine.agent.AgentBoard;
import ai.dvine.llm.LocalModel;
import ai.dvine.llm.UsageLog;

/**
 * The workload ring's sheet: how full the model's working memory is (the context window, as in Claude's Usage), what
 * fills it, how hot that is and when it clears itself, the agents at work now with their code names, and today's
 * use. Live: it refreshes every 5 seconds while open.
 */
public final class WorkloadSheet {
    private WorkloadSheet() {
    }

    /** The sheet refreshes this often while open (the workers' timers tick by the second). */
    static final long REFRESH_MS = 2000;
    static final int RED = 0xFFE5534B;

    public static Dialog show(final Activity a, final LocalModel model) {
        return show(a, model, "", false);
    }

    /**
     * {@code brain}: the brain answering now (its name in the header); {@code own}: DVINE 1 is answering, which has no
     * context window to fill.
     */
    public static Dialog show(final Activity a, final LocalModel model, final String brain, final boolean own) {
        Ui.init(a);
        final LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 16), Ui.dp(a, 12));
        fill(a, body, model, brain, own);
        final Dialog d = BottomSheet.showView(a, "Workload", body);
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!d.isShowing()) {
                    return;
                }
                fill(a, body, model, brain, own);
                body.postDelayed(this, REFRESH_MS);
            }
        };
        body.postDelayed(tick, REFRESH_MS);
        return d;
    }

    static void fill(Activity a, LinearLayout body, LocalModel model) {
        fill(a, body, model, "", false);
    }

    static void fill(final Activity a, final LinearLayout body, final LocalModel model, final String brain, final boolean own) {
        body.removeAllViews();
        long t = System.currentTimeMillis();
        if (brain != null && !brain.isEmpty()) {
            body.addView(label(a, "Answering now"));
            LinearLayout who = card(a);
            who.addView(Ui.text(a, brain, 17, Ui.TEXT, false));
            body.addView(who, Ui.wide(a, 4));
        }

        // ---- context window (a model's working memory; DVINE 1 has none to fill)
        body.addView(label(a, "Context"));
        LinearLayout card = card(a);
        int heat;
        if (own) {
            heat = ai.dvine.stage.WorkloadGauge.colourOf(0f);
            LinearLayout top = new LinearLayout(a);
            top.addView(Ui.text(a, "Memory", 17, Ui.TEXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
            top.addView(Ui.text(a, "never fills", 15, Ui.SUB, false));
            card.addView(top);
            card.addView(bar(a, 0f, heat), Ui.wide(a, 12));
            card.addView(Ui.text(a, "DVINE 1 answers from what it has learned and from research, without a context window,"
                    + " so nothing here fills up or needs clearing.", 13, Ui.SUB, false), Ui.wide(a, 8));
            ai.dvine.dvine1.Knowledge k = ai.dvine.dvine1.Store.knowledge(new java.io.File(a.getFilesDir(), "brain"));
            card.addView(row(a, "Answers you confirmed", String.valueOf(k.size())), Ui.wide(a, 10));
            card.addView(Ui.text(a, ai.dvine.dvine1.Dvine1.scoreLine(a.getFilesDir()), 13, Ui.SUB, false), Ui.wide(a, 8));
        } else {
            UsageLog.Call c = model.usage.last();
            int window = c != null ? c.window : model.window();
            int used = c == null ? 0 : Math.min(window, c.used());
            float load = window <= 0 ? 0f : used / (float) window;
            heat = ai.dvine.stage.WorkloadGauge.colourOf(load);
            LinearLayout top = new LinearLayout(a);
            top.addView(Ui.text(a, "Context window", 17, Ui.TEXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
            top.addView(Ui.text(a, percent(used, window) + " used", 15, Ui.SUB, false));
            card.addView(top);
            card.addView(bar(a, load, heat), Ui.wide(a, 12));
            card.addView(Ui.text(a, c == null ? "Nothing measured yet: send a message." : tokens(used) + " of " + tokens(window) + " tokens"
                    + (c.measured ? ", counted by the API" : ", estimated"), 13, Ui.SUB, false), Ui.wide(a, 8));
            if (c != null) {
                String[] names = {"Conversation", "Instructions", "Your message", "Reply"};
                int[] parts = {c.conversation, c.system, c.message, c.reply};
                for (int i = 0; i < names.length; i++) {
                    card.addView(row(a, names[i], tokens(parts[i]) + "  \u00B7  " + percent(parts[i], window)), Ui.wide(a, 6));
                }
                card.addView(row(a, "Free", tokens(Math.max(0, window - used))), Ui.wide(a, 6));
                card.addView(row(a, "Measured on", c.brain + ", " + ago(t - c.at)), Ui.wide(a, 10));
            }
            body.addView(card, Ui.wide(a, 4));
            card = null;
            TextView status = Ui.text(a, load >= ai.dvine.stage.WorkloadGauge.HOT ? "Hot: the working memory is nearly full. It is summed up and cleared after this reply."
                    : load >= 0.6f ? "Warm: filling up. At 85% it is summed up and cleared by itself."
                    : "Cool: plenty of room. At 85% it is summed up and cleared by itself.", 14, heat, false);
            status.setPadding(Ui.dp(a, 4), Ui.dp(a, 12), Ui.dp(a, 4), 0);
            body.addView(status);
        }
        if (card != null) {
            body.addView(card, Ui.wide(a, 4));
        }

        // ---- workers
        List<AgentBoard.Entry> now = AgentBoard.now();
        body.addView(label(a, "Workers \u00B7 " + (now.isEmpty() ? "none working" : now.size() + " working")));
        LinearLayout workers = card(a);
        if (now.isEmpty()) {
            workers.addView(Ui.text(a, "Nobody is working right now. Each reply, task, check or night of learning shows here while it runs.",
                    14, Ui.SUB, false));
        }
        for (int i = 0; i < now.size(); i++) {
            worker(a, workers, now.get(i), t, i == 0);
        }
        body.addView(workers, Ui.wide(a, 4));
        List<AgentBoard.Entry> done = AgentBoard.recent();
        if (!done.isEmpty()) {
            body.addView(label(a, "Finished lately"));
            LinearLayout recent = card(a);
            for (int i = 0; i < done.size(); i++) {
                worker(a, recent, done.get(i), t, i == 0);
            }
            body.addView(recent, Ui.wide(a, 4));
        }

        // ---- projects (long tasks on record): open ones, each with Close; closed ones counted
        final java.io.File ws = new java.io.File(a.getFilesDir(), "ibiko");
        java.util.List<ai.dvine.scribe.Scribe.Job> jobs = ai.dvine.scribe.Scribe.jobs(ws);
        int open = 0, closed = 0;
        for (ai.dvine.scribe.Scribe.Job j : jobs) {
            if (j.open) {
                open++;
            } else {
                closed++;
            }
        }
        body.addView(label(a, "Projects \u00B7 " + open + " open"));
        LinearLayout project = card(a);
        if (open == 0) {
            project.addView(Ui.text(a, "No open project. A build or a long instruction opens one.", 14, Ui.SUB, false));
        }
        boolean first = true;
        for (final ai.dvine.scribe.Scribe.Job j : jobs) {
            if (!j.open) {
                continue;
            }
            LinearLayout head = new LinearLayout(a);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(chip(a, j.state, stateColour(j.state)));
            head.addView(Ui.text(a, "  updated " + ago(t - j.updated), 12, Ui.SUB, false), new LinearLayout.LayoutParams(0, -2, 1f));
            if (!j.state.equals("running")) {
                TextView close = Ui.text(a, "Close", 14, Ui.ACCENT, false);
                close.setPadding(Ui.dp(a, 10), Ui.dp(a, 4), 0, Ui.dp(a, 4));
                close.setContentDescription("Close " + j.title);
                close.setOnClickListener(v -> {
                    ai.dvine.scribe.Scribe.close(ws, j.id);
                    fill(a, body, model, brain, own);
                });
                head.addView(close);
            }
            project.addView(head, Ui.wide(a, first ? 0 : 14));
            TextView title = Ui.text(a, j.title, 14, Ui.TEXT, false);
            title.setPadding(0, Ui.dp(a, 6), 0, 0);
            project.addView(title);
            TextView next = Ui.text(a, (j.done + j.left > 0 ? j.done + " of " + (j.done + j.left) + " steps done. " : "") + "Next: " + j.next,
                    13, Ui.SUB, false);
            next.setPadding(0, Ui.dp(a, 2), 0, 0);
            project.addView(next);
            first = false;
        }
        if (closed > 0) {
            project.addView(Ui.text(a, closed + (closed == 1 ? " finished or closed project" : " finished or closed projects") + " on record.",
                    13, Ui.SUB, false), Ui.wide(a, open == 0 ? 8 : 14));
        }
        body.addView(project, Ui.wide(a, 4));

        String commands = ai.dvine.ops.termux.TermuxSessionLog.pending(new java.io.File(a.getFilesDir(), "ibiko/memory/termux"));
        if (!commands.isEmpty()) {
            body.addView(label(a, "Commands"));
            LinearLayout commandCard = card(a);
            commandCard.addView(Ui.text(a, commands, 14, Ui.TEXT, false));
            body.addView(commandCard, Ui.wide(a, 4));
        }

        // ---- today
        body.addView(label(a, "Today"));
        LinearLayout today = card(a);
        today.addView(row(a, "Model requests", String.valueOf(model.usage.callsToday(t))));
        today.addView(row(a, "Tokens sent", tokens(model.usage.inToday(t))), Ui.wide(a, 6));
        today.addView(row(a, "Tokens written", tokens(model.usage.outToday(t))), Ui.wide(a, 6));
        today.addView(row(a, "Failed replies", String.valueOf(model.usage.failedToday(t))), Ui.wide(a, 6));
        int mine = model.usage.ownToday(t);
        today.addView(row(a, "DVINE 1 answers", mine == 0 ? "0" : mine + " (" + model.usage.ownMemoryToday(t) + " from memory, "
                + model.usage.ownResearchToday(t) + " researched)"), Ui.wide(a, 6));
        body.addView(today, Ui.wide(a, 4));
    }

    /** One worker: code name, state, role, brain, task, what it is doing now, and its time. */
    private static void worker(Activity a, LinearLayout into, AgentBoard.Entry e, long t, boolean first) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView code = Ui.text(a, e.code, 12, Ui.ACCENT, true);
        code.setBackground(Ui.round(a, Ui.PILL, 0, 8));
        code.setPadding(Ui.dp(a, 8), Ui.dp(a, 3), Ui.dp(a, 8), Ui.dp(a, 3));
        r.addView(code);
        TextView role = Ui.text(a, "  " + e.role, 14, Ui.TEXT, true);
        r.addView(role, new LinearLayout.LayoutParams(0, -2, 1f));
        String state = e.ended == 0 ? "working" : e.outcome == AgentBoard.Outcome.FAILED ? "failed"
                : e.outcome == AgentBoard.Outcome.STOPPED ? "stopped" : "done";
        r.addView(chip(a, state, stateColour(state)));
        into.addView(r, Ui.wide(a, first ? 0 : 14));
        if (!e.brain.isEmpty()) {
            into.addView(row(a, "Brain", e.brain), Ui.wide(a, 6));
        }
        into.addView(row(a, e.ended == 0 ? "Working for" : "Took", ai.dvine.stage.ChatPane.duration(e.took(t) / 1000)
                + (e.ended == 0 ? "" : ", " + ago(t - e.ended))), Ui.wide(a, 4));
        if (e.ended == 0 && !e.step.isEmpty()) {
            // a long step ("Reading projects/…/DECISIONS.md") on its own line: beside its label it squeezed "Now" into a column
            TextView now = Ui.text(a, "Now: " + e.step, 13, Ui.TEXT, false);
            now.setMaxLines(2);
            now.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            into.addView(now, Ui.wide(a, 4));
        }
        if (!e.task.isEmpty()) {
            TextView task = Ui.text(a, e.task, 13, Ui.SUB, false);
            task.setPadding(0, Ui.dp(a, 4), 0, 0);
            task.setMaxLines(3);
            task.setEllipsize(android.text.TextUtils.TruncateAt.END);
            into.addView(task);
        }
    }

    private static TextView chip(Activity a, String text, int colour) {
        TextView c = Ui.text(a, text, 12, colour, true);
        c.setBackground(Ui.round(a, (colour & 0x00FFFFFF) | 0x26000000, 0, 8));
        c.setPadding(Ui.dp(a, 8), Ui.dp(a, 2), Ui.dp(a, 8), Ui.dp(a, 2));
        return c;
    }

    static int stateColour(String state) {
        switch (state) {
            case "working":
            case "running":
            case "resuming":
            case "verifying":
                return Ui.BLUE;
            case "done":
            case "completed":
                return Ui.GREEN;
            case "failed":
                return RED;
            default:
                return Ui.AMBER; // paused, waiting, stopped
        }
    }

    /** "just now", "5 min ago", "3 h ago", "2 days ago". */
    static String ago(long ms) {
        long s = Math.max(0, ms / 1000);
        return s < 45 ? "just now" : s < 3600 ? Math.max(1, Math.round(s / 60.0)) + " min ago" : s < 86_400 ? s / 3600 + " h ago"
                : s / 86_400 + (s / 86_400 == 1 ? " day ago" : " days ago");
    }

    static String percent(int part, int whole) {
        return whole <= 0 ? "0%" : String.format(Locale.US, "%.1f%%", 100f * part / whole).replace(".0%", "%");
    }

    private static TextView label(Activity a, String s) {
        TextView t = Ui.text(a, s, 15, Ui.SUB, false);
        t.setPadding(Ui.dp(a, 4), Ui.dp(a, 18), 0, Ui.dp(a, 6));
        return t;
    }

    private static LinearLayout card(Activity a) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.round(a, Ui.SURFACE, 0, 20));
        int p = Ui.dp(a, 16);
        c.setPadding(p, p, p, p);
        return c;
    }

    private static View bar(Activity a, float load, int colour) {
        LinearLayout b = new LinearLayout(a);
        b.setBackground(Ui.round(a, Ui.TRACK, 0, 5));
        b.setClipToOutline(true);
        View fill = new View(a);
        GradientDrawable g = new GradientDrawable();
        g.setColor(colour);
        g.setCornerRadius(Ui.dp(a, 5));
        fill.setBackground(g);
        b.addView(fill, new LinearLayout.LayoutParams(0, Ui.dp(a, 10), Math.max(0.01f, load)));
        b.addView(new View(a), new LinearLayout.LayoutParams(0, Ui.dp(a, 10), Math.max(0.01f, 1f - load)));
        return b;
    }

    private static LinearLayout row(Activity a, String name, String value) {
        LinearLayout r = new LinearLayout(a);
        r.addView(Ui.text(a, name, 14, Ui.SUB, false), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(Ui.text(a, value, 14, Ui.TEXT, false));
        return r;
    }

    static String tokens(int n) {
        return n >= 1_000_000 ? String.format(Locale.US, "%.1fM", n / 1e6) : n >= 1000 ? String.format(Locale.US, "%.1fk", n / 1000.0) : String.valueOf(n);
    }
}
