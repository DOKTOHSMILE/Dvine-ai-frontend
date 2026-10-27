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
    private volatile String problem;
    private long handle;
    private final List<String[]> history = new ArrayList<String[]>();
    private static final int HISTORY_TURNS = 6;

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

    public boolean ready() {
        return state == State.READY;
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
        if (state != State.READY) {
            return null;
        }
        String asked = context == null ? question : context + "\n\nThe user says: " + question;
        for (int attempt = 0; attempt < 3; attempt++) {
            List<String[]> turns = new ArrayList<String[]>(history);
            turns.add(new String[]{"user", asked});
            String[] roles = new String[turns.size()], texts = new String[turns.size()];
            for (int i = 0; i < turns.size(); i++) {
                roles[i] = turns.get(i)[0];
                texts[i] = turns.get(i)[1];
            }
            String answer = NativeLlm.nReply(handle, system, roles, texts, maxTokens, 0.7f, sink);
            if (answer != null && !answer.trim().isEmpty()) {
                remember(question, answer.trim());
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
        if (state != State.READY) {
            return null;
        }
        String answer = NativeLlm.nReply(handle, system, new String[]{"user"}, new String[]{prompt}, maxTokens, 0.35f, sink);
        if (answer == null || answer.trim().isEmpty()) {
            problem = NativeLlm.nError(handle);
            return null;
        }
        return answer.trim();
    }

    /** Stops a reply being written (from any thread). */
    public void cancel() {
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
        history.add(new String[]{"assistant", answer});
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
