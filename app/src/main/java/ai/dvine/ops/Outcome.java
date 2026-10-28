package ai.dvine.ops;

/** What the activity should say and show. Raw command lines are not included. */
public final class Outcome {
    public boolean handled;
    public boolean stopGeneration;
    public boolean resumeSpeech;
    public boolean listen;
    public String speech = "";
    public String activity = "";
    public String solidText = "";
    public String subject = "";
    public String taskId = "";

    public static Outcome pass() {
        Outcome o = new Outcome();
        o.handled = false;
        return o;
    }

    public static Outcome say(String speech, boolean stopGeneration) {
        Outcome o = new Outcome();
        o.handled = true;
        o.speech = speech == null ? "" : speech;
        o.stopGeneration = stopGeneration;
        return o;
    }
}
