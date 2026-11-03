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
 * use. Live: it refreshes every second while open.
 */
public final class WorkloadSheet {
    private WorkloadSheet() {
    }

    public static Dialog show(final Activity a, final LocalModel model) {
        Ui.init(a);
        final LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 16), Ui.dp(a, 12));
        fill(a, body, model);
        final Dialog d = BottomSheet.showView(a, "Workload", body);
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!d.isShowing()) {
                    return;
                }
                fill(a, body, model);
                body.postDelayed(this, 1000);
            }
        };
        body.postDelayed(tick, 1000);
        return d;
    }

    static void fill(Activity a, LinearLayout body, LocalModel model) {
        body.removeAllViews();
        UsageLog.Call c = model.usage.last();
        int window = c != null ? c.window : model.window();
        int used = c == null ? 0 : Math.min(window, c.used());
        float load = window <= 0 ? 0f : used / (float) window;
        int heat = ai.dvine.stage.WorkloadGauge.colourOf(load);

        // ---- context window
        body.addView(label(a, "Context"));
        LinearLayout card = card(a);
        LinearLayout top = new LinearLayout(a);
        top.addView(Ui.text(a, "Context window", 17, Ui.TEXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(Ui.text(a, Math.round(load * 100) + "% used", 15, Ui.SUB, false));
        card.addView(top);
        card.addView(bar(a, load, heat), Ui.wide(a, 12));
        card.addView(Ui.text(a, c == null ? "Nothing measured yet: send a message." : tokens(used) + " of " + tokens(window) + " tokens"
                + (c.measured ? "" : " (estimated)"), 13, Ui.SUB, false), Ui.wide(a, 8));
        if (c != null) {
            String[] names = {"Conversation", "Instructions", "Your message", "Reply"};
            int[] parts = {c.conversation, c.system, c.message, c.reply};
            for (int i = 0; i < names.length; i++) {
                card.addView(row(a, names[i], tokens(parts[i])), Ui.wide(a, 6));
            }
            card.addView(row(a, "Free", tokens(Math.max(0, window - used))), Ui.wide(a, 6));
        }
        body.addView(card, Ui.wide(a, 4));
        TextView status = Ui.text(a, load >= ai.dvine.stage.WorkloadGauge.HOT ? "Hot: the working memory is nearly full. It is summed up and cleared after this reply."
                : load >= 0.6f ? "Warm: filling up. At 85% it is summed up and cleared by itself."
                : "Cool: plenty of room. At 85% it is summed up and cleared by itself.", 14, heat, false);
        status.setPadding(Ui.dp(a, 4), Ui.dp(a, 12), Ui.dp(a, 4), 0);
        body.addView(status);

        // ---- agents at work
        body.addView(label(a, "Agents working now"));
        LinearLayout agents = card(a);
        List<AgentBoard.Entry> now = AgentBoard.now();
        if (now.isEmpty()) {
            agents.addView(Ui.text(a, "None at the moment.", 14, Ui.SUB, false));
        }
        long t = System.currentTimeMillis();
        for (int i = 0; i < now.size(); i++) {
            AgentBoard.Entry e = now.get(i);
            LinearLayout r = new LinearLayout(a);
            r.setGravity(Gravity.CENTER_VERTICAL);
            TextView code = Ui.text(a, e.code, 12, Ui.ACCENT, true);
            code.setBackground(Ui.round(a, Ui.PILL, 0, 8));
            code.setPadding(Ui.dp(a, 8), Ui.dp(a, 3), Ui.dp(a, 8), Ui.dp(a, 3));
            r.addView(code);
            TextView role = Ui.text(a, "  " + e.role, 14, Ui.TEXT, false);
            r.addView(role, new LinearLayout.LayoutParams(0, -2, 1f));
            r.addView(Ui.text(a, ai.dvine.stage.ChatPane.duration((t - e.since) / 1000), 13, Ui.SUB, false));
            agents.addView(r, Ui.wide(a, i == 0 ? 0 : 12));
            if (!e.task.isEmpty()) {
                TextView task = Ui.text(a, e.task, 13, Ui.SUB, false);
                task.setPadding(0, Ui.dp(a, 4), 0, 0);
                agents.addView(task);
            }
        }
        body.addView(agents, Ui.wide(a, 4));

        // ---- today
        body.addView(label(a, "Today"));
        LinearLayout today = card(a);
        today.addView(row(a, "Requests", String.valueOf(model.usage.callsToday(t))));
        today.addView(row(a, "Tokens sent", tokens(model.usage.inToday(t))), Ui.wide(a, 6));
        today.addView(row(a, "Tokens written", tokens(model.usage.outToday(t))), Ui.wide(a, 6));
        body.addView(today, Ui.wide(a, 4));
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
