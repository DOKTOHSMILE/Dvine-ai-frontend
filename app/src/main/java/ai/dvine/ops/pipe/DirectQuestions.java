package ai.dvine.ops.pipe;

import java.util.Locale;

/** Questions the app can answer without a model, and ones that must not be treated as edits. */
public final class DirectQuestions {
    private DirectQuestions() {
    }

    public static String bare(String text) {
        String n = text == null ? "" : text.toLowerCase(Locale.US);
        n = n.replaceAll("[.!?]+", "").replaceAll("\\s+", " ").trim();
        return n;
    }

    /** The current clock only. "What is the date of the battle" is not this. */
    public static boolean clock(String text) {
        String n = bare(text);
        return n.matches("what(?:'s| is) today'?s date")
                || n.matches("what(?:'s| is) the date( today| now)?")
                || n.matches("what day is (it|today)( today)?")
                || n.matches("what(?:'s| is) the time( now)?")
                || n.matches("what time is it( now)?")
                || n.matches("what year is it( now)?")
                || n.matches("today'?s date")
                || n.equals("today");
    }

    public static boolean timeOnly(String text) {
        String n = bare(text);
        return n.matches("what(?:'s| is) the time( now)?") || n.matches("what time is it( now)?");
    }

    public static boolean yearOnly(String text) {
        return bare(text).matches("what year is it( now)?");
    }

    /** A question about a side effect, not a request to do it. */
    public static boolean hypothetical(String text) {
        String n = bare(text);
        return n.contains("what would happen") || n.contains("explain what would happen");
    }

    public static boolean news(String text) {
        String n = bare(text);
        return n.contains("news") || n.contains("headlines") || n.contains("what's happening today")
                || n.contains("what is happening today") || n.contains("whats happening today");
    }

    public static String pickerKind(String text) {
        String n = bare(text);
        if (n.contains("video")) {
            return "video";
        }
        if (n.contains("image") || n.contains("photo") || n.contains("picture")) {
            return "image";
        }
        if (n.contains("audio") || n.contains("recording")) {
            return "audio";
        }
        if (n.contains("document") || n.contains("pdf") || n.contains("scan")) {
            return "document";
        }
        if (n.contains("picker") || n.contains("file")) {
            return "file";
        }
        return null;
    }

    public static boolean wantsPicker(String text) {
        String n = bare(text);
        if (n.contains("folder") || n.contains("project")) {
            return false;
        }
        return n.matches(".*(open (the )?(file )?picker|file picker|select an image|select a photo|select a video|"
                + "select a document|scan a document|open (a |my |the )?document|examine a video|inspect a file|"
                + "let me (select|choose|pick)|i want you to (inspect|scan|examine|open)).*");
    }

    public static boolean wantsLearn(String text) {
        String n = bare(text);
        return n.matches(".*(learn from|add (this|the|it|that) to (your )?memory|study this|remember the components|"
                + "remember this (document|file|image|text)).*");
    }

    /**
     * "What model are you" — not "a 3D model of a car" and not a lesson about models in general.
     */
    public static boolean modelIdentity(String text) {
        String n = bare(text);
        if (n.contains("3d") || n.contains("model of") || n.contains("a model")) {
            return false;
        }
        return n.matches("what(?:'s| is) your (model|brain)( files)?")
                || n.matches("(what|which) (language |ai |on-device |offline )?(model|brain|llm) (are you|do you (use|have|run)|is (this|running))( using| running| on this phone)?")
                || n.matches("identify (your|the) (model|brain)");
    }

    /** What this phone can do, asked as a question rather than a task. */
    public static boolean abilities(String text) {
        String n = bare(text);
        return n.matches("what can you do( offline| right now| on this phone)?")
                || n.matches("what are your (basic )?capabilities")
                || n.matches("what are you able to do( offline| right now)?");
    }
}
