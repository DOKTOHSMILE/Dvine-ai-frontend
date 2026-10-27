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

    private static final Pattern FORM = Pattern.compile(
            "^(?:please |can you |could you |now )?(?:form|shape|sculpt|mould|mold|model|build|make|create|turn into|transform into|become)"
                    + "(?: yourself| it| them| the dots| the shape)?(?: into| in the shape of| like)?(?: me| us)?(?: a| an| the| some)? (.+?)(?: in 3d| in three d| for me| please)?[.!?]*$");

    /**
     * What to shape in "form a dragon", "build me a lion in 3D", "turn into a star" ("dragon"), or null. Only for
     * things the 3D creation engine doesn't know (it has its own houses, cars, tables …): the caller asks it first.
     */
    public static String formSubject(String said) {
        if (said == null) {
            return null;
        }
        String t = said.trim().toLowerCase(Locale.US);
        Matcher m = FORM.matcher(t);
        if (!m.matches()) {
            return null;
        }
        String s = m.group(1).trim();
        if (s.isEmpty() || s.split(" ").length > 6
                || s.matches("^(it|that|this|them|me|you|yourself|your .*|my .*|sure|sense|(note|call|list|plan|reminder|wish|decision|choice|mistake|joke|song|story|poem|cup of .*|coffee|tea|food|breakfast|lunch|dinner)\\b.*|"
                + "(bigger|smaller|larger|faster|slower|louder|quieter|red|blue|green|yellow|brighter|darker).*|it .*|(drawing|picture|sketch)\\b.*)$")) {
            return null;
        }
        return s;
    }

    /** Whether a drawing request asks for 3D ("draw a car in 3d"). */
    public static boolean in3d(String said) {
        return said != null && said.toLowerCase(Locale.US).matches(".*\\b(in 3d|in three d|3d|three dimensional)\\b.*");
    }

    /** What to draw in "draw me a red bicycle" ("red bicycle"), or null when it isn't a drawing request. */
    public static String subject(String said) {
        if (said == null) {
            return null;
        }
        Matcher m = ASK.matcher(said.trim().toLowerCase(Locale.US));
        if (!m.matches()) {
            return null;
        }
        String s = m.group(1).trim().replaceAll("\\s+(in 3d|in three d|in three dimensions)$", "").replaceAll("^3d ", "");
        return s.isEmpty() || s.split(" ").length > 8 ? null : s;
    }

    /**
     * The instructions: the subject as its 3D parts in words (solid, colour, where, size), one per line; the app
     * builds them ({@link ai.dvine.render.field.SolidSpec}). Words, because a small model places parts far better
     * by "above the body" than by coordinates.
     */
    public static String system() {
        return "You plan 3D models built from simple solids. List the subject's real visible parts, biggest first, one per line, as:\n"
                + "part | solid | colour | where | size\n"
                + "solid: sphere, oval, box, cylinder, cone, pyramid, ring or disc.\n"
                + "colour: white, grey, silver, red, orange, yellow, gold, green, darkgreen, teal, cyan, blue, navy, sky,"
                + " purple, pink, brown, tan, beige or skin.\n"
                + "where: for the first part 'middle centre'; for the others 'on X', 'above X', 'below X', 'left of X',"
                + " 'right of X', 'in front of X', 'behind X' or 'around X', where X is a part already listed;"
                + " add ', left' or ', right' to shift it within X; add ', both sides' for a matching pair.\n"
                + "size: tiny, small, medium, large or huge, and optionally wide, tall, deep, flat or thin;"
                + " a cylinder or disc can be sideways (lying along left-right) or forward.\n\n"
                + "Example, a snowman:\n"
                + "base | sphere | white | middle centre | large\n"
                + "middle | sphere | white | above base | medium\n"
                + "head | sphere | white | above middle | small\n"
                + "nose | cone | orange | in front of head | tiny forward\n"
                + "eyes | sphere | navy | on head, left | tiny, both sides\n"
                + "hat | cylinder | navy | above head | small\n"
                + "brim | disc | navy | below hat | small flat\n"
                + "arms | cylinder | brown | left of middle | small thin sideways, both sides\n\n"
                + "Example, a fish:\n"
                + "body | oval | orange | middle centre | large wide\n"
                + "tail | cone | orange | left of body | medium sideways\n"
                + "fin | cone | gold | above body | small\n"
                + "eye | sphere | white | on body, right | tiny\n"
                + "pupil | sphere | navy | in front of eye | tiny\n\n"
                + "Start with the subject's own biggest part, not a stand or base unless it has one."
                + " Choose each part's solid by its real shape: a wheel is a disc, a mast is a cylinder, a sail is a box"
                + " (flat), a head is a sphere.\n"
                + "Use 4 to 14 parts. Never copy an example: list the parts the asked-for subject really has."
                + " Write only the part lines. /no_think";
    }

    /** The model from what the planner wrote; empty when it wrote nothing usable. */
    public static ai.dvine.render.field.SolidSpec read(String written) {
        return ai.dvine.render.field.SolidSpec.parse(written);
    }

    /** As {@link #read(String)}, leaving out a base the subject doesn't have. */
    public static ai.dvine.render.field.SolidSpec read(String written, String subject) {
        return ai.dvine.render.field.SolidSpec.parse(written, subject);
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
