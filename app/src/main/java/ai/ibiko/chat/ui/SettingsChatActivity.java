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

/** Settings \u203A Chat: its own screen and layout (activity_settings_chat.xml), opened from the list of sections. */
public class SettingsChatActivity extends SettingsActivity {
    @Override
    protected int layoutRes() {
        return ai.ibiko.R.layout.activity_settings_chat;
    }

    @Override
    protected String sectionTitle() {
        return "Chat";
    }

    @Override
    protected void buildSection() {
            // ---- chat
            LinearLayout chat = group("Chat", "Enter to send, thinking together, fact-checks, working steps");
            chat.addView(toggle("Enter sends the message", "Off: Enter starts a new line; send with the arrow.", "send_enter", false, null));
            chat.addView(toggle("Think together on weighty questions", "Decisions, plans and comparisons: three passes each"
                    + " work on it, then you get one answer. Slower; say \u201cthink together\u201d to ask for it any time.",
                    "think_together", false, null));
            chat.addView(toggle("Clear working memory when full", "The ring at the top shows how full the model's memory is. Past 85%,"
                    + " the conversation is summed up and the rest cleared, so replies stay quick.", "auto_clear", true, null));
            chat.addView(toggle("Fact-check answers", "Online: names, numbers and dates in a factual answer are looked up after it is"
                    + " shown; a correction follows only if something was wrong.", "fact_check", false, null));
            chat.addView(toggle("Interpreter for Pidgin, Yoruba and French", "A message in Pidgin, Yoruba or French is put into"
                    + " English for the model, and the answer put back into your language. Models think best in English; it takes"
                    + " a little longer.", "interpreter", true, null));
            chat.addView(toggle("Answer offline with the phone's model", "With an API chosen and no internet, the model"
                    + " downloaded to this phone answers instead of the message waiting, and the reply says so.",
                    ai.ibiko.llm.ModelHub.OFFLINE_FALLBACK, false,
                    on -> ai.ibiko.llm.ModelHub.get(this).model.offlineFallback(on)));
            chat.addView(toggle("Fix typos in my messages", "Clear spelling mistakes are fixed before Ibiko reads them (the chat"
                    + " keeps what you typed), and a note says what it read differently: tap it to keep your spelling. Names,"
                    + " Pidgin, code and words you use often are left alone.", ai.ibiko.AutoCorrect.TYPOS, true, null));
            chat.addView(toggle("Show the working steps", "What Ibiko is doing while it answers: searches, sources, models.", "show_steps", true, null),
                    Ui.wide(this, 10));

    }
}
