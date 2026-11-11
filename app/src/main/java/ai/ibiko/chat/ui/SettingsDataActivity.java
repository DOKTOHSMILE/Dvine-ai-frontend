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

/** Settings \u203A Data and privacy: its own screen and layout (activity_settings_data.xml), opened from the list of sections. */
public class SettingsDataActivity extends SettingsActivity {
    @Override
    protected int layoutRes() {
        return ai.ibiko.R.layout.activity_settings_data;
    }

    @Override
    protected String sectionTitle() {
        return "Data and privacy";
    }

    @Override
    protected void buildSection() {
            // ---- data and privacy
            LinearLayout data = group("Data and privacy", "Memory, learning, export and deleting chats");
            data.addView(toggle("Learn from the API models", "Their answers are kept on this device to teach Ibiko's own model.",
                    "learn_on", true, on -> hub.model.learning = on ? new ai.ibiko.learn.LearningLog(new java.io.File(getFilesDir(), "learning")) : null));
            data.addView(link("What Ibiko remembers", "Notes, facts you taught, how it reads your words: forget any of them",
                    MemoryActivity.class), Ui.wide(this, 10));
            data.addView(link("Usage and learning", "Context, tokens today, what was learned, export", UsageActivity.class), Ui.wide(this, 10));
            data.addView(action("Export chats", "Saves all chats to Downloads/Ibiko.", this::exportChats), Ui.wide(this, 6));
            data.addView(action("Delete all chats", "Every chat and attached image on this device.",
                    () -> confirm("Delete all chats? This can't be undone.", () -> {
                        new java.io.File(getFilesDir(), "chats.json").delete();
                        delete(new java.io.File(getFilesDir(), "attachments"));
                        prefs.edit().putBoolean("chats_reset", true).apply();
                        toast("All chats deleted.");
                    })), Ui.wide(this, 6));
            data.addView(Ui.text(this, "Keys, chats and what Ibiko learns stay on this device. A message goes only to the AI provider you chose.",
                    13, Ui.SUB, false), Ui.wide(this, 10));

    }
}
