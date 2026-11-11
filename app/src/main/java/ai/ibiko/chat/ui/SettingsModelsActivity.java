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

/** Settings \u203A Models: its own screen and layout (activity_settings_models.xml), opened from the list of sections. */
public class SettingsModelsActivity extends SettingsActivity {
    @Override
    protected int layoutRes() {
        return ai.ibiko.R.layout.activity_settings_models;
    }

    @Override
    protected String sectionTitle() {
        return "Models";
    }

    @Override
    protected void buildSection() {
            // ---- models
            LinearLayout apis = group("Models", "AI providers and the model on this device");
            apis.addView(link("AI providers", "API keys, up to three that take over from each other, fast and strong models",
                    ProvidersActivity.class));
            LinearLayout model = new LinearLayout(this);
            model.setOrientation(LinearLayout.VERTICAL);
            model.setPadding(0, Ui.dp(this, 14), 0, 0);
            modelTitle = Ui.text(this, "", 17, Ui.TEXT, true);
            model.addView(modelTitle);
            modelAbout = Ui.text(this, "", 13, Ui.SUB, false);
            model.addView(modelAbout, Ui.wide(this, 4));
            modelState = Ui.text(this, "", 15, Ui.TEXT, false);
            model.addView(modelState, Ui.wide(this, 12));
            bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.CORAL));
            model.addView(bar, Ui.wide(this, 8));
            modelDetail = Ui.text(this, "", 13, Ui.SUB, false);
            model.addView(modelDetail, Ui.wide(this, 4));
            LinearLayout buttons = new LinearLayout(this);
            modelAction = Ui.button(this, "Download", true);
            modelAction.setOnClickListener(v -> modelAction());
            buttons.addView(modelAction, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            modelRemove = Ui.button(this, "Remove", false);
            modelRemove.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setMessage("Remove the model from this device? It frees " + hub.chosen().sizeWords()
                            + ". Answers then need an API until you download it again.")
                    .setPositiveButton("Remove", (d, w) -> {
                        hub.stopDownload();
                        new Thread(() -> {
                            hub.model.uninstall();
                            runOnUiThread(this::refreshModel);
                        }, "ibiko-remove-model").start();
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            rp.leftMargin = Ui.dp(this, 10);
            buttons.addView(modelRemove, rp);
            model.addView(buttons, Ui.wide(this, 14));
            speed = Ui.text(this, "", 13, Ui.SUB, false);
            speed.setVisibility(View.GONE);
            onPhone = new LinearLayout(this);
            onPhone.setOrientation(LinearLayout.VERTICAL);
            model.addView(onPhone, Ui.wide(this, 6));
            apis.addView(model);
            refreshModel();

    }
}
