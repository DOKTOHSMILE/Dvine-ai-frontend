package ai.ibiko.chat.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import ai.ibiko.llm.LocalModel;
import ai.ibiko.llm.ModelHub;

/** Settings \u203A Advanced: its own screen and layout (activity_settings_advanced.xml), opened from the list of sections. */
public class SettingsAdvancedActivity extends SettingsActivity {
    @Override
    protected int layoutRes() {
        return ai.ibiko.R.layout.activity_settings_advanced;
    }

    @Override
    protected String sectionTitle() {
        return "Advanced";
    }

    @Override
    protected void buildSection() {
            // ---- advanced: the limits DVINE works within. Closed until opened on purpose; each number is a row, changed only
            // in its own editor (a slider in a scrolling list moved whenever a finger scrolled past it)
            LinearLayout adv = group("Advanced", "Reply lengths, agent steps, offline memory and other limits");
            adv.addView(Ui.text(this, "The numbers Ibiko works with. The defaults suit most people: change one only for a reason."
                    + " Each provider's own settings are under AI providers › a provider › Advanced.", 13, Ui.SUB, false));
            for (final ai.ibiko.chat.Tuning.Knob k : ai.ibiko.chat.Tuning.ALL) {
                adv.addView(knobRow(k), Ui.wide(this, 4));
            }
            TextView reset = Ui.text(this, "Back to the defaults", 15, Ui.ACCENT, false);
            reset.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 4));
            reset.setOnClickListener(x -> confirm("Put every advanced number back to its default?", () -> {
                android.content.SharedPreferences.Editor e = prefs.edit();
                for (ai.ibiko.chat.Tuning.Knob k : ai.ibiko.chat.Tuning.ALL) {
                    e.remove(k.key);
                }
                e.apply();
                fill();
            }));
            adv.addView(reset);

    }
}
