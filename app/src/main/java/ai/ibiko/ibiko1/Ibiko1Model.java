package ai.ibiko.ibiko1;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Ibiko 1's entry point, the same shape as any other model: give it a message, get its reply. It needs no network
 * and no other model; it answers from its built-in knowledge and what it has learned, researches through
 * {@link Plug#web} when one is plugged in, and learns from feedback. Its code is this folder and its knowledge the
 * app's {@code assets/knowledge}; {@code tools/export_ibiko1.sh} puts both into a standalone library folder, which another
 * app adds with {@code implementation project(':ibiko1')}.
 *
 * <pre>
 * Ibiko1Model brain = new Ibiko1Model(context);          // or new Ibiko1Model(context, dataDir)
 * Ibiko1Model.Reply r = brain.reply("What is the capital of Ghana?", 15000);
 * r.text      // "The capital of Ghana is Accra."
 * r.code      // a function line for the host app ("§SR ..."), when the message asked for an action
 * brain.feedback(question, r.text, true);                // right or wrong: it learns
 * </pre>
 *
 * The app it ships in (Ibiko) plugs it into its chat screen through {@link Ibiko1.Host} instead, for the working steps
 * and the check and cancel buttons; this class is the plain way in.
 */
public final class Ibiko1Model {
    /** The model's id and address, as other models have theirs. */
    public static final String ID = Endpoint.MODEL;
    public static final String ADDRESS = Endpoint.ADDRESS;
    public static final String NAME = Ibiko1.NAME;

    /** One reply: its text, or the function line it wrote for the host app, and the steps it took. */
    public static final class Reply {
        public final String text;
        public final String code;
        public final List<String> steps;

        Reply(String text, String code, List<String> steps) {
            this.text = text;
            this.code = code;
            this.steps = Collections.unmodifiableList(steps);
        }
    }

    private final ScheduledExecutorService thread = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ibiko1");
        t.setDaemon(true);
        return t;
    });
    private final Headless host;
    private final Ibiko1 model;

    /** Keeps its memory in the app's files folder ({@code brain/}), as the Ibiko app does. */
    public Ibiko1Model(android.content.Context context) {
        this(context, context.getFilesDir());
    }

    /** Keeps its memory under {@code dataDir/brain}. The context may be null where no Android context exists. */
    public Ibiko1Model(android.content.Context context, File dataDir) {
        if (context != null && isDefault()) {
            final android.content.res.AssetManager assets = context.getApplicationContext() == null ? context.getAssets()
                    : context.getApplicationContext().getAssets();
            Plug.asset = path -> {
                try {
                    return assets.open(path);
                } catch (java.io.IOException e) {
                    return null;
                }
            };
        }
        host = new Headless(context);
        model = new Ibiko1(host, dataDir);
    }

    /** True while no app has told Ibiko 1 where its files are. */
    private static boolean isDefault() {
        try (java.io.InputStream in = Plug.asset.apply("knowledge/world_facts.tsv")) {
            return in == null;
        } catch (java.io.IOException e) {
            return true;
        }
    }

    /** Answers one message, waiting at most {@code timeoutMs}. The text is null when it ran out of time. */
    public Reply reply(final String message, long timeoutMs) {
        final Run run = new Run();
        thread.execute(() -> {
            host.run = run;
            model.answer(message);
        });
        try {
            run.done.await(Math.max(1, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (run) {
            return new Reply(run.text, run.code, new ArrayList<String>(run.steps));
        }
    }

    /** The pilot said an answer was right or wrong: it is remembered, or forgotten and looked up again next time. */
    public void feedback(String question, String answer, boolean right) {
        thread.execute(() -> model.endpoint().feedback(question, answer, right));
    }

    /** The device's model Ibiko 1 may ask (system, question → answer, or null), or none. */
    private volatile java.util.function.BiFunction<String, String, String> deviceModel;

    /**
     * Gives Ibiko 1 a language model to ask for what its own memory doesn't hold (blocking; called off the caller's
     * thread). Its answers come back unchecked; feedback on them is what Ibiko 1 keeps. Null takes it away.
     */
    public Ibiko1Model withModel(java.util.function.BiFunction<String, String, String> ask) {
        deviceModel = ask;
        return this;
    }

    /** The model itself, for an app that plugs in its own {@link Ibiko1.Host}. */
    public Ibiko1 model() {
        return model;
    }

    /** Stops its thread. */
    public void close() {
        thread.shutdownNow();
    }

    private static final class Run {
        final CountDownLatch done = new CountDownLatch(1);
        final List<String> steps = new ArrayList<String>();
        String text, code;
    }

    /** A host with no screen: steps are kept, the reply ends the run, the steps' pacing for the eye is skipped. */
    private final class Headless implements Ibiko1.Host {
        final android.content.Context context;
        volatile Run run;
        /** What the other models taught it, kept here when no app holds a brain box for it. */
        private final java.util.Map<String, String> talk = new java.util.concurrent.ConcurrentHashMap<String, String>();

        Headless(android.content.Context context) {
            this.context = context;
        }

        @Override
        public String persona() {
            return "ibiko";
        }

        @Override
        public void step(String step) {
            Run r = run;
            if (r != null) {
                synchronized (r) {
                    r.steps.add(step);
                }
            }
        }

        @Override
        public void stepsDone() {
        }

        @Override
        public void say(String persona, String text) {
            Run r = run;
            if (r != null) {
                synchronized (r) {
                    r.text = text;
                }
                r.done.countDown();
            }
        }

        @Override
        public void askRating(String question, String answer) {
        }

        @Override
        public void noRating(String text) {
        }

        @Override
        public boolean ratingWaiting() {
            return false;
        }

        @Override
        public void clearRating() {
        }

        @Override
        public boolean online() {
            return Plug.web != Plug.NONE; // research only when the app plugged in the web
        }

        @Override
        public boolean modelReady() {
            return deviceModel != null;
        }

        @Override
        public String askModel(String system, String question) {
            java.util.function.BiFunction<String, String, String> m = deviceModel;
            return m == null ? null : m.apply(system, question);
        }

        @Override
        public String sum(String message) {
            return null;
        }

        @Override
        public String recallTalk(String question) {
            return question == null ? null : talk.get(question.trim().toLowerCase(java.util.Locale.US));
        }

        @Override
        public void learnTalk(String question, String answer) {
            if (question != null && answer != null) {
                talk.put(question.trim().toLowerCase(java.util.Locale.US), answer);
            }
        }

        @Override
        public void forgetTalk(String question) {
            if (question != null) {
                talk.remove(question.trim().toLowerCase(java.util.Locale.US));
            }
        }

        @Override
        public int talkSize() {
            return talk.size();
        }

        @Override
        public int shapes() {
            return 0;
        }

        @Override
        public List<String> aboutPilot() {
            return new ArrayList<String>();
        }

        @Override
        public void answered(Ibiko1.How how) {
        }

        @Override
        public void note(String text) {
        }

        @Override
        public void later(Runnable r, long ms) {
            thread.schedule(r, Math.min(ms, 5), TimeUnit.MILLISECONDS); // no screen to pace the steps for
        }

        @Override
        public void onScreen(Runnable r) {
            thread.execute(r);
        }

        @Override
        public android.content.Context context() {
            return context;
        }

        @Override
        public boolean allows(String code) {
            return true;
        }

        @Override
        public void act(String message, String codeLine) {
            Run r = run;
            if (r != null) {
                synchronized (r) {
                    r.code = codeLine;
                }
                r.done.countDown();
            }
        }
    }
}
