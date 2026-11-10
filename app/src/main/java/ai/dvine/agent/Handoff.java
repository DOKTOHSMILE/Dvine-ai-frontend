package ai.dvine.agent;

import java.util.Locale;

/**
 * Which persona a task belongs to, when it isn't the one on screen: code to write, fix, build or run, repositories
 * to clone or push, things to run on the phone are Ibiko's work. Obi or Ivie then offer to hand it to him (a button
 * that opens a new chat under Ibiko with the task). Questions about code ("what is an API?") stay where they are:
 * only a task, a verb that asks for work on something code-like, is handed over. Pure Java.
 */
public final class Handoff {
    private Handoff() {
    }

    private static final String DO = "(write|code|fix|debug|build|compile|implement|refactor|create|make|set up|setup|deploy"
            + "|run|install|clone|push|pull|commit|merge|audit|review|test|migrate|port|scaffold|generate|update|upgrade|sync|fetch|uninstall|execute)";
    private static final String CODE = "(code|function|class|method|script|program|app|apk|bug|bugs|error|crash|stack trace|compile"
            + "|build|repo|repository|github|git|pull request|branch|commit|codebase|api|endpoint|database|sql|query|server|website|web app"
            + "|termux|terminal|shell|command|package|library|gradle|android|kotlin|java|python|javascript|typescript|html|css|react"
            + "|node|flutter|docker|unit tests?|test suite|linux|alpine|pip|npm|apk add|packages?|dependenc(?:y|ies))";

    /** True when the words ask Ibiko's kind of work. */
    public static boolean forIbiko(String text) {
        String t = text == null ? "" : text.toLowerCase(Locale.US).trim();
        if (t.isEmpty() || t.matches("(?s)^(what|why|how does|how do|who|when|where|is|are|explain|tell me about|define)\\b.*")
                && !t.matches("(?s).*\\b(for me|can you " + DO + ")\\b.*")) {
            return false; // a question about it, for whoever is on screen
        }
        if (t.matches("(?s).*\\b(pdf|word document|spreadsheet|excel)\\b.*") && !t.matches("(?s).*\\b(parser|reader|library)\\b.*")) {
            return false; // a document to make: any persona does that
        }
        if (t.matches("(?s)^(?:(?:hey |ok |okay )?ibiko[,:]?\\s+)?(?:please\\s+|just\\s+|now\\s+)?(pull|clone|run|execute|install|build|compile|deploy|push|commit|merge)"
                + " (it|that|this|them|these|those)\\b.*")) {
            return true; // "pull it now", "run it directly": a command to carry out, which is his
        }
        if (t.matches("(?s).*\\b((linux|alpine) (environment|terminal|shell)|(built-?in|internal|in-?app) (linux|terminal|shell))\\b.*")) {
            return true; // the Linux inside the app is where he works
        }
        return t.matches("(?s).*\\b" + DO + "\\b.*\\b" + CODE + "\\b.*") || t.matches("(?s).*```.*");
    }

    private static final String MAKE = "(write|create|make|generate|compose|draft|prepare|produce|compile|draw|design|craft|come up with)";
    private static final String IVIE = "(story|stories|short story|novel|chapter|script|screenplay|skit|poem|poetry|lyrics|song|tale|fable"
            + "|dialogue|scene|drawing|picture|image|illustration|logo|sketch|3d|character|comic|storyboard|plot)";
    private static final String OBI = "(report|reports|analysis|business plan|proposal|summary|research|brief|briefing|memo|essay|article"
            + "|presentation|white ?paper|case study|market study|feasibility study|executive summary)";

    /**
     * Whose work a message is: "ibiko" for code, "ivie" for stories, scripts, poems and drawings, "obi" for reports,
     * analyses and proposals; null when anyone can answer it (a question, a chat, a social post).
     */
    public static String owner(String text) {
        String low = text == null ? "" : text.toLowerCase(Locale.US);
        if (low.matches("(?s).*\\b(script|scripts)\\b.*\\b(skit|play|film|movie|video|ad|advert|commercial|scene|episode|show"
                + "|youtube|tiktok|reel|podcast|drama|story)\\b.*") || low.matches("(?s).*\\b(screenplay|stage play)\\b.*")) {
            return "ivie"; // a script to perform, not code
        }
        if (forIbiko(text)) {
            return "ibiko";
        }
        String t = text == null ? "" : text.toLowerCase(Locale.US).trim();
        if (t.isEmpty() || t.matches("(?s)^(what|why|how|who|when|where|is|are|explain|tell me about|define)\\b.*")
                || t.matches("(?s).*\\b(facebook|twitter|instagram|reddit|telegram|linkedin|tweet|post on|post to|caption)\\b.*")) {
            return null; // a question, or a social post: whoever is on screen
        }
        if (t.matches("(?s).*\\b" + MAKE + "\\b.*\\b" + IVIE + "\\b.*")) {
            return "ivie";
        }
        if (t.matches("(?s).*\\b" + MAKE + "\\b.*\\b" + OBI + "\\b.*")) {
            return "obi";
        }
        return null;
    }

    /** "@Ivie …", "Obi, …": the persona the message names first, or null. */
    public static String mentioned(String text) {
        String t = text == null ? "" : text.trim().toLowerCase(Locale.US);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:^|\\s)@(obi|ivie|ibiko)\\b|^(?:hey |hi |ok |okay )?(obi|ivie|ibiko)\\b(?!\\s+(?:is|was|said|says|and|or|did|does|has)\\b)").matcher(t);
        if (m.find()) {
            return m.group(1) != null ? m.group(1) : m.group(2);
        }
        return null;
    }

    /**
     * Who answers in the group chat, with no buttons to pick: the one named ("@Ivie", "Obi, …"), else the one whose
     * work it is (code, a story, a report), else whoever is already on it when the message only carries on ("continue",
     * "yes", "retry", a short answer to their question), else Obi, who takes everything else.
     */
    public static String groupRoute(String text, String current) {
        String named = mentioned(text);
        if (named != null) {
            return named;
        }
        String owner = owner(text);
        if (owner != null) {
            return owner;
        }
        String t = text == null ? "" : text.trim().toLowerCase(Locale.US).replaceAll("[.!?]+$", "");
        boolean carriesOn = t.matches("^(continue|go on|keep going|carry on|proceed|resume|retry|try again|again|redo|yes|yeah|yep|no|nope|ok|okay"
                + "|sure|do it|go ahead|stop|cancel|thanks|thank you|both|the first one|the second one|the last one|that one|why|how)( please)?$")
                || t.matches("^(finish|show me|give me|send me|what did you find|and|so)\\b.{0,40}$");
        if (carriesOn && current != null && !current.isEmpty()) {
            return current;
        }
        return "obi";
    }

    /** Why a message went to that persona, in a few words. */
    public static String why(String persona) {
        switch (persona == null ? "" : persona) {
            case "ibiko":
                return "code is Ibiko's work";
            case "ivie":
                return "stories, scripts and drawings are Ivie's work";
            case "obi":
                return "reports and analyses are Obi's work";
            default:
                return "";
        }
    }

    /** A short chat name from the task ("Fix the login crash in my app"). */
    public static String title(String task) {
        String t = task == null ? "" : task.trim().replaceAll("\\s+", " ");
        if (t.length() > 40) {
            int cut = t.lastIndexOf(' ', 40);
            t = t.substring(0, cut > 20 ? cut : 40) + "…";
        }
        return t.isEmpty() ? "Ibiko code" : t.substring(0, 1).toUpperCase(Locale.US) + t.substring(1);
    }
}
