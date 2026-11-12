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

/**
 * Settings, in one place: the model on this phone (its download with live progress, pause, resume and remove),
 * whether replies are read aloud, day or night, and the ways into AI providers, Connectors and Usage.
 */
public class SettingsActivity extends AppCompatActivity {
    protected LinearLayout body;
    protected ModelHub hub;
    protected SharedPreferences prefs;
    protected ProgressBar bar;
    protected TextView modelState, modelDetail, modelAction, modelRemove;
    protected final Runnable onChange = this::refreshModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        hub = ModelHub.get(this);
        prefs = getSharedPreferences("ibiko", MODE_PRIVATE);
        setContentView(layoutRes()); // each section's screen has its own layout file
        android.view.View root = findViewById(ai.ibiko.R.id.settings_root);
        root.setBackgroundColor(Ui.BG);
        findViewById(ai.ibiko.R.id.settings_close).setOnClickListener(v -> finish());
        section = sectionTitle();
        if (section == null && getIntent().getStringExtra(EXTRA_SECTION) != null) {
            // an older link to a section: that section's own screen opens instead
            startActivity(intentFor(this, getIntent().getStringExtra(EXTRA_SECTION)));
            finish();
            return;
        }
        TextView title = findViewById(ai.ibiko.R.id.settings_title);
        title.setText(section == null ? "Settings" : section);
        title.setTextColor(Ui.TEXT);
        body = findViewById(ai.ibiko.R.id.settings_body);
        Ui.fitInsets(root);
        fill();
    }

    protected void fill() {
        body.removeAllViews();
        if (section == null) {
            home();
            return;
        }
        buildSection();

    }

    /** This screen's layout: the list of sections; each section's own screen has its own. */
    protected int layoutRes() {
        return ai.ibiko.R.layout.activity_settings;
    }

    /** The section this screen shows; null for the list. Each section's own Activity names itself. */
    protected String sectionTitle() {
        return null;
    }

    /** Builds this section's controls (in each section's own Activity). */
    protected void buildSection() {
    }

    /** The screens of the sections, in the order of {@link #SECTIONS}. */
    static final Class<?>[] SCREENS = {SettingsProfileActivity.class, SettingsChatActivity.class,
            SettingsLearningActivity.class, SettingsWorkActivity.class, SettingsModelsActivity.class, SettingsWebActivity.class,
            SettingsActionsActivity.class, SettingsCodingActivity.class, SettingsAppearanceActivity.class, SettingsDataActivity.class,
            SettingsAdvancedActivity.class, SettingsAboutActivity.class};

    /** Opens one section's own screen ("Chat"), or the list when the title is unknown. */
    public static Intent intentFor(android.content.Context c, String title) {
        for (int i = 0; i < SECTIONS.length; i++) {
            if (SECTIONS[i][0].equals(title)) {
                return new Intent(c, SCREENS[i]).putExtra(EXTRA_SECTION, title);
            }
        }
        return new Intent(c, SettingsActivity.class);
    }

    /** The screen an intent from {@link #intentFor} opens (for building it directly, as the tests do). */
    @SuppressWarnings("unchecked")
    public static Class<SettingsActivity> screen(Intent i) {
        String name = i.getComponent() == null ? null : i.getComponent().getClassName();
        for (Class<?> c : SCREENS) {
            if (c.getName().equals(name)) {
                return (Class<SettingsActivity>) c;
            }
        }
        String section = i.getStringExtra(EXTRA_SECTION);
        for (int k = 0; section != null && k < SECTIONS.length; k++) {
            if (SECTIONS[k][0].equals(section)) {
                return (Class<SettingsActivity>) SCREENS[k];
            }
        }
        return SettingsActivity.class;
    }

    /** Open on one section's page ("Chat"); without it, Settings is the list of sections. */
    public static final String EXTRA_SECTION = "section";
    /** The section this page shows, or null for the list. */
    protected String section;

    /**
     * The sections, in order: Settings opens as this list, and each section is its own page, built only when it is
     * opened (only its controls exist, so the device keeps less in memory and opens Settings at once).
     */
    static final String[][] SECTIONS = {
            {"Profile", "Your name and who starts the chat"},
            {"Chat", "Enter to send, thinking together, fact-checks, working steps"},
            {"Ibiko 1 and learning", "The brain in use, the learning schedule, training file"},
            {"Work and actions", "What long work says in the chat; sharing and posting"},
            {"Models", "AI providers and the model on this device"},
            {"Web and services", "Web look-ups and connectors"},
            {"Actions", "Whether posts and shares wait for your yes"},
            {"Coding", "Commands, long jobs, running in the background"},
            {"Appearance", "System, light or dark"},
            {"Data and privacy", "Memory, learning, export and deleting chats"},
            {"Advanced", "Reply lengths, agent steps, offline memory and other limits"},
            {"About", "The version of Ibiko"},
    };

    /** Each section's Material icon, beside its name in the list and on its page. */
    static int sectionIcon(String title) {
        switch (title) {
            case "Profile": return ai.ibiko.R.drawable.ic_set_profile;
            case "Chat": return ai.ibiko.R.drawable.ic_set_chat;
            case "Ibiko 1 and learning": return ai.ibiko.R.drawable.ic_set_brain;
            case "Work and actions": return ai.ibiko.R.drawable.ic_set_work;
            case "Models": return ai.ibiko.R.drawable.ic_set_models;
            case "Web and services": return ai.ibiko.R.drawable.ic_set_web;
            case "Actions": return ai.ibiko.R.drawable.ic_set_actions;
            case "Coding": return ai.ibiko.R.drawable.ic_set_coding;
            case "Appearance": return ai.ibiko.R.drawable.ic_set_appearance;
            case "Data and privacy": return ai.ibiko.R.drawable.ic_set_privacy;
            case "Advanced": return ai.ibiko.R.drawable.ic_set_advanced;
            default: return ai.ibiko.R.drawable.ic_set_about;
        }
    }

    /** Whether this page builds that section: only the section opened, never the rest. */
    protected boolean page(String title) {
        return title.equals(section);
    }

    /** Settings' first page: one row a section; a tap opens that section on its own page. */
    protected void home() {
        for (final String[] s : SECTIONS) {
            LinearLayout row = card();
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Ui.dp(this, 64));
            LinearLayout words = new LinearLayout(this);
            words.setOrientation(LinearLayout.VERTICAL);
            words.addView(Ui.text(this, s[0], 16, Ui.TEXT, true));
            words.addView(Ui.text(this, s[1], 13, Ui.SUB, false), Ui.wide(this, 2));
            android.widget.ImageView lead = Ui.glyph(this, sectionIcon(s[0]), Ui.ACCENT, ai.ibiko.IconSize.STANDARD);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) lead.getLayoutParams();
            lp.rightMargin = Ui.dp(this, 16);
            row.addView(lead, lp);
            row.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Ui.glyph(this, ai.ibiko.R.drawable.ic_chevron_right, Ui.ACCENT, ai.ibiko.IconSize.STANDARD));
            row.setContentDescription(s[0]);
            Ui.press(row);
            row.setOnClickListener(v -> startActivity(intentFor(this, s[0])));
            body.addView(row, Ui.wide(this, 10));
        }
    }

    /** The groups the user opened, kept while the screen is rebuilt. */
    protected final java.util.Set<String> opened = new java.util.HashSet<String>();

    /**
     * A section folded to one row (its title, a line saying what is inside, and a chevron); the settings show only
     * after a tap, so a scrolling finger never lands on them. Returns the box to put the settings in.
     */
    /** A voice played here to try a choice ("Hear it"); made on the first tap, closed with the screen. */
    protected ai.ibiko.voice.TalkBridge preview;

    protected LinearLayout group(final String title, String summary) {
        if (section != null) {
            LinearLayout c = card();
            body.addView(c, Ui.wide(this, 10));
            return c; // this page is the section: its settings at once, no header to fold
        }
        final LinearLayout c = card();
        c.setPadding(0, 0, 0, 0);
        final LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 18);
        head.setPadding(p, Ui.dp(this, 16), p, Ui.dp(this, 16));
        head.setMinimumHeight(Ui.dp(this, 64));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(Ui.text(this, title, 16, Ui.TEXT, true));
        words.addView(Ui.text(this, summary, 13, Ui.SUB, false), Ui.wide(this, 2));
        head.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final android.widget.ImageView chevron = Ui.glyph(this, ai.ibiko.R.drawable.ic_chevron_down, Ui.ACCENT, ai.ibiko.IconSize.STANDARD);
        chevron.setRotation(opened.contains(title) ? 180f : 0f);
        head.addView(chevron);
        head.setContentDescription(title);
        Ui.press(head);
        c.addView(head);
        final LinearLayout inside = new LinearLayout(this);
        inside.setOrientation(LinearLayout.VERTICAL);
        inside.setPadding(p, 0, p, p);
        inside.setVisibility(opened.contains(title) ? View.VISIBLE : View.GONE);
        c.addView(inside);
        head.setOnClickListener(v -> {
            boolean open = inside.getVisibility() != View.VISIBLE;
            inside.setVisibility(open ? View.VISIBLE : View.GONE);
            chevron.animate().rotation(open ? 180f : 0f).setDuration(160).start();
            if (open) {
                opened.add(title);
            } else {
                opened.remove(title);
            }
        });
        body.addView(c, Ui.wide(this, 10));
        return inside;
    }

    /** One advanced number as a row: its name and value; a tap opens its editor. Nothing changes from the list itself. */
    protected View knobRow(final ai.ibiko.chat.Tuning.Knob k) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.dp(this, 52));
        r.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        int now = k.get(prefs);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(Ui.text(this, plainName(k), 15, Ui.TEXT, false));
        if (now != k.def) {
            words.addView(Ui.text(this, "changed (default " + label(k, k.def) + ")", 12, Ui.SUB, false));
        }
        r.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(Ui.text(this, label(k, now) + "  ›", 15, now != k.def ? Ui.ACCENT : Ui.SUB, now != k.def));
        r.setContentDescription(k.name);
        r.setOnClickListener(v -> editKnob(k));
        return r;
    }

    /** A knob's name without the "(0 = automatic)" style note, which the value already shows. */
    static String plainName(ai.ibiko.chat.Tuning.Knob k) {
        return k.name.replaceAll("\\s*\\(0[^)]*\\)\\s*$", "");
    }

    /** The editor for one number: what it does, minus and plus, a slider, Default; kept only with Save. */
    protected void editKnob(final ai.ibiko.chat.Tuning.Knob k) {
        final int[] stops = k.stops();
        final int[] at = {k.stopFor(k.get(prefs))};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 22);
        box.setPadding(p, Ui.dp(this, 8), p, 0);
        box.addView(Ui.text(this, k.about, 13, Ui.SUB, false));
        final TextView value = Ui.text(this, label(k, stops[at[0]]), 26, Ui.TEXT, true);
        value.setGravity(Gravity.CENTER);
        LinearLayout step = new LinearLayout(this);
        step.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = Ui.button(this, "−", false);
        TextView plus = Ui.button(this, "+", false);
        step.addView(minus, new LinearLayout.LayoutParams(Ui.dp(this, 56), LinearLayout.LayoutParams.WRAP_CONTENT));
        step.addView(value, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        step.addView(plus, new LinearLayout.LayoutParams(Ui.dp(this, 56), LinearLayout.LayoutParams.WRAP_CONTENT));
        box.addView(step, Ui.wide(this, 16));
        final android.widget.SeekBar bar = new android.widget.SeekBar(this);
        bar.setMax(stops.length - 1);
        bar.setProgress(at[0]);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        box.addView(bar, Ui.wide(this, 14));
        LinearLayout ends = new LinearLayout(this);
        ends.addView(Ui.text(this, label(k, k.min), 11, Ui.SUB, false), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ends.addView(Ui.text(this, "default " + label(k, k.def), 11, Ui.SUB, false));
        TextView hi = Ui.text(this, label(k, k.max), 11, Ui.SUB, false);
        hi.setGravity(Gravity.END);
        ends.addView(hi, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(ends);
        final Runnable show = () -> {
            value.setText(label(k, stops[at[0]]));
            if (bar.getProgress() != at[0]) {
                bar.setProgress(at[0]);
            }
        };
        minus.setOnClickListener(v -> {
            at[0] = Math.max(0, at[0] - 1);
            show.run();
        });
        plus.setOnClickListener(v -> {
            at[0] = Math.min(stops.length - 1, at[0] + 1);
            show.run();
        });
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar b, int i, boolean byUser) {
                if (byUser) {
                    at[0] = i;
                    value.setText(label(k, stops[i]));
                }
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar b) {
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar b) {
            }
        });
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(plainName(k))
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    prefs.edit().putInt(k.key, stops[at[0]]).apply();
                    fill();
                })
                .setNeutralButton("Default", (d, w) -> {
                    prefs.edit().remove(k.key).apply();
                    fill();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    /** Keeps a number, brought into its range (the field shows what was kept). */
    /** A switch row kept in the preferences; {@code changed} also runs when it flips. */
    /** Ibiko 1's learning as a training file in Downloads/Ibiko. */
    protected void exportTraining() {
        java.io.File dir = new java.io.File(getFilesDir(), "brain");
        String name = "ibiko1-training-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(new java.util.Date()) + ".jsonl";
        android.content.ContentValues v = new android.content.ContentValues();
        v.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name);
        v.put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/jsonl");
        v.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/Ibiko");
        String said;
        try {
            android.net.Uri uri = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) {
                throw new java.io.IOException("no Downloads folder");
            }
            int n;
            try (java.io.OutputStream o = getContentResolver().openOutputStream(uri)) {
                n = ai.ibiko.ibiko1.Training.write(o, ai.ibiko.ibiko1.Store.knowledge(dir),
                        ai.ibiko.ibiko1.Store.facts(dir), ai.ibiko.ibiko1.Store.lexicon(dir));
            }
            said = n == 0 ? "Nothing learned yet." : "Saved " + n + " examples to Downloads/Ibiko/" + name;
        } catch (java.io.IOException | RuntimeException e) {
            said = "Couldn't export: " + e.getMessage();
        }
        android.widget.Toast.makeText(this, said, android.widget.Toast.LENGTH_LONG).show();
    }

    protected View toggle(String title, String about, final String key, boolean def, final java.util.function.Consumer<Boolean> changed) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        // each setting its own row with air around it: title, then its explanation a little below, the switch apart
        r.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        r.setMinimumHeight(Ui.dp(this, 56));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(0, 0, Ui.dp(this, 16), 0);
        words.addView(Ui.text(this, title, 16, Ui.TEXT, false));
        if (about != null) {
            words.addView(Ui.text(this, about, 13, Ui.SUB, false), Ui.wide(this, 4));
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

    protected View action(String title, String about, final Runnable run) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        r.addView(Ui.text(this, title, 16, Ui.ACCENT, false));
        r.addView(Ui.text(this, about, 13, Ui.SUB, false), Ui.wide(this, 4));
        r.setOnClickListener(v -> run.run());
        Ui.press(r);
        return r;
    }

    protected void confirm(String message, final Runnable yes) {
        new androidx.appcompat.app.AlertDialog.Builder(this).setMessage(message)
                .setPositiveButton("Delete", (d, w) -> yes.run())
                .setNegativeButton("Cancel", null).show();
    }

    protected void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    protected void exportChats() {
        java.io.File f = new java.io.File(getFilesDir(), "chats.json");
        if (!f.isFile()) {
            toast("No chats yet.");
            return;
        }
        String name = "ibiko-chats-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(new java.util.Date()) + ".json";
        android.content.ContentValues v = new android.content.ContentValues();
        v.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name);
        v.put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/json");
        v.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/Ibiko");
        try {
            android.net.Uri uri = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) {
                throw new java.io.IOException("no Downloads folder");
            }
            try (java.io.OutputStream o = getContentResolver().openOutputStream(uri)) {
                o.write(java.nio.file.Files.readAllBytes(f.toPath()));
            }
            toast("Saved to Downloads/Ibiko/" + name);
        } catch (java.io.IOException | RuntimeException e) {
            toast("Couldn't export: " + e.getMessage());
        }
    }

    protected static void delete(java.io.File f) {
        java.io.File[] kids = f.listFiles();
        if (kids != null) {
            for (java.io.File k : kids) {
                delete(k);
            }
        }
        f.delete();
    }

    protected static long size(java.io.File f) {
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

    protected static String bytes(long b) {
        return b >= 1_000_000 ? String.format(java.util.Locale.US, "%.1f MB", b / 1e6) : b >= 1000
                ? String.format(java.util.Locale.US, "%.0f kB", b / 1e3) : b + " bytes";
    }

    protected String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /** The model's state, progress and buttons, kept live while the screen is open. */
    protected void refreshModel() {
        if (modelState == null) {
            return;
        }
        LocalModel m = hub.model;
        modelTitle.setText("On this device: " + hub.chosen().weights);
        modelAbout.setText("Answers offline and when every API fails. " + hub.chosen().sizeWords() + ", best on Wi-Fi.");
        boolean downloading = hub.downloading() || m.state() == LocalModel.State.DOWNLOADING;
        boolean installed = m.installed();
        String state;
        switch (m.state()) {
            case READY: state = "Ready and running"; break;
            case LOADING: state = "Starting"; break;
            case INSTALLED: state = "Downloaded"; break;
            case UNSUPPORTED: state = "This device can't run it: " + m.problem(); break;
            case FAILED: state = "Couldn't start: " + m.problem(); break;
            default: state = downloading ? "Downloading" : hub.status.phase() == ai.ibiko.llm.DownloadStatus.Phase.PAUSED
                    ? "Paused" : "Not downloaded";
        }
        if (downloading) {
            state = "Downloading · " + hub.status.percent() + "%";
        }
        modelState.setText(state);
        boolean showBar = downloading || (!installed && hub.status.percent() > 0);
        bar.setVisibility(showBar ? View.VISIBLE : View.GONE);
        bar.setProgress(hub.status.percent());
        String detail = downloading || hub.status.phase() == ai.ibiko.llm.DownloadStatus.Phase.PAUSED
                || hub.status.phase() == ai.ibiko.llm.DownloadStatus.Phase.FAILED ? hub.status.detail() : "";
        modelDetail.setText(detail == null ? "" : detail);
        modelDetail.setVisibility(detail == null || detail.isEmpty() ? View.GONE : View.VISIBLE);
        boolean unsupported = m.state() == LocalModel.State.UNSUPPORTED;
        modelAction.setVisibility(installed || unsupported ? View.GONE : View.VISIBLE);
        modelAction.setText(downloading ? "Pause" : hub.status.percent() > 0 ? "Resume" : "Download");
        modelRemove.setVisibility(installed || (!downloading && hub.status.percent() > 0) ? View.VISIBLE : View.GONE);
        fillOnPhone();
    }

    protected LinearLayout onPhone;

    /** What Test measured for each model this visit (by file), shown under its row. */
    protected final java.util.Map<String, String> tested = new java.util.HashMap<String, String>();

    /** The model a Test is running on (its file), or null. One at a time: they share the device. */
    protected String testing;

    /**
     * Every offline model Ibiko offers, always listed: its size, the memory it wants (against this device's) and its
     * state. A downloaded one has Use and Test, and a Delete link; one not here has Download (or Resume).
     */
    protected void fillOnPhone() {
        if (onPhone == null) {
            return;
        }
        onPhone.removeAllViews();
        int here = 0;
        for (ai.ibiko.llm.ModelSpec m : ai.ibiko.llm.ModelSpec.ALL) {
            if (hub.model.has(m)) {
                here++;
            }
        }
        TextView head = Ui.text(this, "Offline models (" + here + " on this device)", 16, Ui.TEXT, true);
        head.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 4));
        onPhone.addView(head);
        long ram = phoneMemoryGb();
        onPhone.addView(Ui.text(this, "Bigger answers better, smaller is faster. Test runs the same question on a model and"
                + " measures it." + (ram > 0 ? " This device has " + ram + " GB of memory: a model that wants more may not"
                + " start, and a big one makes the device slow and warm." : ""), 12, Ui.SUB, false), Ui.wide(this, 2));
        for (final ai.ibiko.llm.ModelSpec m : ai.ibiko.llm.ModelSpec.ALL) {
            final long bytes = hub.model.onPhone(m);
            final boolean has = hub.model.has(m);
            final boolean using = m.file.equals(hub.chosen().file);
            final boolean tooBig = ram > 0 && ram < m.ramGb;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
            item.addView(Ui.text(this, m.weights + (using ? "  \u00B7  in use" : ""), 15, Ui.TEXT, using));
            String state = has ? "Downloaded" : bytes > 0 ? "Part downloaded (" + bytes(bytes) + ")" : "Not downloaded";
            item.addView(Ui.text(this, String.format(java.util.Locale.US, "%.1f GB", m.bytes / 1e9) + "  \u00B7  needs "
                    + m.ramGb + " GB memory  \u00B7  " + state, 12, tooBig ? Ui.CORAL : Ui.SUB, false));
            if (m.about != null && !m.about.isEmpty()) {
                item.addView(Ui.text(this, m.about, 12, Ui.SUB, false));
            }
            LinearLayout buttons = new LinearLayout(this);
            buttons.setGravity(android.view.Gravity.CENTER_VERTICAL);
            if (has) {
                TextView use = Ui.button(this, using ? "In use" : "Use", !using);
                use.setEnabled(!using);
                use.setContentDescription(using ? m.weights + " is in use" : "Use " + m.weights);
                use.setOnClickListener(v -> pick(m));
                buttons.addView(use, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                boolean busy = m.file.equals(testing);
                TextView test = Ui.button(this, busy ? "Testing\u2026" : "Test", false);
                test.setEnabled(testing == null);
                test.setContentDescription("Test the speed of " + m.weights);
                test.setOnClickListener(v -> speedTest(m));
                LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                tp.leftMargin = Ui.dp(this, 10);
                buttons.addView(test, tp);
            } else {
                TextView get = Ui.button(this, bytes > 0 ? "Resume" : "Download", true);
                get.setContentDescription("Download " + m.weights);
                get.setOnClickListener(v -> downloadModel(m, tooBig, ram));
                buttons.addView(get, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            }
            if (has || bytes > 0) {
                TextView delete = Ui.text(this, "Delete", 15, Ui.CORAL, true);
                delete.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12));
                delete.setContentDescription("Delete " + m.weights);
                delete.setOnClickListener(v -> deleteModel(m, bytes, using));
                buttons.addView(delete);
            }
            item.addView(buttons, Ui.wide(this, 10));
            String result = tested.get(m.file);
            if (result != null) {
                item.addView(Ui.text(this, result, 13, Ui.SUB, false), Ui.wide(this, 6));
            }
            onPhone.addView(item);
        }
    }

    protected void deleteModel(final ai.ibiko.llm.ModelSpec m, long bytes, final boolean using) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage("Delete " + m.weights + " from this device? It frees " + bytes(bytes) + "."
                        + (using ? " Answers then need an API until you download a model again." : ""))
                .setPositiveButton("Delete", (d, w) -> {
                    if (using) {
                        hub.stopDownload();
                    }
                    new Thread(() -> {
                        hub.model.delete(m);
                        runOnUiThread(() -> {
                            refreshModel();
                            toast(m.weights + " deleted.");
                        });
                    }, "ibiko-delete-model").start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Downloads a model that isn't here: it becomes the offline model and its download starts. */
    protected void downloadModel(final ai.ibiko.llm.ModelSpec m, boolean tooBig, long ram) {
        if (hub.downloading()) {
            toast("One download at a time: pause the one running first.");
            return;
        }
        Runnable go = () -> {
            new ai.ibiko.llm.ModelGuard(getSharedPreferences("ibiko", MODE_PRIVATE)).clear(m.file);
            hub.choose(this, m, false);
            hub.startDownload(this);
            refreshModel();
            toast("Downloading " + m.weights + " (" + m.sizeWords() + ").");
        };
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage("Download " + m.weights + " (" + m.sizeWords() + ", best on Wi-Fi)? It becomes the offline model."
                        + (tooBig ? " It wants " + m.ramGb + " GB of memory and this device has " + ram + " GB: it may not start." : ""))
                .setPositiveButton("Download", (d, w) -> go.run())
                .setNegativeButton("Cancel", null)
                .show();
    }

    protected TextView modelTitle, modelAbout, speed;

    /**
     * Measures the phone's own model where it runs, on this phone: how long it takes to start, to say its first word,
     * and how many tokens a second it writes. The same question every time, so models and memory sizes compare.
     */
    protected void speedTest() {
        speedTest(hub.chosen());
    }

    /** Test on one model's row: the same question, measured; the result is shown under that row. */
    protected void speedTest(final ai.ibiko.llm.ModelSpec m) {
        if (!hub.model.has(m)) {
            toast("Download " + m.weights + " first.");
            return;
        }
        if (testing != null) {
            return;
        }
        if (hub.downloading()) {
            toast("A download is running: test when it is done, so the result is fair.");
            return;
        }
        testing = m.file;
        tested.put(m.file, "Testing " + m.weights + "\u2026 (up to a minute)");
        fillOnPhone();
        final SharedPreferences p = getSharedPreferences("ibiko", MODE_PRIVATE);
        final int threads = ai.ibiko.chat.Tuning.modelThreads(p);
        new Thread(() -> {
            boolean inUse = m.file.equals(hub.chosen().file);
            if (inUse) {
                hub.model.context(ai.ibiko.chat.Tuning.OFFLINE_CONTEXT.get(p));
            }
            ai.ibiko.llm.ModelGuard guard = new ai.ibiko.llm.ModelGuard(p);
            guard.loading(m.file);
            ai.ibiko.llm.ReplyService.hold(this);
            LocalModel.Speed s;
            try {
                s = hub.model.speedTest(m, threads, 96);
            } finally {
                ai.ibiko.llm.ReplyService.release(this);
            }
            guard.loaded();
            final String words = s == null ? "The test couldn't run: " + hub.model.problem()
                    : s.words() + " " + threads + " threads (Settings \u203A Advanced \u203A Offline model threads).";
            runOnUiThread(() -> {
                testing = null;
                tested.put(m.file, words);
                refreshModel();
            });
        }, "ibiko-speed-test").start();
    }

    /**
     * The offline models on offer: first the family (Qwen3, Llama 3.2, Gemma 3, Phi-4), then its size, as Claude
     * comes as Haiku, Sonnet and Opus. Each size shows its download, the memory it wants and whether it is here.
     */
    protected void chooseModel() {
        final java.util.List<String> families = ai.ibiko.llm.ModelSpec.families();
        BottomSheet.Item[] items = new BottomSheet.Item[families.size()];
        for (int i = 0; i < families.size(); i++) {
            String f = families.get(i);
            StringBuilder sizes = new StringBuilder();
            for (ai.ibiko.llm.ModelSpec m : ai.ibiko.llm.ModelSpec.sizes(f)) {
                sizes.append(sizes.length() == 0 ? "" : " \u00B7 ").append(m.size);
            }
            boolean using = f.equals(hub.chosen().family);
            items[i] = new BottomSheet.Item(using ? ai.ibiko.R.drawable.ic_check : ai.ibiko.R.drawable.ic_cube, f,
                    "Sizes: " + sizes + (using ? ". In use: " + hub.chosen().size + "." : "."));
        }
        BottomSheet.show(this, "Offline model", items, which -> chooseSize(families.get(which)));
    }

    /** One family's sizes: smaller is faster and lighter, bigger answers better. */
    protected void chooseSize(String family) {
        final java.util.List<ai.ibiko.llm.ModelSpec> sizes = ai.ibiko.llm.ModelSpec.sizes(family);
        long ram = phoneMemoryGb();
        BottomSheet.Item[] items = new BottomSheet.Item[sizes.size()];
        for (int i = 0; i < sizes.size(); i++) {
            ai.ibiko.llm.ModelSpec m = sizes.get(i);
            boolean using = m.file.equals(hub.chosen().file);
            String about = m.about + " " + String.format(java.util.Locale.US, "%.1f GB", m.bytes / 1e9) + ", wants " + m.ramGb
                    + " GB of memory" + (ram > 0 && ram < m.ramGb ? " (this device has " + ram + " GB: it may not start)" : "")
                    + (hub.model.has(m) ? ". On this device." : ".");
            items[i] = new BottomSheet.Item(using ? ai.ibiko.R.drawable.ic_check : ai.ibiko.R.drawable.ic_cube,
                    m.weights + (using ? "  \u00B7 in use" : ""), about);
        }
        BottomSheet.show(this, family, items, which -> pick(sizes.get(which)));
    }

    /** Switches to {@code next}, asking first whether to keep the model in use on the phone. */
    protected void pick(final ai.ibiko.llm.ModelSpec next) {
        final ai.ibiko.llm.ModelSpec now = hub.chosen();
        if (next.file.equals(now.file)) {
            return;
        }
        if (hub.downloading()) {
            toast("Pause the download first, then change the model.");
            return;
        }
        if (hub.model.has(now)) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setMessage("Switch to " + next.weights + ". Keep " + now.weights + " on the device to switch back later, or delete it to free "
                            + now.sizeWords() + "?")
                    .setPositiveButton("Delete it", (d, w) -> switchModel(next, true))
                    .setNegativeButton("Keep it", (d, w) -> switchModel(next, false))
                    .show();
        } else {
            switchModel(next, false); // a part-download of the old one stays, listed with a Delete
        }
    }

    protected void switchModel(ai.ibiko.llm.ModelSpec next, boolean deleteOld) {
        new ai.ibiko.llm.ModelGuard(getSharedPreferences("ibiko", MODE_PRIVATE)).clear(next.file); // chosen on purpose: tried again
        hub.choose(this, next, deleteOld);
        refreshModel();
        toast(hub.model.has(next) ? next.weights + " is on the device: it starts when you go back to the chat."
                : "Now tap Download to get " + next.weights + " (" + next.sizeWords() + ").");
    }

    /** The phone's memory, in whole gigabytes (0 when unknown). */
    protected long phoneMemoryGb() {
        android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (am == null) {
            return 0;
        }
        android.app.ActivityManager.MemoryInfo info = new android.app.ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        return Math.round(info.totalMem / 1e9);
    }

    protected void modelAction() {
        if (hub.downloading()) {
            hub.stopDownload();
        } else {
            hub.startDownload(this);
        }
        modelState.postDelayed(this::refreshModel, 300);
    }

    @Override
    protected void onDestroy() {
        if (preview != null) {
            preview.shutdown();
            preview = null;
        }
        super.onDestroy();
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

    protected void section(String s) {
        TextView t = Ui.text(this, s, 15, Ui.SUB, false);
        t.setPadding(Ui.dp(this, 6), Ui.dp(this, 20), 0, Ui.dp(this, 4));
        body.addView(t);
    }

    protected LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.round(this, Ui.SURFACE, 0, 20));
        int p = Ui.dp(this, 18);
        c.setPadding(p, Ui.dp(this, 6), p, Ui.dp(this, 6));
        // a thin line between one setting and the next, so each reads on its own
        android.graphics.drawable.GradientDrawable line = new android.graphics.drawable.GradientDrawable();
        line.setColor(Ui.LINE);
        line.setSize(1, Math.max(1, Ui.dp(this, 1) / 2 + 1));
        c.setDividerDrawable(line);
        c.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        return c;
    }

    /** A row of options, the chosen one filled with the accent. */
    protected View choice(final String[] labels, final String[] keys, String now, final java.util.function.Consumer<String> pick) {
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
                if (parent == null) {
                    return; // the choice redrew its own part of the page (the voice options): nothing left to swap
                }
                int index = parent.indexOfChild(row);
                android.view.ViewGroup.LayoutParams lp = row.getLayoutParams();
                parent.removeView(row);
                parent.addView(choice(labels, keys, key, pick), index, lp);
            });
            row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        return row;
    }

    protected View link(String name, String about, final Class<?> screen) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(Ui.text(this, name, 16, Ui.TEXT, false));
        words.addView(Ui.text(this, about, 13, Ui.SUB, false));
        r.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(Ui.glyph(this, ai.ibiko.R.drawable.ic_chevron_right, Ui.ACCENT, ai.ibiko.IconSize.STANDARD));
        r.setContentDescription(name);
        r.setOnClickListener(v -> startActivity(new Intent(this, screen)));
        Ui.press(r);
        return r;
    }

    /** The number beside a slider. A tier shows Automatic, Fast, Small, Big or Strong instead of a raw count. */
    static String label(ai.ibiko.chat.Tuning.Knob k, int value) {
        if (k == ai.ibiko.chat.Tuning.RUN_MODE) {
            String[] modes = {"Quick", "Balanced", "Deep", "Extended", "Autonomous"};
            return modes[Math.max(0, Math.min(modes.length - 1, value))];
        }
        if (k == ai.ibiko.chat.Tuning.MODEL_THREADS && value == 0) {
            return "Automatic";
        }
        if (k != null && k.tier) {
            String name = ai.ibiko.chat.Tuning.tierName(null, value);
            if (name != null) {
                return name;
            }
        }
        return k == null ? "" : k.shown(value);
    }
}
