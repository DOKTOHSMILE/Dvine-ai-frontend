package ai.ibiko.chat.ui;

import android.app.Activity;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

/**
 * The standard "please wait" dialog: a spinner and one line, in the app's dialog style, with Cancel. It cannot be
 * tapped away by accident, so a slow step (GitHub listing repositories) is not started twice.
 */
public final class Waiting {
    private Waiting() {
    }

    /** Shows it; {@code onCancel} runs when Cancel is tapped (null: no Cancel button). */
    public static AlertDialog show(Activity a, String line, Runnable onCancel) {
        float d = a.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(24 * d);
        row.setPadding(pad, Math.round(22 * d), pad, Math.round(10 * d));
        ProgressBar spin = new ProgressBar(a);
        spin.setIndeterminate(true);
        spin.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(a.getColor(ai.ibiko.R.color.accent)));
        row.addView(spin, new LinearLayout.LayoutParams(Math.round(32 * d), Math.round(32 * d)));
        TextView text = new TextView(a);
        text.setText(line);
        text.setTextSize(15);
        text.setTextColor(a.getColor(ai.ibiko.R.color.text_primary));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(Math.round(16 * d));
        row.addView(text, lp);
        row.setContentDescription(line);
        AlertDialog.Builder b = new AlertDialog.Builder(a).setView(row).setCancelable(false);
        if (onCancel != null) {
            b.setNegativeButton("Cancel", (dlg, w) -> onCancel.run());
        }
        AlertDialog dialog = b.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        return dialog;
    }
}
