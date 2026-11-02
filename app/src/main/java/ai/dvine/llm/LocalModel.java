package ai.dvine.llm;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * DVINE's own language model on the phone: whether it is installed, downloading, loaded or can't run here,
 * plus the conversation it remembers (the last few turns). Loading and answering block: call them off the
 * UI thread. Android-free apart from what the caller passes in.
 */
public final class LocalModel {
    public enum State { NOT_INSTALLED, DOWNLOADING, INSTALLED, LOADING, READY, UNSUPPORTED, FAILED }

    /** The weights in use; another can be chosen ({@link #switchTo}). */
    public volatile ModelSpec spec;
    private volatile File file;
    private final File dir;
    private volatile State state;
    private final java.util.concurrent.atomic.AtomicInteger epoch = new java.util.concurrent.atomic.AtomicInteger();
    private volatile String problem;
    private long handle;
    private final List<String[]> history = new ArrayList<String[]>();
    private static final int HISTORY_TURNS = 6;
    private final ReplyLease replies = new ReplyLease();
    /** What each reply used, for the Usage screen. */
    public final UsageLog usage = new UsageLog();
    /** The smallest context window the phone's own brain is loaded with (tokens): the fallback when memory is short. */
    public static final int LOCAL_WINDOW = 2048;
    /** The memory asked for (Settings › Advanced › Offline model memory) and what the loaded model really has. */
    private volatile int contextTokens = 8192, loadedContext;
    /** Said once after a load that had to take less memory than asked; null otherwise. */
    private volatile String memoryNote;
    /** The last few local replies' speeds, tokens a second, newest last. */
    private final java.util.ArrayDeque<Float> speeds = new java.util.ArrayDeque<Float>();
    /** The model and memory the slowness advice was last given for, so it is said once. */
    private volatile String advisedFor;
    /**
     * The brains the user brought (APIs), in the order they are tried: the first that isn't resting answers; one
     * that fails before it has said anything hands over to the next (up to three), and rests for a while.
     */
    private volatile List<Slot> chain = new ArrayList<Slot>();
    private volatile java.util.function.BooleanSupplier online = () -> true;
    private volatile RemoteBrain current;
    private volatile String switchNote;
    /** Ibiko is answering: every call goes to the strong model. */
    public volatile boolean coding;
    /** How long a provider rests after failing: a minute, or five when it is out of credit or rate-limited. */
    static final long REST_MS = 60_000, REST_LONG_MS = 300_000;

    /** One API in the switching order. */
    static final class Slot {
        final RemoteBrain brain;
        /** The same provider's fast model for simple questions, or null. */
        RemoteBrain fast;
        final String name;
        final int window;
        volatile long restUntil;

        Slot(RemoteBrain brain, String name, int window) {
            this.brain = brain;
            this.name = name;
            this.window = window > 0 ? window : 32_768;
        }
    }

    /** A cautious count of characters per token, for fitting text into the phone's own model's memory. */
    static final int CHARS_PER_TOKEN = 3;

    /**
     * {@code text} cut to {@code chars} characters, keeping its start (the task, a quarter) and its end (the latest
     * work), with a mark where the middle was left out. Unchanged when it fits.
     */
    static String fit(String text, int chars) {
        String t = text == null ? "" : text;
        int room = Math.max(400, chars);
        if (t.length() <= room) {
            return t;
        }
        String gap = "\n\u2026(earlier work left out to fit the phone's model)\u2026\n";
        int head = (room - gap.length()) / 4;
        int tail = room - gap.length() - head;
        return t.substring(0, Math.max(0, head)) + gap + t.substring(t.length() - Math.max(0, tail));
    }

    /** Keeps a remembered answer short so the next reply is not rereading a long one. */
    static String clip(String answer, int n) {
        String t = answer == null ? "" : answer.trim();
        return t.length() <= n ? t : t.substring(0, n) + "...";
    }

    static String clipMemory(String answer) {
        if (answer == null) {
            return "";
        }
        String t = answer.trim();
        if (t.length() <= 420) {
            return t;
        }
        return t.substring(0, 417).trim() + "...";
    }

    public LocalModel(File dir, ModelSpec spec, boolean cpuSupported) {
        this.spec = spec;
        this.dir = dir;
        this.file = new File(dir, spec.file);
        state = !cpuSupported ? State.UNSUPPORTED : installed() ? State.INSTALLED : State.NOT_INSTALLED;
        if (!cpuSupported) {
            problem = "this phone's processor is too old for it";
        }
    }

    public State state() {
        return state;
    }

    public String problem() {
        return problem;
    }

    public File file() {
        return file;
    }

    public boolean installed() {
        return file.isFile() && file.length() == spec.bytes;
    }

    /** Ready to answer: the phone's own brain is loaded, or the user's API is set and the phone is online. */
    public boolean ready() {
        return state == State.READY || remoteOn();
    }

    /** Whether the phone's own brain is loaded (whatever the API is doing). */
    public boolean localReady() {
        return state == State.READY;
    }

    /**
     * Uses the user's API ({@code null}: only the phone's own brain from now on). {@code window} is that model's
     * context window in tokens, for the Usage screen; {@code online} says whether the phone has a connection.
     */
    public void useRemote(RemoteBrain brain, int window, java.util.function.BooleanSupplier online) {
        List<RemoteBrain> one = new ArrayList<RemoteBrain>();
        List<String> names = new ArrayList<String>();
        List<Integer> windows = new ArrayList<Integer>();
        if (brain != null) {
            one.add(brain);
            names.add(brain.model);
            windows.add(window);
        }
        useRemotes(one, names, windows, online);
    }

    /**
     * Uses these APIs in this order (at most three), switching to the next when one fails; an empty list means
     * only the phone's own brain.
     */
    public void useRemotes(List<RemoteBrain> brains, List<String> names, List<Integer> windows, java.util.function.BooleanSupplier online) {
        useRemotes(brains, null, names, windows, online);
    }

    /** As above, each API with its fast model for simple questions ({@code fasts}, null entries for none). */
    public void useRemotes(List<RemoteBrain> brains, List<RemoteBrain> fasts, List<String> names, List<Integer> windows,
            java.util.function.BooleanSupplier online) {
        List<Slot> next = new ArrayList<Slot>();
        for (int i = 0; i < brains.size() && next.size() < 3; i++) {
            if (brains.get(i) != null) {
                Slot sl = new Slot(brains.get(i), i < names.size() ? names.get(i) : brains.get(i).model,
                        i < windows.size() ? windows.get(i) : 32_768);
                sl.fast = fasts != null && i < fasts.size() ? fasts.get(i) : null;
                next.add(sl);
            }
        }
        // a provider still resting keeps resting when the order is set again
        for (Slot n : next) {
            for (Slot old : chain) {
                if (old.name.equals(n.name) && old.brain.model.equals(n.brain.model)) {
                    n.restUntil = old.restUntil;
                }
            }
        }
        this.chain = next;
        if (online != null) {
            this.online = online;
        }
    }

    /** The first API in the order, or null. */
    public RemoteBrain remote() {
        List<Slot> c = chain;
        return c.isEmpty() ? null : c.get(0).brain;
    }

    /** How many APIs are in the switching order. */
    public int remoteCount() {
        return chain.size();
    }

    /** The user's API will answer the next question (one is set, and the phone is online). */
    public boolean remoteOn() {
        return !chain.isEmpty() && online.getAsBoolean();
    }

    /** "Claude didn't answer (…), so OpenAI did." once, after a switch; then null. */
    public String takeSwitchNote() {
        String n = switchNote;
        switchNote = null;
        return n;
    }

    /** The context window of the brain that will answer next. */
    public int window() {
        if (remoteOn()) {
            long now = System.currentTimeMillis();
            for (Slot sl : chain) {
                if (sl.restUntil <= now) {
                    return sl.window;
                }
            }
            return chain.get(0).window;
        }
        return localWindow();
    }

    /** The phone's own brain's context window: what it was loaded with, or what it will be. */
    public int localWindow() {
        return loadedContext > 0 ? loadedContext : contextTokens;
    }

    /**
     * The memory to load the model with, in tokens. A loaded model with another size is freed (true: load it again
     * with {@link #load}); otherwise the size is used at the next load.
     */
    public synchronized boolean context(int tokens) {
        int t = Math.max(LOCAL_WINDOW, tokens);
        if (t == contextTokens) {
            return false;
        }
        contextTokens = t;
        advisedFor = null;
        if (handle != 0 && loadedContext != t) {
            NativeLlm.nFree(handle);
            handle = 0;
            loadedContext = 0;
            history.clear();
            if (state == State.READY) {
                state = State.INSTALLED;
            }
            return true;
        }
        return false;
    }

    /** What to tell the user once after a load that had to settle for less memory; null when there is nothing. */
    public String takeMemoryNote() {
        String n = memoryNote;
        memoryNote = null;
        return n;
    }

    /**
     * Advice when the phone's own model is slow: the last three local replies averaged under {@code slowBelow}
     * tokens a second. It names a smaller size of the same family, or less memory, or an API; said once per model
     * and memory size. Null when it is fast enough, or the advice was already given.
     */
    public String slowAdvice(int slowBelow) {
        float sum = 0;
        int n;
        synchronized (speeds) {
            n = speeds.size();
            for (float f : speeds) {
                sum += f;
            }
        }
        if (n < 3 || sum / n >= slowBelow) {
            return null;
        }
        String key = spec.file + "|" + localWindow();
        if (key.equals(advisedFor)) {
            return null;
        }
        advisedFor = key;
        String speed = String.format(java.util.Locale.US, "%.1f", sum / n);
        ModelSpec smaller = null;
        for (ModelSpec m : ModelSpec.sizes(spec.family)) {
            if (m.bytes < spec.bytes) {
                smaller = m; // the next size down
            }
        }
        StringBuilder b = new StringBuilder(spec.weights + " is running slowly on this phone (" + speed + " tokens a second). For faster replies:");
        if (smaller != null) {
            b.append("\n\u2022 a smaller size, ").append(smaller.weights).append(" (tap the model name at the top)");
        }
        if (localWindow() > 4096) {
            b.append("\n\u2022 less memory: Settings \u203A Advanced \u203A Offline model memory, 4,096 instead of ")
                    .append(String.format(java.util.Locale.US, "%,d", localWindow()));
        }
        b.append("\n\u2022 an API model for the heavy work (Settings \u203A AI providers)");
        return b.toString();
    }

    /** Downloads and verifies the model (blocking); the progress callback may stop it. */
    public ModelDownloader.Result download(ModelDownloader.Source source, ModelDownloader.Progress progress) {
        if (state == State.UNSUPPORTED) {
            return ModelDownloader.Result.FAILED;
        }
        state = State.DOWNLOADING;
        ModelDownloader.Result r = ModelDownloader.fetch(spec, file, source, progress);
        state = r == ModelDownloader.Result.DONE ? State.INSTALLED : State.NOT_INSTALLED;
        if (r == ModelDownloader.Result.BAD_FILE) {
            problem = "the download didn't match its checksum, so I deleted it";
        }
        return r;
    }

    /** Loads the installed model into memory (blocking, a few seconds). */
    public synchronized boolean load(int threads) {
        if (state == State.READY) {
            return true;
        }
        if (!installed()) {
            state = state == State.UNSUPPORTED ? state : State.NOT_INSTALLED;
            return false;
        }
        if (!NativeLlm.available()) {
            state = State.UNSUPPORTED;
            problem = "its engine isn't in this build";
            return false;
        }
        state = State.LOADING;
        int want = contextTokens;
        handle = NativeLlm.nLoad(file.getPath(), want, threads);
        if (handle == 0 && want > LOCAL_WINDOW) {
            // not enough memory for that much: the smallest working memory still runs, and the user is told
            handle = NativeLlm.nLoad(file.getPath(), LOCAL_WINDOW, threads);
            if (handle != 0) {
                memoryNote = "There wasn't enough free memory for " + String.format(java.util.Locale.US, "%,d", want) + " tokens, so "
                        + spec.weights + " runs with " + String.format(java.util.Locale.US, "%,d", LOCAL_WINDOW)
                        + " (Settings \u203A Advanced \u203A Offline model memory).";
                want = LOCAL_WINDOW;
            }
        }
        loadedContext = handle == 0 ? 0 : want;
        if (handle == 0) {
            state = State.FAILED;
            problem = "the model couldn't be loaded (not enough memory, or a damaged file)";
            return false;
        }
        state = State.READY;
        return true;
    }

    /**
     * Answers as a persona: its system prompt, the recent conversation and the new question; the reply streams to
     * {@code sink} as it is written. Returns the whole reply, or null when the model isn't ready or failed.
     */
    public synchronized String reply(String system, String question, int maxTokens, NativeLlm.Sink sink) {
        return reply(system, question, null, maxTokens, sink);
    }

    /**
     * As {@link #reply(String, String, int, NativeLlm.Sink)}, with {@code context} (taught facts, a fresh reference)
     * given alongside this question only: it is not remembered as part of the conversation. If the conversation
     * has grown too long for the model's memory, the oldest exchanges are dropped and it tries again.
     */
    public synchronized String reply(String system, String question, String context, int maxTokens, NativeLlm.Sink sink) {
        String asked = context == null ? question : context + "\n\nThe user says: " + question;
        if (remoteOn()) {
            List<String[]> turns = new ArrayList<String[]>(history);
            turns.add(new String[]{"user", asked});
            String answer = askRemote(system, turns, maxTokens, 0.7f, sink, TaskSize.ofCall(question, maxTokens, coding));
            if (answer != null) {
                remember(question, ActionTags.strip(answer));
                return answer.trim();
            }
            if (state != State.READY || "stopped".equals(problem)) {
                return null;
            }
            // the API failed: the phone's own brain answers instead
        }
        if (state != State.READY) {
            return null;
        }
        final String apiProblem = remoteOn() ? problem : null; // why the API didn't answer, kept for the user
        final int room = Math.min(maxTokens, Math.max(256, localWindow() / 4));
        asked = fit(asked, (localWindow() - room) * CHARS_PER_TOKEN - (system == null ? 0 : system.length()) - 600);
        ai.dvine.learn.LearningLog l = learning;
        String learned = l == null ? "" : l.examplesFor(question, 2, 1400);
        step("Answering with the model on this phone");
        if (!learned.isEmpty()) {
            step("Recalled similar answers I learned before");
            asked = learned + "\n\n" + (context == null ? "" : context + "\n\n") + "The user says: " + question;
        }
        final int ticket = epoch.get();
        for (int attempt = 0; attempt < 3; attempt++) {
            List<String[]> turns = new ArrayList<String[]>(history);
            turns.add(new String[]{"user", asked});
            String[] roles = new String[turns.size()], texts = new String[turns.size()];
            for (int i = 0; i < turns.size(); i++) {
                roles[i] = turns.get(i)[0];
                texts[i] = ai.dvine.chat.Attachments.withoutImages(turns.get(i)[1]); // the phone's model can't see images
            }
            final int[] pieces = {0};
            final long began = System.currentTimeMillis();
            String answer = withTimeout(counting(sink, pieces), wrapped -> NativeLlm.nReply(handle, spec.prompt(system), roles, texts, room, 0.7f, wrapped));
            if (ticket != epoch.get()) {
                return null;
            }
            recordLocal(system, history, asked, answer, pieces[0], began);
            if (answer != null && !answer.trim().isEmpty()) {
                remember(question, ActionTags.strip(answer));
                return answer.trim();
            }
            String local = NativeLlm.nError(handle);
            problem = apiProblem == null ? local
                    : apiProblem + "; the phone's own model couldn't take over" + (local == null || local.isEmpty() ? "" : " (" + local + ")");
            if (local == null || !local.contains("too long") || history.isEmpty()) {
                return null;
            }
            // too long: forget the oldest half of the conversation and try once more
            int drop = Math.max(2, history.size() / 2 / 2 * 2);
            for (int i = 0; i < drop && !history.isEmpty(); i++) {
                history.remove(0);
            }
        }
        return null;
    }

    /**
     * One task outside the conversation (a drawing to describe): its own instructions, no history, nothing
     * remembered; a lower temperature so the shapes come out well formed. Null when not ready or it failed.
     */
    public synchronized String task(String system, String prompt, int maxTokens, NativeLlm.Sink sink) {
        if (remoteOn()) {
            List<String[]> turns = new ArrayList<String[]>();
            turns.add(new String[]{"user", prompt});
            String answer = askRemote(system, turns, maxTokens, 0.35f, sink, TaskSize.ofCall(prompt, maxTokens, coding));
            if (answer != null) {
                return answer.trim();
            }
            if (state != State.READY || "stopped".equals(problem)) {
                return null;
            }
        }
        if (state != State.READY) {
            return null;
        }
        final String apiProblem = remoteOn() ? problem : null; // why the API didn't answer, kept for the user
        // the phone's own model takes over with what fits its memory: the start of the task and the latest work
        final int reply = Math.min(maxTokens, Math.max(256, localWindow() / 4));
        final String fitted = fit(prompt, (localWindow() - reply) * CHARS_PER_TOKEN - system.length() - 200);
        final int[] pieces = {0};
        final long began = System.currentTimeMillis();
        String answer = withTimeout(counting(sink, pieces), wrapped -> NativeLlm.nReply(handle, spec.prompt(system), new String[]{"user"},
                new String[]{ai.dvine.chat.Attachments.withoutImages(fitted)}, reply, 0.35f, wrapped));
        recordLocal(system, java.util.Collections.<String[]>emptyList(), fitted, answer, pieces[0], began);
        if (answer == null || answer.trim().isEmpty()) {
            String local = NativeLlm.nError(handle);
            problem = apiProblem == null ? local
                    : apiProblem + "; the phone's own model couldn't take over" + (local == null || local.isEmpty() ? "" : " (" + local + ")");
            return null;
        }
        return answer.trim();
    }

    /**
     * Cancels a reply that has gone quiet for 12 seconds, or that is still running after 90.
     * A token resets the quiet timer, so a slow but live answer is not cut off at 25 seconds.
     */
    private String withTimeout(NativeLlm.Sink sink, java.util.function.Function<NativeLlm.Sink, String> call) {
        final int mine = replies.open();
        final ReplyWatch watch = new ReplyWatch(12_000, 180_000, System.currentTimeMillis());
        final int ticket = epoch.get();
        NativeLlm.Sink wrapped = text -> {
            if (ticket != epoch.get()) {
                return false;
            }
            watch.touch(System.currentTimeMillis());
            return sink == null || sink.onText(text);
        };
        Thread watchdog = new Thread(() -> {
            while (!watch.finished()) {
                try {
                    Thread.sleep(400);
                } catch (InterruptedException e) {
                    return;
                }
                if (watch.expired(System.currentTimeMillis()) && replies.mine(mine)) {
                    String why = watch.reason(System.currentTimeMillis());
                    problem = "cap".equals(why) ? "the reply took too long" : "the reply went quiet";
                    cancel();
                    return;
                }
            }
        }, "dvine-model-timeout");
        watchdog.setDaemon(true);
        watchdog.start();
        try {
            return call.apply(wrapped);
        } catch (RuntimeException e) {
            return null;
        } finally {
            watch.finish();
            watchdog.interrupt();
        }
    }

    /**
     * The user's APIs answer, in order: the first that isn't resting is asked; one that fails (or goes quiet) before
     * it has said anything rests, and the next is asked. Once a reply has started, it isn't switched mid-sentence.
     * Same limits and cancelling as the phone's brain; what it used is recorded.
     */
    private String askRemote(String system, List<String[]> turns, int maxTokens, float temperature, NativeLlm.Sink sink,
            TaskSize.Tier tier) {
        List<Slot> order = new ArrayList<Slot>(chain);
        long now = System.currentTimeMillis();
        // resting providers go last, not out: if every one is resting, they are still tried
        List<Slot> ready = new ArrayList<Slot>(), resting = new ArrayList<Slot>();
        for (Slot sl : order) {
            (sl.restUntil <= now ? ready : resting).add(sl);
        }
        ready.addAll(resting);
        String firstFailure = null, failedName = null;
        // each provider: its fast model first for a simple request (when it has one), then its strong model
        List<Object[]> tries = new ArrayList<Object[]>();
        for (Slot sl : ready) {
            if (tier == TaskSize.Tier.FAST && sl.fast != null) {
                tries.add(new Object[]{sl, sl.fast});
            }
            tries.add(new Object[]{sl, sl.brain});
        }
        lastTier = tier;
        for (Object[] attempt : tries) {
            final Slot sl = (Slot) attempt[0];
            final RemoteBrain r = (RemoteBrain) attempt[1];
            current = r;
            step("Asking " + sl.name + " (" + r.model + ")" + (sl.fast != null ? (r == sl.fast ? ", the fast model" : ", the strong model") : ""));
            final int ticket = epoch.get();
            final int[] pieces = {0};
            final long began = System.currentTimeMillis();
            problem = null;
            String answer = withTimeout(counting(sink, pieces), wrapped -> r.chat(system, turns, maxTokens, temperature, wrapped));
            boolean timedOut = problem != null && problem.startsWith("the reply");
            if (ticket != epoch.get() && !timedOut) {
                problem = "stopped"; // the user stopped it: nothing more is asked
                return null;
            }
            if (answer != null && !answer.trim().isEmpty() && ticket == epoch.get()) {
                if (failedName != null) {
                    switchNote = failedName + " didn't answer (" + firstFailure + "), so " + sl.name + " did.";
                }
                record(sl, r, system, turns, answer, pieces[0], began);
                ai.dvine.learn.LearningLog l = learning;
                if (l != null) {
                    l.record(system, turns, answer, sl.name + " (" + r.model + ")", tier.name(), System.currentTimeMillis());
                }
                return answer;
            }
            String why = timedOut ? problem : r.problem() == null ? "it sent nothing back" : r.problem();
            usage.failure(System.currentTimeMillis());
            boolean credit = why.contains("(402") || why.contains("(429") || why.contains("credit") || why.contains("too many");
            if (r == sl.brain) { // a provider rests when its main model fails (its fast model failing alone doesn't)
                sl.restUntil = System.currentTimeMillis() + (credit ? REST_LONG_MS : REST_MS);
            }
            step(sl.name + " didn't answer (" + why + ")");
            if (firstFailure == null) {
                firstFailure = why;
                failedName = sl.name;
            }
            problem = why;
            if (pieces[0] > 0) {
                return null; // it had started speaking: switching now would repeat or contradict it
            }
        }
        return null;
    }

    /**
     * Where DVINE keeps what the API models answer, to learn from (null: not learning). When the phone's own model
     * answers, the closest past answers are given to it as examples.
     */
    public volatile ai.dvine.learn.LearningLog learning;

    /** Told each step of an answer as it happens ("Asking OpenAI (gpt-5-mini), the fast model"); may be null. */
    public volatile java.util.function.Consumer<String> onStep;

    private void step(String line) {
        java.util.function.Consumer<String> s = onStep;
        if (s != null) {
            try {
                s.accept(line);
            } catch (RuntimeException ignored) {
                // a display problem must not stop the answer
            }
        }
    }

    /** Which tier the last call asked for (for the Usage screen). */
    public volatile TaskSize.Tier lastTier = TaskSize.Tier.FAST;

    private void record(Slot sl, RemoteBrain r, String system, List<String[]> turns, String answer, int pieces, long began) {
        int conversation = 0;
        for (int i = 0; i < turns.size() - 1; i++) {
            conversation += UsageLog.estimate(turns.get(i)[1]);
        }
        int sys = UsageLog.estimate(system), msg = UsageLog.estimate(turns.get(turns.size() - 1)[1]);
        RemoteBrain.Used u = r.used();
        boolean measured = u != null && u.promptTokens > 0 && u.outputTokens >= 0;
        if (measured) {
            // the server counted the whole prompt: share it out in the same proportions as the estimate
            int est = Math.max(1, sys + conversation + msg);
            sys = Math.round((float) u.promptTokens * sys / est);
            conversation = Math.round((float) u.promptTokens * conversation / est);
            msg = Math.max(0, u.promptTokens - sys - conversation);
        }
        int out = u != null && u.outputTokens >= 0 ? u.outputTokens : Math.max(pieces, UsageLog.estimate(answer));
        usage.record(new UsageLog.Call("API: " + sl.name + " (" + r.model + ")", sl.window, sys, conversation, msg, out, measured,
                System.currentTimeMillis() - began, System.currentTimeMillis()));
    }

    /** Counts the pieces as they stream (the phone's brain sends one token a piece). */
    private static NativeLlm.Sink counting(NativeLlm.Sink sink, int[] count) {
        return text -> {
            count[0]++;
            return sink == null || sink.onText(text);
        };
    }

    private void recordLocal(String system, List<String[]> turns, String message, String answer, int pieces, long began) {
        long now = System.currentTimeMillis();
        if (answer == null || answer.trim().isEmpty()) {
            usage.failure(now);
            return;
        }
        int conversation = 0;
        for (String[] t : turns) {
            conversation += UsageLog.estimate(t[1]);
        }
        usage.record(new UsageLog.Call("On this phone: " + spec.weights, localWindow(), UsageLog.estimate(system), conversation,
                UsageLog.estimate(message), Math.max(pieces, 1), false, now - began, now));
        noteSpeed(pieces, now - began);
    }

    /** Keeps the speed of a local reply (pieces written a second) for {@link #slowAdvice}; short replies don't count. */
    void noteSpeed(int pieces, long millis) {
        if (pieces < 16 || millis <= 0) {
            return;
        }
        synchronized (speeds) {
            speeds.addLast(pieces * 1000f / millis);
            while (speeds.size() > 3) {
                speeds.removeFirst();
            }
        }
    }

    /** Stops a reply being written (from any thread). */
    public void cancel() {
        RemoteBrain r = current;
        if (r != null) {
            r.cancel();
        }
        epoch.incrementAndGet();
        long h = handle;
        if (h != 0) {
            NativeLlm.nCancel(h);
        }
    }

    /** Forgets the conversation (a new topic, or the user asked). */
    public synchronized void forget() {
        history.clear();
    }

    /**
     * An exchange the model didn't write (the offline brain answered, or a persona asked something), kept in the
     * conversation so later questions are read in its light.
     */
    public synchronized void note(String question, String answer) {
        if (question != null && answer != null && !question.trim().isEmpty() && !answer.trim().isEmpty()) {
            remember(question.trim(), answer.trim());
        }
    }

    synchronized void remember(String question, String answer) {
        // an image is sent once, with its question; the remembered conversation keeps "[an image]"
        history.add(new String[]{"user", ai.dvine.chat.Attachments.withoutImages(question)});
        // the phone's small model keeps a short memory; an API model keeps the whole reply (a report stays a report)
        history.add(new String[]{"assistant", remoteOn() ? clip(answer, 6000) : clipMemory(answer)});
        while (history.size() > HISTORY_TURNS * 2) {
            history.remove(0);
        }
    }

    synchronized int historySize() {
        return history.size();
    }

    /**
     * Uses other weights from now on: the loaded model is freed and the conversation forgotten; the new one is
     * loaded (once on the phone) by {@link #load}. The old file stays unless {@code deleteOld}.
     */
    public synchronized void switchTo(ModelSpec next, boolean deleteOld) {
        if (next == null || next.file.equals(spec.file)) {
            return;
        }
        if (handle != 0) {
            NativeLlm.nFree(handle);
            handle = 0;
        }
        loadedContext = 0;
        synchronized (speeds) {
            speeds.clear(); // a new model's speed is its own
        }
        history.clear();
        File old = file;
        if (deleteOld) {
            old.delete();
            new File(old.getPath() + ".part").delete();
        }
        spec = next;
        file = new File(dir, next.file);
        problem = null;
        if (state != State.UNSUPPORTED) {
            state = installed() ? State.INSTALLED : State.NOT_INSTALLED;
        }
    }

    /** Whether {@code m}'s file is on the phone, whole. */
    public boolean has(ModelSpec m) {
        File f = new File(dir, m.file);
        return f.isFile() && f.length() == m.bytes;
    }

    /** The space {@code m} takes on the phone: its file, whole or part-downloaded (bytes). */
    public long onPhone(ModelSpec m) {
        File f = new File(dir, m.file), part = new File(dir, m.file + ".part");
        return (f.isFile() ? f.length() : 0) + (part.isFile() ? part.length() : 0);
    }

    /** Deletes a model that is not in use (its file and any part-download), to free the space. */
    public synchronized boolean delete(ModelSpec m) {
        if (m.file.equals(spec.file)) {
            return uninstall();
        }
        File f = new File(dir, m.file);
        new File(dir, m.file + ".part").delete();
        return !f.exists() || f.delete();
    }

    /** Frees the model and deletes its file. */
    public synchronized boolean uninstall() {
        if (handle != 0) {
            NativeLlm.nFree(handle);
            handle = 0;
        }
        history.clear();
        boolean gone = !file.exists() || file.delete();
        new File(file.getPath() + ".part").delete();
        if (state != State.UNSUPPORTED) {
            state = State.NOT_INSTALLED;
        }
        return gone;
    }

    /** The phone's processor has 64-bit ARM with dot-product instructions (what the library is built for). */
    public static boolean cpuSupported(String[] abis, String cpuinfo) {
        boolean arm64 = false;
        for (String a : abis) {
            arm64 |= "arm64-v8a".equals(a);
        }
        return arm64 && cpuinfo != null && java.util.regex.Pattern.compile("(?m)^Features\\s*:.*\\basimddp\\b").matcher(cpuinfo).find();
    }

    /** How many threads to answer with: the phone's big cores, between 2 and 4. */
    public static int threads(int cores) {
        return Math.max(2, Math.min(4, cores / 2));
    }
}
