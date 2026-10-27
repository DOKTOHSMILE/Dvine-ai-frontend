package ai.dvine.turn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Obi and Ivie on one question: each answers through its own agent (its own
 * session and what it has said so far), the second hears the first and gives
 * its own view, and the first closes with a summary that keeps agreement and
 * disagreement apart. Turn states are explicit; the exchange stops on a
 * repeat, when neither has more to say, at the turn limit, or on cancel.
 *
 * <p>Honest about what it is: the agents are whatever {@link Agent} is
 * plugged in. Offline that is the rules-and-retrieval brain, so a "debate"
 * is two retrieved answers compared, not free reasoning; online each persona
 * asks the server desk on its own. Nothing here invents a disagreement. Pure
 * Java; the caller runs {@link #discuss} off the main thread.
 */
public final class DualAgentCoordinator {
    public enum State {
        IDLE, QUESTION_RECEIVED, AGENT_A_THINKING, AGENT_A_SPEAKING, AGENT_B_ANALYZING, AGENT_B_SPEAKING,
        DEBATE_CONTINUING, CONSENSUS, FINAL_SUMMARY, CANCELLED, ERROR
    }

    public enum Mode {
        /** The second gives a second opinion on the first's answer. */
        SECOND_OPINION,
        /** As above, then each may answer the other once more before the summary. */
        DEBATE,
        /** They explain together: the second adds to the first. */
        JOINT
    }

    /** One persona's answer. */
    public static final class Reply {
        public final List<String> say;
        /** False when the agent had no real answer (an apology, "I don't know"). */
        public final boolean known;

        public Reply(List<String> say, boolean known) {
            this.say = say;
            this.known = known;
        }

        String text() {
            StringBuilder b = new StringBuilder();
            for (String s : say) {
                if (b.length() > 0) {
                    b.append(' ');
                }
                b.append(s);
            }
            return b.toString();
        }
    }

    /** One persona's reasoning session. {@code heard} is the discussion so far (both voices). */
    public interface Agent {
        Reply answer(String question, List<TurnLine> heard) throws Exception;
    }

    /** Told about each state change (for the stage: who thinks, who speaks). */
    public interface Listener {
        void state(State s, String persona);
    }

    /** A request to discuss, parsed from what the user said. */
    public static final class Request {
        public final Mode mode;
        public final String question;
        /** "obi", "ivie", or null for the persona on screen. */
        public final String first;

        Request(Mode mode, String question, String first) {
            this.mode = mode;
            this.question = question;
            this.first = first;
        }
    }

    /** Turns at most (spoken lines from either persona, the summary included). */
    public static final int MAX_TURNS = 6;
    /** Word overlap above which two answers say the same thing. */
    static final float SAME = 0.6f;

    private final Agent obi, ivie;
    private volatile boolean cancelled;
    private volatile State state = State.IDLE;

    public DualAgentCoordinator(Agent obi, Agent ivie) {
        this.obi = obi;
        this.ivie = ivie;
    }

    public State state() {
        return state;
    }

    /** Stops the discussion at the next turn boundary. */
    public void cancel() {
        cancelled = true;
    }

    /**
     * Runs a whole discussion and returns the lines to play, in order (THINK
     * lines with no text show thinking). Blocks while the agents answer.
     */
    public List<TurnLine> discuss(String question, String first, Mode mode, Listener listener) {
        cancelled = false;
        String a = "ivie".equals(first) ? "ivie" : "obi", b = a.equals("obi") ? "ivie" : "obi";
        Agent agentA = a.equals("obi") ? obi : ivie, agentB = a.equals("obi") ? ivie : obi;
        List<TurnLine> out = new ArrayList<TurnLine>();
        set(State.QUESTION_RECEIVED, a, listener);

        set(State.AGENT_A_THINKING, a, listener);
        Reply ra = ask(agentA, question, out);
        if (ra == null) {
            return fail(out, a, listener);
        }
        if (stop(out, listener)) {
            return out;
        }
        out.add(new TurnLine(a, "THINK", ""));
        set(State.AGENT_A_SPEAKING, a, listener);
        say(out, a, ra.say);

        set(State.AGENT_B_ANALYZING, b, listener);
        out.add(new TurnLine(b, "THINK", ""));
        // the second answers the question itself first, hearing the first's answer
        Reply rb = ask(agentB, question, out);
        if (rb == null) {
            return fail(out, b, listener);
        }
        if (stop(out, listener)) {
            return out;
        }
        set(State.AGENT_B_SPEAKING, b, listener);
        String nameA = name(a), nameB = name(b);
        boolean agree;
        if (!rb.known) {
            agree = ra.known;
            say(out, b, line(ra.known ? "I don't have more to add to what " + nameA + " said." : "I don't know this one either."));
        } else if (!ra.known) {
            agree = false;
            List<String> l = new ArrayList<String>();
            l.add("I can help with that.");
            l.addAll(rb.say);
            say(out, b, l);
        } else if (overlap(ra.text(), rb.text()) >= SAME) {
            agree = true;
            List<String> l = new ArrayList<String>();
            String extra = newSentence(rb.say, ra.text());
            if (mode == Mode.JOINT) {
                l.add(extra != null ? "Yes, and to add to that:" : "That's right.");
            } else {
                l.add("I agree with " + nameA + ".");
            }
            if (extra != null) {
                l.add(extra);
            }
            say(out, b, l);
        } else {
            agree = false;
            List<String> l = new ArrayList<String>();
            l.add(mode == Mode.JOINT ? "I'd add this:" : "I see it a little differently.");
            l.addAll(rb.say);
            say(out, b, l);
        }

        // a debate: each may answer the other once more, while it says something new
        Reply lastA = ra, lastB = rb;
        if (mode == Mode.DEBATE && !agree && ra.known && rb.known) {
            for (int round = 0; round < 2 && turns(out) < MAX_TURNS - 1; round++) {
                set(State.DEBATE_CONTINUING, round % 2 == 0 ? a : b, listener);
                boolean aTurn = round % 2 == 0;
                Agent who = aTurn ? agentA : agentB;
                String p = aTurn ? a : b;
                Reply r = ask(who, question, out);
                if (r == null || stop(out, listener)) {
                    if (cancelled) {
                        return out;
                    }
                    break;
                }
                Reply before = aTurn ? lastA : lastB;
                if (!r.known || overlap(r.text(), before.text()) >= SAME) {
                    // nothing new: a repeat would only loop
                    say(out, p, line(aTurn ? "I'll stand by what I said." : "I still see it my way."));
                    break;
                }
                say(out, p, r.say);
                if (aTurn) {
                    lastA = r;
                } else {
                    lastB = r;
                }
            }
        }

        if (stop(out, listener)) {
            return out;
        }
        if (agree) {
            set(State.CONSENSUS, a, listener);
        }
        set(State.FINAL_SUMMARY, a, listener);
        List<String> sum = new ArrayList<String>();
        if (!ra.known && !rb.known) {
            // both have said they don't know; a summary would only repeat it
            set(State.IDLE, a, listener);
            return out;
        } else if (agree) {
            sum.add("So we agree: " + first(ra.known ? lastA : lastB));
        } else if (ra.known && rb.known) {
            sum.add("In short: " + first(lastA) + " " + nameB + "'s view: " + first(lastB));
            sum.add("We don't fully agree, so weigh both.");
        } else {
            sum.add("In short: " + first(ra.known ? lastA : lastB));
        }
        say(out, a, sum);
        set(State.IDLE, a, listener);
        return out;
    }

    /**
     * Reads a request for both to discuss: "both of you discuss X", "debate X",
     * "what do you both think about X", "ask both of you X", "explain X together".
     * Null when the text isn't one.
     */
    public static Request parse(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim().replaceAll("\\s+", " ");
        String l = t.toLowerCase(Locale.US).replaceAll("^(hey |ok |okay )?(obi|ivie), ", "");
        String first = null;
        if (l.matches("^(obi|ivie),? (start|go first|you first)\\b.*")) {
            first = l.startsWith("obi") ? "obi" : "ivie";
        }
        java.util.regex.Matcher m;
        String[][] forms = {
                {"^(?:can you )?(?:both|the two of you|you two|obi and ivie|ivie and obi),? debate(?: about| on)? (.+)$", "DEBATE"},
                {"^(?:have a )?debate(?: about| on)? (.+)$", "DEBATE"},
                {"^(?:can you )?(?:both( of you)?|the two of you|you two|obi and ivie|ivie and obi),? (?:discuss|talk about|argue about) (.+)$", "SECOND_OPINION"},
                {"^(?:let )?(?:both of you|the two of you|you two) (?:discuss|talk about) (.+)$", "SECOND_OPINION"},
                {"^discuss (.+?)(?: together| between you)?$", "SECOND_OPINION"},
                {"^what do (?:you both|both of you|you two) think (?:about|of) (.+)$", "SECOND_OPINION"},
                {"^(?:i want to )?ask (?:you )?both(?: of you)?,? (.+)$", "SECOND_OPINION"},
                {"^(?:explain|teach me) (.+?) together$", "JOINT"},
                {"^(?:both of you|together),? explain (.+)$", "JOINT"},
        };
        for (String[] f : forms) {
            m = java.util.regex.Pattern.compile(f[0]).matcher(l);
            if (m.find()) {
                String q = m.group(m.groupCount()).replaceAll("[?.!]+$", "").trim();
                if (q.length() < 2) {
                    return null;
                }
                // a bare topic ("water", "remote work") becomes a question the brain can answer
                if (!q.matches("^(what|who|why|how|when|where|which|is|are|can|does|do|should|will|whether)\\b.*")) {
                    q = (q.split(" ").length <= 3 ? "what is " : "tell me about ") + q;
                }
                q = q.replaceFirst("^whether ", "is it true that ");
                return new Request(Mode.valueOf(f[1]), q, first);
            }
        }
        return null;
    }

    // ------------------------------------------------------------ helpers

    private Reply ask(Agent agent, String question, List<TurnLine> heard) {
        if (cancelled) {
            return new Reply(new ArrayList<String>(), false);
        }
        try {
            return agent.answer(question, new ArrayList<TurnLine>(heard));
        } catch (Exception failed) {
            return null;
        }
    }

    private boolean stop(List<TurnLine> out, Listener listener) {
        if (cancelled) {
            set(State.CANCELLED, null, listener);
            return true;
        }
        return false;
    }

    private List<TurnLine> fail(List<TurnLine> out, String who, Listener listener) {
        if (cancelled) {
            set(State.CANCELLED, null, listener);
            return out;
        }
        set(State.ERROR, who, listener);
        out.add(new TurnLine(who, "SAY", "Sorry, I lost my train of thought there. Ask me again?"));
        return out;
    }

    private void set(State s, String persona, Listener listener) {
        state = s;
        if (listener != null) {
            listener.state(s, persona);
        }
    }

    private static void say(List<TurnLine> out, String who, List<String> lines) {
        for (String s : lines) {
            if (s != null && s.trim().length() > 0) {
                out.add(new TurnLine(who, "SAY", s.trim()));
            }
        }
    }

    private static List<String> line(String s) {
        List<String> l = new ArrayList<String>();
        l.add(s);
        return l;
    }

    private static int turns(List<TurnLine> out) {
        int n = 0;
        String last = null;
        for (TurnLine t : out) {
            if ("SAY".equals(t.cue) && !t.agent.equals(last)) {
                n++;
                last = t.agent;
            }
        }
        return n;
    }

    static String name(String persona) {
        return "ivie".equals(persona) ? "Ivie" : "Obi";
    }

    /** The first sentence of an answer, ending with a full stop. */
    private static String first(Reply r) {
        String s = r.say.isEmpty() ? "" : r.say.get(0).trim();
        int dot = s.indexOf(". ");
        if (dot > 20) {
            s = s.substring(0, dot + 1);
        }
        return s.endsWith(".") || s.endsWith("?") || s.endsWith("!") ? s : s + ".";
    }

    /** A sentence of {@code lines} that says something {@code known} doesn't, or null. */
    private static String newSentence(List<String> lines, String known) {
        for (String s : lines) {
            if (overlap(s, known) < 0.35f && words(s).size() >= 4) {
                return s;
            }
        }
        return null;
    }

    /** Share of content words two texts have in common (0..1, of the shorter). */
    static float overlap(String x, String y) {
        Set<String> a = words(x), b = words(y);
        if (a.isEmpty() || b.isEmpty()) {
            return 0f;
        }
        int common = 0;
        for (String w : a) {
            if (b.contains(w)) {
                common++;
            }
        }
        return common / (float) Math.min(a.size(), b.size());
    }

    private static final Set<String> STOP = new HashSet<String>(java.util.Arrays.asList(
            "the", "a", "an", "and", "or", "of", "to", "in", "on", "is", "are", "was", "it", "its", "that", "this",
            "i", "you", "we", "he", "she", "they", "my", "your", "our", "be", "as", "at", "by", "for", "with", "so",
            "but", "not", "do", "does", "can", "about", "from", "what", "which", "who", "how", "there", "here"));

    private static Set<String> words(String s) {
        Set<String> out = new HashSet<String>();
        for (String w : s.toLowerCase(Locale.US).split("[^a-z0-9]+")) {
            if (w.length() > 1 && !STOP.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }
}
