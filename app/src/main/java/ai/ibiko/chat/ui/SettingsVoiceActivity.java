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

/** Settings \u203A Voice: its own screen and layout (activity_settings_voice.xml), opened from the list of sections. */
public class SettingsVoiceActivity extends SettingsActivity {
    @Override
    protected int layoutRes() {
        return ai.ibiko.R.layout.activity_settings_voice;
    }

    @Override
    protected String sectionTitle() {
        return "Voice";
    }

    @Override
    protected void buildSection() {
            // ---- voice
            LinearLayout voice = group("Voice", "Reading replies aloud and how fast");
            voice.addView(Ui.text(this, "Read replies aloud", 16, Ui.TEXT, false));
            voice.addView(choice(new String[]{"Always", "After the mic", "Never"}, new String[]{"always", "mic", "off"},
                    prefs.getString("read_aloud", "always"), k -> prefs.edit().putString("read_aloud", k).apply()), Ui.wide(this, 10));
            voice.addView(Ui.text(this, "Speaking speed", 16, Ui.TEXT, false), Ui.wide(this, 14));
            voice.addView(choice(new String[]{"Slower", "Normal", "Faster"}, new String[]{"0.85", "1.0", "1.2"},
                    prefs.getString("speech_rate", "1.0"), k -> prefs.edit().putString("speech_rate", k).apply()), Ui.wide(this, 10));
            voiceChoices(voice);
            voice.addView(toggle("Have each reply's audio ready", "While you read a reply, its audio is made in the background"
                    + " and kept on this device for a day, so Listen plays at once. After that it is cleared and made again when"
                    + " you listen. Off: the audio is made when you tap Listen.", "voice_prepare", true, null), Ui.wide(this, 10));
            voice.addView(toggle("Soft music under the voice", "A low, quiet chord that swells while a reply is read aloud. Off:"
                    + " no sound but the voice, and no audio stream is open while nothing is read aloud. Takes effect the next time Ibiko opens.", "music", false, null), Ui.wide(this, 10));
            voice.addView(Ui.text(this, "The mic writes what you say into the message box, so you can edit it before sending.",
                    13, Ui.SUB, false), Ui.wide(this, 10));

    }
}
