package ai.dvine.chat.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A sheet that slides up from the bottom of the screen with a row per choice (an icon, a name and a line under it),
 * the standard Android way of offering a short menu. Tapping outside it, or Back, closes it.
 */
public final class BottomSheet {
    /** One choice: its icon, its name and an optional line of explanation. */
    public static final class Item {
        final int icon;
        final String name, about;

        public Item(int icon, String name, String about) {
            this.icon = icon;
            this.name = name;
            this.about = about;
        }
    }

    public interface Pick {
        void picked(int which);
    }

    private BottomSheet() {
    }

    /**
     * A sheet holding any view under its title (the steps of a piece of work, say), scrolling when it is long and
     * never taller than about three quarters of the screen.
     */
    public static Dialog showView(Activity a, String title, View content) {
        Ui.init(a);
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet = frame(a, d, title);
        android.widget.ScrollView sv = new android.widget.ScrollView(a) {
            @Override
            protected void onMeasure(int w, int h) {
                int most = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.72f);
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(most, MeasureSpec.AT_MOST));
            }
        };
        sv.addView(content);
        sheet.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        place(a, d, sheet);
        return d;
    }

    /** The sheet's rounded body with its grip and title. */
    private static LinearLayout frame(Activity a, Dialog d, String title) {
        LinearLayout sheet = new LinearLayout(a);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.SHEET);
        float r = Ui.dp(a, 24);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(bg);
        sheet.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 16));
        View grip = new View(a);
        grip.setBackground(Ui.round(a, Ui.LINE, 0, 3));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Ui.dp(a, 36), Ui.dp(a, 4));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        gp.bottomMargin = Ui.dp(a, 10);
        sheet.addView(grip, gp);
        if (title != null) {
            TextView t = Ui.text(a, title, 17, Ui.TEXT, true);
            t.setPadding(Ui.dp(a, 24), Ui.dp(a, 4), Ui.dp(a, 24), Ui.dp(a, 8));
            sheet.addView(t);
        }
        return sheet;
    }

    /** Shows the sheet at the bottom of the screen, dimming what is behind, clear of the navigation bar. */
    private static void place(Activity a, Dialog d, LinearLayout sheet) {
        d.setContentView(sheet);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.5f);
            w.getAttributes().windowAnimations = android.R.style.Animation_InputMethod; // slides up from the bottom
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(sheet, (v, insets) -> {
                int nav = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom;
                v.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 16) + nav);
                return insets;
            });
        }
        d.setCanceledOnTouchOutside(true);
        d.show();
    }

    public static Dialog show(Activity a, String title, Item[] items, final Pick pick) {
        return show(a, title, null, items, -1, pick);
    }

    /**
     * A choice of one: a line under the title, and the chosen row ({@code selected}) in the accent colour with a
     * check mark ({@code -1}: none).
     */
    public static Dialog show(Activity a, String title, String subtitle, Item[] items, int selected, final Pick pick) {
        Ui.init(a);
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet = new LinearLayout(a);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.SHEET);
        float r = Ui.dp(a, 24);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(bg);
        sheet.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 16));

        View grip = new View(a);
        grip.setBackground(Ui.round(a, Ui.LINE, 0, 3));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Ui.dp(a, 36), Ui.dp(a, 4));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        gp.bottomMargin = Ui.dp(a, 10);
        sheet.addView(grip, gp);
        if (title != null) {
            TextView t = Ui.text(a, title, 17, Ui.TEXT, true);
            t.setPadding(Ui.dp(a, 24), Ui.dp(a, 4), Ui.dp(a, 24), Ui.dp(a, 8));
            sheet.addView(t);
        }
        if (subtitle != null) {
            TextView st = Ui.text(a, subtitle, 13, Ui.SUB, false);
            st.setPadding(Ui.dp(a, 24), 0, Ui.dp(a, 24), Ui.dp(a, 8));
            sheet.addView(st);
        }
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            Item it = items[i];
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Ui.dp(a, 56));
            row.setPadding(Ui.dp(a, 24), Ui.dp(a, 8), Ui.dp(a, 24), Ui.dp(a, 8));
            android.util.TypedValue ripple = new android.util.TypedValue();
            a.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
            row.setBackgroundResource(ripple.resourceId);
            ImageView icon = new ImageView(a);
            icon.setImageResource(it.icon);
            row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(a, 24), Ui.dp(a, 24)));
            LinearLayout words = new LinearLayout(a);
            words.setOrientation(LinearLayout.VERTICAL);
            words.setPadding(Ui.dp(a, 20), 0, 0, 0);
            boolean on = i == selected;
            words.addView(Ui.text(a, it.name, 16, on ? Ui.ACCENT : Ui.TEXT, false));
            if (it.about != null && !it.about.isEmpty()) {
                words.addView(Ui.text(a, it.about, 13, on ? Ui.ACCENT : Ui.SUB, false));
            }
            row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (on) {
                ImageView check = new ImageView(a);
                check.setImageResource(ai.dvine.R.drawable.ic_check);
                check.setColorFilter(Ui.ACCENT);
                row.addView(check, new LinearLayout.LayoutParams(Ui.dp(a, 22), Ui.dp(a, 22)));
                row.setSelected(true);
            }
            row.setContentDescription(it.name + (on ? ", chosen" : "") + (it.about == null ? "" : ". " + it.about));
            row.setOnClickListener(v -> {
                d.dismiss();
                pick.picked(which);
            });
            sheet.addView(row);
        }
        d.setContentView(sheet);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.5f);
            w.getAttributes().windowAnimations = android.R.style.Animation_InputMethod; // slides up from the bottom
            // keep clear of the navigation bar
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(sheet, (v, insets) -> {
                int nav = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom;
                v.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 16) + nav);
                return insets;
            });
        }
        d.setCanceledOnTouchOutside(true);
        d.show();
        return d;
    }
}
