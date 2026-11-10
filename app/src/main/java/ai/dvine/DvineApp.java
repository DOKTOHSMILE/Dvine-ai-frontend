package ai.dvine;

import android.app.Application;

/**
 * The app as a whole. It installs the crash catcher before any screen, service or thread starts, so a crash anywhere
 * leaves a report in Download/DVINE. It also collects, off the main thread, any native crash or freeze from the last
 * run that the catcher could not see.
 */
public final class DvineApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashLog.install(this);
        ai.dvine.chat.Tuning.resetOnce(getSharedPreferences("dvine", MODE_PRIVATE));
        if (!"robolectric".equals(android.os.Build.FINGERPRINT)) {
            // Google search with no key: google.com read in a browser on the device that isn't shown
            ai.dvine.net.WebSearch.browser = (q, most) -> ai.dvine.net.GoogleWeb.search(this, q, most);
        }
        plugDvine1();
        Thread t = new Thread(() -> {
            try {
                CrashLog.collectExits(this);
            } catch (Throwable ignored) {
                // never let the report keep the app from starting
            }
        }, "dvine-exits");
        t.setDaemon(true);
        t.start();
    }

    /**
     * DVINE 1 (the dvine1 folder) is given what it needs from the app: the web, the workers board, what the pilot is
     * into, an icon. This and the screen's {@code main/Dvine1Screen} are the only places it is plugged in.
     */
    private void plugDvine1() {
        final android.content.Context app = this;
        ai.dvine.dvine1.Plug.web = new ai.dvine.dvine1.Plug.Web() {
            @Override
            public String get(String url) {
                ai.dvine.net.WebGet.Page p = ai.dvine.net.WebGet.page(url, 12_000, 400_000);
                return p.status == 200 ? p.body : null;
            }

            @Override
            public String search(String question) {
                return new ai.dvine.connect.ConnectorStore(app).research(question, 2, 8000);
            }
        };
        ai.dvine.dvine1.Plug.workers = new ai.dvine.dvine1.Plug.Workers() {
            @Override
            public String start(String role, String task, String brain) {
                return ai.dvine.agent.AgentBoard.start(role, task, brain);
            }

            @Override
            public void step(String code, String what) {
                ai.dvine.agent.AgentBoard.step(code, what);
            }

            @Override
            public void end(String code, boolean done) {
                ai.dvine.agent.AgentBoard.end(code, done ? ai.dvine.agent.AgentBoard.Outcome.DONE : ai.dvine.agent.AgentBoard.Outcome.STOPPED);
            }
        };
        ai.dvine.dvine1.Plug.interests = () -> {
            ai.dvine.learn.PilotProfile profile = ai.dvine.learn.PilotProfile.at(new java.io.File(getFilesDir(), "brain/pilot.tsv"));
            java.util.List<String> out = new java.util.ArrayList<String>();
            for (String key : new String[] { "building", "likes", "works as", "works at", "home", "from" }) {
                if (profile.get(key) != null) {
                    out.add(profile.get(key));
                }
            }
            return out;
        };
        ai.dvine.dvine1.Plug.icon = R.drawable.ic_send;
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
            ai.dvine.llm.ModelHub hub = ai.dvine.llm.ModelHub.get(this);
            hub.worker.execute(hub.model::releaseIfIdle);
        } catch (RuntimeException ignored) {
            // a trim must never take the app down
        }
    }
}
