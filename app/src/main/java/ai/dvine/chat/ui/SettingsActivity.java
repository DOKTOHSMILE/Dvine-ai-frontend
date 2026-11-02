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
        knobs.clear();

        // ---- profile
        section("Profile");
        LinearLayout profile = card();
        profile.addView(Ui.text(this, "What should DVINE call you?", 16, Ui.TEXT, false));
        final android.widget.EditText name = Ui.field(this, "Your name", prefs.getString("user_name", ""), false, false);
        name.setOnFocusChangeListener((v, focused) -> {
            if (!focused) {
                String n = name.getText().toString().trim();
                prefs.edit().putString("user_name", n).putString("given_name", n).apply();
            }
        });
        profile.addView(name, Ui.wide(this, 10));
        profile.addView(Ui.text(this, "Start with", 16, Ui.TEXT, false), Ui.wide(this, 14));
        profile.addView(choice(new String[]{"Ivie", "Obi", "Ibiko"}, new String[]{"ivie", "obi", "ibiko"},
                prefs.getString("face", "ivie"), k -> prefs.edit().putString("face", k).apply()), Ui.wide(this, 8));
        profile.addView(Ui.text(this, "Ivie: images, drawings and 3D. Obi: conversation and questions. Ibiko: code, files and GitHub.",
                13, Ui.SUB, false), Ui.wide(this, 8));
        body.addView(profile, Ui.wide(this, 8));

        // ---- chat
        section("Chat");
        LinearLayout chat = card();
        chat.addView(toggle("Enter sends the message", "Off: Enter starts a new line; send with the arrow.", "send_enter", false, null));
        chat.addView(toggle("Show the working steps", "What DVINE is doing while it answers: searches, sources, models.", "show_steps", true, null),
                Ui.wide(this, 10));
        body.addView(chat, Ui.wide(this, 8));

        // ---- voice
        section("Voice");
        LinearLayout voice = card();
        voice.addView(Ui.text(this, "Read replies aloud", 16, Ui.TEXT, false));
        voice.addView(choice(new String[]{"Always", "After the mic", "Never"}, new String[]{"always", "mic", "off"},
                prefs.getString("read_aloud", "always"), k -> prefs.edit().putString("read_aloud", k).apply()), Ui.wide(this, 10));
        voice.addView(Ui.text(this, "Speaking speed", 16, Ui.TEXT, false), Ui.wide(this, 14));
        voice.addView(choice(new String[]{"Slower", "Normal", "Faster"}, new String[]{"0.85", "1.0", "1.2"},
                prefs.getString("speech_rate", "1.0"), k -> prefs.edit().putString("speech_rate", k).apply()), Ui.wide(this, 10));
        voice.addView(Ui.text(this, "The mic writes what you say into the message box, so you can edit it before sending.",
                13, Ui.SUB, false), Ui.wide(this, 10));
        body.addView(voice, Ui.wide(this, 8));

        // ---- models
        section("Models");
        LinearLayout apis = card();
        apis.addView(link("AI providers", "API keys, up to three that take over from each other, fast and strong models",
                ProvidersActivity.class));
        body.addView(apis, Ui.wide(this, 8));
        LinearLayout model = card();
        model.addView(Ui.text(this, "On this phone: " + ai.dvine.llm.ModelSpec.WEIGHTS, 17, Ui.TEXT, true));
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

        // ---- web and services
        section("Web and services");
        LinearLayout web = card();
        web.addView(toggle("Look things up on the web", "For news, prices and anything recent; the sources are listed.", "fresh_facts", true, null));
        web.addView(link("Connectors", "Web search provider, GitHub, calendar, Notion, MCP servers", ConnectorsActivity.class), Ui.wide(this, 10));
        body.addView(web, Ui.wide(this, 8));

        // ---- ibiko
        section("Ibiko (coding)");
        LinearLayout ibiko = card();
        ibiko.addView(toggle("Run commands without asking", "Auto-accept: shell commands, git pushes and pull requests go ahead.",
                "auto_accept", false, null));
        final java.io.File ws = new java.io.File(getFilesDir(), "ibiko");
        ibiko.addView(action("Clear Ibiko's workspace", "Deletes the cloned repositories and files on this phone (" + bytes(size(ws)) + ").",
                () -> confirm("Delete everything in Ibiko's workspace? Unpushed changes are lost.", () -> {
                    delete(ws);
                    toast("Workspace cleared.");
                    fill();
                })), Ui.wide(this, 10));
        body.addView(ibiko, Ui.wide(this, 8));

        // ---- appearance
        section("Appearance");
        LinearLayout look = card();
        look.addView(choice(new String[]{"System", "Light", "Dark"}, new String[]{"system", "light", "dark"},
                prefs.getString("theme", "system"), k -> {
                    prefs.edit().putString("theme", k).apply();
                    ai.dvine.MainActivity.applyTheme(k);
                }));
        body.addView(look, Ui.wide(this, 8));

        // ---- data and privacy
        section("Data and privacy");
        LinearLayout data = card();
        data.addView(toggle("Learn from the API models", "Their answers are kept on this phone to teach DVINE's own model.",
                "learn_on", true, on -> hub.model.learning = on ? new ai.dvine.learn.LearningLog(new java.io.File(getFilesDir(), "learning")) : null));
        data.addView(link("Usage and learning", "Context, tokens today, what was learned, export", UsageActivity.class), Ui.wide(this, 10));
        data.addView(action("Export chats", "Saves all chats to Downloads/DVINE.", this::exportChats), Ui.wide(this, 6));
        data.addView(action("Delete all chats", "Every chat and attached image on this phone.",
                () -> confirm("Delete all chats? This can't be undone.", () -> {
                    new java.io.File(getFilesDir(), "chats.json").delete();
                    delete(new java.io.File(getFilesDir(), "attachments"));
                    prefs.edit().putBoolean("chats_reset", true).apply();
                    toast("All chats deleted.");
                })), Ui.wide(this, 6));
        data.addView(Ui.text(this, "Keys, chats and what DVINE learns stay on this phone. A message goes only to the AI provider you chose.",
                13, Ui.SUB, false), Ui.wide(this, 10));
        body.addView(data, Ui.wide(this, 8));

        // ---- advanced: the limits DVINE works within, all changeable
        section("Advanced");
        LinearLayout adv = card();
        adv.addView(Ui.text(this, "The numbers DVINE works with. Each provider's own settings (images, reasoning, length, time,"
                + " extra request fields) are under AI providers \u203A a provider \u203A Advanced.", 13, Ui.SUB, false));
        for (final ai.dvine.chat.Tuning.Knob k : ai.dvine.chat.Tuning.ALL) {
            adv.addView(Ui.text(this, k.name, 15, Ui.TEXT, false), Ui.wide(this, 14));
            adv.addView(Ui.text(this, k.about + " (" + k.min + " to " + k.max + ", default " + k.def + ")", 12, Ui.SUB, false));
            final android.widget.EditText v = Ui.field(this, String.valueOf(k.def), String.valueOf(k.get(prefs)), false, false);
            v.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            v.setOnFocusChangeListener((view, focused) -> {
                if (!focused) {
                    save(k, v);
                }
            });
            v.setOnEditorActionListener((view, action, e) -> {
                save(k, v);
                return false;
            });
            knobs.put(k, v);
            adv.addView(v, Ui.wide(this, 6));
        }
        TextView reset = Ui.text(this, "Back to the defaults", 15, Ui.ACCENT, false);
        reset.setPadding(0, Ui.dp(this, 14), 0, 0);
        reset.setOnClickListener(x -> {
            android.content.SharedPreferences.Editor e = prefs.edit();
            for (ai.dvine.chat.Tuning.Knob k : ai.dvine.chat.Tuning.ALL) {
                e.remove(k.key);
            }
            e.apply();
            knobs.clear();
            fill();
        });
        adv.addView(reset);
        body.addView(adv, Ui.wide(this, 8));

        // ---- about
        section("About");
        LinearLayout about = card();
        about.addView(Ui.text(this, "DVINE " + version(), 16, Ui.TEXT, false));
        about.addView(Ui.text(this, "Ivie, Obi and Ibiko, with your models or the one on this phone.", 13, Ui.SUB, false), Ui.wide(this, 4));
        body.addView(about, Ui.wide(this, 8));
    }

    private final java.util.Map<ai.dvine.chat.Tuning.Knob, android.widget.EditText> knobs =
            new java.util.LinkedHashMap<ai.dvine.chat.Tuning.Knob, android.widget.EditText>();

    /** Keeps a number, brought into its range (the field shows what was kept). */
    private void save(ai.dvine.chat.Tuning.Knob k, android.widget.EditText v) {
        try {
            int n = Integer.parseInt(v.getText().toString().trim());
            prefs.edit().putInt(k.key, Math.max(k.min, Math.min(k.max, n))).apply();
        } catch (NumberFormatException e) {
            prefs.edit().remove(k.key).apply();
        }
        String kept = String.valueOf(k.get(prefs));
        if (!kept.equals(v.getText().toString())) {
            v.setText(kept);
        }
    }

    /** A switch row kept in the preferences; {@code changed} also runs when it flips. */
    private View toggle(String title, String about, final String key, boolean def, final java.util.function.Consumer<Boolean> changed) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(Ui.text(this, title, 16, Ui.TEXT, false));
        if (about != null) {
            words.addView(Ui.text(this, about, 13, Ui.SUB, false));
        }
        r.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.Switch sw = new android.widget.Switch(this);
        sw.setChecked(prefs.getBoolean(key, def));
        sw.setTrackTintList(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{Ui.CORAL, Ui.FIELD}));
        sw.setOnCheckedChangeListener((b, on) -> {
            prefs.edit().putBoolean(key, on).apply();
            if (changed != null) {
                changed.accept(on);
            }
        });
        r.addView(sw);
        r.setOnClickListener(v -> sw.toggle());
        return r;
    }

    private View action(String title, String about, final Runnable run) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        r.addView(Ui.text(this, title, 16, Ui.ACCENT, false));
        r.addView(Ui.text(this, about, 13, Ui.SUB, false));
        r.setOnClickListener(v -> run.run());
        return r;
    }

    private void confirm(String message, final Runnable yes) {
        new androidx.appcompat.app.AlertDialog.Builder(this).setMessage(message)
                .setPositiveButton("Delete", (d, w) -> yes.run())
                .setNegativeButton("Cancel", null).show();
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    private void exportChats() {
        java.io.File f = new java.io.File(getFilesDir(), "chats.json");
        if (!f.isFile()) {
            toast("No chats yet.");
            return;
        }
        String name = "dvine-chats-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(new java.util.Date()) + ".json";
        android.content.ContentValues v = new android.content.ContentValues();
        v.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name);
        v.put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/json");
        v.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/DVINE");
        try {
            android.net.Uri uri = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) {
                throw new java.io.IOException("no Downloads folder");
            }
            try (java.io.OutputStream o = getContentResolver().openOutputStream(uri)) {
                o.write(java.nio.file.Files.readAllBytes(f.toPath()));
            }
            toast("Saved to Downloads/DVINE/" + name);
        } catch (java.io.IOException | RuntimeException e) {
            toast("Couldn't export: " + e.getMessage());
        }
    }

    private static void delete(java.io.File f) {
        java.io.File[] kids = f.listFiles();
        if (kids != null) {
            for (java.io.File k : kids) {
                delete(k);
            }
        }
        f.delete();
    }

    private static long size(java.io.File f) {
        if (!f.exists()) {
            return 0;
        }
        if (f.isFile()) {
            return f.length();
        }
        long t = 0;
        java.io.File[] kids = f.listFiles();
        if (kids != null) {
            for (java.io.File k : kids) {
                t += size(k);
            }
        }
        return t;
    }

    private static String bytes(long b) {
        return b >= 1_000_000 ? String.format(java.util.Locale.US, "%.1f MB", b / 1e6) : b >= 1000
                ? String.format(java.util.Locale.US, "%.0f kB", b / 1e3) : b + " bytes";
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
        for (java.util.Map.Entry<ai.dvine.chat.Tuning.Knob, android.widget.EditText> e : knobs.entrySet()) {
            save(e.getKey(), e.getValue()); // a number still being typed is kept too
        }
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
