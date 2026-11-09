package ai.dvine.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ai.dvine.R;

/**
 * Code as an editor shows it: a gutter of right-aligned line numbers that stays put, the code coloured by language
 * beside it, and long lines that scroll sideways instead of wrapping (a wrapped line lost its number and pushed the
 * next numbers out of line). The note the reader wrote above the code ("Lines 1–130 of 861 …") is a small header.
 */
public final class CodeView {
    private CodeView() {
    }

    /** The viewer for {@code c}; {@code name} is the file's name or path, for the colours. */
    public static View of(Context ctx, String name, NumberedCode c, float textSp) {
        Code.resolve(ctx);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(ctx, 12);
        if (!c.note.isEmpty()) {
            TextView note = new TextView(ctx);
            note.setText(c.note);
            note.setTextSize(11f);
            note.setTextColor(ctx.getColor(R.color.text_secondary));
            note.setPadding(pad, dp(ctx, 8), pad, dp(ctx, 4));
            box.addView(note);
        }
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        if (c.numbered()) {
            TextView gutter = mono(ctx, textSp);
            gutter.setText(c.gutter());
            gutter.setGravity(Gravity.END);
            gutter.setTextColor(ctx.getColor(R.color.text_secondary));
            gutter.setAlpha(0.7f);
            gutter.setPadding(pad, 0, dp(ctx, 10), 0);
            row.addView(gutter, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            View rule = new View(ctx);
            rule.setBackgroundColor(ctx.getColor(R.color.line));
            row.addView(rule, new LinearLayout.LayoutParams(Math.max(1, dp(ctx, 1) / 2), ViewGroup.LayoutParams.MATCH_PARENT));
        }
        HorizontalScrollView across = new HorizontalScrollView(ctx);
        across.setHorizontalScrollBarEnabled(false);
        across.setFillViewport(true);
        TextView code = mono(ctx, textSp);
        String source = c.source();
        code.setText(paintAll(name, source));
        code.setTextColor(ctx.getColor(R.color.code_text));
        code.setHorizontallyScrolling(true);
        code.setPadding(dp(ctx, 10), 0, pad, 0);
        code.setTextIsSelectable(true);
        across.addView(code, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(across, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    /** Every line coloured ({@link Code#paint} colours at most 40 lines a call, so a long file is done in slices). */
    static CharSequence paintAll(String name, String source) {
        String[] lines = source.split("\n", -1);
        android.text.SpannableStringBuilder out = new android.text.SpannableStringBuilder();
        for (int from = 0; from < lines.length; from += 40) {
            StringBuilder slice = new StringBuilder();
            for (int i = from; i < Math.min(lines.length, from + 40); i++) {
                slice.append(lines[i]);
                if (i < lines.length - 1) {
                    slice.append('\n');
                }
            }
            out.append(Code.paint(name, slice.toString(), 40, 0));
        }
        return out;
    }

    private static TextView mono(Context ctx, float sp) {
        TextView t = new TextView(ctx);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextSize(sp);
        t.setLineSpacing(0f, 1.15f);
        t.setIncludeFontPadding(false);
        return t;
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
