package ai.dvine.library;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the user says about saving and finding drawings, in words: "save it as Robo", "save this drawing, call it
 * my red car", "save it as Tom: a cat with a long tail", "name it Speedy", "save it" (then asked for a name),
 * "save it to my phone as an STL", "export it for 3D printing", "show me Robo", "open my drawing of a red car",
 * "what drawings have I saved", "delete the drawing called Robo". Pure Java.
 */
public final class LibraryWords {
    public enum Kind { SAVE, EXPORT, OPEN, LIST, DELETE }

    public final Kind kind;
    /** SAVE: the name (null: ask for one); OPEN, DELETE: the name or description. */
    public final String name;
    /** SAVE: what the user said about it, or "". */
    public final String description;
    /** OPEN: whether the words were plainly about a saved drawing ("my drawing …"), not perhaps a new one. */
    public final boolean plainlySaved;

    private LibraryWords(Kind kind, String name, String description, boolean plainlySaved) {
        this.kind = kind;
        this.name = name;
        this.description = description;
        this.plainlySaved = plainlySaved;
    }

    private static final String IT = "(?:it|this|that|the drawing|this drawing|the design|this design|my design|my drawing|the model|this model|the 3d model|the 3d design|this 3d design|the thing)";
    private static final Pattern SAVE = Pattern.compile("^(?:save|keep|store|remember) " + IT
            + "(?:,? (?:as|under|with the name|and call it|and name it|call it|name it|named|called|by the name(?: of)?) (.+?))?"
            + "(?:(?:,|:| -| and) (?:described as |description |it'?s |it is |which is |showing |about )?(.+))?$");
    private static final Pattern NAME = Pattern.compile("^(?:name|call|title) " + IT + " (.+?)(?:(?:,|:| -) (.+))?$");
    private static final Pattern EXPORT = Pattern.compile("^(?:save|export|download|send|put|store|write) " + IT
            + "(?: (?:to|on|onto|into|in) (?:my |the )?(?:phone|device|downloads?|files?|storage|computer|gallery))?"
            + "(?: (?:as|in|to|into|for) (?:an? |the )?(.+?))?(?: (?:to|on) (?:my |the )?(?:phone|device|downloads|files))?$");
    private static final Pattern OPEN = Pattern.compile("^(?:show|open|load|bring up|bring back|find|get|pull up|display|let me see)(?: me)?(?: back)?"
            + " (?:my |the )?(?:(saved) )?(?:(drawing|design|model) )?(?:(?:called|named|of|i saved as|i called) )?(.+?)(?: (?:again|that i saved|i saved|i made))?$");
    private static final Pattern DELETE = Pattern.compile("^(?:delete|remove|forget|erase|throw away) (?:my |the )?(?:saved )?(?:drawing|design|model) (?:called |named |of )?(.+)$");

    /** The library request the words make, or null. {@code exportWords}: export when a file type or the phone is named. */
    public static LibraryWords parse(String said) {
        if (said == null) {
            return null;
        }
        String t = said.trim().replaceAll("[.!?]+$", "").replaceAll("(?i)^(please|can you|could you|now|okay|ok|dvine|ivie|obi|ibiko),? ", "")
                .replaceAll("(?i)^(please|can you|could you) ", "").replaceAll("(?i),? please$", "").trim();
        String low = t.toLowerCase(Locale.US);
        if (low.matches("^(what|which) (drawings|designs|models|things) (have i|did i|do i have|are) (saved|kept|stored)?.*|^(list|show me|show) (all )?(my|the) (saved )?(drawings|designs|models)( i saved)?$|^(what have i saved|my drawings|my saved drawings)$")) {
            return new LibraryWords(Kind.LIST, null, "", true);
        }
        Matcher m = DELETE.matcher(low);
        if (m.matches()) {
            return new LibraryWords(Kind.DELETE, original(t, m.group(1)), "", true);
        }
        // a file on the phone in a 3D format: an export, not a save into the library
        boolean fileWords = low.matches(".*\\b(phone|device|downloads?|files?|storage|gallery|computer|glb|gltf|obj|stl|ply|fbx|3d format|3d file|file format|format|3d print\\w*|blender|unity|unreal|godot|maya|printer)\\b.*|.*\\bfor (my |a |the )?games?\\b.*");
        m = EXPORT.matcher(low);
        if (fileWords && (m.matches() || low.matches("^(export|download) .*"))) {
            return new LibraryWords(Kind.EXPORT, null, "", true);
        }
        m = SAVE.matcher(low);
        if (m.matches()) {
            String name = m.group(1) == null ? null : original(t, m.group(1));
            String about = m.group(2) == null ? "" : original(t, m.group(2));
            if (name == null && !about.isEmpty()) {
                name = about; // "save it, Robo": the bit after the comma is the name
                about = "";
            }
            return new LibraryWords(Kind.SAVE, strip(name), about, true);
        }
        m = NAME.matcher(low);
        if (m.matches()) {
            return new LibraryWords(Kind.SAVE, strip(original(t, m.group(1))), m.group(2) == null ? "" : original(t, m.group(2)), true);
        }
        m = OPEN.matcher(low);
        if (m.matches() && m.group(3) != null && !m.group(3).matches("^(it|that|this|them|me|you|up|the time|the weather)$")) {
            boolean plain = m.group(1) != null || m.group(2) != null || low.matches(".*\\b(i saved|saved|i made|again|i called)\\b.*");
            return new LibraryWords(Kind.OPEN, original(t, m.group(3)), "", plain);
        }
        return null;
    }

    /** The same words as said (their capitals kept), found again in the original. */
    private static String original(String t, String lowPart) {
        int i = t.toLowerCase(Locale.US).indexOf(lowPart);
        return i < 0 ? lowPart : t.substring(i, i + lowPart.length());
    }

    private static String strip(String name) {
        if (name == null) {
            return null;
        }
        String n = name.replaceAll("^[\"'“‘]+|[\"'”’]+$", "").replaceAll("(?i)^(the name |a name )", "").trim();
        return n.isEmpty() ? null : n;
    }

    /** A name taken from a reply to "What should I call it?" ("call it Robo", "Robo", "let's say Robo"). */
    public static String nameFrom(String reply) {
        if (reply == null) {
            return null;
        }
        String r = reply.trim().replaceAll("[.!?]+$", "").replaceAll("(?i)^(call it|name it|let'?s call it|let'?s say|how about|maybe|i'?ll call it|its name is|the name is|name|save it as)\\s+", "");
        return strip(r);
    }
}
