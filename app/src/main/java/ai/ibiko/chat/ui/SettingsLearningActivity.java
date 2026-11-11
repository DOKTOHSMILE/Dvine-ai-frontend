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

/** Settings \u203A Ibiko 1 and learning: its own screen and layout (activity_settings_learning.xml), opened from the list of sections. */
public class SettingsLearningActivity extends SettingsActivity {
    @Override
    protected int layoutRes() {
        return ai.ibiko.R.layout.activity_settings_learning;
    }

    @Override
    protected String sectionTitle() {
        return "Ibiko 1 and learning";
    }

    @Override
    protected void buildSection() {
            // ---- Ibiko 1, the work in the chat, and actions (in step with the model menu and the tools sheet)
            LinearLayout own = group("Ibiko 1 and learning", "The brain in use, the learning schedule, training file");
            String pick = prefs.getString("brain_pick", "api");
            own.addView(Ui.text(this, "Answering now: " + ("ibiko".equals(pick) ? ai.ibiko.ibiko1.Ibiko1.NAME : "device".equals(pick)
                    ? "the offline model" : "your API models") + ". Change it from the model name under the chat's title.", 14, Ui.SUB, false));
            final ai.ibiko.ibiko1.LearningSchedule.Plan plan = ai.ibiko.ibiko1.LearningJob.plan(this);
            own.addView(toggle("Learn on a schedule", (plan.on ? "On, " : "Hours: ") + plan.words() + ". " + ai.ibiko.ibiko1.Ibiko1.NAME
                    + " goes online and learns from what you asked. Set the hours from the model menu.", "learn_schedule_on", plan.on,
                    on -> ai.ibiko.ibiko1.LearningJob.setPlan(this, new ai.ibiko.ibiko1.LearningSchedule.Plan(on, plan.start, plan.end,
                            plan.wifiOnly, plan.chargingOnly, plan.most))), Ui.wide(this, 10));
            own.addView(Ui.text(this, ai.ibiko.ibiko1.Ibiko1.NAME + " learns from every message, every model's answer, what it researches"
                    + " and the social pages it reads (language only: people's words are never taken as facts). "
                    + ai.ibiko.ibiko1.Ibiko1.scoreLine(getFilesDir()) + ". See it under Memory.", 13, Ui.SUB, false), Ui.wide(this, 10));
            android.view.View learnNow = Ui.button(this, "Learn now", false);
            learnNow.setOnClickListener(v -> {
                ai.ibiko.ibiko1.LearningJob.learnNow(this);
                android.widget.Toast.makeText(this, "Learning now. The Workload sheet shows it.", android.widget.Toast.LENGTH_SHORT).show();
            });
            own.addView(learnNow, Ui.wide(this, 10));
            android.view.View train = Ui.button(this, "Export Ibiko 1's training file", false);
            train.setOnClickListener(v -> exportTraining());
            own.addView(train, Ui.wide(this, 8));
            own.addView(Ui.text(this, "Everything Ibiko 1 learned (confirmed answers, facts, words) as a JSONL file in Downloads/Ibiko,"
                    + " for training a model of your own.", 13, Ui.SUB, false), Ui.wide(this, 6));

    }
}
