package ai.dvine.turn;

import java.util.Locale;

/**
 * Whether what a persona just said waits for the user's answer, so the microphone opens by itself: a question, or
 * an invitation to reply ("tell me more", "let me know", "go ahead", "your turn"). Rhetorical endings and plain
 * statements don't. Pure Java.
 */
public final class Followup {
    private Followup() {
    }

    public static boolean wantsReply(String said) {
        if (said == null) {
            return false;
        }
        String t = said.trim();
        if (t.isEmpty()) {
            return false;
        }
        if (t.endsWith("?") || t.endsWith("?\"") || t.endsWith("?)")) {
            return true;
        }
        // the last sentence only
        String[] parts = t.split("(?<=[.!])\\s+");
        String last = parts[parts.length - 1].toLowerCase(Locale.US).replaceAll("[.!\"')]+$", "").trim();
        return last.matches("^(please )?(tell me|let me know|go ahead|say|share|describe|explain|teach me)\\b.*")
                || last.matches(".*\\b(your turn|over to you|i'?m listening|i am listening|i'?d love to hear|"
                + "i would love to hear|tell me more|what do you think|let me know|when you'?re ready)$");
    }
}
