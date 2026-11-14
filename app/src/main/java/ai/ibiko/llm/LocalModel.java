package ai.ibiko.llm;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Ibiko's own language model on the phone: whether it is installed, downloading, loaded or can't run here,
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
    /** How many exchanges the model holds itself; older ones reach it as the chat's notes record. */
    public static final int HISTORY_TURNS = 6;
    /** An API model: the exchanges remembered, and how many are dropped at once when that is passed. */
    static final int REMOTE_HISTORY_TURNS = 24, REMOTE_DROP_TURNS = 12;
    /** The least room an API model's chat reply gets (tokens). */
    static final int REMOTE_REPLY_MIN = 2048;
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

    /** True while a native call is inside {@link #withTimeout}, including the time spent reading the prompt. */
    private volatile boolean inCall;

    public boolean working() {
        // a call stopped with cancel() is over for the screen at once, though its thread may take a while to unwind
        // (a network read giving up): Stop turns back into Send then, not when the old call finally returns
        return inCall && callTicket == epoch.get();
    }

    /** The {@link #epoch} the running call started in. */
    private volatile int callTicket;

    /** The longest pause between words before a reply counts as stuck. */
    static final long IDLE_MS = 60_000;
    /** How long a reply may be written after its first word (the reading time is separate). Overridden by a run mode. */
    public volatile long watchIdleMs, watchCapMs, watchReadMs;

    /** The longest the phone's model may spend reading a question before its first word. */
    static final long READ_MS = 600_000;

    /** How many times a reply that is still too long is cut down further and tried again. */
    static final int FIT_TRIES = 4;

    /**
     * The model's work stays at normal priority. Foreground priority held the cores at full speed,
     * which is the hum, and it did not make the answer faster on the slow cores.
     */
    static void raisePriority() {
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DEFAULT);
        } catch (Throwable ignored) {
            // not on a phone (desktop tests) or not allowed: the default priority still works
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
        String gap = "\n\u2026(earlier work left out to fit the device's model)\u2026\n";
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
            problem = "this device's processor is too old for it";
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

    /** Models this provider offers, so a question can be passed to similar ones ({@link ModelRing}). */
    private volatile List<String> bench = new ArrayList<String>();

    public void bench(List<String> models) {
        bench = models == null ? new ArrayList<String>() : new ArrayList<String>(models);
    }

    /** The selected model, then up to two of a similar size from {@link #bench}. */
    public List<String> ring() {
        String selected = "";
        List<Slot> now = chain;
        if (!now.isEmpty() && now.get(0).brain != null && now.get(0).brain.model != null) {
            selected = now.get(0).brain.model;
        }
        return ModelRing.choose(selected, bench);
    }

    /**
     * One named model on the first API, not the fast/strong switch. Null when no API is on or the call failed.
     * Used by {@link RoundThink}, which asks several models one after another.
     */
    public String askNamed(String model, String system, String prompt, int maxTokens) {
        if (!remoteOn() || model == null || model.trim().isEmpty()) {
            return null;
        }
        Slot sl;
        synchronized (this) {
            if (chain.isEmpty()) {
                return null;
            }
            sl = chain.get(0);
        }
        RemoteBrain r = sl.brain.withModel(model.trim());
        r.effortFor(TaskSize.Tier.STRONG);
        List<String[]> turns = new ArrayList<String[]>();
        turns.add(new String[] {"user", prompt == null ? "" : prompt});
        final int ticket = epoch.get();
        parkNative();
        String answer = withTimeout(null, wrapped -> r.chat(system, turns, maxTokens, 0.4f, wrapped), true);
        if (ticket != epoch.get()) {
            return null;
        }
        return answer == null ? null : answer.trim();
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

    /**
     * An API is the chosen brain, online or not. While it is, the device's own model never answers in its place: a
     * moment without internet is said as that, not quietly answered by another model.
     */
    public boolean apiChosen() {
        return !chain.isEmpty();
    }

    /** The phone's heat and battery, read before the phone's model starts a turn ({@link DeviceGuard}); null: not checked. */
    private volatile java.util.function.Supplier<DeviceGuard.Decision> guard;
    /** Settings › Chat: with the API chosen and no internet, the phone's own model answers (and says so). Off by default. */
    private volatile boolean offlineFallback;

    public void guard(java.util.function.Supplier<DeviceGuard.Decision> g) {
        guard = g;
    }

    public void offlineFallback(boolean on) {
        offlineFallback = on;
    }

    /** Whether the phone's model may answer when the API can't be reached: the setting is on and the model is on the phone. */
    public boolean fallsBackOffline() {
        return offlineFallback && installed();
    }

    /**
     * With the API out of reach: the phone's model answers instead when the user allowed it, and the reply says so
     * (never a silent switch). False: nothing answers.
     */
    private boolean answerOffline() {
        if (!fallsBackOffline()) {
            return false;
        }
        problem = null;
        switchNote = "There's no internet, so the phone's own model (" + spec.name + ") answered instead of your API.";
        return true;
    }

    private void dropOfflineNote() {
        String n = switchNote;
        if (n != null && n.startsWith("There's no internet")) {
            switchNote = null;
        }
    }

    /** Whether the phone is cool and charged enough for its model to start a turn; when not, {@link #problem} says why. */
    private boolean deviceMayRun() {
        java.util.function.Supplier<DeviceGuard.Decision> g = guard;
        DeviceGuard.Decision d = g == null ? null : g.get();
        if (d != null && d.paused()) {
            problem = d.why();
            return false;
        }
        return true;
    }

    /** The most threads the phone's heat allows now (two when it is cool). */
    private int threadsAllowed(int asked) {
        java.util.function.Supplier<DeviceGuard.Decision> g = guard;
        DeviceGuard.Decision d = g == null ? null : g.get();
        return d == null || d.maxThreads <= 0 ? asked : Math.min(asked, d.maxThreads);
    }

    /** The API is chosen but the device is offline right now: nothing answers, and the user hears why. */
    private boolean apiUnreachable() {
        if (apiChosen() && !remoteOn()) {
            problem = "no internet connection, so the API you picked couldn't be reached";
            return true;
        }
        return false;
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
    public boolean context(int tokens) {
        // never takes the model's lock: it is called on the main thread, and a model being loaded (seconds, or much
        // longer for a big one) holds the lock; the change is applied by the next load(), which frees and reloads
        int t = Math.max(LOCAL_WINDOW, tokens);
        if (t == contextTokens) {
            return false; // a smaller cache than this ceiling is on purpose; a long question grows it
        }
        contextTokens = t;
        advisedFor = null;
        return handle != 0;
    }

    /** The memory asked for when the loaded model was loaded (it may have settled for less), and its threads. */
    private volatile int askedAtLoad, loadedThreads;

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
        StringBuilder b = new StringBuilder(spec.weights + " is running slowly on this device (" + speed + " tokens a second). For faster replies:");
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

    /** Loads the installed model into memory (blocking, a few seconds). A short cache is loaded; a long question grows it. */
    public synchronized boolean load(int threads) {
        int light = PromptFit.localContext(0, contextTokens);
        // a bigger memory already loaded is kept: shrinking it reloaded the model and threw away everything read
        if (state == State.READY && handle != 0 && loadedThreads == threads && loadedContext >= light && loadedContext <= contextTokens) {
            return true;
        }
        return loadSized(threads, light);
    }

    /** Loads {@code tokens} of cache, never above the user's ceiling and never below {@link #LOCAL_WINDOW}. */
    private synchronized boolean loadSized(int threads, int tokens) {
        // Two threads is the cap. More pins the slow cores, the phone hums, and the answer waits on the slowest one.
        // A warm phone gets one (DeviceGuard); the check below then keeps a model already loaded that way.
        threads = Math.max(1, Math.min(2, threadsAllowed(threads)));
        int want = Math.max(LOCAL_WINDOW, Math.min(contextTokens, tokens));
        if (state == State.READY && handle != 0 && loadedThreads == threads && loadedContext == want) {
            return true;
        }
        if (handle != 0) {
            NativeLlm.nFree(handle);
            handle = 0;
            loadedContext = 0;
            state = State.INSTALLED;
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
        // the model's own limit and the device's free memory, read before loading: never ask past what the model
        // was trained for, and step down before the load instead of failing it
        ai.ibiko.bridge.Probe probe = ai.ibiko.engine.GgufProbe.of(file);
        if (probe.contextLength > 0) {
            long free = ai.ibiko.engine.GgufProbe.freeBytes();
            ai.ibiko.engine.EngineConfig fit = ai.ibiko.engine.EngineContext.decide(probe, null, file.length(), free,
                    threads, false);
            if (fit.nCtx > 0 && fit.nCtx < want) {
                int was = want;
                want = fit.nCtx;
                if (fit.reason.contains("free memory")) {
                    memoryNote = "There wasn't enough free memory for " + String.format(java.util.Locale.US, "%,d", was)
                            + " tokens, so " + spec.weights + " runs with " + String.format(java.util.Locale.US, "%,d", want)
                            + " (Settings › Advanced › Offline model memory).";
                }
            }
        }
        askedAtLoad = contextTokens;
        loadedThreads = threads;
        raisePriority();
        handle = NativeLlm.nLoad(file.getPath(), want, threads);
        if (handle == 0 && want > LOCAL_WINDOW) {
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
        adapterNote = layAdapter(file, handle);
        return true;
    }

    /** What the last load said about the trained adapter (empty when there was none). */
    public volatile String adapterNote = "";

    /**
     * The trained adapter kept beside a model ({@code <model>.adapter.gguf}, see {@link Adapters}): laid over the
     * frozen 4-bit weights when the model loads, so what Ibiko was trained on from its own knowledge is in every
     * reply. One that doesn't fit this model is set aside ({@code .rejected}) and the model runs as it was.
     */
    static String layAdapter(File model, long handle) {
        File a = Adapters.of(model);
        if (!a.isFile()) {
            return "";
        }
        if (NativeLlm.adapterIfPossible(handle, a.getPath(), Adapters.STRENGTH)) {
            return "trained adapter " + a.getName() + " in use";
        }
        a.renameTo(new File(a.getPath() + ".rejected"));
        return "the trained adapter " + a.getName() + " didn't fit this model and was set aside";
    }

    /**
     * Frees the on-device weights when nothing is being written. The file stays on the phone and is loaded again
     * for the next question. Called when the app leaves the screen, so an idle chat does not keep the cache.
     */
    public void releaseIfIdle() {
        if (inCall) {
            return;
        }
        synchronized (this) {
            if (inCall || state == State.LOADING) {
                return;
            }
            if (handle == 0) {
                return;
            }
            NativeLlm.nCancel(handle);
            NativeLlm.nFree(handle);
            handle = 0;
            loadedContext = 0;
            if (state == State.READY) {
                state = State.INSTALLED;
            }
        }
    }

    /**
     * Answers as a persona: its system prompt, the recent conversation and the new question; the reply streams to
     * {@code sink} as it is written. Returns the whole reply, or null when the model isn't ready or failed.
     */
    public String reply(String system, String question, int maxTokens, NativeLlm.Sink sink) {
        return reply(system, question, null, maxTokens, sink);
    }

    /**
     * Whether a call made at {@code asked} (the epoch then) was cancelled while it waited for the model: a call waits
     * here while the one before it finishes, and a cancel in that time must stop it too, before it reads a long
     * prompt nobody wants any more (the engine's own stop flag is reset when a reply starts, so it can't see it).
     */
    private boolean cancelledWhileWaiting(int asked) {
        if (asked != epoch.get()) {
            problem = "stopped";
            return true;
        }
        return false;
    }

    /**
     * As {@link #reply(String, String, int, NativeLlm.Sink)}, with {@code context} (taught facts, a fresh reference)
     * given alongside this question only: it is not remembered as part of the conversation. If the conversation
     * has grown too long for the model's memory, the oldest exchanges are dropped and it tries again.
     */
    public String reply(String system, String question, String context, int maxTokens, NativeLlm.Sink sink) {
        final int asked = epoch.get();
        synchronized (this) {
            return cancelledWhileWaiting(asked) ? null : replyNow(system, question, context, maxTokens, sink);
        }
    }

    private synchronized String replyNow(String system, String question, String context, int maxTokens, NativeLlm.Sink sink) {
        String asked = context == null ? question : context + "\n\nThe user says: " + question;
        if (remoteOn()) {
            List<String[]> turns = new ArrayList<String[]>(history);
            turns.add(new String[]{"user", asked});
            // the everyday length (160 tokens) is sized for the phone's model: an API model gets room for a real answer,
            // or its replies are cut off mid-thought (it still writes short when short is right)
            int room = Math.max(maxTokens, REMOTE_REPLY_MIN);
            String answer = askRemote(system, turns, room, 0.7f, sink, TaskSize.ofCall(question, maxTokens, coding));
            if (answer != null) {
                remember(question, ActionTags.strip(answer));
                return answer.trim();
            }
            // the API failed: the model the user chose is the one that answers, so the device's own model doesn't
            // quietly take over (problem says why the API didn't answer)
            return null;
        }
        if (apiUnreachable() && !answerOffline()) {
            return null;
        }
        if (!deviceMayRun() || !ensureFitted(system, asked)) {
            dropOfflineNote(); // nothing answered: the next reply isn't labelled as the phone's
            return null;
        }
        final String apiProblem = remoteOn() ? problem : null; // why the API didn't answer, kept for the user
        final int room = PromptFit.localReplyTokens(localWindow(), maxTokens, false);
        asked = fit(asked, PromptFit.localPromptChars(localWindow(), room, system == null ? 0 : system.length()));
        ai.ibiko.learn.LearningLog l = learning;
        // an everyday message goes the speed test's way: the persona (already read), the chat, the question; the
        // learned examples are read only for real work, where they help more than the seconds they cost
        String learned = l == null || quick(question, maxTokens) ? "" : l.examplesFor(question, 1, 700);
        if (!learned.isEmpty()) {
            step("Recalled similar answers I learned before");
            asked = learned + "\n\n" + (context == null ? "" : context + "\n\n") + "The user says: " + question;
        }
        final int ticket = epoch.get();
        raisePriority();
        for (int attempt = 0; attempt < FIT_TRIES + 2; attempt++) {
            List<String[]> turns = new ArrayList<String[]>(history);
            turns.add(new String[]{"user", asked});
            String[] roles = new String[turns.size()], texts = new String[turns.size()];
            for (int i = 0; i < turns.size(); i++) {
                roles[i] = turns.get(i)[0];
                texts[i] = ai.ibiko.chat.Attachments.forDevice(turns.get(i)[1]); // pixels stay out; a file's words stay in
            }
            final int[] pieces = {0};
            final long began = System.currentTimeMillis();
            // the phone's small model may write the user's next turn itself and loop: stopped and cut where that starts
            String answer = FakeTurn.cut(withTimeout(counting(FakeTurn.guard(sink), pieces),
                    wrapped -> NativeLlm.nReply(handle, spec.prompt(deviceSystem(system)), roles, texts, room, 0.7f, wrapped)));
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
                    : apiProblem + "; the device's own model couldn't take over" + (local == null || local.isEmpty() ? "" : " (" + local + ")");
            if (local == null || !local.contains("too long") || pieces[0] > 0) {
                return null; // a reply that already started is not "too long"; that error can be left over
            }
            if (history.isEmpty()) {
                if (asked.length() <= 400) {
                    return null; // as short as it can be
                }
                asked = fit(asked, asked.length() * 55 / 100); // the message itself, cut down further
                continue;
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
     * A task for an API model only, without the phone model's lock, so several can run side by side (mini agents
     * working in parallel online). Null when no API is on or it failed; the caller falls back to {@link #task}.
     */
    public String remoteTask(String system, String prompt, int maxTokens) {
        return remoteTask(system, prompt, maxTokens, null);
    }

    /** As {@link #remoteTask(String, String, int)}, but the caller picks the fast or the strong model. */
    public String remoteTask(String system, String prompt, int maxTokens, TaskSize.Tier tier) {
        if (!remoteOn()) {
            return null;
        }
        List<String[]> turns = new ArrayList<String[]>();
        turns.add(new String[]{"user", prompt});
        TaskSize.Tier use = tier != null ? tier : TaskSize.ofCall(prompt, maxTokens, coding);
        String answer = askRemote(system, turns, maxTokens, 0.35f, null, use);
        return answer == null ? null : answer.trim();
    }

    public String task(String system, String prompt, int maxTokens, NativeLlm.Sink sink) {
        return task(system, prompt, maxTokens, sink, null);
    }

    /** As {@link #task(String, String, int, NativeLlm.Sink)}. {@code tier} null lets the words decide the model. */
    public String task(String system, String prompt, int maxTokens, NativeLlm.Sink sink, TaskSize.Tier tier) {
        final int asked = epoch.get();
        synchronized (this) {
            return cancelledWhileWaiting(asked) ? null : taskNow(system, prompt, maxTokens, sink, tier);
        }
    }

    /**
     * Whether the device's own model may reason in a hidden block before it answers. Off: a thinking model (Qwen3)
     * on the device spent its whole allowance (up to thousands of tokens, minutes on a small model) thinking with
     * nothing on screen, then was asked again. Deep thinking (the Think pill) plans and checks in its own passes.
     */
    public volatile boolean localThinking = false;

    /** The instructions as the device's model gets them: no hidden reasoning block unless {@link #localThinking}. */
    /** An everyday message for the device's model: short, not code, a short reply. */
    public boolean quick(String question, int maxTokens) {
        return TaskSize.ofCall(question, maxTokens, coding) == TaskSize.Tier.FAST;
    }

    String deviceSystem(String system) {
        String s = system == null ? "" : system;
        return spec.thinks && !localThinking && !s.contains("/no_think") ? s + " /no_think" : s;
    }

    private synchronized String taskNow(String system, String prompt, int maxTokens, NativeLlm.Sink sink, TaskSize.Tier tier) {
        if (remoteOn()) {
            List<String[]> turns = new ArrayList<String[]>();
            turns.add(new String[]{"user", prompt});
            String answer = askRemote(system, turns, maxTokens, 0.35f, sink,
                    tier != null ? tier : TaskSize.ofCall(prompt, maxTokens, coding));
            // the chosen model does the task (a design's measurements, a plan, a file) or says why it couldn't:
            // the device's own model never takes over behind the user's back
            return answer == null ? null : answer.trim();
        }
        if (apiUnreachable() && !answerOffline()) {
            return null;
        }
        if (!deviceMayRun() || !ensureFitted(system, prompt)) {
            dropOfflineNote(); // nothing answered: the next reply isn't labelled as the phone's
            return null;
        }
        final String apiProblem = remoteOn() ? problem : null; // why the API didn't answer, kept for the user
        // the phone's own model takes over with what fits its memory: the start of the task and the latest work
        // characters per token is only an estimate (code and paths take more tokens): when the model says it is
        // still too long, the same work is cut down further and tried again, never just refused
        boolean report = maxTokens >= 8000;
        int reply = PromptFit.localReplyTokens(localWindow(), maxTokens, report);
        int budget = PromptFit.localPromptChars(localWindow(), reply, system == null ? 0 : system.length());
        String local = null;
        raisePriority();
        String sys = deviceSystem(system);
        boolean thoughtOut = false;
        for (int attempt = 0; attempt < FIT_TRIES + 1; attempt++) {
            final String fitted = fit(prompt, budget);
            final int room = reply;
            final String asSystem = sys;
            final int[] pieces = {0};
            final long began = System.currentTimeMillis();
            String answer = withTimeout(counting(FakeTurn.guard(sink), pieces), wrapped -> NativeLlm.nReply(handle, spec.prompt(asSystem), new String[]{"user"},
                    new String[]{ai.ibiko.chat.Attachments.forDevice(fitted)}, room, 0.35f, wrapped));
            answer = FakeTurn.cut(answer);
            recordLocal(system, java.util.Collections.<String[]>emptyList(), fitted, answer, pieces[0], began);
            if (answer != null && !answer.trim().isEmpty()) {
                return answer.trim();
            }
            local = NativeLlm.nError(handle);
            if ((local == null || local.isEmpty()) && spec.thinks && !thoughtOut && !sys.contains("/no_think") && !"stopped".equals(problem)) {
                // nothing written and no error: a thinking model (Qwen3) spent its whole allowance thinking. Once
                // more, answering straight away
                thoughtOut = true;
                sys = sys + " /no_think";
                continue;
            }
            if (local == null || !local.contains("too long") || pieces[0] > 0) {
                break; // it wrote tokens: the memory error is from an earlier try, not this one
            }
            budget = budget * 55 / 100;
            reply = Math.max(128, reply * 3 / 4);
        }
        if (local != null && local.contains("too long")) {
            local = "even cut down, the work is too much for " + spec.weights + "'s memory of " + String.format(java.util.Locale.US, "%,d",
                    localWindow()) + " tokens: a bigger offline model, more memory (Settings \u203A Advanced), or an API would fit it";
        }
        problem = apiProblem == null ? local
                : apiProblem + "; the device's own model couldn't take over" + (local == null || local.isEmpty() ? "" : " (" + local + ")");
        return null;
    }

    /**
     * Runs one reply under a watch. Waiting for the first token is not a failure: reading a long prompt on this phone
     * can take longer than ten minutes. After the first token, silence of {@link #IDLE_MS} or the write cap stops it.
     * A token resets the quiet timer. The reason it stopped is kept in {@link #problem}.
     */
    private String withTimeout(NativeLlm.Sink sink, java.util.function.Function<NativeLlm.Sink, String> call) {
        return withTimeout(sink, call, false);
    }

    /** How long an API reply may go quiet between words before it counts as stuck. */
    static final long REMOTE_IDLE_MS = 120_000;

    /**
     * {@code remote}: an API reply has no cap on writing. A large model writing a long answer took more than three
     * minutes and was cut off while it was still writing; only silence ({@link #REMOTE_IDLE_MS}) or Stop ends it.
     */
    private String withTimeout(NativeLlm.Sink sink, java.util.function.Function<NativeLlm.Sink, String> call,
            boolean remote) {
        final int mine = replies.open();
        // silence between words, and a cap on writing. Reading the prompt has no clock: that wait is the work.
        long idle = remote ? Math.max(REMOTE_IDLE_MS, watchIdleMs) : watchIdleMs > 0 ? watchIdleMs : IDLE_MS;
        long cap = remote ? Long.MAX_VALUE / 4 : watchCapMs > 0 ? watchCapMs : 180_000;
        long read = watchReadMs > 0 ? watchReadMs : READ_MS;
        final ReplyWatch watch = new ReplyWatch(idle, cap, read, System.currentTimeMillis());
        final int ticket = epoch.get();
        callTicket = ticket;
        inCall = true;
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
        }, "ibiko-model-timeout");
        watchdog.setDaemon(true);
        watchdog.start();
        try {
            return call.apply(wrapped);
        } catch (RuntimeException e) {
            return null;
        } finally {
            inCall = false;
            watch.finish();
            watchdog.interrupt();
        }
    }

    /**
     * Loads the phone's model if it is not loaded, and at a cache size that fits this text. A short question does
     * not keep an 8,192-token cache. A long one grows, up to the user's ceiling.
     */
    private boolean ensureFitted(String system, String text) {
        int chars = (system == null ? 0 : system.length()) + (text == null ? 0 : text.length());
        for (String[] t : history) {
            chars += t[1] == null ? 0 : t[1].length();
        }
        int want = PromptFit.localContext(chars, contextTokens);
        // big enough is enough: a smaller question does not reload a smaller memory (that threw away what was read)
        if (state == State.READY && handle != 0 && loadedContext >= want && loadedContext <= Math.max(want, contextTokens)) {
            return true;
        }
        if (!installed() && handle == 0) {
            return false;
        }
        return loadSized(loadedThreads > 0 ? loadedThreads : 4, want);
    }

    /**
     * Drops the on-device weights while an API answers. The file stays on the phone and is loaded again the next
     * time the phone's own model is asked to run. Safe to call when nothing is loaded.
     */
    private void parkNative() {
        synchronized (this) {
            if (handle == 0) {
                return;
            }
            NativeLlm.nCancel(handle);
            NativeLlm.nFree(handle);
            handle = 0;
            loadedContext = 0;
            if (state == State.READY) {
                state = State.INSTALLED;
            }
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
        parkNative(); // the on-device weights are not used for an API reply; leaving them loaded swapped the phone out
        for (Object[] attempt : tries) {
            final Slot sl = (Slot) attempt[0];
            final RemoteBrain r = (RemoteBrain) attempt[1];
            current = r;
            // a quick message trims the chat only on a small window; a big model reads it all (and from its cache)
            final boolean brief = tier == TaskSize.Tier.FAST && sl.window > 0 && sl.window < 32_000;
            r.effortFor(tier);
            final List<String[]> fitted = PromptFit.remoteTurns(turns, sl.window, maxTokens, brief);
            // no "Asking <provider>" step: it repeated between every real step (the model shows in the header)
            final int ticket = epoch.get();
            final int[] pieces = {0};
            final long began = System.currentTimeMillis();
            problem = null;
            String answer = withTimeout(counting(sink, pieces), wrapped -> r.chat(system, fitted, maxTokens, temperature, wrapped),
                    true);
            boolean timedOut = problem != null && problem.startsWith("the reply");
            if (ticket != epoch.get() && !timedOut) {
                problem = "stopped"; // the user stopped it: nothing more is asked
                return null;
            }
            if (answer != null && !answer.trim().isEmpty() && ticket == epoch.get()) {
                if (failedName != null && !failedName.equals(sl.name)) {
                    switchNote = failedName + " didn't answer (" + firstFailure + "), so " + sl.name + " did.";
                }
                record(sl, r, system, fitted, answer, pieces[0], began);
                ai.ibiko.learn.LearningLog l = learning;
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
            if (!credit) {
                step(sl.name + " didn't answer (" + why + ")"); // a credit error is said once, in the reply, not also as a step
            }
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
     * Where Ibiko keeps what the API models answer, to learn from (null: not learning). When the phone's own model
     * answers, the closest past answers are given to it as examples.
     */
    public volatile ai.ibiko.learn.LearningLog learning;

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
    private NativeLlm.Sink counting(NativeLlm.Sink sink, int[] count) {
        return text -> {
            if (count[0]++ == 0) {
                firstPieceAt = System.currentTimeMillis();
            }
            return sink == null || sink.onText(text);
        };
    }

    /** When the last local reply wrote its first piece; and how long it read before that, with how much it read. */
    private volatile long firstPieceAt;
    public volatile long lastReadMs;
    public volatile int lastReadTokens;

    private void recordLocal(String system, List<String[]> turns, String message, String answer, int pieces, long began) {
        long now = System.currentTimeMillis();
        int read = UsageLog.estimate(system) + UsageLog.estimate(message);
        for (String[] t : turns) {
            read += UsageLog.estimate(t[1]);
        }
        if (firstPieceAt >= began) {
            lastReadMs = firstPieceAt - began;
            lastReadTokens = read;
            // said in the steps, so a slow start shows where the time went
            step(String.format(java.util.Locale.US, "Read about %,d tokens, first word after %.0f s", read, lastReadMs / 1000.0));
        }
        if (answer == null || answer.trim().isEmpty()) {
            usage.failure(now);
            return;
        }
        int conversation = 0;
        for (String[] t : turns) {
            conversation += UsageLog.estimate(t[1]);
        }
        usage.record(new UsageLog.Call("On this device: " + spec.weights, localWindow(), UsageLog.estimate(system), conversation,
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

    /**
     * Reads instructions into the phone model's memory ahead of time (after loading, or when the persona changes), so
     * the first question doesn't wait for a thousand tokens of instructions to be read. Nothing when an API answers or
     * the model isn't loaded.
     */
    public boolean warm(String system) {
        final int asked = epoch.get();
        synchronized (this) {
            return !cancelledWhileWaiting(asked) && warmNow(system);
        }
    }

    /**
     * Gets the device's model ready before the question comes, as the voice makes a reply's audio while it is read:
     * loaded if it was let go (the app was away), then its instructions read into memory. Started when the user
     * begins to type, so by Send only the new words are left to read. Nothing when an API answers.
     */
    public boolean prepare(String system, int threads) {
        final int asked = epoch.get();
        synchronized (this) {
            if (cancelledWhileWaiting(asked) || remoteOn() || system == null) {
                return false;
            }
            if (state != State.READY && !(installed() && load(threads))) {
                return false;
            }
            return warmNow(system);
        }
    }

    private synchronized boolean warmNow(String system) {
        if (state != State.READY || handle == 0 || system == null) {
            return false;
        }
        return NativeLlm.warmIfPossible(handle, spec.prompt(deviceSystem(system)));
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
        history.add(new String[]{"user", ai.ibiko.chat.Attachments.forMemory(question)});
        // the phone's small model keeps a short memory; an API model keeps the whole reply (a report stays a report)
        history.add(new String[]{"assistant", remoteOn() ? fit(answer, 8_000) : clipMemory(answer)});
        if (remoteOn()) {
            // an API model keeps far more of the chat, and the oldest part is dropped a block at a time: the start of
            // the conversation then stays the same between messages, so the provider's prompt cache keeps working
            // (dropping one exchange per message changed the start every time and the whole chat was read again)
            if (history.size() > REMOTE_HISTORY_TURNS * 2) {
                int drop = Math.min(history.size() - 2, REMOTE_DROP_TURNS * 2);
                history.subList(0, drop).clear();
            }
        } else {
            while (history.size() > HISTORY_TURNS * 2) {
                history.remove(0);
            }
        }
    }

    /** How full the working memory was on the last reply, 0..1 (0 before any reply). */
    public float workload() {
        UsageLog.Call c = usage.last();
        return c == null || c.window <= 0 ? 0f : Math.min(1f, c.used() / (float) c.window);
    }

    /** The conversation the model remembers, as plain lines (for a summary). */
    public synchronized String historyText() {
        StringBuilder b = new StringBuilder();
        for (String[] t : history) {
            b.append("user".equals(t[0]) ? "User: " : "Assistant: ").append(t[1]).append('\n');
        }
        return b.toString();
    }

    /** Clears the remembered conversation down to a summary of it (the working memory was nearly full). */
    public synchronized void compact(String summary) {
        history.clear();
        if (summary != null && !summary.trim().isEmpty()) {
            history.add(new String[]{"user", "What have we talked about so far?"});
            history.add(new String[]{"assistant", summary.trim()});
        }
    }

    /**
     * The user edited an earlier message: the conversation the model remembers goes back to just before it (that
     * question and every exchange after it are dropped), so the edited message is answered as if the old one had
     * never been asked. When the message isn't remembered (too old, or never sent to the model), nothing changes.
     */
    public synchronized void rewindTo(String question) {
        String q = question == null ? "" : question.trim();
        String plain = ai.ibiko.chat.Attachments.withoutImages(q);
        String mem = ai.ibiko.chat.Attachments.forMemory(q);
        for (int i = history.size() - 1; i >= 0; i--) {
            if (!"user".equals(history.get(i)[0]) || history.get(i)[1] == null) {
                continue;
            }
            String stored = history.get(i)[1].trim();
            if (stored.equals(q) || stored.equals(plain == null ? "" : plain.trim()) || stored.equals(mem == null ? "" : mem.trim())) {
                while (history.size() > i) {
                    history.remove(history.size() - 1);
                }
                return;
            }
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

    /** One speed test of the phone's own model: how long it took to start, to say its first word, and to write. */
    public static final class Speed {
        public final String weights;
        public final int window, pieces;
        public final long loadMs, firstMs, totalMs;

        Speed(String weights, int window, long loadMs, long firstMs, long totalMs, int pieces) {
            this.weights = weights;
            this.window = window;
            this.loadMs = loadMs;
            this.firstMs = firstMs;
            this.totalMs = totalMs;
            this.pieces = pieces;
        }

        /** Tokens written a second, once writing began (the reading of the question left out). */
        public float writing() {
            long ms = totalMs - firstMs;
            return pieces <= 1 || ms <= 0 ? 0f : (pieces - 1) * 1000f / ms;
        }

        /** In words: "Gemma 3 1B: first word after 1.2 s, then 8.4 tokens a second (96 in 12.5 s); 8,192 tokens of memory." */
        public String words() {
            return String.format(java.util.Locale.US, "%s: %sfirst word after %.1f s, then %.1f tokens a second (%d in %.1f s); %,d tokens of memory.",
                    weights, loadMs > 0 ? String.format(java.util.Locale.US, "started in %.1f s, ", loadMs / 1000f) : "",
                    firstMs / 1000f, writing(), pieces, totalMs / 1000f, window);
        }
    }

    /** The question the speed test asks: the same every time, so results can be compared. */
    static final String SPEED_PROMPT = "Describe a busy market in Lagos in about eighty words.";

    /**
     * Measures the phone's own model (never an API): loads it if needed, then writes up to {@code tokens} tokens for
     * the same question. Null when it can't run (not on the phone, or it failed).
     */
    public synchronized Speed speedTest(int threads, int tokens) {
        long t0 = System.currentTimeMillis();
        boolean wasLoaded = state == State.READY;
        if (!load(threads)) {
            return null;
        }
        long loadMs = wasLoaded ? 0 : System.currentTimeMillis() - t0;
        Speed s = measure(handle, spec, localWindow(), loadMs, tokens);
        if (s == null) {
            problem = NativeLlm.nError(handle);
            return null;
        }
        noteSpeed(s.pieces, s.totalMs);
        return s;
    }

    /**
     * The same test for any model on the device, in use or not. Another model is loaded on its own for the test and
     * let go after; the one in use leaves memory meanwhile (two at once would not fit) and loads again for the next
     * question. Null when it can't run.
     */
    public synchronized Speed speedTest(ModelSpec m, int threads, int tokens) {
        if (m == null || m.file.equals(spec.file)) {
            return speedTest(threads, tokens);
        }
        if (!has(m)) {
            problem = m.weights + " isn't downloaded";
            return null;
        }
        if (!NativeLlm.available()) {
            problem = "its engine isn't in this build";
            return null;
        }
        if (handle != 0) {
            NativeLlm.nFree(handle);
            handle = 0;
            loadedContext = 0;
            if (state == State.READY) {
                state = State.INSTALLED;
            }
        }
        threads = Math.max(1, Math.min(2, threads));
        long t0 = System.currentTimeMillis();
        long other = NativeLlm.nLoad(new File(dir, m.file).getPath(), LOCAL_WINDOW, threads);
        if (other == 0) {
            problem = m.weights + " couldn't be loaded (not enough memory, or a damaged file)";
            return null;
        }
        long loadMs = System.currentTimeMillis() - t0;
        try {
            Speed s = measure(other, m, LOCAL_WINDOW, loadMs, tokens);
            if (s == null) {
                problem = NativeLlm.nError(other);
            }
            return s;
        } finally {
            NativeLlm.nFree(other);
        }
    }

    /** The speed test's question on a loaded model: when the first word came, and how many followed. */
    private static Speed measure(long h, ModelSpec m, int window, long loadMs, int tokens) {
        final long[] first = {-1};
        final int[] pieces = {0};
        final long start = System.currentTimeMillis();
        String out = NativeLlm.nReply(h, m.prompt("You are a helpful assistant. /no_think"), new String[]{"user"},
                new String[]{SPEED_PROMPT}, tokens, 0.7f, text -> {
                    if (first[0] < 0) {
                        first[0] = System.currentTimeMillis() - start;
                    }
                    pieces[0]++;
                    return true;
                });
        long total = System.currentTimeMillis() - start;
        if (out == null || pieces[0] == 0) {
            return null;
        }
        return new Speed(m.weights, window, loadMs, Math.max(0, first[0]), total, pieces[0]);
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

    /** How many threads to answer with: two fast cores. More than that makes the phone hum and does not go faster. */
    public static int threads(int cores) {
        int fast = fastCores(clusters());
        return fast > 0 ? Math.max(1, Math.min(2, fast)) : Math.max(1, Math.min(2, Math.max(1, cores / 2)));
    }

    /**
     * How many of the phone's fast cores to use, from each core's top speed (kHz). A phone has clusters: on a Tecno
     * with 2 fast and 6 slow cores, using 4 threads puts two on slow cores, and llama.cpp waits for the slowest, so
     * the whole model runs at their pace. The fastest clusters are taken until there are at least two cores (one
     * prime core plus the next cluster), at most four. 0 when the speeds can't be read.
     */
    static int fastCores(long[] maxKhz) {
        if (maxKhz == null || maxKhz.length == 0) {
            return 0;
        }
        java.util.TreeMap<Long, Integer> byspeed = new java.util.TreeMap<Long, Integer>(java.util.Collections.reverseOrder());
        for (long k : maxKhz) {
            if (k > 0) {
                byspeed.merge(k, 1, Integer::sum);
            }
        }
        int n = 0;
        for (int count : byspeed.values()) {
            n += count;
            if (n >= 2) {
                break;
            }
        }
        return Math.min(4, n);
    }

    /** Each core's top speed (kHz) from the system; empty when it can't be read (not a phone). */
    static long[] clusters() {
        java.util.List<Long> out = new java.util.ArrayList<Long>();
        for (int i = 0; i < 16; i++) {
            File f = new File("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
            if (!f.isFile()) {
                break;
            }
            try {
                out.add(Long.parseLong(new String(java.nio.file.Files.readAllBytes(f.toPath())).trim()));
            } catch (java.io.IOException | RuntimeException e) {
                return new long[0];
            }
        }
        long[] a = new long[out.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = out.get(i);
        }
        return a;
    }
}
