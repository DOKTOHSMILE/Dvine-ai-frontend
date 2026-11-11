package ai.ibiko.agent;

import java.util.Locale;
import java.util.Set;

/**
 * What a message sent while a job runs is, so it goes to the right place (I1–I6) and the job's workers keep going:
 * <ul>
 * <li>I1 {@link Kind#CHAT}: thanks, okay, a greeting. Answered in a word; nothing reaches the job.</li>
 * <li>I2 {@link Kind#QUESTION}: the user asks something ("how far are you?", "what does X do?"). Answered beside the
 * job; the plan is not touched and the job is not interrupted.</li>
 * <li>I3 {@link Kind#REQUEST}: a separate errand that is not about the job ("make me a birthday picture", "what's the
 * weather"). Handled on its own, beside the job; never written into the job's plan.</li>
 * <li>I4 {@link Kind#STEER}: how the job should go, not what it builds ("focus on the api first", "skip the tests",
 * "don't push yet", "stop"). Given to the job as a note; the plan is not reopened.</li>
 * <li>I5 {@link Kind#PLAN_CHANGE}: an idea or a change for the project itself ("also check the login code", "add
 * dark mode to it", "use Postgres instead"). Only this goes into the plan file, as new steps.</li>
 * <li>I6 Unsure: a message that shares the job's subject is treated as for the job (a note, I4); one that doesn't is
 * handled on its own (I3). A question mark always wins over a change word unless the message asks for the change
 * ("can you also add…?").</li>
 * </ul>
 * Pure Java, no model call: the sort takes microseconds, so it never slows the job.
 */
public final class Intake {
    public enum Kind { CHAT, QUESTION, REQUEST, STEER, PLAN_CHANGE }

    private Intake() {
    }

    /** Marks a note for the job: what it is, so the job knows whether it changes the plan. */
    public static final String PLAN = "⟦plan⟧ ", STEER = "⟦steer⟧ ";

    private static final String CHANGE = "(also |and |please |pls |can you |could you |would you |kindly |then )*"
            + "(add|include|make sure|change|use|switch|replace|remove|drop|rename|move|put|build|implement|support|cover"
            + "|check|audit|review|scan|test|fix|handle|extend|expand|instead)\\b";
    private static final String IDEA = "(it should|we should|i want it|i'd like it|i would like it|let it|it needs|it must"
            + "|don't forget to|dont forget to|remember to|what if we|how about we|how about adding|why not add)\\b";
    private static final String HOW = "(stop|pause|wait|hold on|skip|focus on|start with|first do|do .* first|prioriti[sz]e"
            + "|faster|hurry|quick(ly)?|slow down|take your time|keep going|continue|go on|carry on|don't push|dont push"
            + "|don't commit|dont commit|no need to|never mind|forget (it|that)|only (do|check|look at)|just (do|check))\\b";
    private static final String ASK = "(what|why|how|when|where|who|which|whose|is|are|was|were|can|could|do|does|did|will"
            + "|would|should|have|has|any|status|progress|update)\\b";

    /** Sorts {@code message}, sent while {@code job} runs. */
    public static Kind sort(String message, String job) {
        String m = message == null ? "" : message.trim().toLowerCase(Locale.US).replaceAll("\\s+", " ");
        if (m.isEmpty()) {
            return Kind.CHAT;
        }
        String words = m.replaceAll("[^\\p{L}\\p{N}' ?]", " ").replaceAll("\\s+", " ").trim();
        if (words.matches("(thanks?( you)?|thank u|thx|ok(ay)?|cool|nice|great|good|alright|sure|wow|lol|hi|hello|hey"
                + "|good (morning|afternoon|evening)|noted|got it|fine|perfect|awesome)( (ibiko|man|bro|boss|dear))?[ !.?]*")) {
            return Kind.CHAT; // I1
        }
        boolean about = shares(m, job);
        boolean question = m.endsWith("?") || words.matches("^" + ASK + ".*") && !words.matches("^(do|does|did|will|would|can"
                + "|could) (it|you|we|they)? ?(also )?" + CHANGE + ".*");
        boolean askedChange = words.matches("^(can|could|would|will) you (also |please )?" + CHANGE + ".*");
        if (words.matches("^(please |pls |now |ok(ay)? |and |but )*" + HOW + ".*")) {
            return Kind.STEER; // I4: how the job goes
        }
        boolean idea = words.matches("^" + IDEA + ".*"); // "what if we…", "it should…": an idea, even as a question
        if (askedChange || idea || !question && (words.matches("^" + CHANGE + ".*")
                || words.matches(".*\\b(also|as well|too|instead)\\b.*") && words.matches(".*\\b" + CHANGE + ".*"))) {
            return about || mentionsWork(words) ? Kind.PLAN_CHANGE : Kind.REQUEST; // I5, or an errand of its own (I3)
        }
        if (question) {
            return Kind.QUESTION; // I2
        }
        return about ? Kind.STEER : Kind.REQUEST; // I6
    }

    /** Whether the message is about the job: they share a subject word. */
    static boolean shares(String message, String job) {
        if (job == null || job.isEmpty()) {
            return false;
        }
        Set<String> want = ai.ibiko.learn.Codes.tags(message), have = ai.ibiko.learn.Codes.tags(job);
        want.retainAll(have);
        return !want.isEmpty();
    }

    /** Words that only make sense about a code project: a change with them is for the job even in other words. */
    static boolean mentionsWork(String m) {
        return m.matches(".*\\b(file|files|folder|screen|page|feature|function|method|class|button|code|codebase|api|repo"
                + "|repository|module|test|tests|audit|report|plan|step|commit|branch|bug|crash|endpoint|database|schema"
                + "|ui|ux|layout|login|auth|build|app|project)s?\\b.*");
    }

    /** The note handed to the running job for a message of this kind (only STEER and PLAN_CHANGE reach it). */
    public static String forJob(Kind k, String message) {
        return (k == Kind.PLAN_CHANGE ? PLAN : STEER) + (message == null ? "" : message.trim());
    }
}
