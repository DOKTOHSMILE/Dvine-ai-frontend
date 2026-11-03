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
            + "|run|install|clone|push|pull|commit|merge|audit|review|test|migrate|port|scaffold|generate)";
    private static final String CODE = "(code|function|class|method|script|program|app|apk|bug|bugs|error|crash|stack trace|compile"
            + "|build|repo|repository|github|git|pull request|branch|commit|codebase|api|endpoint|database|sql|query|server|website|web app"
            + "|termux|terminal|shell|command|package|library|gradle|android|kotlin|java|python|javascript|typescript|html|css|react"
            + "|node|flutter|docker|unit tests?|test suite)";

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
        return t.matches("(?s).*\\b" + DO + "\\b.*\\b" + CODE + "\\b.*") || t.matches("(?s).*```.*");
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
