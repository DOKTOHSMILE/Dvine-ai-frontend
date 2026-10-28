package ai.dvine.llm;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asking the language model for a picture: it can't make images, but it can describe one as simple shapes
 * ({@link ai.dvine.render.field.DrawSpec}) that the particles then form. Pure Java.
 *
 * Improved for correct object identification from search facts and accurate structural part lists.
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
     *
     * Strengthened to force correct identification from provided facts and realistic anatomy/structure.
     */
    public static String system() {
        return "You are an expert 3D model planner. First correctly identify the real-world object from the description and facts given. "
                + "Then list ONLY its actual visible structural parts, biggest and most characteristic first. One part per line:\n"
                + "part | solid | colour | where | size\n"
                + "solid: sphere, oval, box, cylinder, cone, pyramid, ring or disc. Choose the solid that matches the real geometry (wheel=disc, leg=cylinder, head=sphere, body=oval or box).\n"
                + "colour: white, grey, silver, red, orange, yellow, gold, green, darkgreen, teal, cyan, blue, navy, sky,"
                + " purple, pink, brown, tan, beige or skin. Use realistic colours for the object.\n"
                + "where: first part 'middle centre'. Others: 'on X', 'above X', 'below X', 'left of X', 'right of X', 'in front of X', 'behind X' or 'around X' where X is a previously listed part. "
                + "Add ', both sides' for pairs (eyes, wheels, legs). Add ', left' or ', right' for offsets.\n"
                + "size: tiny, small, medium, large or huge; optionally wide, tall, deep, flat or thin. Cylinders/discs can be sideways or forward.\n\n"
                + "CRITICAL RULES:\n"
                + "- Identify the object correctly. A cat has body, head, ears, legs, tail — NEVER walls, roof or windows.\n"
                + "- Use the facts provided to get the right parts and proportions. Do not invent unrelated objects.\n"
                + "- 5 to 12 parts. Start with the largest real part (body, hull, chassis).\n"
                + "- Write ONLY the part lines. No explanations. /no_think";
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
        return "Identify and accurately plan the 3D parts for: " + subject + ".";
    }

    /**
     * As {@link #user(String)}, with what was found out about it online (its looks from every side, in a few
     * sentences) so the parts are the real ones; {@code facts} may be null.
     */
    public static String user(String subject, String facts) {
        if (facts == null || facts.trim().isEmpty()) {
            return user(subject);
        }
        String f = facts.trim();
        return "Object: " + subject + ". Facts from search: " + (f.length() > 600 ? f.substring(0, 600) : f)
                + "\nFirst correctly identify this object, then list only its real structural parts in the exact format.";
    }

    /** A second try after a plan that was too thin or about something else: stricter words. */
    public static String retry(String subject) {
        return "The previous plan was wrong or incomplete. Correctly identify " + article(subject)
                + " and list 6 to 12 of its ACTUAL real-world parts only (body, head, limbs, wheels, etc. as appropriate). "
                + "One per line in the part | solid | colour | where | size format. Nothing else.";
    }

    /**
     * Whether a plan is good enough to show for the subject: at least three parts, at least two different part
     * names, and not a plan for some other thing (a "building" plan for a cat: its parts are walls, roof, windows).
     */
    public static boolean usable(ai.dvine.render.field.SolidSpec plan, String subject) {
        if (plan == null || plan.parts.size() < 3) {
            return false;
        }
        java.util.Set<String> names = new java.util.HashSet<String>();
        for (ai.dvine.render.field.SolidSpec.Part p : plan.parts) {
            names.add(p.name);
        }
        if (names.size() < 2) {
            return false;
        }
        String s = subject.toLowerCase(Locale.US);
        boolean building = s.matches(".*\\b(house|home|building|tower|castle|church|shop|school|hut|barn|cabin|office|hotel|temple|skyscraper)s?\\b.*");
        if (!building) {
            int walls = 0;
            for (String n : names) {
                if (n.matches(".*\\b(wall|walls|roof|storey|story|floor|chimney|balcony|window|door)\\b.*")) {
                    walls++;
                }
            }
            if (walls >= 1) {  // stricter: even one building part rejects for non-buildings
                return false;
            }
        }
        // Additional check: if subject is animal, require body or head or leg or similar
        if (s.matches(".*\\b(cat|dog|lion|tiger|bird|fish|horse|elephant|animal|creature)s?\\b.*")) {
            boolean hasBody = false;
            for (String n : names) {
                if (n.matches(".*\\b(body|head|torso|chest|leg|paw|tail|wing|fin)\\b.*")) {
                    hasBody = true;
                    break;
                }
            }
            if (!hasBody) return false;
        }
        return true;
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
