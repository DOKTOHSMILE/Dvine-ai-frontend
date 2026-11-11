package ai.ibiko.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * The workers: who is working right now, each with a code name (FALCON-1, LYNX-2…): the reply being written, the lead
 * agent on a task and its helpers, the council of three, the fact checker, the memory keeper, Ibiko 1's researcher,
 * the night learner. Each says its role, the brain it uses, its task, what it is doing at this moment and since when;
 * finished ones are kept a while with how long they took and how they ended. The Workload sheet lists them. Pure
 * Java; thread-safe (workers start and end on their own threads).
 */
public final class AgentBoard {
    private AgentBoard() {
    }

    static final String[] NAMES = {"FALCON", "LYNX", "ORION", "ATLAS", "NOVA", "ECHO", "KESTREL", "RAVEN", "SABLE", "VEGA"};
    /** How many finished workers are kept. */
    static final int RECENT = 6;

    /** How a worker ended. */
    public enum Outcome { DONE, FAILED, STOPPED }

    /** One worker. */
    public static final class Entry {
        public final String code, role, task, brain;
        public final long since;
        /** What it is doing now ("Reading en.wikipedia.org"), or "". */
        public final String step;
        /** When it ended (0 while working), and how. */
        public final long ended;
        public final Outcome outcome;

        Entry(String code, String role, String task, String brain, long since, String step, long ended, Outcome outcome) {
            this.code = code;
            this.role = role;
            this.task = task;
            this.brain = brain == null ? "" : brain;
            this.since = since;
            this.step = step == null ? "" : step;
            this.ended = ended;
            this.outcome = outcome;
        }

        Entry withStep(String s) {
            return new Entry(code, role, task, brain, since, s, ended, outcome);
        }

        Entry finished(long at, Outcome how) {
            return new Entry(code, role, task, brain, since, step, at, how);
        }

        /** How long it has worked (or worked), in milliseconds. */
        public long took(long now) {
            return Math.max(0, (ended > 0 ? ended : now) - since);
        }
    }

    private static final List<Entry> NOW = new ArrayList<Entry>();
    private static final List<Entry> DONE = new ArrayList<Entry>();
    private static int count;

    /** A worker starts: its code name, to end it with. */
    public static String start(String role, String task) {
        return start(role, task, "");
    }

    /** A worker starts on {@code brain} (the model or Ibiko 1): its code name, to end it with. */
    public static synchronized String start(String role, String task, String brain) {
        count++;
        String code = NAMES[(count - 1) % NAMES.length] + "-" + count;
        String t = task == null ? "" : task.replaceAll("(?is)<attachment[^>]*>.*?(</attachment>|$)", " ").replaceAll("<[^>]+>", " ")
                .trim().replaceAll("\\s+", " ");
        NOW.add(new Entry(code, role, t.length() > 140 ? t.substring(0, 140) + "…" : t, brain, System.currentTimeMillis(), "", 0, null));
        return code;
    }

    /** What the worker is doing now. */
    public static synchronized void step(String code, String what) {
        for (int i = 0; i < NOW.size(); i++) {
            if (NOW.get(i).code.equals(code)) {
                String s = what == null ? "" : what.trim().replaceAll("\\s+", " ");
                NOW.set(i, NOW.get(i).withStep(s.length() > 100 ? s.substring(0, 100) + "…" : s));
            }
        }
    }

    public static void end(String code) {
        end(code, Outcome.DONE);
    }

    public static synchronized void end(String code, Outcome how) {
        for (int i = NOW.size() - 1; i >= 0; i--) {
            if (NOW.get(i).code.equals(code)) {
                DONE.add(0, NOW.remove(i).finished(System.currentTimeMillis(), how == null ? Outcome.DONE : how));
            }
        }
        while (DONE.size() > RECENT) {
            DONE.remove(DONE.size() - 1);
        }
    }

    /** The workers at work now, oldest first. */
    public static synchronized List<Entry> now() {
        return new ArrayList<Entry>(NOW);
    }

    /** The workers that finished lately, newest first. */
    public static synchronized List<Entry> recent() {
        return new ArrayList<Entry>(DONE);
    }

    /** For tests: an empty board. */
    static synchronized void clear() {
        NOW.clear();
        DONE.clear();
    }
}
