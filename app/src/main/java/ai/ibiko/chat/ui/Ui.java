package ai.ibiko.chat.ui;

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
     * One palette for every screen, DVINE's own: day (cool off-white) or night (deep blue-black), with a violet
     * accent. Read from the theme's colours by {@link #init}, so it follows the phone's day / night setting or the
     * one chosen in DVINE.
     */
    static int BG, SURFACE, LINE, CORAL, ON_CORAL, SHEET, CARD, CARD_ON, BORDER, BORDER_ON, FIELD, TEXT, SUB, ACCENT, PILL,
            PILL_TEXT, ORANGE, GREY, TRACK;
    static final int BLUE = 0xFF5B8DEF, GREEN = 0xFF5FB37F, AMBER = 0xFFE8B04A;

    static {
        // night values until a screen calls init (tests that build views without an activity)
        BG = 0xFF121218; SURFACE = 0xFF1C1C24; LINE = 0xFF2E2D3A; CORAL = 0xFF8B7CFF; ON_CORAL = 0xFF0E0E14; SHEET = 0xFF17171F;
        CARD = 0xFF1E1E28; CARD_ON = 0xFF2A2545; BORDER = LINE; BORDER_ON = CORAL; FIELD = 0xFF262632; TEXT = 0xFFECECF3;
        SUB = 0xFF9A99AA; ACCENT = CORAL; PILL = 0xFF2D2650; PILL_TEXT = 0xFFDCD6FF; ORANGE = CORAL; GREY = 0xFF8A8898;
        TRACK = 0xFF2E2D3A;
    }

    /**
     * Keeps a screen clear of the status bar, the navigation bar, a camera cutout and the keyboard. Android 15 draws
     * apps edge to edge and no longer shrinks them for the keyboard ("adjustResize" does nothing there), so without
     * this the keyboard covers the fields being typed in. The focused field is scrolled back into view.
     */
    static void fitInsets(final android.view.View root) {
        final int l = root.getPaddingLeft(), t = root.getPaddingTop(), r = root.getPaddingRight(), b = root.getPaddingBottom();
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets in = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()
                    | androidx.core.view.WindowInsetsCompat.Type.displayCutout()
                    | androidx.core.view.WindowInsetsCompat.Type.ime());
            v.setPadding(l + in.left, t + in.top, r + in.right, b + in.bottom);
            final android.view.View focus = v.findFocus();
            if (focus != null && insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())) {
                v.post(() -> {
                    android.graphics.Rect box = new android.graphics.Rect(0, 0, focus.getWidth(), focus.getHeight());
                    focus.requestRectangleOnScreen(box, false);
                });
            }
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(root);
    }

    /** Reads the palette for the screen's current day or night mode. */
    static void init(Context c) {
        BG = c.getColor(ai.ibiko.R.color.bg_main);
        SURFACE = c.getColor(ai.ibiko.R.color.bg_surface);
        LINE = c.getColor(ai.ibiko.R.color.line);
        CORAL = c.getColor(ai.ibiko.R.color.accent);
        ON_CORAL = c.getColor(ai.ibiko.R.color.on_accent);
        SHEET = c.getColor(ai.ibiko.R.color.sheet);
        CARD = c.getColor(ai.ibiko.R.color.card);
        CARD_ON = c.getColor(ai.ibiko.R.color.card_on);
        BORDER = LINE;
        BORDER_ON = CORAL;
        FIELD = c.getColor(ai.ibiko.R.color.field);
        TEXT = c.getColor(ai.ibiko.R.color.text_primary);
        SUB = c.getColor(ai.ibiko.R.color.text_secondary);
        ACCENT = CORAL;
        ORANGE = CORAL;
        PILL = c.getColor(ai.ibiko.R.color.pill);
        PILL_TEXT = c.getColor(ai.ibiko.R.color.pill_text);
        GREY = c.getColor(ai.ibiko.R.color.chat_hint);
        TRACK = c.getColor(ai.ibiko.R.color.track);
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
        // room between lines: explanations wrapped tight read as one block (every screen built with this helper)
        t.setLineSpacing(dp(c, sp <= 14 ? 3 : 2), 1f);
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
