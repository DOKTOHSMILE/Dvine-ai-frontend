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

    public final ModelSpec spec;
    private final File file;
    private volatile State state;
    private final java.util.concurrent.atomic.AtomicInteger epoch = new java.util.concurrent.atomic.AtomicInteger();
    private volatile String problem;
    private long handle;
    private final List<String[]> history = new ArrayList<String[]>();
    private static final int HISTORY_TURNS = 6;
    private final ReplyLease replies = new ReplyLease();
    /** What each reply used, for the Usage screen. */
    public final UsageLog usage = new UsageLog();
    /** The context window the phone's own brain is loaded with (tokens). */
    public static final int LOCAL_WINDOW = 2048;
    /** A brain the user brought (an API), used instead of the phone's own when it is set and the phone is online. */
    private volatile RemoteBrain remote;
    private volatile int remoteWindow = 32_768;
    private volatile java.util.function.BooleanSupplier online = () -> true;

    /** Keeps a remembered answer short so the next reply is not rereading a long one. */
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
        this.remote = brain;
        this.remoteWindow = window > 0 ? window : 32_768;
        if (online != null) {
            this.online = online;
        }
    }

    public RemoteBrain remote() {
        return remote;
    }

    /** The user's API will answer the next question (it is set, and the phone is online). */
    public boolean remoteOn() {
        RemoteBrain r = remote;
        return r != null && online.getAsBoolean();
    }

    /** The context window of the brain that will answer next. */
    public int window() {
        return remoteOn() ? remoteWindow : LOCAL_WINDOW;
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
        handle = NativeLlm.nLoad(file.getPath(), 2048, threads);
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
            String answer = askRemote(system, turns, maxTokens, 0.7f, sink);
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
        final int ticket = epoch.get();
        for (int attempt = 0; attempt < 3; attempt++) {
            List<String[]> turns = new ArrayList<String[]>(history);
            turns.add(new String[]{"user", asked});
            String[] roles = new String[turns.size()], texts = new String[turns.size()];
            for (int i = 0; i < turns.size(); i++) {
                roles[i] = turns.get(i)[0];
                texts[i] = turns.get(i)[1];
            }
            final int[] pieces = {0};
            final long began = System.currentTimeMillis();
            String answer = withTimeout(counting(sink, pieces), wrapped -> NativeLlm.nReply(handle, system, roles, texts, maxTokens, 0.7f, wrapped));
            if (ticket != epoch.get()) {
                return null;
            }
            recordLocal(system, history, asked, answer, pieces[0], began);
            if (answer != null && !answer.trim().isEmpty()) {
                remember(question, ActionTags.strip(answer));
                return answer.trim();
            }
            problem = NativeLlm.nError(handle);
            if (problem == null || !problem.contains("too long") || history.isEmpty()) {
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
            String answer = askRemote(system, turns, maxTokens, 0.35f, sink);
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
        final int[] pieces = {0};
        final long began = System.currentTimeMillis();
        String answer = withTimeout(counting(sink, pieces), wrapped -> NativeLlm.nReply(handle, system, new String[]{"user"}, new String[]{prompt}, maxTokens, 0.35f, wrapped));
        recordLocal(system, java.util.Collections.<String[]>emptyList(), prompt, answer, pieces[0], began);
        if (answer == null || answer.trim().isEmpty()) {
            problem = NativeLlm.nError(handle);
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

    /** The user's API answers: same limits and cancelling as the phone's brain; what it used is recorded. */
    private String askRemote(String system, List<String[]> turns, int maxTokens, float temperature, NativeLlm.Sink sink) {
        final RemoteBrain r = remote;
        if (r == null) {
            return null;
        }
        final int ticket = epoch.get();
        final int[] pieces = {0};
        final long began = System.currentTimeMillis();
        String answer = withTimeout(counting(sink, pieces), wrapped -> r.chat(system, turns, maxTokens, temperature, wrapped));
        if (ticket != epoch.get()) {
            problem = "stopped";
            return null;
        }
        if (answer == null || answer.trim().isEmpty()) {
            String why = r.problem();
            problem = why == null ? "the API sent nothing back" : why;
            usage.failure(System.currentTimeMillis());
            return null;
        }
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
        int out = u != null && u.outputTokens >= 0 ? u.outputTokens : Math.max(pieces[0], UsageLog.estimate(answer));
        usage.record(new UsageLog.Call("API: " + r.model + " at " + r.host(), remoteWindow, sys, conversation, msg, out, measured,
                System.currentTimeMillis() - began, System.currentTimeMillis()));
        return answer;
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
        usage.record(new UsageLog.Call("On this phone: " + ModelSpec.WEIGHTS, LOCAL_WINDOW, UsageLog.estimate(system), conversation,
                UsageLog.estimate(message), Math.max(pieces, 1), false, now - began, now));
    }

    /** Stops a reply being written (from any thread). */
    public void cancel() {
        RemoteBrain r = remote;
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
        history.add(new String[]{"user", question});
        history.add(new String[]{"assistant", clipMemory(answer)});
        while (history.size() > HISTORY_TURNS * 2) {
            history.remove(0);
        }
    }

    synchronized int historySize() {
        return history.size();
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
