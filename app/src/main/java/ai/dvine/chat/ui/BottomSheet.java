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

    public static Dialog show(Activity a, String title, Item[] items, final Pick pick) {
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
            words.addView(Ui.text(a, it.name, 16, Ui.TEXT, false));
            if (it.about != null && !it.about.isEmpty()) {
                words.addView(Ui.text(a, it.about, 13, Ui.SUB, false));
            }
            row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
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
