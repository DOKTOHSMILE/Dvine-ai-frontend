package ai.dvine.ops.pipe;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Breaks one utterance into an action, a target, and the restrictions that must survive. */
public final class ConversationDecompositionEngine {
    private static final Pattern BUT = Pattern.compile("\\b(?:but|without|except|except for)\\b(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DO_NOT = Pattern.compile("\\b(?:do not|don't|never|without touching|leave|keep)\\b", Pattern.CASE_INSENSITIVE);

    private final SemanticObjectExtractor objects = new SemanticObjectExtractor();
    private final SemanticRelationshipEngine relations = new SemanticRelationshipEngine();

    public void fill(SemanticTypes.Decomposition d) {
        String raw = d.original;
        String n = raw.toLowerCase(Locale.US).replaceAll("[.!?]+$", "").replaceAll("\\s+", " ").trim();
        d.question = n.matches("^(what|why|how|when|where|who|would|could|can you explain|explain)\\b.*")
                || n.contains("what would happen");
        d.action = action(n);
        d.target = objects.target(n);
        d.parent = objects.parent(n);
        relations.preserve(n, d.preserve);
        if (DO_NOT.matcher(n).find()) {
            d.negations.add(n);
        }
        Matcher but = BUT.matcher(n);
        if (but.find()) {
            d.negations.add(but.group(1).trim());
        }
        d.ambiguous = n.matches("^(take it out|remove it|remove that|make it bigger|fix the problem|continue from there|stop that)$");
    }

    private static String action(String n) {
        if (n.matches(".*(what would happen if i deleted|explain what would happen).*")) {
            return "explain";
        }
        if (n.startsWith("delete") || n.contains(" delete ")) {
            return "delete";
        }
        if (n.matches(".*(remove|take out|take off).*")) {
            return "remove";
        }
        if (n.contains("find ") || n.startsWith("find")) {
            return "find";
        }
        if (n.contains("build") || n.contains("reconstruct") || n.contains("3d model")) {
            return "reconstruct";
        }
        if (n.contains("connect") || n.contains("server") || n.contains("logs")) {
            return "ssh";
        }
        if (n.contains("storage") || n.contains("space")) {
            return "storage";
        }
        if (n.contains("search") || n.contains("look up") || n.contains("unfamiliar") || n.contains("xyzzy")) {
            return "research";
        }
        return "chat";
    }
}
