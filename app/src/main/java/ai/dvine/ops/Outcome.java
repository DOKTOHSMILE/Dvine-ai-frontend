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
    /** Speak this as the reply, without cancelling other work. */
    public boolean speak;
    /** "obi", "ivie", or "ibiko" when the user named one. */
    public String persona = "";
    /** document, image, video, audio, or file. Empty when no picker is needed. */
    public String picker = "";
    /** Save a note from the chosen file. Not model training. */
    public boolean learn;
    /** A machine operation, such as INSTALL_TERMUX. Empty when there is none. */
    public String command = "";

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

    public static Outcome reply(String speech) {
        Outcome o = say(speech, false);
        o.speak = true;
        return o;
    }
}
