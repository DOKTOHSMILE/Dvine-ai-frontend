package ai.dvine.chat.ui;

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

import ai.dvine.llm.LocalModel;
import ai.dvine.llm.ModelHub;

/**
 * Settings, in one place: the model on this phone (its download with live progress, pause, resume and remove),
 * whether replies are read aloud, day or night, and the ways into AI providers, Connectors and Usage.
 */
public final class SettingsActivity extends AppCompatActivity {
    private LinearLayout body;
    private ModelHub hub;
    private SharedPreferences prefs;
    private ProgressBar bar;
    private TextView modelState, modelDetail, modelAction, modelRemove;
    private final Runnable onChange = this::refreshModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        hub = ModelHub.get(this);
        prefs = getSharedPreferences("dvine", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        int pad = Ui.dp(this, 16);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(pad, Ui.dp(this, 20), pad, Ui.dp(this, 8));
        TextView close = Ui.text(this, "✕", 22, Ui.TEXT, false);
        close.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        close.setOnClickListener(v -> finish());
        head.addView(close);
        TextView title = Ui.text(this, "Settings", 19, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.gap(this, 1), new LinearLayout.LayoutParams(Ui.dp(this, 34), 1));
        root.addView(head);
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, 0, pad, Ui.dp(this, 32));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        Ui.fitInsets(root);
        fill();
    }

    private void fill() {
        body.removeAllViews();

        // ---- the model on this phone
        section("Model on this phone");
        LinearLayout model = card();
        model.addView(Ui.text(this, ai.dvine.llm.ModelSpec.WEIGHTS, 17, Ui.TEXT, true));
        model.addView(Ui.text(this, "Answers offline and when every API fails. " + hub.model.spec.sizeWords() + ", best on Wi-Fi.",
                13, Ui.SUB, false), Ui.wide(this, 4));
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
                .setMessage("Remove the model from this phone? It frees " + hub.model.spec.sizeWords()
                        + ". Answers then need an API until you download it again.")
                .setPositiveButton("Remove", (d, w) -> {
                    hub.stopDownload();
                    new Thread(() -> {
                        hub.model.uninstall();
                        runOnUiThread(this::refreshModel);
                    }, "dvine-remove-model").start();
                })
                .setNegativeButton("Cancel", null)
                .show());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        rp.leftMargin = Ui.dp(this, 10);
        buttons.addView(modelRemove, rp);
        model.addView(buttons, Ui.wide(this, 14));
        body.addView(model, Ui.wide(this, 8));
        refreshModel();

        // ---- voice
        section("Voice");
        LinearLayout voice = card();
        voice.addView(Ui.text(this, "Read replies aloud", 16, Ui.TEXT, false));
        voice.addView(choice(new String[]{"Always", "After the mic", "Never"}, new String[]{"always", "mic", "off"},
                prefs.getString("read_aloud", "always"), k -> prefs.edit().putString("read_aloud", k).apply()), Ui.wide(this, 10));
        voice.addView(Ui.text(this, "The mic writes what you say into the message box, so you can edit it before sending.",
                13, Ui.SUB, false), Ui.wide(this, 10));
        body.addView(voice, Ui.wide(this, 8));

        // ---- appearance
        section("Appearance");
        LinearLayout look = card();
        look.addView(choice(new String[]{"System", "Light", "Dark"}, new String[]{"system", "light", "dark"},
                prefs.getString("theme", "system"), k -> {
                    prefs.edit().putString("theme", k).apply();
                    ai.dvine.MainActivity.applyTheme(k);
                }));
        body.addView(look, Ui.wide(this, 8));

        // ---- the rest
        section("More");
        LinearLayout more = card();
        more.addView(link("AI providers", "API keys, backups, fast and strong models", ProvidersActivity.class));
        more.addView(link("Connectors", "Web search, calendar, GitHub, Notion, MCP", ConnectorsActivity.class), Ui.wide(this, 4));
        more.addView(link("Usage and learning", "Context, tokens, what DVINE has learned, export", UsageActivity.class), Ui.wide(this, 4));
        body.addView(more, Ui.wide(this, 8));

        TextView version = Ui.text(this, "DVINE " + version(), 13, Ui.SUB, false);
        version.setGravity(Gravity.CENTER);
        body.addView(version, Ui.wide(this, 20));
    }

    private String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /** The model's state, progress and buttons, kept live while the screen is open. */
    private void refreshModel() {
        if (modelState == null) {
            return;
        }
        LocalModel m = hub.model;
        boolean downloading = hub.downloading() || m.state() == LocalModel.State.DOWNLOADING;
        boolean installed = m.installed();
        String state;
        switch (m.state()) {
            case READY: state = "Ready and running"; break;
            case LOADING: state = "Starting"; break;
            case INSTALLED: state = "Downloaded"; break;
            case UNSUPPORTED: state = "This phone can't run it: " + m.problem(); break;
            case FAILED: state = "Couldn't start: " + m.problem(); break;
            default: state = downloading ? "Downloading" : hub.status.phase() == ai.dvine.llm.DownloadStatus.Phase.PAUSED
                    ? "Paused" : "Not downloaded";
        }
        if (downloading) {
            state = "Downloading · " + hub.status.percent() + "%";
        }
        modelState.setText(state);
        boolean showBar = downloading || (!installed && hub.status.percent() > 0);
        bar.setVisibility(showBar ? View.VISIBLE : View.GONE);
        bar.setProgress(hub.status.percent());
        String detail = downloading || hub.status.phase() == ai.dvine.llm.DownloadStatus.Phase.PAUSED
                || hub.status.phase() == ai.dvine.llm.DownloadStatus.Phase.FAILED ? hub.status.detail() : "";
        modelDetail.setText(detail == null ? "" : detail);
        modelDetail.setVisibility(detail == null || detail.isEmpty() ? View.GONE : View.VISIBLE);
        boolean unsupported = m.state() == LocalModel.State.UNSUPPORTED;
        modelAction.setVisibility(installed || unsupported ? View.GONE : View.VISIBLE);
        modelAction.setText(downloading ? "Pause" : hub.status.percent() > 0 ? "Resume" : "Download");
        modelRemove.setVisibility(installed || (!downloading && hub.status.percent() > 0) ? View.VISIBLE : View.GONE);
    }

    private void modelAction() {
        if (hub.downloading()) {
            hub.stopDownload();
        } else {
            hub.startDownload(this);
        }
        modelState.postDelayed(this::refreshModel, 300);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hub.addListener(onChange);
        refreshModel();
    }

    @Override
    protected void onPause() {
        hub.removeListener(onChange);
        super.onPause();
    }

    // ------------------------------------------------------------ pieces

    private void section(String s) {
        TextView t = Ui.text(this, s, 15, Ui.SUB, false);
        t.setPadding(Ui.dp(this, 6), Ui.dp(this, 20), 0, Ui.dp(this, 4));
        body.addView(t);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.round(this, Ui.SURFACE, 0, 20));
        int p = Ui.dp(this, 18);
        c.setPadding(p, p, p, p);
        return c;
    }

    /** A row of options, the chosen one filled with the accent. */
    private View choice(final String[] labels, final String[] keys, String now, final java.util.function.Consumer<String> pick) {
        final LinearLayout row = new LinearLayout(this);
        row.setBackground(Ui.round(this, Ui.FIELD, 0, 14));
        row.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        for (int i = 0; i < labels.length; i++) {
            final String key = keys[i];
            boolean on = key.equals(now);
            TextView t = Ui.text(this, labels[i], 14, on ? Ui.ON_CORAL : Ui.TEXT, on);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(Ui.dp(this, 40));
            t.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
            if (on) {
                t.setBackground(Ui.round(this, Ui.CORAL, 0, 11));
            }
            t.setOnClickListener(v -> {
                pick.accept(key);
                LinearLayout parent = (LinearLayout) row.getParent();
                int index = parent.indexOfChild(row);
                android.view.ViewGroup.LayoutParams lp = row.getLayoutParams();
                parent.removeView(row);
                parent.addView(choice(labels, keys, key, pick), index, lp);
            });
            row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        return row;
    }

    private View link(String name, String about, final Class<?> screen) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        r.addView(Ui.text(this, name + "  ›", 16, Ui.TEXT, false));
        r.addView(Ui.text(this, about, 13, Ui.SUB, false));
        r.setOnClickListener(v -> startActivity(new Intent(this, screen)));
        return r;
    }
}
