package ai.dvine.chat.ui;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import ai.dvine.llm.LocalModel;
import ai.dvine.llm.ModelHub;
import ai.dvine.llm.ProviderStore;

/**
 * The first screen when DVINE has no brain yet: everything said or typed goes to a brain to be understood, so one
 * is needed before the chat. Two buttons: download the brain to this phone (with its progress here, and the chat
 * open while it downloads), or use an API key (the AI providers sheet). It comes back until one is chosen; on a
 * phone that can't run the downloaded brain, only the API is offered.
 */
public final class SetupActivity extends AppCompatActivity {
    /** Whether the app has a brain, or is getting one: then there is nothing to set up. */
    public static boolean needed(android.content.Context c) {
        ModelHub hub = ModelHub.get(c);
        LocalModel m = hub.model;
        return !m.installed() && m.state() != LocalModel.State.DOWNLOADING && !hub.downloading()
                && !new ProviderStore(c).hasApi();
    }

    private ModelHub hub;
    private TextView downloadButton, downloadNote, continueButton;
    private ProgressBar bar;
    private final Runnable onChange = this::refresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        hub = ModelHub.get(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        int side = Ui.dp(this, 24);
        page.setPadding(side, Ui.dp(this, 64), side, Ui.dp(this, 32));
        scroll.addView(page);

        TextView mark = Ui.text(this, "D", 24, Ui.ON_CORAL, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(Ui.round(this, Ui.CORAL, 0, 20));
        page.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)));
        TextView title = Ui.text(this, "Choose Ibiko's model", 28, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        page.addView(title, Ui.wide(this, 24));
        TextView lead = Ui.text(this, "Everything you say or type goes to an AI model to be understood. Choose one to start.",
                16, Ui.SUB, false);
        lead.setGravity(Gravity.CENTER);
        lead.setLineSpacing(0, 1.2f);
        page.addView(lead, Ui.wide(this, 10));

        // ---- download
        LinearLayout phone = card();
        phone.addView(Ui.text(this, "Download the model", 19, Ui.TEXT, true));
        LocalModel m = hub.model;
        phone.addView(Ui.text(this, m.spec.weights + " runs on this device, private and offline. "
                + m.spec.sizeWords() + ", best on Wi-Fi.", 14, Ui.SUB, false), Ui.wide(this, 6));
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.CORAL));
        bar.setVisibility(View.GONE);
        phone.addView(bar, Ui.wide(this, 14));
        downloadNote = Ui.text(this, "", 13, Ui.SUB, false);
        downloadNote.setVisibility(View.GONE);
        phone.addView(downloadNote, Ui.wide(this, 6));
        downloadButton = Ui.button(this, "Download", true);
        downloadButton.setOnClickListener(v -> {
            hub.startDownload(this);
            refresh();
        });
        phone.addView(downloadButton, Ui.wide(this, 14));
        page.addView(phone, Ui.wide(this, 32));

        // ---- API
        LinearLayout api = card();
        api.addView(Ui.text(this, "Use an API key", 19, Ui.TEXT, true));
        api.addView(Ui.text(this, "Claude, GPT, Gemini, OpenRouter or your own gateway. Needs a connection; up to three can"
                + " take over from each other.", 14, Ui.SUB, false), Ui.wide(this, 6));
        TextView add = Ui.button(this, "Add an API key", false);
        add.setOnClickListener(v -> startActivity(new Intent(this, ProvidersActivity.class)));
        api.addView(add, Ui.wide(this, 14));
        page.addView(api, Ui.wide(this, 12));

        continueButton = Ui.button(this, "Continue to the chat while it downloads", false);
        continueButton.setVisibility(View.GONE);
        continueButton.setOnClickListener(v -> finish());
        page.addView(continueButton, Ui.wide(this, 24));
        setContentView(scroll);
        Ui.fitInsets(scroll);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.round(this, Ui.SURFACE, Ui.LINE, 18));
        int p = Ui.dp(this, 18);
        c.setPadding(p, p, p, p);
        return c;
    }

    private void refresh() {
        LocalModel m = hub.model;
        boolean downloading = m.state() == LocalModel.State.DOWNLOADING || hub.downloading();
        if (m.state() == LocalModel.State.UNSUPPORTED) {
            downloadButton.setEnabled(false);
            downloadButton.setAlpha(0.4f);
            downloadNote.setVisibility(View.VISIBLE);
            downloadNote.setText("This device can't run it: " + m.problem() + ". Use an API key instead.");
        } else if (downloading) {
            bar.setVisibility(View.VISIBLE);
            bar.setProgress(hub.status.percent());
            downloadNote.setVisibility(View.VISIBLE);
            downloadNote.setText(hub.status.detail());
            downloadButton.setVisibility(View.GONE);
            continueButton.setVisibility(View.VISIBLE);
        }
        if (m.installed() || new ProviderStore(this).hasApi()) {
            finish(); // a brain is here: on to the chat
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hub.addListener(onChange);
        refresh();
    }

    @Override
    protected void onPause() {
        hub.removeListener(onChange);
        super.onPause();
    }

    /** Back without a brain closes DVINE: it can't understand anything without one. */
    @Override
    public void onBackPressed() {
        if (needed(this)) {
            finishAffinity();
        } else {
            super.onBackPressed();
        }
    }
}
