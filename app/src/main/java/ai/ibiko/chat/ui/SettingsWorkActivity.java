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

/** Settings \u203A Work and actions: its own screen and layout (activity_settings_work.xml), opened from the list of sections. */
public class SettingsWorkActivity extends SettingsActivity {
    @Override
    protected int layoutRes() {
        return ai.ibiko.R.layout.activity_settings_work;
    }

    @Override
    protected String sectionTitle() {
        return "Work and actions";
    }

    @Override
    protected void buildSection() {
            LinearLayout work = group("Work and actions", "What long work says in the chat; sharing and posting");
            work.addView(toggle("Say what each step found", "During long work, a line in the chat after each step: what it looked for"
                    + " and what it found. The full summary comes at the end.", "narrate_steps", true, null));
            work.addView(toggle("Share and post without asking first", "Off (recommended): each share, post or copy is shown and waits"
                    + " for your yes.", "actions_auto", false, null), Ui.wide(this, 10));
            work.addView(link("What Ibiko remembers", "Answers you marked correct, what it knows about you, actions done", MemoryActivity.class),
                    Ui.wide(this, 10));

    }
}
