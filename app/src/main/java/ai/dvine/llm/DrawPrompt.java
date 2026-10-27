package ai.dvine.llm;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asking the language model for a picture: it can't make images, but it can describe one as simple shapes
 * ({@link ai.dvine.render.field.DrawSpec}) that the particles then form. Pure Java.
 */
public final class DrawPrompt {
    private DrawPrompt() {
    }


    private static final Pattern ASK = Pattern.compile(
            "^(?:please |can you |could you |will you )?(?:draw|sketch|paint|doodle|illustrate|show me a (?:picture|drawing|image) of"
                    + "|make (?:me )?a (?:picture|drawing|image) of)(?: me| us)?(?: a| an| the| some)? (.+?)(?: for me| please)?[.!?]*$");

    /** What to draw in "draw me a red bicycle" ("red bicycle"), or null when it isn't a drawing request. */
    public static String subject(String said) {
        if (said == null) {
            return null;
        }
        Matcher m = ASK.matcher(said.trim().toLowerCase(Locale.US));
        if (!m.matches()) {
            return null;
        }
        String s = m.group(1).trim();
        return s.isEmpty() || s.split(" ").length > 8 ? null : s;
    }

    /**
     * The instructions: the subject as its parts in words (shape, colour, where, size), one per line; the app lays
     * them out ({@link ai.dvine.render.field.PartSpec}). Words, because a small model places parts far better by
     * "above the body" than by coordinates.
     */
    public static String system() {
        return "You plan drawings. List the subject's real visible parts, biggest first, one per line, as:\n"
                + "part | shape | colour | where | size\n"
                + "shape: circle, oval, box, triangle or line.\n"
                + "colour: white, grey, silver, red, orange, yellow, gold, green, darkgreen, teal, cyan, blue, navy, sky,"
                + " purple, pink, brown, tan, beige or skin (bright: the background is black).\n"
                + "where: for the first part a place like 'middle centre', 'top left', 'bottom right'; for the others"
                + " 'on X', 'above X', 'below X', 'left of X' or 'right of X', where X is a part already listed.\n"
                + "size: tiny, small, medium, large or huge, and optionally wide, tall, thin; a triangle can point down,"
                + " left or right; a line can be vertical.\n\n"
                + "Example, a bird:\n"
                + "body | oval | sky | middle centre | large\n"
                + "head | circle | sky | right of body | medium\n"
                + "eye | circle | white | on head | tiny\n"
                + "beak | triangle | orange | right of head | small right\n"
                + "wing | triangle | blue | on body | medium\n"
                + "tail | triangle | blue | left of body | small left\n"
                + "legs | line | orange | below body | small vertical\n"
                + "branch | line | brown | below legs | huge thick\n\n"
                + "Example, a house:\n"
                + "walls | box | beige | lower centre | large\n"
                + "roof | triangle | red | above walls | large wide\n"
                + "door | box | brown | on walls | small tall\n"
                + "window | box | sky | on walls left | small\n"
                + "chimney | box | grey | above roof | tiny tall\n\n"
                + "Use 5 to 14 parts. Never copy an example: list the parts the asked-for subject really has."
                + " Write only the part lines. /no_think";
    }

    /** The drawing from what the model wrote: its parts laid out, or (if it wrote shapes instead) those shapes. */
    public static ai.dvine.render.field.DrawSpec read(String written) {
        ai.dvine.render.field.DrawSpec d = ai.dvine.render.field.PartSpec.layout(written);
        return d.items.size() >= 2 ? d : ai.dvine.render.field.DrawSpec.parse(written);
    }

    public static String user(String subject) {
        return "Draw " + subject + ".";
    }

    /** "a lighthouse", "an owl", "the moon", "two cats" as said. */
    public static String article(String subject) {
        String s = subject.trim();
        if (s.matches("^(a|an|the|some|my|your|two|three|four|five|\\d+) .*") || s.endsWith("s")) {
            return s;
        }
        return (s.matches("^[aeiou].*") ? "an " : "a ") + s;
    }
}
