package ai.ibiko.ops.task;

import java.util.Locale;

/**
 * Turns one authorized utterance into a task-control suggestion.
 * It does not execute anything, and it is not used on ordinary chat, model text, code or documents.
 */
public final class CommandPhrasing {
    public final IbikoCommandGateway.Action action;
    /** Null means the current task. */
    public final String name;
    public final boolean control;

    private CommandPhrasing(IbikoCommandGateway.Action action, String name, boolean control) {
        this.action = action;
        this.name = name;
        this.control = control;
    }

    public static CommandPhrasing read(String raw) {
        if (raw == null) {
            return talk();
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || untrustedShape(trimmed)) {
            return talk();
        }
        String n = trimmed.toLowerCase(Locale.US).replaceAll("[.!?]+$", "").replaceAll("\\s+", " ").trim();
        if (n.isEmpty() || question(n) || reported(n) || n.contains(" and ")) {
            return talk();
        }
        Lead lead = lead(n);
        if (lead == null) {
            return talk();
        }
        String rest = n.substring(lead.prefix.length()).trim();
        rest = rest.replaceFirst("^(the |a |my )", "");
        if (rest.isEmpty() || current(rest)) {
            return new CommandPhrasing(lead.action, null, true);
        }
        return new CommandPhrasing(lead.action, rest, true);
    }

    private static CommandPhrasing talk() {
        return new CommandPhrasing(null, null, false);
    }

    /** JSON, code, quotes and multi-line text are never a single spoken command. */
    private static boolean untrustedShape(String t) {
        if (t.indexOf('\n') >= 0 || t.indexOf('\r') >= 0) {
            return true;
        }
        if (t.startsWith("{") || t.startsWith("[") || t.startsWith("//") || t.startsWith("/*")
                || t.startsWith("#") || t.startsWith("```") || t.startsWith("\"") || t.startsWith("'")
                || t.startsWith("“")) {
            return true;
        }
        String low = t.toLowerCase(Locale.US);
        return low.contains("\"action\"") && (low.contains("\"type\"") || low.contains("\"source\""));
    }

    private static boolean question(String n) {
        return n.indexOf('?') >= 0
                || n.matches("^(what|why|how|when|who|where|which|if|can|could|would|should|do|does|did|is|are|am)\\b.*")
                || n.contains("what happens")
                || n.contains("the word ");
    }

    private static boolean reported(String n) {
        return n.contains("someone told") || n.contains("told me") || n.contains("they said")
                || n.startsWith("if i ") || n.startsWith("if you ");
    }

    private static boolean current(String rest) {
        return rest.matches("^(it|that|this|current|current project|current task|project|task|this task|that task)$");
    }

    private static final class Lead {
        final String prefix;
        final IbikoCommandGateway.Action action;

        Lead(String prefix, IbikoCommandGateway.Action action) {
            this.prefix = prefix;
            this.action = action;
        }
    }

    private static Lead lead(String n) {
        Lead[] leads = {
                new Lead("carry on", IbikoCommandGateway.Action.RESUME),
                new Lead("hold on", IbikoCommandGateway.Action.PAUSE),
                new Lead("pause", IbikoCommandGateway.Action.PAUSE),
                new Lead("hold", IbikoCommandGateway.Action.PAUSE),
                new Lead("resume", IbikoCommandGateway.Action.RESUME),
                new Lead("continue", IbikoCommandGateway.Action.RESUME),
                new Lead("cancel", IbikoCommandGateway.Action.CANCEL),
                new Lead("abort", IbikoCommandGateway.Action.CANCEL),
                new Lead("stop", IbikoCommandGateway.Action.STOP)
        };
        for (Lead lead : leads) {
            if (n.equals(lead.prefix) || n.startsWith(lead.prefix + " ")) {
                return lead;
            }
        }
        return null;
    }
}
