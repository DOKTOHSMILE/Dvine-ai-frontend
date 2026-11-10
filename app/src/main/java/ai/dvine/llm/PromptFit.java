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

    /** Older turns kept for an API doing real work. The question being asked is separate and may use the window. */
    public static final int KEPT_HISTORY_CHARS = 12_000;

    /**
     * Older turns kept for a simple question. A greeting does not resend the last 12,000 characters of chat.
     * The latest turn is still sent whole (up to the window).
     */
    public static final int BRIEF_HISTORY_CHARS = 4_000;

    /**
     * How many tokens to ask a plain or reasoning API to write, before the reasoning form adds room to think.
     * The user's own cap wins. A one-line order is not padded to 1,024: that padding is what the model is
     * allowed to spend, and a short call was being billed as a long one.
     */
    public static int outputTokens(int asked, boolean reasoning, int userCap) {
        if (userCap > 0) {
            return userCap;
        }
        int want = asked <= 0 ? 256 : asked;
        if (reasoning) {
            // 4,096, not 1,024: a reasoning model hides its thinking inside the same limit as the answer,
            // and the small limit was the reason big models answered with nothing ("the model didn't answer").
            return Math.max(4096, want);
        }
        return Math.max(32, Math.min(want, 8192));
    }

    /**
     * Tokens for {@code max_completion_tokens}. A user cap is never raised. A reasoning model gets four times
     * its allowance of room, because the hidden thinking is spent from the same limit as the visible answer:
     * 16,384 for a short question, 32,000 for real work. Plain models send the allowance as it is.
     */
    public static int completionTokens(int asked, boolean reasoning, int userCap) {
        int allowance = outputTokens(asked, reasoning, userCap);
        if (!reasoning || userCap > 0) {
            return allowance;
        }
        if (asked <= 256) {
            return Math.min(16_384, allowance * 4);
        }
        return Math.min(32_000, allowance * 4);
    }

    /**
     * How many tokens of memory to load for a prompt of {@code promptChars}, never above the user's ceiling.
     * A short question loads 2,048. The cache grows by doubling only when the words need it.
     */
    public static int localContext(int promptChars, int ceiling) {
        int cap = Math.max(2048, ceiling);
        int need = Math.max(2048, Math.max(0, promptChars) / 3 + 768);
        int step = 2048;
        while (step < need && step < cap) {
            int next = step * 2;
            if (next > cap) {
                break;
            }
            step = next;
        }
        return Math.min(cap, Math.max(2048, step));
    }

    /**
     * The turns an API is sent. The latest turn may use the window. Older turns share
     * {@link #KEPT_HISTORY_CHARS}, so a 128k model is not billed for 128k tokens of old chat.
     * {@code brief} is a simple question: older turns share {@link #BRIEF_HISTORY_CHARS} instead.
     * The list given in is not changed.
     */
    public static List<String[]> remoteTurns(List<String[]> turns, int window, int maxTokens) {
        return remoteTurns(turns, window, maxTokens, false);
    }

    public static List<String[]> remoteTurns(List<String[]> turns, int window, int maxTokens, boolean brief) {
        if (turns == null || turns.isEmpty() || window <= 512) {
            return turns;
        }
        int reply = Math.min(Math.max(256, maxTokens), Math.max(256, window / 2));
        int windowChars = Math.max(2_000, (window - reply - 300) * 3);
        int historyCap = brief ? BRIEF_HISTORY_CHARS : KEPT_HISTORY_CHARS;
        int lastAt = turns.size() - 1;
        String[] end = turns.get(lastAt);
        int endLen = end[1] == null ? 0 : end[1].length();
        boolean shortened = endLen > windowChars;
        int keptEnd = shortened ? windowChars : endLen;
        int olderBudget = Math.min(historyCap, Math.max(0, windowChars - keptEnd));
        int older = 0;
        for (int i = 0; i < lastAt; i++) {
            older += turns.get(i)[1] == null ? 0 : turns.get(i)[1].length();
        }
        if (!shortened && older <= olderBudget) {
            return turns;
        }
        List<String[]> copy = new ArrayList<String[]>(turns);
        if (shortened) {
            copy.set(lastAt, new String[]{end[0], LocalModel.fit(end[1], windowChars)});
        }
        while (copy.size() > 1 && older > olderBudget) {
            String[] drop = copy.remove(0);
            older -= drop[1] == null ? 0 : drop[1].length();
        }
        return copy;
    }

    /**
     * A dropped connection, including a phone resolver that briefly cannot name a real host
     * ("Unable to resolve host … No address associated with hostname"), is worth another try.
     * Refusing anything but https is not.
     */
    public static boolean retryReach(String message) {
        if (message == null) {
            return true;
        }
        String m = message.toLowerCase(Locale.US);
        return !m.contains("only https");
    }
}
