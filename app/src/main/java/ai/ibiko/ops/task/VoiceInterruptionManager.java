package ai.ibiko.ops.task;

/**
 * Legacy adapter retained for source compatibility. Conversational utterances are not control events.
 * Task control must be submitted as a typed, authorized event through {@link IbikoCommandGateway}.
 */
@Deprecated
public final class VoiceInterruptionManager {
    public enum Action { NONE, CONTINUE, PAUSE, RESUME, CANCEL, CLARIFY, SWITCH }

    public static final class Decision {
        public final Action action;
        public final boolean stopGeneration;
        public final String say;
        public final String followUp;

        public Decision(Action action, boolean stopGeneration, String say, String followUp) {
            this.action = action;
            this.stopGeneration = stopGeneration;
            this.say = say;
            this.followUp = followUp;
        }

        public static Decision none() { return new Decision(Action.NONE, false, null, null); }
    }

    /** Never interprets text as task authority, regardless of phrasing or recognition confidence. */
    public Decision decide(String utterance, double confidence, boolean busy, int pausedCount, long now) {
        return Decision.none();
    }
}
