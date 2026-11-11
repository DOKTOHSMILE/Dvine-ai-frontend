package ai.ibiko.chat;

/**
 * Whether the words on the live line are a reply to keep when the next step starts. A status line
 * ("I'll fix those paths", "Alright, I am continuing now") is not the reply: it stays with the steps.
 * Pure Java.
 */
public final class LiveKeep {
    private LiveKeep() {
    }

    public static boolean keep(String live, int minChars) {
        if (live == null) {
            return false;
        }
        String t = live.trim();
        return t.length() >= minChars && !ai.ibiko.agent.CodeAgent.promise(t);
    }
}
