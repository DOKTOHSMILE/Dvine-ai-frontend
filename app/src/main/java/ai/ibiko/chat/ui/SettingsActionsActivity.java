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

/** Settings \u203A Actions: its own screen and layout (activity_settings_actions.xml), opened from the list of sections. */
public class SettingsActionsActivity extends SettingsActivity {
    @Override
    protected int layoutRes() {
        return ai.ibiko.R.layout.activity_settings_actions;
    }

    @Override
    protected String sectionTitle() {
        return "Actions";
    }

    @Override
    protected void buildSection() {
            // ---- actions on the user's behalf
            LinearLayout acts = group("Actions", "Whether posts and shares wait for your yes");
            acts.addView(toggle("Auto-post on your behalf", "Posts and comments you ask for go out from your account at once,"
                    + " without asking first (also in Social accounts). Off: each one waits for your yes.", "social_auto", false, null));
            acts.addView(toggle("Auto-approve actions", "Posts, shares and copies go ahead without asking first. Off: each one is"
                    + " shown with its exact text and where it goes, and waits for your yes.", "actions_auto", false, null));

    }
}
