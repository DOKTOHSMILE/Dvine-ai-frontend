package ai.dvine.chat.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ai.dvine.agent.StepDetail;

/**
 * What a step did, opened from the chat: a file's changes with added lines in green and removed lines in red (as in
 * Claude), or what a command printed, or what a file holds; full screen, scrolling both ways.
 */
public final class DetailSheet {
    public static final int GREEN = 0xFF3FB950, RED = 0xFFF85149;

    private DetailSheet() {
    }

    public static void show(Activity a, String step, StepDetail.Detail d) {
        Ui.init(a);
        final Dialog dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Ui.BG);
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 16), Ui.dp(a, 4));
        TextView close = Ui.text(a, "✕", 20, Ui.TEXT, false);
        close.setPadding(0, 0, Ui.dp(a, 16), 0);
        close.setOnClickListener(v -> dialog.dismiss());
        head.addView(close);
        LinearLayout titles = new LinearLayout(a);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Ui.text(a, step, 16, Ui.TEXT, true));
        LinearLayout sub = new LinearLayout(a);
        sub.addView(Ui.text(a, d.title, 13, Ui.SUB, false));
        if (d.diff()) {
            sub.addView(Ui.text(a, "   +" + d.added, 13, GREEN, false));
            sub.addView(Ui.text(a, "  −" + d.removed, 13, RED, false));
        }
        titles.addView(sub);
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(head);

        if (!d.diff() && !d.lines.isEmpty()) {
            // a file or an output: the code viewer, with its own line numbers and colours
            ScrollView down = new ScrollView(a);
            LinearLayout pane = new LinearLayout(a);
            pane.setOrientation(LinearLayout.VERTICAL);
            pane.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 24));
            pane.addView(ai.dvine.ui.CodeView.of(a, d.title, ai.dvine.ui.NumberedCode.of(d.lines, false, 0), 12.5f));
            down.addView(pane);
            box.addView(down, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            dialog.setContentView(box);
            Window w = dialog.getWindow();
            if (w != null) {
                w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
            dialog.show();
            return;
        }
        ScrollView down = new ScrollView(a);
        HorizontalScrollView across = new HorizontalScrollView(a);
        LinearLayout code = new LinearLayout(a);
        code.setOrientation(LinearLayout.VERTICAL);
        code.setPadding(0, Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 24));
        int number = 0;
        for (String l : d.lines) {
            char k = l.isEmpty() ? ' ' : l.charAt(0);
            String body = l.isEmpty() ? "" : l.substring(1);
            TextView t;
            if (k == '@') {
                t = Ui.text(a, "  ⋯ " + body.trim(), 12, Ui.SUB, false);
                t.setPadding(Ui.dp(a, 8), Ui.dp(a, 6), 0, Ui.dp(a, 6));
                try {
                    number = Integer.parseInt(body.replaceAll("\\D+", "")) - 1;
                } catch (NumberFormatException ignored) {
                    // a note, not a line number
                }
            } else {
                if (k != '+') {
                    number++;
                }
                String gutter = d.diff() ? (k == '+' ? "    + " : k == '-' ? String.format(java.util.Locale.US, "%4d - ", number)
                        : String.format(java.util.Locale.US, "%4d   ", number)) : "";
                t = Ui.text(a, gutter + body, 12, k == '+' ? GREEN : k == '-' ? RED : Ui.TEXT, false);
                t.setPadding(Ui.dp(a, 8), 0, 0, 0);
                if (d.diff() && (k == '+' || k == '-')) {
                    t.setBackgroundColor(k == '+' ? 0x263FB950 : 0x26F85149);
                }
            }
            t.setTypeface(Typeface.MONOSPACE);
            t.setTextIsSelectable(true);
            code.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (d.lines.isEmpty()) {
            code.addView(Ui.text(a, "  (nothing)", 13, Ui.SUB, false));
        }
        across.addView(code);
        down.addView(across);
        box.addView(down, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        dialog.setContentView(box);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        dialog.show();
    }
}
