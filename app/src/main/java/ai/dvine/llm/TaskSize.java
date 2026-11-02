package ai.dvine.llm;

import java.util.Locale;

/**
 * How big a request is, to pick the model: a quick question, small talk or a one-line order goes to the fast,
 * cheap model; real work (writing, building, coding, analysing, planning, a long or many-part message, anything
 * for Ibiko) goes to the strong one. A guess from the words, deliberately simple, so it costs nothing. Pure Java.
 */
public final class TaskSize {
    public enum Tier { FAST, STRONG }

    private TaskSize() {
    }

    private static final String WORK = ".*\\b(write|rewrite|draft|compose|build|create|make me|design|implement|develop|code|"
            + "program|script|function|class|app|website|api|debug|fix|refactor|optimi[sz]e|review|analy[sz]e|analysis|"
            + "compare|evaluate|plan|strategy|research|summari[sz]e|report|essay|article|proposal|business plan|"
            + "explain (in detail|step by step|thoroughly)|step by step|in detail|prove|derive|calculate|solve|"
            + "translate|outline|curriculum|itinerary|budget)\\b.*";

    /** STRONG for real work, FAST for the rest. */
    public static Tier of(String text, boolean coding) {
        if (coding) {
            return Tier.STRONG; // Ibiko codes: always the strong model
        }
        if (text == null) {
            return Tier.FAST;
        }
        String t = text.trim();
        String low = t.toLowerCase(Locale.US);
        if (t.length() > 280 || t.contains("```") || low.split("\\?").length > 2) {
            return Tier.STRONG; // long, carries code, or several questions at once
        }
        if (low.matches("(?s)" + WORK)) {
            return Tier.STRONG;
        }
        return Tier.FAST;
    }

    private static final String ACTION = ".*\\b(search|look up|google|latest|news|today|weather|price|photo|picture|"
            + "image|draw|zip|unzip|file|folder|run|command|shell|terminal|github|repo|repository|issue|calendar|"
            + "schedule|notion|time|call me|my name|open|switch|debate)\\b.*";

    /**
     * Whether a message may be an order for the phone (a search, a photo, a file, a connector) rather than
     * something to answer in words: those still go through the routing call first.
     */
    public static boolean mayNeedAction(String text) {
        return text != null && text.toLowerCase(Locale.US).matches("(?s)" + ACTION);
    }

    /** For a call that isn't a user's message: a short one (an order, a test) is fast, a long write-up is strong. */
    public static Tier ofCall(String text, int maxTokens, boolean coding) {
        if (maxTokens <= 120) {
            return Tier.FAST;
        }
        if (maxTokens >= 1200) {
            return Tier.STRONG;
        }
        return of(text, coding);
    }
}
