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
    /**
     * One palette for every screen, after Claude's: day (warm off-white) or night (warm near-black), with a coral
     * accent. Read from the theme's colours by {@link #init}, so it follows the phone's day / night setting or the
     * one chosen in DVINE.
     */
    static int BG, SURFACE, LINE, CORAL, ON_CORAL, SHEET, CARD, CARD_ON, BORDER, BORDER_ON, FIELD, TEXT, SUB, ACCENT, PILL,
            PILL_TEXT, ORANGE, GREY, TRACK;
    static final int BLUE = 0xFF5B8DEF, GREEN = 0xFF5FB37F, AMBER = 0xFFE8B04A;

    static {
        // night values until a screen calls init (tests that build views without an activity)
        BG = 0xFF1F1E1D; SURFACE = 0xFF2A2927; LINE = 0xFF3A3936; CORAL = 0xFFD97757; ON_CORAL = 0xFF1F1E1D; SHEET = 0xFF262624;
        CARD = 0xFF2E2D2A; CARD_ON = 0xFF3B2C26; BORDER = LINE; BORDER_ON = CORAL; FIELD = 0xFF3A3936; TEXT = 0xFFECEBE6;
        SUB = 0xFF9E9C95; ACCENT = CORAL; PILL = 0xFF4A3A2A; PILL_TEXT = 0xFFF2D9C7; ORANGE = CORAL; GREY = 0xFF8A8880;
        TRACK = 0xFF3A3936;
    }

    /** Reads the palette for the screen's current day or night mode. */
    static void init(Context c) {
        BG = c.getColor(ai.dvine.R.color.bg_main);
        SURFACE = c.getColor(ai.dvine.R.color.bg_surface);
        LINE = c.getColor(ai.dvine.R.color.line);
        CORAL = c.getColor(ai.dvine.R.color.accent);
        ON_CORAL = c.getColor(ai.dvine.R.color.on_accent);
        SHEET = c.getColor(ai.dvine.R.color.sheet);
        CARD = c.getColor(ai.dvine.R.color.card);
        CARD_ON = c.getColor(ai.dvine.R.color.card_on);
        BORDER = LINE;
        BORDER_ON = CORAL;
        FIELD = c.getColor(ai.dvine.R.color.field);
        TEXT = c.getColor(ai.dvine.R.color.text_primary);
        SUB = c.getColor(ai.dvine.R.color.text_secondary);
        ACCENT = CORAL;
        ORANGE = CORAL;
        PILL = c.getColor(ai.dvine.R.color.pill);
        PILL_TEXT = c.getColor(ai.dvine.R.color.pill_text);
        GREY = c.getColor(ai.dvine.R.color.chat_hint);
        TRACK = c.getColor(ai.dvine.R.color.track);
    }

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
