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

/** Settings \u203A Web and services: its own screen and layout (activity_settings_web.xml), opened from the list of sections. */
public class SettingsWebActivity extends SettingsActivity {
    @Override
    protected int layoutRes() {
        return ai.ibiko.R.layout.activity_settings_web;
    }

    @Override
    protected String sectionTitle() {
        return "Web and services";
    }

    @Override
    protected void buildSection() {
            // ---- web and services
            LinearLayout web = group("Web and services", "Web look-ups and connectors");
            web.addView(toggle("Look things up on the web", "For news, prices and anything recent; the sources are listed.", "fresh_facts", true, null));
            web.addView(link("Connectors", "Web search provider, GitHub, calendar, Notion, MCP servers", ConnectorsActivity.class), Ui.wide(this, 10));

    }
}
