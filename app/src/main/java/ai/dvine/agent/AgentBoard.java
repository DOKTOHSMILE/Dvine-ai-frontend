package ai.dvine.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * Who is working right now, each with a code name (FALCON-1, LYNX-2…): the lead agent on a task, its mini-agent
 * helpers, the council of three, the fact checker, the memory keeper. The Workload sheet lists them with what they
 * are on and for how long. Pure Java; thread-safe (agents start and end on their own threads).
 */
public final class AgentBoard {
    private AgentBoard() {
    }

    static final String[] NAMES = {"FALCON", "LYNX", "ORION", "ATLAS", "NOVA", "ECHO", "KESTREL", "RAVEN", "SABLE", "VEGA"};

    /** One agent at work. */
    public static final class Entry {
        public final String code, role, task;
        public final long since;

        Entry(String code, String role, String task, long since) {
            this.code = code;
            this.role = role;
            this.task = task;
            this.since = since;
        }
    }

    private static final List<Entry> NOW = new ArrayList<Entry>();
    private static int count;

    /** An agent starts: its code name, to end it with. */
    public static synchronized String start(String role, String task) {
        count++;
        String code = NAMES[(count - 1) % NAMES.length] + "-" + count;
        String t = task == null ? "" : task.trim().replaceAll("\\s+", " ");
        NOW.add(new Entry(code, role, t.length() > 90 ? t.substring(0, 90) + "…" : t, System.currentTimeMillis()));
        return code;
    }

    public static synchronized void end(String code) {
        for (int i = NOW.size() - 1; i >= 0; i--) {
            if (NOW.get(i).code.equals(code)) {
                NOW.remove(i);
            }
        }
    }

    /** The agents at work now, oldest first. */
    public static synchronized List<Entry> now() {
        return new ArrayList<Entry>(NOW);
    }
}
