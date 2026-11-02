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

/** The look shared by DVINE's screens: dark rounded cards, a coral accent, pill badges, 48 dp buttons. */
final class Ui {
    /** One palette for every screen, after Claude's dark theme: warm near-black, a coral accent. */
    static final int BG = 0xFF1F1E1D, SURFACE = 0xFF2A2927, LINE = 0xFF3A3936, CORAL = 0xFFD97757, ON_CORAL = 0xFF1F1E1D;
    static final int SHEET = 0xFF262624, CARD = 0xFF2E2D2A, CARD_ON = 0xFF3B2C26, BORDER = 0xFF3D3C38, BORDER_ON = CORAL,
            FIELD = 0xFF3A3936, TEXT = 0xFFECEBE6, SUB = 0xFF9E9C95, ACCENT = CORAL, PILL = 0xFF4A3A2A, PILL_TEXT = 0xFFF2D9C7,
            BLUE = 0xFF5B8DEF, ORANGE = CORAL, GREEN = 0xFF5FB37F, AMBER = 0xFFE8B04A, GREY = 0xFF8A8880, TRACK = 0xFF3A3936;

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

    /** A full-width button, 48 dp tall: coral when it is the main action, outlined otherwise. */
    static TextView button(Context c, String label, boolean primary) {
        TextView b = text(c, label, 16, primary ? ON_CORAL : TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 48));
        b.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        b.setBackground(primary ? round(c, CORAL, 0, 14) : round(c, SURFACE, LINE, 14));
        b.setClickable(true);
        return b;
    }

    static View gap(Context c, float h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, h)));
        return v;
    }
}
