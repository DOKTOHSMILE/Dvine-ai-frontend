package ai.ibiko;

import android.app.Application;

/**
 * The app as a whole. It installs the crash catcher before any screen, service or thread starts, so a crash anywhere
 * leaves a report in Download/Ibiko. It also collects, off the main thread, any native crash or freeze from the last
 * run that the catcher could not see.
 */
public final class IbikoApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // where the dictionary is: the brain's coded files are read with it, so it opens itself the first time it is needed
        ai.ibiko.learn.WordBook.at(new java.io.File(getFilesDir(), "brain/knowledge/lexicon.tsv"));
        ai.ibiko.learn.Symbols.open(getFilesDir()); // first: word ids are read back before any is made, the same for good
        ai.ibiko.main.FormerName.move(this); // once: what an older install kept under the former name moves over
        CrashLog.install(this);
        try {
            ai.ibiko.net.Transfers.use(new ai.ibiko.chat.ui.DownloadNotice(this)); // every download shows its progress
        } catch (RuntimeException ignored) {
            // no notifications: downloads still run
        }
        ai.ibiko.chat.Tuning.resetOnce(getSharedPreferences("ibiko", MODE_PRIVATE));
        android.content.SharedPreferences p = getSharedPreferences("ibiko", MODE_PRIVATE);
        if (!p.getBoolean("approvals_back_v1", false)) {
            // "Always run" in the old approval dialog turned asking off for good, without saying so: asking is back on
            // once; Settings › Coding › Run commands without asking turns it off again
            p.edit().putBoolean("auto_accept", false).putBoolean("approvals_back_v1", true).apply();
        }
        if (!"robolectric".equals(android.os.Build.FINGERPRINT)) {
            // Google search with no key: google.com read in a browser on the device that isn't shown
            ai.ibiko.net.WebSearch.browser = (q, most) -> ai.ibiko.net.GoogleWeb.search(this, q, most);
        }
        plugIbiko1();
        if (!"robolectric".equals(android.os.Build.FINGERPRINT)) {
            ai.ibiko.main.Warmup.start(this); // gets the chat and Ibiko 1 ready while the splash shows
        }
        // the voice is loaded as Ibiko starts and kept on standby, so the first reply read aloud doesn't wait for it
        if (ai.ibiko.main.AppFiles.ready(getFilesDir()) && !"robolectric".equals(android.os.Build.FINGERPRINT)) {
            ai.ibiko.voice.NeuralVoice.preload(getFilesDir(), ai.ibiko.voice.VoicePacks.KOKORO.key); // the one voice
            final java.io.File files = getFilesDir();
            new Thread(() -> ai.ibiko.voice.VoicePacks.dropOthers(files), "ibiko-old-voices").start(); // Piper, no longer offered
        }
        Thread t = new Thread(() -> {
            try {
                CrashLog.collectExits(this);
            } catch (Throwable ignored) {
                // never let the report keep the app from starting
            }
        }, "ibiko-exits");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Ibiko 1 (the ibiko1 folder) is given what it needs from the app: the web, the workers board, what the pilot is
     * into, an icon. This and the screen's {@code main/Ibiko1Screen} are the only places it is plugged in.
     */
    private void plugIbiko1() {
        final android.content.Context app = this;
        ai.ibiko.ibiko1.Plug.asset = path -> {
            try {
                return getAssets().open(path);
            } catch (java.io.IOException e) {
                return null;
            }
        };
        ai.ibiko.ibiko1.Plug.web = new ai.ibiko.ibiko1.Plug.Web() {
            @Override
            public String get(String url) {
                ai.ibiko.net.WebGet.Page p = ai.ibiko.net.WebGet.page(url, 12_000, 400_000);
                return p.status == 200 ? p.body : null;
            }

            @Override
            public String search(String question) {
                return new ai.ibiko.connect.ConnectorStore(app).research(question, 2, 8000);
            }
        };
        ai.ibiko.ibiko1.Plug.workers = new ai.ibiko.ibiko1.Plug.Workers() {
            @Override
            public String start(String role, String task, String brain) {
                return ai.ibiko.agent.AgentBoard.start(role, task, brain);
            }

            @Override
            public void step(String code, String what) {
                ai.ibiko.agent.AgentBoard.step(code, what);
            }

            @Override
            public void end(String code, boolean done) {
                ai.ibiko.agent.AgentBoard.end(code, done ? ai.ibiko.agent.AgentBoard.Outcome.DONE : ai.ibiko.agent.AgentBoard.Outcome.STOPPED);
            }
        };
        ai.ibiko.ibiko1.Plug.interests = () -> {
            ai.ibiko.learn.PilotProfile profile = ai.ibiko.learn.PilotProfile.at(new java.io.File(getFilesDir(), "brain/pilot.tsv"));
            java.util.List<String> out = new java.util.ArrayList<String>();
            for (String key : new String[] { "building", "likes", "works as", "works at", "home", "from" }) {
                if (profile.get(key) != null) {
                    out.add(profile.get(key));
                }
            }
            return out;
        };
        ai.ibiko.ibiko1.Plug.icon = R.drawable.ic_send;
    }

    /**
     * Android is short of memory. Drop the on-device weights if nothing is being written. The file stays
     * on the device and is loaded again for the next offline question. An API reply already parks them.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level < TRIM_MEMORY_RUNNING_LOW) {
            return;
        }
        try {
            ai.ibiko.llm.ModelHub hub = ai.ibiko.llm.ModelHub.get(this);
            hub.worker.execute(hub.model::releaseIfIdle);
        } catch (RuntimeException ignored) {
            // a trim must never take the app down
        }
    }
}
