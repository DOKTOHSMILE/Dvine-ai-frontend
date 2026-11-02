package ai.dvine.chat.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The look shared by the settings sheets: dark rounded cards, a pink accent, pill badges. */
final class Ui {
    static final int SHEET = 0xFF2B2A2E, CARD = 0xFF333238, CARD_ON = 0xFF5E3446, BORDER = 0xFF4A4850, BORDER_ON = 0xFFF0A3BE,
            FIELD = 0xFF403E45, TEXT = 0xFFF2F2F7, SUB = 0xFFB9B6C0, ACCENT = 0xFFF4A3BF, PILL = 0xFF6E5A2E, PILL_TEXT = 0xFFF6E7C1,
            BLUE = 0xFF3B82F6, ORANGE = 0xFFE5603B, GREEN = 0xFF22A06B, AMBER = 0xFFE8A93A, GREY = 0xFF8E8E93, TRACK = 0xFF3A3A40;

    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable round(Context c, int fill, int stroke, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radius));
        if (stroke != 0) {
            d.setStroke(dp(c, 1.5f), stroke);
        }
        return d;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    static TextView pill(Context c, String s) {
        TextView t = text(c, s, 13, PILL_TEXT, true);
        t.setBackground(round(c, PILL, 0, 14));
        t.setPadding(dp(c, 12), dp(c, 5), dp(c, 12), dp(c, 5));
        return t;
    }

    static EditText field(Context c, String hint, String value, boolean secret, boolean multi) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(TEXT);
        e.setHintTextColor(GREY);
        e.setTextSize(15);
        e.setBackground(round(c, FIELD, 0, 16));
        e.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        if (secret) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else if (multi) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            e.setMinLines(2);
            e.setMaxLines(6);
            e.setGravity(Gravity.TOP | Gravity.START);
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            e.setSingleLine(true);
        }
        return e;
    }

    static LinearLayout.LayoutParams wide(Context c, float top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(c, top);
        return p;
    }

    static View gap(Context c, float h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, h)));
        return v;
    }
}
