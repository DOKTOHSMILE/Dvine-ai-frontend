package ai.dvine.llm;

/** When a world question must not be guessed. */
public final class AnswerGate {
    private AnswerGate() {
    }

    /**
     * Refuse only when the phone has not already answered and there is no model to ask.
     * A packed fact that is already answered is kept.
     */
    public static boolean refuseUngrounded(boolean modelReady, boolean alreadyAnswered, boolean worthLooking) {
        return !modelReady && !alreadyAnswered && worthLooking;
    }
}
