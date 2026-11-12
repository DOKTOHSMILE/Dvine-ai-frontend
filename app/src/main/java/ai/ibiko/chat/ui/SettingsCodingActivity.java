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

/** Settings \u203A Coding: its own screen and layout (activity_settings_coding.xml), opened from the list of sections. */
public class SettingsCodingActivity extends SettingsActivity {
    @Override
    protected int layoutRes() {
        return ai.ibiko.R.layout.activity_settings_coding;
    }

    @Override
    protected String sectionTitle() {
        return "Coding";
    }

    @Override
    protected void buildSection() {
            // ---- ibiko
            LinearLayout ibiko = group("Coding", "Commands, long jobs and the workspace");
            ibiko.addView(toggle("Run commands without asking", "Auto-accept: shell commands, git pushes and pull requests go ahead.",
                    "auto_accept", false, null));
            ibiko.addView(toggle("Keep working in the background", "While Ibiko works (a reply, a clone, a download) it"
                    + " carries on with the screen off or another app open, with one quiet notification saying what it is doing;"
                    + " idle, nothing shows. Off: Android may stop work in the background.",
                    ai.ibiko.main.KeepAlive.PREF, true, on -> {
                        if (!on) {
                            ai.ibiko.main.KeepAlive.stop(this);
                        }
                    }), Ui.wide(this, 10));
            ibiko.addView(toggle("Check code after each edit", "When Ibiko writes or changes code it runs the project's tests"
                    + " (npm, pytest, cargo, go, make), or checks the file's syntax, and fixes what fails before going on. The check"
                    + " is a command, so it asks first unless commands run without asking.", ai.ibiko.agent.EditCheck.PREF, true, null),
                    Ui.wide(this, 10));
            ibiko.addView(toggle("Keep working until done", "Long jobs run for hours if needed: plan, build, test, with a short progress"
                    + " note in the chat every few steps. Off: Ibiko stops after one batch of steps.", "ibiko_keep_going", true, null),
                    Ui.wide(this, 10));
            ibiko.addView(toggle("Mini agents for heavy jobs", "Audits, research and whole-repo reviews are split into parts; a fresh"
                    + " helper takes each in turn (on the device's model too) and its findings come as it finishes.", "mini_agents", true, null),
                    Ui.wide(this, 10));
            final java.io.File ws = new java.io.File(getFilesDir(), "ibiko");
            ibiko.addView(action("Clear Ibiko's workspace", "Deletes the cloned repositories and files on this device (" + bytes(size(ws)) + ").",
                    () -> confirm("Delete everything in Ibiko's workspace? Unpushed changes are lost.", () -> {
                        delete(ws);
                        toast("Workspace cleared.");
                        fill();
                    })), Ui.wide(this, 10));

    }
}
