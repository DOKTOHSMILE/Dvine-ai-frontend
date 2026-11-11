package ai.ibiko.llm;

/**
 * What the downloaded brain needs to know about the app it lives in, so it answers as part of Ibiko rather than as
 * a generic chatbot: what Ibiko is and who made it, the three personas and how to switch between them, what the
 * app can really do (only things it can do), where the brain itself runs, the app's version and the phone. Built
 * fresh for each conversation from the app's current state ({@link Facts}), so it is always in step with this
 * build. Also the one action the brain may take itself: bringing a persona forward, with a tag the app acts on
 * and never speaks ({@link ActionTags}). Pure Java.
 */
public final class IbikoSelf {
    private IbikoSelf() {
    }

    /** The app's state that the sheet describes. */
    public static final class Facts {
        public String version = "?";
        public String device = "this device";
        public int taughtFacts;
        public String userName;
        /** "ivie", "obi" or "ibiko": who is speaking now. */
        public String speaking = "ivie";
    }

    public static String sheet(Facts f) {
        StringBuilder s = new StringBuilder();
        s.append("About the app you live in: Ibiko (version ").append(f.version).append("), an AI companion on the user's Android device (")
                .append(f.device).append("), made by DOKTOHSMILE.");
        s.append(" Ibiko is one assistant with one voice (there are no personas). Pictures it makes (icons, logos,"
                + " illustrations) come from an image model and show in the chat; there is no 3D.");
        s.append(" You are the on-device model: a small language model stored on this device, working offline. The app's"
                + " offline rules handle the time, maths, memory, corrections and commands before you.");
        s.append(" What Ibiko can do: talk by voice (touch and hold anywhere to speak, let go to send; double-tap to type); argue both sides of any topic; remember what"
                + " the user teaches and accept corrections; look things up online when allowed; make a picture (an icon, a"
                + " logo, an illustration) with an image model; open apps such as WhatsApp, Telegram or Instagram, copy and share text; change its"
                + " voices; turn subtitles, thoughts and music on or off; give a status report; download or remove the model.");
        s.append(" It cannot send messages, buy, delete or change anything on the device by itself.");
        if (f.userName != null && !f.userName.trim().isEmpty()) {
            s.append(" The user is ").append(f.userName.trim()).append('.');
        }
        if (f.taughtFacts > 0) {
            s.append(" The user has taught you ").append(f.taughtFacts).append(f.taughtFacts == 1 ? " fact" : " facts")
                    .append("; those that matter to a question come with it.");
        }
        s.append(" If the user asks for another persona or assistant, say in one short sentence that there is only Ibiko here.");
        return s.toString();
    }
}
