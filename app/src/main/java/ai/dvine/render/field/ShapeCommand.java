package ai.dvine.render.field;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Changing the thing on show by voice: "make it bigger", "spin it", "stop spinning", "turn it around", "make it red",
 * "move it left", "scatter it", "put it back together", "clear it". Read only while something is shown, so "bigger"
 * can't be taken from another request. Pure Java.
 */
public final class ShapeCommand {
    public enum Op {
        BIGGER, SMALLER, SPIN, SPIN_VERTICAL, STOP, FASTER, SLOWER, TURN, TURN_BY, TILT_BY, VIEW, LEFT, RIGHT, UP, DOWN,
        COLOUR, SCATTER, GATHER, CLEAR, RESET
    }

    public final Op op;
    /** The colour for COLOUR (ARGB). */
    public final int colour;
    /**
     * SPIN, SPIN_VERTICAL: the direction (1 or -1: right / down, left / up); TURN_BY, TILT_BY: degrees; VIEW: the
     * turn (degrees) to look from, with {@link #b} the tilt.
     */
    public final float a, b;

    private ShapeCommand(Op op, int colour) {
        this(op, colour, 0f, 0f);
    }

    private ShapeCommand(Op op, int colour, float a, float b) {
        this.op = op;
        this.colour = colour;
        this.a = a;
        this.b = b;
    }

    /** The words the brain may rewrite any way of saying it into (see {@link #CANONICAL}). */
    public static final String[] CANONICAL = {"spin it", "spin it to the left", "rotate it vertically", "rotate it up",
            "stop rotating", "spin faster", "spin slower", "turn it around", "turn it left", "turn it right", "turn it 90 degrees",
            "tilt it up", "tilt it down", "turn it upside down", "show me the top", "show me the bottom", "show me the left side",
            "show me the right side", "show me the back", "show me the front", "reset the view", "make it bigger",
            "make it smaller", "move it left", "move it right", "move it up", "move it down", "scatter it",
            "put it back together", "clear it"};

    private static final String ROT = "(?:rotat\\w*|spin\\w*|turn\\w*|revolv\\w*|twirl\\w*|swivel\\w*|roll\\w*|tumbl\\w*|pivot\\w*|orbit\\w*|circl\\w*|whirl\\w*|flip\\w*|tilt\\w*|twist\\w*|go(?:ing)? round|go(?:ing)? around)";

    private static final String IT = "(?:it|that|this|the (?:drawing|picture|shape|model|thing|object|image)|them)";
    private static final Pattern COLOUR = Pattern.compile("^(?:make|turn|colou?r|paint) " + IT + " (?:in )?([a-z#0-9]+)$");

    public static ShapeCommand parse(String said) {
        if (said == null) {
            return null;
        }
        String t = said.toLowerCase(Locale.US).trim().replaceAll("[.!?]+$", "").replaceAll("^(please|now|okay|ok|can you|could you) ", "")
                .replaceAll(" please$", "").trim();
        ShapeCommand view = view(t);
        if (view != null) {
            return view;
        }
        if (t.matches("^(make )?" + IT + " (bigger|larger|grow)$|^(zoom in|bigger|grow it|enlarge it|scale it up)$")) return of(Op.BIGGER);
        if (t.matches("^(make )?" + IT + " (smaller|tinier|shrink)$|^(zoom out|smaller|shrink it|scale it down)$")) return of(Op.SMALLER);
        if (t.matches("^(spin|rotate|turn) " + IT + "( round| around)?( slowly| faster)?$|^(keep )?spinning$")) {
            return of(t.matches(".*(turn|rotate) " + IT + " (round|around)$") && t.startsWith("turn") ? Op.TURN : Op.SPIN);
        }
        if (t.matches("^stop (spinning|rotating|turning)( it)?$|^(hold|keep) " + IT + " still$")) return of(Op.STOP);
        if (t.matches("^(show me )?(the )?(back|other side)( of " + IT + ")?$|^flip " + IT + "( around)?$")) return of(Op.TURN);
        Matcher m = Pattern.compile("^(?:move|push|slide|shift) " + IT + " (left|right|up|down)(?: a bit)?$").matcher(t);
        if (m.matches()) {
            return of(Op.valueOf(m.group(1).toUpperCase(Locale.US)));
        }
        if (t.matches("^(scatter|explode|break|blow up|shatter|dissolve) " + IT + "( up| apart)?$|^break " + IT + " apart$")) return of(Op.SCATTER);
        if (t.matches("^(put|bring) " + IT + " back( together)?$|^(rebuild|reassemble|re-form|reform|gather) " + IT + "$")) return of(Op.GATHER);
        if (t.matches("^(clear|remove|hide|delete|erase) " + IT + "$|^(clear|remove|hide|delete|erase) (the )?(drawing|picture|sketch|image|shape|model)$")) {
            return of(Op.CLEAR);
        }
        if (t.matches("^(reset|restore) " + IT + "$|^(back to|make it) (normal|the original)( size)?$")) return of(Op.RESET);
        m = COLOUR.matcher(t);
        if (m.matches()) {
            Integer c = DrawSpec.Palette.colour(m.group(1));
            if (c != null) {
                return new ShapeCommand(Op.COLOUR, c);
            }
        }
        return null;
    }

    /**
     * Turning it, every way people say it: "stop rotating", "freeze", "rotate it horizontally", "spin it side to side",
     * "rotate it vertically", "rotate it up to down", "make it tumble", "spin it to the left", "turn it 90 degrees",
     * "tilt it forward", "turn it upside down", "show me the top", "faster", "reset the view". Null when the words are
     * about something else.
     */
    static ShapeCommand view(String t) {
        String s = " " + t.replaceAll("[,]", " ").replaceAll("\\s+", " ") + " ";
        boolean rot = s.matches(".* " + ROT + " .*") || s.matches(".* (vertically|horizontally|upside down|side to side|up and down|clockwise|anticlockwise|counterclockwise|counter clockwise) .*");
        // stopping
        if (s.matches("^ (stop|pause|freeze|halt|cease|end|quit|don'?t|do not|no more|enough) ?(it |the |that |with the |from )*(" + ROT + "|moving|motion|movement|spinning it|that|it)?( it| that| around| round| now)* $")
                && !s.matches("^ (stop|pause) (it|that) $") || s.matches("^ (hold|keep|stay|leave|let) (it |that )?(still|steady|in place|there|be|stay still)( now)? $")
                || s.matches("^ (freeze|freeze it|stay still|be still|no rotation|no spinning|hold on|hold it) $")) {
            return s.matches("^ (stop|pause) (it|that) $") ? null : of(Op.STOP);
        }
        if (s.matches("^ (spin |rotate |turn |go )?(it |that )?(faster|quicker|more quickly|speed up|speed it up)( please)? $|^ (spin|rotate|turn) (it )?faster $")) {
            return of(Op.FASTER);
        }
        if (s.matches("^ (spin |rotate |turn |go )?(it |that )?(slower|more slowly|slow down|slow it down|gently)( please)? $")) {
            return of(Op.SLOWER);
        }
        if (s.matches("^ (reset|restore|straighten|level|centre|center) (the )?(view|angle|rotation|orientation|it|it out|it up)( please)? $|^ (face me|look at me|back to (the )?front|front view|normal view|show me the front|show the front)( view)? $")) {
            return new ShapeCommand(Op.VIEW, 0, 0f, 0f);
        }
        // looking from a side
        java.util.regex.Matcher side = Pattern.compile("^ (?:show (?:me )?|let me see |view |look at |look from )?(?:it )?(?:from )?(?:the |its )?(top|above|bottom|underneath|below|under ?side|left|right|back|rear|side|left side|right side|other side|front)(?: side| view| of it| of " + IT + ")? (?:view )?$").matcher(s);
        if (side.matches() && !s.matches("^ (left|right|up|down) $")) {
            switch (side.group(1).replace(" ", "")) {
                case "top": case "above": return new ShapeCommand(Op.VIEW, 0, 0f, 90f);
                case "bottom": case "underneath": case "below": case "underside": return new ShapeCommand(Op.VIEW, 0, 0f, -90f);
                case "left": case "leftside": return new ShapeCommand(Op.VIEW, 0, 90f, 0f);
                case "right": case "rightside": case "side": return new ShapeCommand(Op.VIEW, 0, -90f, 0f);
                case "back": case "rear": case "otherside": return new ShapeCommand(Op.VIEW, 0, 180f, 0f);
                default: return new ShapeCommand(Op.VIEW, 0, 0f, 0f);
            }
        }
        if (!rot && !s.matches(".* (degrees?|quarter|half) .*")) {
            return null;
        }
        if (s.matches(".* (it|that) (around|round)? ?(to|towards?) (me|the camera) .*")) {
            return new ShapeCommand(Op.VIEW, 0, 0f, 0f);
        }
        if (s.matches(".* upside ?down .*|.* on its head .*")) {
            return new ShapeCommand(Op.TILT_BY, 0, 180f, 0f);
        }
        boolean vertical = s.matches(".* (vertical\\w*|up and down|up to down|up-down|top to bottom|bottom to top|down to up|down and up|head over heels|end over end|over and over|forwards?|backwards?|tumbl\\w*|flip\\w*( it| that)? over|x axis|x-axis|on its side|toward me|towards me|away from me) .*")
                || s.matches(".* " + ROT + " (it |that )?(up|down|upwards?|downwards?) .*") || s.matches("^ (tumble|flip|roll)( it| that)?( over)? $");
        boolean horizontal = s.matches(".* (horizontal\\w*|side to side|left to right|right to left|sideways|y axis|y-axis|like a (carousel|turntable|top)|clockwise|anticlockwise|counterclockwise|counter clockwise) .*");
        if (horizontal) {
            vertical = false;
        }
        float dir = s.matches(".* (to the left|left|leftwards?|anticlockwise|counterclockwise|counter clockwise|right to left) .*") ? -1f : 1f;
        float vdir = s.matches(".* (up|upwards?|backwards?|bottom to top|down to up|away from me) .*") && !s.matches(".* up (to|and) down .*") ? -1f : 1f;
        // how far: "90 degrees", "a quarter turn", "half way", "a bit"
        Float deg = null;
        java.util.regex.Matcher m = Pattern.compile(" (\\d{1,3}(?:\\.\\d+)?) ?(?:degrees?|deg|°)").matcher(s);
        if (m.find()) {
            deg = Float.parseFloat(m.group(1));
        } else if (s.matches(".* (a )?quarter( turn)? .*")) {
            deg = 90f;
        } else if (s.matches(".* half( a)?( turn| way)? .*")) {
            deg = 180f;
        } else if (s.matches(".* (a bit|a little|slightly|a touch|a tad) .*")) {
            deg = 25f;
        }
        boolean continuous = s.matches(".* (keep|keeps|continu\\w*|nonstop|non-stop|all the time|forever|constantly|repeatedly|over and over|spinning|rotating|revolving|tumbling|slowly|round and round|around and around) .*")
                || s.matches("^ (make |let |have )?(it |that )?(spin|rotate|revolve|twirl|whirl|tumble|orbit|roll)( it| that| the \\w+)?( .*)? $") && deg == null
                && !s.matches(".* " + ROT + " (it |that )?(around|round) $");
        if (deg != null && !continuous) {
            if (vertical) {
                return new ShapeCommand(Op.TILT_BY, 0, deg * vdir, 0f);
            }
            return new ShapeCommand(Op.TURN_BY, 0, deg * dir, 0f);
        }
        if (continuous) {
            return new ShapeCommand(vertical ? Op.SPIN_VERTICAL : Op.SPIN, 0, vertical ? vdir : dir, 0f);
        }
        // a single turn: "turn it around", "turn it left", "tilt it forward"
        if (s.matches(".* (around|round) .*") && !vertical) {
            return of(Op.TURN);
        }
        if (s.matches(".* tilt\\w* .*") || vertical) {
            return new ShapeCommand(Op.TILT_BY, 0, (vertical || s.matches(".* (up|down|forwards?|backwards?) .*") ? vdir : 1f) * 30f, 0f);
        }
        if (s.matches(".* (left|right) .*")) {
            return new ShapeCommand(Op.TURN_BY, 0, dir * 45f, 0f);
        }
        return null;
    }

    private static ShapeCommand of(Op op) {
        return new ShapeCommand(op, 0);
    }

    /** A picture recoloured: its shading kept, its hue the new colour. */
    public static Shape tint(Shape s, int colour) {
        float r = ((colour >> 16) & 255) / 255f, g = ((colour >> 8) & 255) / 255f, b = (colour & 255) / 255f;
        float[] rgb = new float[s.count * 3];
        for (int i = 0; i < s.count; i++) {
            float lum = Math.min(1f, (0.3f * s.rgb[i * 3] + 0.59f * s.rgb[i * 3 + 1] + 0.11f * s.rgb[i * 3 + 2]) * 1.6f);
            float k = 0.35f + 0.85f * lum;
            rgb[i * 3] = Math.min(1f, r * k);
            rgb[i * 3 + 1] = Math.min(1f, g * k);
            rgb[i * 3 + 2] = Math.min(1f, b * k);
        }
        return new Shape(s.xyz, rgb, s.count);
    }

    /** The same points blown out into a cloud around where they were. */
    public static Shape scatter(Shape s, long seed) {
        java.util.Random rnd = new java.util.Random(seed);
        float[] xyz = new float[s.count * 3];
        for (int i = 0; i < s.count; i++) {
            float x = s.xyz[i * 3], y = s.xyz[i * 3 + 1], z = s.xyz[i * 3 + 2];
            float k = 1.6f + rnd.nextFloat() * 1.4f;
            xyz[i * 3] = x * k + (rnd.nextFloat() - 0.5f) * 1.2f;
            xyz[i * 3 + 1] = y * k + (rnd.nextFloat() - 0.5f) * 1.2f;
            xyz[i * 3 + 2] = z * k + (rnd.nextFloat() - 0.5f) * 1.2f;
        }
        return new Shape(xyz, s.rgb, s.count);
    }
}
