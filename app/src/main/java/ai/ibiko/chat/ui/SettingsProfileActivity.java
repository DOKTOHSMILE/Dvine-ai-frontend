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

/** Settings \u203A Profile: its own screen and layout (activity_settings_profile.xml), opened from the list of sections. */
public class SettingsProfileActivity extends SettingsActivity {
    @Override
    protected int layoutRes() {
        return ai.ibiko.R.layout.activity_settings_profile;
    }

    @Override
    protected String sectionTitle() {
        return "Profile";
    }

    @Override
    protected void buildSection() {
            // ---- profile
            LinearLayout profile = group("Profile", "Your name and who starts the chat");
            profile.addView(Ui.text(this, "What should Ibiko call you?", 16, Ui.TEXT, false));
            final android.widget.EditText name = Ui.field(this, "Your name", prefs.getString("user_name", ""), false, false);
            name.setOnFocusChangeListener((v, focused) -> {
                if (!focused) {
                    String n = name.getText().toString().trim();
                    prefs.edit().putString("user_name", n).putString("given_name", n).apply();
                }
            });
            profile.addView(name, Ui.wide(this, 10));

    }
}
