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

/** Settings \u203A Appearance: its own screen and layout (activity_settings_appearance.xml), opened from the list of sections. */
public class SettingsAppearanceActivity extends SettingsActivity {
    @Override
    protected int layoutRes() {
        return ai.ibiko.R.layout.activity_settings_appearance;
    }

    @Override
    protected String sectionTitle() {
        return "Appearance";
    }

    @Override
    protected void buildSection() {
            // ---- appearance
            LinearLayout look = group("Appearance", "System, light or dark");
            look.addView(choice(new String[]{"System", "Light", "Dark"}, new String[]{"system", "light", "dark"},
                    prefs.getString("theme", "system"), k -> {
                        prefs.edit().putString("theme", k).apply();
                        ai.ibiko.MainActivity.applyTheme(k);
                    }));

    }
}
