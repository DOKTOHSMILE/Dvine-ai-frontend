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
        section = getIntent().getStringExtra(EXTRA_SECTION);
        TextView title = Ui.text(this, section == null ? "Settings" : section, 19, Ui.TEXT, true);
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
        if (section == null) {
            home();
            return;
        }

        if (page("Profile")) {
            // ---- profile
            LinearLayout profile = group("Profile", "Your name and who starts the chat");
            profile.addView(Ui.text(this, "What should Ibiko call you?", 16, Ui.TEXT, false));
            final android.widget.EditText name = Ui.field(this, "Your name", prefs.getString("user_name", ""), false, false);
            name.setOnFocusChangeListener((v, focused) -> {
                if (!focused) {
                    String n = name.getText().toString().trim();
                    prefs.edit().putString("user_name", n).putString("given_name", n).apply();
                }
            });
            profile.addView(name, Ui.wide(this, 10));

        }
        if (page("Chat")) {
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
            chat.addView(toggle("Show the working steps", "What Ibiko is doing while it answers: searches, sources, models.", "show_steps", true, null),
                    Ui.wide(this, 10));

        }
        if (page("Voice")) {
            // ---- voice
            LinearLayout voice = group("Voice", "Reading replies aloud and how fast");
            voice.addView(Ui.text(this, "Read replies aloud", 16, Ui.TEXT, false));
            voice.addView(choice(new String[]{"Always", "After the mic", "Never"}, new String[]{"always", "mic", "off"},
                    prefs.getString("read_aloud", "always"), k -> prefs.edit().putString("read_aloud", k).apply()), Ui.wide(this, 10));
            voice.addView(Ui.text(this, "Speaking speed", 16, Ui.TEXT, false), Ui.wide(this, 14));
            voice.addView(choice(new String[]{"Slower", "Normal", "Faster"}, new String[]{"0.85", "1.0", "1.2"},
                    prefs.getString("speech_rate", "1.0"), k -> prefs.edit().putString("speech_rate", k).apply()), Ui.wide(this, 10));
            voiceChoices(voice);
            voice.addView(toggle("Soft music under the voice", "A low, quiet chord that swells while a reply is read aloud. Off:"
                    + " no sound but the voice, and no audio stream is open while nothing is read aloud. Takes effect the next time Ibiko opens.", "music", false, null), Ui.wide(this, 10));
            voice.addView(Ui.text(this, "The mic writes what you say into the message box, so you can edit it before sending.",
                    13, Ui.SUB, false), Ui.wide(this, 10));

        }
        if (page("Ibiko 1 and learning")) {
            // ---- DVINE 1, the work in the chat, and actions (in step with the model menu and the tools sheet)
            LinearLayout own = group("Ibiko 1 and learning", "The brain in use, the learning schedule, training file");
            String pick = prefs.getString("brain_pick", "api");
            own.addView(Ui.text(this, "Answering now: " + ("dvine".equals(pick) ? ai.ibiko.dvine1.Dvine1.NAME : "device".equals(pick)
                    ? "the offline model" : "your API models") + ". Change it from the model name under the chat's title.", 14, Ui.SUB, false));
            final ai.ibiko.dvine1.LearningSchedule.Plan plan = ai.ibiko.dvine1.LearningJob.plan(this);
            own.addView(toggle("Learn on a schedule", (plan.on ? "On, " : "Hours: ") + plan.words() + ". " + ai.ibiko.dvine1.Dvine1.NAME
                    + " goes online and learns from what you asked. Set the hours from the model menu.", "learn_schedule_on", plan.on,
                    on -> ai.ibiko.dvine1.LearningJob.setPlan(this, new ai.ibiko.dvine1.LearningSchedule.Plan(on, plan.start, plan.end,
                            plan.wifiOnly, plan.chargingOnly, plan.most))), Ui.wide(this, 10));
            own.addView(Ui.text(this, ai.ibiko.dvine1.Dvine1.NAME + " learns from every message, every model's answer, what it researches"
                    + " and the social pages it reads (language only: people's words are never taken as facts). "
                    + ai.ibiko.dvine1.Dvine1.scoreLine(getFilesDir()) + ". See it under Memory.", 13, Ui.SUB, false), Ui.wide(this, 10));
            android.view.View learnNow = Ui.button(this, "Learn now", false);
            learnNow.setOnClickListener(v -> {
                ai.ibiko.dvine1.LearningJob.learnNow(this);
                android.widget.Toast.makeText(this, "Learning now. The Workload sheet shows it.", android.widget.Toast.LENGTH_SHORT).show();
            });
            own.addView(learnNow, Ui.wide(this, 10));
            android.view.View train = Ui.button(this, "Export Ibiko 1's training file", false);
            train.setOnClickListener(v -> exportTraining());
            own.addView(train, Ui.wide(this, 8));
            own.addView(Ui.text(this, "Everything Ibiko 1 learned (confirmed answers, facts, words) as a JSONL file in Downloads/Ibiko,"
                    + " for training a model of your own.", 13, Ui.SUB, false), Ui.wide(this, 6));

        }
        if (page("Work and actions")) {
            LinearLayout work = group("Work and actions", "What long work says in the chat; sharing and posting");
            work.addView(toggle("Say what each step found", "During long work, a line in the chat after each step: what it looked for"
                    + " and what it found. The full summary comes at the end.", "narrate_steps", true, null));
            work.addView(toggle("Share and post without asking first", "Off (recommended): each share, post or copy is shown and waits"
                    + " for your yes.", "actions_auto", false, null), Ui.wide(this, 10));
            work.addView(link("What Ibiko remembers", "Answers you marked correct, what it knows about you, actions done", MemoryActivity.class),
                    Ui.wide(this, 10));

        }
        if (page("Models")) {
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
                        }, "dvine-remove-model").start();
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            rp.leftMargin = Ui.dp(this, 10);
            buttons.addView(modelRemove, rp);
            model.addView(buttons, Ui.wide(this, 14));
            model.addView(action("Change the offline model", "Qwen3, Llama, Gemma or Phi, each in sizes: bigger answers better,"
                    + " smaller is faster", this::chooseModel), Ui.wide(this, 10));
            speed = Ui.text(this, "", 13, Ui.SUB, false);
            speed.setVisibility(View.GONE);
            model.addView(action("Test the speed", "Runs the same question on the device's own model and measures it",
                    this::speedTest), Ui.wide(this, 10));
            model.addView(speed, Ui.wide(this, 4));
            onPhone = new LinearLayout(this);
            onPhone.setOrientation(LinearLayout.VERTICAL);
            model.addView(onPhone, Ui.wide(this, 6));
            apis.addView(model);
            refreshModel();

        }
        if (page("Web and services")) {
            // ---- web and services
            LinearLayout web = group("Web and services", "Web look-ups and connectors");
            web.addView(toggle("Look things up on the web", "For news, prices and anything recent; the sources are listed.", "fresh_facts", true, null));
            web.addView(link("Connectors", "Web search provider, GitHub, calendar, Notion, MCP servers", ConnectorsActivity.class), Ui.wide(this, 10));

        }
        if (page("Actions")) {
            // ---- actions on the user's behalf
            LinearLayout acts = group("Actions", "Whether posts and shares wait for your yes");
            acts.addView(toggle("Auto-post on your behalf", "Posts and comments you ask for go out from your account at once,"
                    + " without asking first (also in Social accounts). Off: each one waits for your yes.", "social_auto", false, null));
            acts.addView(toggle("Auto-approve actions", "Posts, shares and copies go ahead without asking first. Off: each one is"
                    + " shown with its exact text and where it goes, and waits for your yes.", "actions_auto", false, null));

        }
        if (page("Coding")) {
            // ---- ibiko
            LinearLayout ibiko = group("Coding", "Commands, long jobs and the workspace");
            ibiko.addView(toggle("Run commands without asking", "Auto-accept: shell commands, git pushes and pull requests go ahead.",
                    "auto_accept", false, null));
            ibiko.addView(toggle("Keep Ibiko running in the background", "Ibiko stays ready with the screen off or another app"
                    + " open, with a quiet notification; Back goes to the background instead of quitting. Close it from the"
                    + " notification or by swiping it away from the recent apps. Off: Android may stop it in the background.",
                    ai.ibiko.main.KeepAlive.PREF, true, on -> {
                        if (on) {
                            ai.ibiko.main.KeepAlive.opened(this);
                        } else {
                            ai.ibiko.main.KeepAlive.stop(this);
                        }
                    }), Ui.wide(this, 10));
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
        if (page("Appearance")) {
            // ---- appearance
            LinearLayout look = group("Appearance", "System, light or dark");
            look.addView(choice(new String[]{"System", "Light", "Dark"}, new String[]{"system", "light", "dark"},
                    prefs.getString("theme", "system"), k -> {
                        prefs.edit().putString("theme", k).apply();
                        ai.ibiko.MainActivity.applyTheme(k);
                    }));

        }
        if (page("Data and privacy")) {
            // ---- data and privacy
            LinearLayout data = group("Data and privacy", "Memory, learning, export and deleting chats");
            data.addView(toggle("Learn from the API models", "Their answers are kept on this device to teach Ibiko's own model.",
                    "learn_on", true, on -> hub.model.learning = on ? new ai.ibiko.learn.LearningLog(new java.io.File(getFilesDir(), "learning")) : null));
            data.addView(link("What Ibiko remembers", "Notes, facts you taught, how it reads your words: forget any of them",
                    MemoryActivity.class), Ui.wide(this, 10));
            data.addView(link("Usage and learning", "Context, tokens today, what was learned, export", UsageActivity.class), Ui.wide(this, 10));
            data.addView(action("Export chats", "Saves all chats to Downloads/Ibiko.", this::exportChats), Ui.wide(this, 6));
            data.addView(action("Delete all chats", "Every chat and attached image on this device.",
                    () -> confirm("Delete all chats? This can't be undone.", () -> {
                        new java.io.File(getFilesDir(), "chats.json").delete();
                        delete(new java.io.File(getFilesDir(), "attachments"));
                        prefs.edit().putBoolean("chats_reset", true).apply();
                        toast("All chats deleted.");
                    })), Ui.wide(this, 6));
            data.addView(Ui.text(this, "Keys, chats and what Ibiko learns stay on this device. A message goes only to the AI provider you chose.",
                    13, Ui.SUB, false), Ui.wide(this, 10));

        }
        if (page("Advanced")) {
            // ---- advanced: the limits DVINE works within. Closed until opened on purpose; each number is a row, changed only
            // in its own editor (a slider in a scrolling list moved whenever a finger scrolled past it)
            LinearLayout adv = group("Advanced", "Reply lengths, agent steps, offline memory and other limits");
            adv.addView(Ui.text(this, "The numbers Ibiko works with. The defaults suit most people: change one only for a reason."
                    + " Each provider's own settings are under AI providers › a provider › Advanced.", 13, Ui.SUB, false));
            for (final ai.ibiko.chat.Tuning.Knob k : ai.ibiko.chat.Tuning.ALL) {
                adv.addView(knobRow(k), Ui.wide(this, 4));
            }
            TextView reset = Ui.text(this, "Back to the defaults", 15, Ui.ACCENT, false);
            reset.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 4));
            reset.setOnClickListener(x -> confirm("Put every advanced number back to its default?", () -> {
                android.content.SharedPreferences.Editor e = prefs.edit();
                for (ai.ibiko.chat.Tuning.Knob k : ai.ibiko.chat.Tuning.ALL) {
                    e.remove(k.key);
                }
                e.apply();
                fill();
            }));
            adv.addView(reset);

        }
        if (page("About")) {
            // ---- about
            LinearLayout about = card();
            about.addView(Ui.text(this, "Ibiko " + version(), 16, Ui.TEXT, false));
            about.addView(Ui.text(this, "Ibiko, with your models or the one on this device.", 13, Ui.SUB, false), Ui.wide(this, 4));
            body.addView(about, Ui.wide(this, 8));
        }
    }

    /** Open on one section's page ("Voice"); without it, Settings is the list of sections. */
    public static final String EXTRA_SECTION = "section";
    /** The section this page shows, or null for the list. */
    private String section;

    /**
     * The sections, in order: Settings opens as this list, and each section is its own page, built only when it is
     * opened (only its controls exist, so the device keeps less in memory and opens Settings at once).
     */
    static final String[][] SECTIONS = {
            {"Profile", "Your name and who starts the chat"},
            {"Chat", "Enter to send, thinking together, fact-checks, working steps"},
            {"Voice", "A man's or a woman's voice, reading aloud, how fast"},
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

    /** Whether this page builds that section: only the section opened, never the rest. */
    private boolean page(String title) {
        return title.equals(section);
    }

    /** Settings' first page: one row a section; a tap opens that section on its own page. */
    private void home() {
        for (final String[] s : SECTIONS) {
            LinearLayout row = card();
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Ui.dp(this, 64));
            LinearLayout words = new LinearLayout(this);
            words.setOrientation(LinearLayout.VERTICAL);
            words.addView(Ui.text(this, s[0], 16, Ui.TEXT, true));
            words.addView(Ui.text(this, s[1], 13, Ui.SUB, false), Ui.wide(this, 2));
            row.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Ui.text(this, "\u203A", 20, Ui.SUB, false));
            row.setContentDescription(s[0]);
            row.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class).putExtra(EXTRA_SECTION, s[0])));
            body.addView(row, Ui.wide(this, 10));
        }
    }

    /** The groups the user opened, kept while the screen is rebuilt. */
    private final java.util.Set<String> opened = new java.util.HashSet<String>();

    /**
     * A section folded to one row (its title, a line saying what is inside, and a chevron); the settings show only
     * after a tap, so a scrolling finger never lands on them. Returns the box to put the settings in.
     */
    /** A voice played here to try a choice ("Hear it"); made on the first tap, closed with the screen. */
    private ai.ibiko.voice.TalkBridge preview;

    /**
     * Settings › Voice: one voice for every reply, a man's or a woman's. The human voice (Kokoro) comes with the app
     * files on the first start; a fast voice (Piper) can be downloaded here, with its progress; the device's own
     * speech engines (another voice app installed) can be chosen too.
     */
    private void voiceChoices(LinearLayout section) {
        voiceBox = new LinearLayout(this);
        voiceBox.setOrientation(LinearLayout.VERTICAL);
        section.addView(voiceBox);
        fillVoice();
    }

    /** The voice options' own box: a choice redraws only this, in place, so nothing else on the page moves or folds. */
    private LinearLayout voiceBox;

    private void fillVoice() {
        if (voiceBox == null || isFinishing()) {
            return;
        }
        LinearLayout voice = voiceBox;
        voice.removeAllViews();
        voice.addView(Ui.text(this, "Voice", 16, Ui.TEXT, false), Ui.wide(this, 14));
        voice.addView(choice(new String[]{"A man's voice", "A woman's voice"}, new String[]{"male", "female"},
                prefs.getString("voice_gender", "male"), k -> {
                    prefs.edit().putString("voice_gender", k).apply();
                    fillVoice();
                }), Ui.wide(this, 10));
        final boolean woman = "female".equals(prefs.getString("voice_gender", "male"));
        final java.io.File files = getFilesDir();
        // which voice speaks
        java.util.List<String> labels = new java.util.ArrayList<String>(), keys = new java.util.ArrayList<String>();
        for (ai.ibiko.voice.VoicePacks.Pack p : ai.ibiko.voice.VoicePacks.ALL) {
            boolean fits = "kokoro".equals(p.kind) || p.speakers.get(0).male != woman;
            if (fits) {
                labels.add(p.title + (ai.ibiko.voice.VoicePacks.installed(files, p) ? "" : " (" + p.size() + " download)"));
                keys.add(p.key);
            }
        }
        labels.add("The device's own voice");
        keys.add("device");
        voice.addView(Ui.text(this, "Human voice", 16, Ui.TEXT, false), Ui.wide(this, 14));
        final String chosen = prefs.getString("voice_pack", "kokoro");
        final TextView state = Ui.text(this, "", 13, Ui.SUB, false);
        final ProgressBar get = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        get.setMax(100);
        get.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        get.setVisibility(View.GONE);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < keys.size(); i++) {
            final String key = keys.get(i);
            final ai.ibiko.voice.VoicePacks.Pack p = ai.ibiko.voice.VoicePacks.byKey(key);
            android.widget.RadioButton r = new android.widget.RadioButton(this);
            r.setText(labels.get(i));
            r.setTextColor(Ui.TEXT);
            r.setChecked(key.equals(chosen));
            r.setOnClickListener(v -> {
                if (p == null || ai.ibiko.voice.VoicePacks.installed(files, p)) {
                    prefs.edit().putString("voice_pack", key).apply();
                    fillVoice();
                    return;
                }
                // a voice not on the device yet: downloaded here, with its progress, then chosen
                state.setText("Downloading " + p.title + "\u2026");
                get.setVisibility(View.VISIBLE);
                get.setProgress(0);
                new Thread(() -> {
                    try {
                        ai.ibiko.voice.VoicePacks.install(files, p, ai.ibiko.voice.VoicePacks.WEB, (stage, pct) -> {
                            ai.ibiko.net.Transfers.progress("voice:" + p.key, stage + " a voice", p.title, pct);
                            runOnUiThread(() -> {
                                get.setProgress(pct);
                                state.setText(stage + " " + p.title + ": " + pct + "%");
                            });
                        });
                        ai.ibiko.net.Transfers.end("voice:" + p.key, "Voice ready", p.title, true);
                        prefs.edit().putString("voice_pack", key).apply();
                        runOnUiThread(this::fillVoice);
                    } catch (java.io.IOException e) {
                        ai.ibiko.net.Transfers.end("voice:" + p.key, "Voice not downloaded", p.title, false);
                        runOnUiThread(() -> {
                            get.setVisibility(View.GONE);
                            state.setText("Couldn't download the voice: " + e.getMessage() + " Check the connection and tap it again.");
                        });
                    }
                }, "ibiko-voice-download").start();
            });
            list.addView(r);
        }
        voice.addView(list, Ui.wide(this, 6));
        java.io.File problem = new java.io.File(ai.ibiko.voice.VoicePacks.root(files), ai.ibiko.voice.NeuralVoice.PROBLEM);
        if (!"device".equals(chosen) && problem.isFile()) {
            try {
                String why = new String(java.nio.file.Files.readAllBytes(problem.toPath()), java.nio.charset.StandardCharsets.UTF_8).trim();
                TextView bad = Ui.text(this, "The human voice couldn't start on this device, so the device's voice reads instead. "
                        + "What went wrong: " + why.replace("\n", " \u2014 "), 13, Ui.CORAL, false);
                voice.addView(bad, Ui.wide(this, 6));
            } catch (java.io.IOException ignored) {
                // nothing to show
            }
        }
        voice.addView(get, Ui.wide(this, 6));
        voice.addView(state, Ui.wide(this, 4));
        // which of Kokoro's voices
        final ai.ibiko.voice.VoicePacks.Pack kokoro = ai.ibiko.voice.VoicePacks.KOKORO;
        if ("kokoro".equals(chosen) && ai.ibiko.voice.VoicePacks.installed(files, kokoro)) {
            java.util.List<String> names = new java.util.ArrayList<String>(), ids = new java.util.ArrayList<String>();
            for (ai.ibiko.voice.VoicePacks.Speaker sp : kokoro.speakers) {
                if (sp.male != woman) {
                    names.add(sp.title);
                    ids.add(sp.name);
                }
            }
            final String prefKey = woman ? "voice_human_woman" : "voice_human_man";
            String now = kokoro.speaker(!woman, prefs.getString(prefKey, "")).name;
            android.widget.Spinner pickVoice = new android.widget.Spinner(this);
            pickVoice.setAdapter(new android.widget.ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, names));
            pickVoice.setSelection(Math.max(0, ids.indexOf(now)));
            pickVoice.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    prefs.edit().putString(prefKey, ids.get(position)).apply();
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                }
            });
            voice.addView(Ui.text(this, "Which voice", 14, Ui.TEXT, false), Ui.wide(this, 10));
            voice.addView(pickVoice, Ui.wide(this, 4));
        }
        View hear = Ui.button(this, "Hear it", false);
        hear.setOnClickListener(v -> hearVoice(state));
        voice.addView(hear, Ui.wide(this, 10));
        // the device's speech engines: another voice app installed on the device can speak for Ibiko
        if ("device".equals(chosen)) {
            java.util.List<String> engines = new java.util.ArrayList<String>(), packages = new java.util.ArrayList<String>();
            engines.add("The device's default");
            packages.add("");
            try {
                android.speech.tts.TextToSpeech probe = new android.speech.tts.TextToSpeech(this, status -> { });
                for (android.speech.tts.TextToSpeech.EngineInfo e : probe.getEngines()) {
                    engines.add(e.label);
                    packages.add(e.name);
                }
                probe.shutdown();
            } catch (RuntimeException ignored) {
                // no engines listed: the default speaks
            }
            voice.addView(Ui.text(this, "Speech engine", 14, Ui.TEXT, false), Ui.wide(this, 10));
            android.widget.Spinner engine = new android.widget.Spinner(this);
            engine.setAdapter(new android.widget.ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, engines));
            engine.setSelection(Math.max(0, packages.indexOf(prefs.getString("tts_engine", ""))));
            engine.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    prefs.edit().putString("tts_engine", packages.get(position)).apply();
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                }
            });
            voice.addView(engine, Ui.wide(this, 4));
            voice.addView(Ui.text(this, "A voice app you installed (its voices) speaks for Ibiko from the next time Ibiko opens.",
                    13, Ui.SUB, false), Ui.wide(this, 4));
            View moreVoices = Ui.button(this, "Download the device's voices", false);
            moreVoices.setOnClickListener(v -> ai.ibiko.voice.TalkBridge.installVoices(this));
            voice.addView(moreVoices, Ui.wide(this, 8));
        }
    }

    /** Says a line in the voice chosen above (the human voice takes a moment to load the first time). */
    private void hearVoice(final TextView state) {
        if (preview == null) {
            preview = new ai.ibiko.voice.TalkBridge(this);
        } else {
            preview.refreshVoice();
        }
        state.setText("Getting the voice ready\u2026");
        final long until = System.currentTimeMillis() + 20000;
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        final boolean human = !"device".equals(prefs.getString("voice_pack", "kokoro"));
        h.post(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || preview == null) {
                    return;
                }
                if (human && !preview.human() && System.currentTimeMillis() < until) {
                    h.postDelayed(this, 250);
                    return;
                }
                state.setText(preview.human() ? "" : human ? "The human voice didn't load: the device's voice speaks instead." : "");
                preview.speak(false, "Hello, I'm Ibiko. This is the voice I'll use to read my replies to you.", null);
            }
        });
    }

    private LinearLayout group(final String title, String summary) {
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
        final TextView chevron = Ui.text(this, opened.contains(title) ? "⌃" : "⌄", 18, Ui.SUB, false);
        head.addView(chevron);
        head.setContentDescription(title);
        c.addView(head);
        final LinearLayout inside = new LinearLayout(this);
        inside.setOrientation(LinearLayout.VERTICAL);
        inside.setPadding(p, 0, p, p);
        inside.setVisibility(opened.contains(title) ? View.VISIBLE : View.GONE);
        c.addView(inside);
        head.setOnClickListener(v -> {
            boolean open = inside.getVisibility() != View.VISIBLE;
            inside.setVisibility(open ? View.VISIBLE : View.GONE);
            chevron.setText(open ? "⌃" : "⌄");
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
    private View knobRow(final ai.ibiko.chat.Tuning.Knob k) {
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
    private void editKnob(final ai.ibiko.chat.Tuning.Knob k) {
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
    /** DVINE 1's learning as a training file in Downloads/DVINE. */
    private void exportTraining() {
        java.io.File dir = new java.io.File(getFilesDir(), "brain");
        String name = "dvine1-training-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(new java.util.Date()) + ".jsonl";
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
                n = ai.ibiko.dvine1.Training.write(o, ai.ibiko.dvine1.Store.knowledge(dir),
                        ai.ibiko.dvine1.Store.facts(dir), ai.ibiko.dvine1.Store.lexicon(dir));
            }
            said = n == 0 ? "Nothing learned yet." : "Saved " + n + " examples to Downloads/Ibiko/" + name;
        } catch (java.io.IOException | RuntimeException e) {
            said = "Couldn't export: " + e.getMessage();
        }
        android.widget.Toast.makeText(this, said, android.widget.Toast.LENGTH_LONG).show();
    }

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

    private LinearLayout onPhone;

    /** Every model on the phone (whole or part-downloaded) with its size and a Delete, to free the space. */
    /** The full list of offline models is folded until opened, so Settings stays short. */
    private boolean modelsOpen;

    /**
     * Every offline model DVINE offers, downloaded or not: its size, the memory it needs (against this device's) and
     * its state. A downloaded one has Use and Delete; one not yet here has Download. Folded behind one row.
     */
    private void fillOnPhone() {
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
        final TextView head = Ui.text(this, "All offline models (" + here + " on this device)  " + (modelsOpen ? "⌃" : "⌄"),
                15, Ui.ACCENT, false);
        head.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 10));
        head.setContentDescription("All offline models");
        head.setOnClickListener(v -> {
            modelsOpen = !modelsOpen;
            fillOnPhone();
        });
        onPhone.addView(head);
        if (!modelsOpen) {
            return;
        }
        long ram = phoneMemoryGb();
        if (ram > 0) {
            onPhone.addView(Ui.text(this, "This device has " + ram + " GB of memory. A model that wants more may not start, and a"
                    + " big one makes the device slow and warm.", 12, Ui.SUB, false), Ui.wide(this, 2));
        }
        for (final ai.ibiko.llm.ModelSpec m : ai.ibiko.llm.ModelSpec.ALL) {
            final long bytes = hub.model.onPhone(m);
            final boolean has = hub.model.has(m);
            final boolean using = m.file.equals(hub.chosen().file);
            final boolean tooBig = ram > 0 && ram < m.ramGb;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Ui.dp(this, 64));
            row.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            LinearLayout words = new LinearLayout(this);
            words.setOrientation(LinearLayout.VERTICAL);
            words.addView(Ui.text(this, m.weights + (using ? "  ·  in use" : ""), 15, Ui.TEXT, using));
            String state = has ? "Downloaded" : bytes > 0 ? "Part downloaded (" + bytes(bytes) + ")" : "Not downloaded";
            words.addView(Ui.text(this, String.format(java.util.Locale.US, "%.1f GB", m.bytes / 1e9) + "  ·  needs "
                    + m.ramGb + " GB memory  ·  " + state, 12, tooBig ? Ui.CORAL : Ui.SUB, false));
            if (m.about != null && !m.about.isEmpty()) {
                words.addView(Ui.text(this, m.about, 12, Ui.SUB, false));
            }
            row.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            if (has) {
                if (!using) {
                    TextView use = Ui.text(this, "Use", 15, Ui.ACCENT, true);
                    use.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12));
                    use.setContentDescription("Use " + m.weights);
                    use.setOnClickListener(v -> pick(m));
                    row.addView(use);
                }
                TextView delete = Ui.text(this, "Delete", 15, Ui.CORAL, true);
                delete.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), 0, Ui.dp(this, 12));
                delete.setContentDescription("Delete " + m.weights);
                delete.setOnClickListener(v -> deleteModel(m, bytes, using));
                row.addView(delete);
            } else {
                TextView get = Ui.text(this, bytes > 0 ? "Resume" : "Download", 15, Ui.ACCENT, true);
                get.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), 0, Ui.dp(this, 12));
                get.setContentDescription("Download " + m.weights);
                get.setOnClickListener(v -> downloadModel(m, tooBig, ram));
                row.addView(get);
                if (bytes > 0) {
                    TextView delete = Ui.text(this, "Delete", 15, Ui.CORAL, true);
                    delete.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), 0, Ui.dp(this, 12));
                    delete.setContentDescription("Delete " + m.weights);
                    delete.setOnClickListener(v -> deleteModel(m, bytes, using));
                    row.addView(delete);
                }
            }
            onPhone.addView(row);
        }
    }

    private void deleteModel(final ai.ibiko.llm.ModelSpec m, long bytes, final boolean using) {
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
                    }, "dvine-delete-model").start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Downloads a model that isn't here: it becomes the offline model and its download starts. */
    private void downloadModel(final ai.ibiko.llm.ModelSpec m, boolean tooBig, long ram) {
        if (hub.downloading()) {
            toast("One download at a time: pause the one running first.");
            return;
        }
        Runnable go = () -> {
            new ai.ibiko.llm.ModelGuard(getSharedPreferences("dvine", MODE_PRIVATE)).clear(m.file);
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

    private TextView modelTitle, modelAbout, speed;

    /**
     * Measures the phone's own model where it runs, on this phone: how long it takes to start, to say its first word,
     * and how many tokens a second it writes. The same question every time, so models and memory sizes compare.
     */
    private void speedTest() {
        if (!hub.model.installed()) {
            toast("Download " + hub.chosen().weights + " first.");
            return;
        }
        speed.setVisibility(View.VISIBLE);
        speed.setText("Testing " + hub.chosen().weights + "\u2026 (up to a minute)");
        new Thread(() -> {
            hub.model.context(ai.ibiko.chat.Tuning.OFFLINE_CONTEXT.get(getSharedPreferences("dvine", MODE_PRIVATE)));
            ai.ibiko.llm.ModelGuard guard = new ai.ibiko.llm.ModelGuard(getSharedPreferences("dvine", MODE_PRIVATE));
            guard.loading(hub.chosen().file);
            ai.ibiko.llm.ReplyService.hold(this);
            LocalModel.Speed s;
            try {
                s = hub.model.speedTest(ai.ibiko.chat.Tuning.modelThreads(getSharedPreferences("dvine", MODE_PRIVATE)), 96);
            } finally {
                ai.ibiko.llm.ReplyService.release(this);
            }
            guard.loaded();
            runOnUiThread(() -> {
                speed.setText(s == null ? "The test couldn't run: " + hub.model.problem()
                        : s.words() + " " + ai.ibiko.chat.Tuning.modelThreads(getSharedPreferences("dvine", MODE_PRIVATE))
                        + " threads (Settings \u203A Advanced \u203A Offline model threads).");
                refreshModel();
            });
        }, "dvine-speed-test").start();
    }

    /**
     * The offline models on offer: first the family (Qwen3, Llama 3.2, Gemma 3, Phi-4), then its size, as Claude
     * comes as Haiku, Sonnet and Opus. Each size shows its download, the memory it wants and whether it is here.
     */
    private void chooseModel() {
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
    private void chooseSize(String family) {
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
    private void pick(final ai.ibiko.llm.ModelSpec next) {
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

    private void switchModel(ai.ibiko.llm.ModelSpec next, boolean deleteOld) {
        new ai.ibiko.llm.ModelGuard(getSharedPreferences("dvine", MODE_PRIVATE)).clear(next.file); // chosen on purpose: tried again
        hub.choose(this, next, deleteOld);
        refreshModel();
        toast(hub.model.has(next) ? next.weights + " is on the device: it starts when you go back to the chat."
                : "Now tap Download to get " + next.weights + " (" + next.sizeWords() + ").");
    }

    /** The phone's memory, in whole gigabytes (0 when unknown). */
    private long phoneMemoryGb() {
        android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (am == null) {
            return 0;
        }
        android.app.ActivityManager.MemoryInfo info = new android.app.ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        return Math.round(info.totalMem / 1e9);
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

    private View link(String name, String about, final Class<?> screen) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        r.addView(Ui.text(this, name + "  ›", 16, Ui.TEXT, false));
        r.addView(Ui.text(this, about, 13, Ui.SUB, false));
        r.setOnClickListener(v -> startActivity(new Intent(this, screen)));
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
