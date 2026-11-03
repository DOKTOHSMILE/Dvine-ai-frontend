package ai.dvine.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How much text a model is actually given. The phone's own model was being handed more than its memory,
 * then failing with "too long" and starting again. An API was being handed the whole chat on every step.
 * This keeps the latest work and leaves room for the reply, before the model is asked.
 */
public final class PromptFit {
    private PromptFit() {
    }

    /** Tokens the phone's model may write: a step stays short so it finishes, a report may be longer. */
    public static int localReplyTokens(int window, int asked, boolean report) {
        int cap = report ? 1024 : 512;
        int share = Math.max(160, window / (report ? 4 : 6));
        return Math.max(128, Math.min(asked <= 0 ? cap : asked, Math.min(cap, share)));
    }

    /**
     * Characters of prompt that fit beside that reply. Two characters a token, and room for the chat template,
     * so the first try fits instead of being rejected and tokenised again.
     */
    public static int localPromptChars(int window, int replyTokens, int systemChars) {
        int promptTokens = Math.max(128, window - replyTokens - 160);
        return Math.max(400, promptTokens * 2 - Math.max(0, systemChars));
    }

    /**
     * The turns an API is sent: the latest ones that fit its window. The last turn is shortened when it alone
     * is too big. The list given in is not changed.
     */
    public static List<String[]> remoteTurns(List<String[]> turns, int window, int maxTokens) {
        if (turns == null || turns.size() <= 1 || window <= 512) {
            return turns;
        }
        int reply = Math.min(Math.max(256, maxTokens), window / 2);
        int budget = Math.max(2000, (window - reply - 300) * 3);
        int used = 0;
        for (String[] t : turns) {
            used += t[1] == null ? 0 : t[1].length();
        }
        if (used <= budget) {
            return turns;
        }
        List<String[]> copy = new ArrayList<String[]>(turns);
        while (copy.size() > 1 && used > budget) {
            String[] drop = copy.remove(0);
            used -= drop[1] == null ? 0 : drop[1].length();
        }
        String[] last = copy.get(copy.size() - 1);
        if (last[1] != null && last[1].length() > budget) {
            copy.set(copy.size() - 1, new String[]{last[0], LocalModel.fit(last[1], budget)});
        }
        return copy;
    }

    /** A dropped connection is worth another try. A host that does not exist is not. */
    public static boolean retryReach(String message) {
        if (message == null) {
            return true;
        }
        String m = message.toLowerCase(Locale.US);
        return !(m.contains("unknown host") || m.contains("unknownhost") || m.contains("no address")
                || m.contains("unable to resolve") || m.contains("only https"));
    }
}
