package ai.dvine.ops.task;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What a new utterance means for work already running.
 * Listening itself is not an utterance and is not handled here.
 */
public final class VoiceInterruptionManager {
    public enum Action {
        NONE, CONTINUE, PAUSE, RESUME, CANCEL, CLARIFY, SWITCH
    }

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

        public static Decision none() {
            return new Decision(Action.NONE, false, null, null);
        }
    }

    private static final Pattern CONTINUE = Pattern.compile(
            "^(please )?(keep working( while i speak)?|don't stop( the current task)?|do not stop( the current task)?|continue what you are doing|keep going|carry on with (that|this)|leave it running)$");
    private static final Pattern PAUSE = Pattern.compile(
            "^(please )?(pause( what you are doing| the current operation| this task| that| it)?|hold on( for a moment)?|wait( a moment| a second)?|stop for now|let'?s leave that for later|keep that( task)? on hold)$");
    private static final Pattern RESUME = Pattern.compile(
            "^(please )?(resume( the task| my .+| the .+)?|continue where you stopped|carry on with the previous operation|pick up where you stopped|continue from there|carry on)$");
    private static final Pattern CANCEL = Pattern.compile(
            "^(please )?(cancel the current task|cancel (that|this|it)|stop building that model and discard the unfinished result|discard (that|this|the unfinished result))$");

    private String lastNorm = "";
    private long lastAt;

    public Decision decide(String utterance, double confidence, boolean busy, int pausedCount, long now) {
        String raw = utterance == null ? "" : utterance.trim();
        String n = raw.toLowerCase(Locale.US).replaceAll("[.!?]+$", "").replaceAll("\\s+", " ").trim();
        if (confidence < 0.45 || n.isEmpty() || n.matches("^(uh+|um+|hmm+|er+|huh)$")) {
            return new Decision(Action.CLARIFY, false,
                    busy ? "I didn't catch that. I'll keep working." : "I didn't catch that. Could you say it again?", null);
        }
        if (n.equals(lastNorm) && now - lastAt < 1500 && now >= lastAt) {
            return new Decision(Action.CLARIFY, false, null, null);
        }
        lastNorm = n;
        lastAt = now;
        if (CONTINUE.matcher(n).matches()) {
            return new Decision(Action.CONTINUE, false,
                    busy ? "I'll keep working." : "Nothing is running right now.", null);
        }
        if (PAUSE.matcher(n).matches()) {
            if (!busy) {
                return Decision.none();
            }
            return new Decision(Action.PAUSE, true,
                    "Paused. Your progress is saved, and you can resume it any time.", null);
        }
        if (n.matches("^(please )?(resume it|resume that|continue it)$") && pausedCount > 1) {
            return new Decision(Action.RESUME, false, null, raw);
        }
        if (RESUME.matcher(n).matches() || n.matches("^(please )?resume .+")) {
            if (!busy && pausedCount == 0) {
                return Decision.none();
            }
            return new Decision(Action.RESUME, false, null, raw);
        }
        if (CANCEL.matcher(n).matches()) {
            return new Decision(Action.CANCEL, true, null, raw);
        }
        String switched = switchRemainder(n);
        if (switched != null) {
            return new Decision(Action.SWITCH, true, null, switched);
        }
        return Decision.none();
    }

    /** "Pause that and check my project folder" → the new request, with the pause already decided. */
    static String switchRemainder(String n) {
        String[] leads = {
                "pause that and ", "pause this and ", "pause the model and ", "pause it and ",
                "keep the current task paused and ", "hold that and ", "stop that for now and "
        };
        for (String lead : leads) {
            if (n.startsWith(lead) && n.length() > lead.length() + 2) {
                return n.substring(lead.length()).trim();
            }
        }
        if (n.startsWith("start another task")) {
            return n.replaceFirst("^start another task( without losing the previous one)?", "").replaceFirst("^(and |to )", "").trim();
        }
        return null;
    }
}
