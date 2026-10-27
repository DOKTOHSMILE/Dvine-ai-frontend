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
    public enum Op { BIGGER, SMALLER, SPIN, STOP, TURN, LEFT, RIGHT, UP, DOWN, COLOUR, SCATTER, GATHER, CLEAR, RESET }

    public final Op op;
    /** The colour for COLOUR (ARGB). */
    public final int colour;

    private ShapeCommand(Op op, int colour) {
        this.op = op;
        this.colour = colour;
    }

    private static final String IT = "(?:it|that|this|the (?:drawing|picture|shape|model|thing|object|image)|them)";
    private static final Pattern COLOUR = Pattern.compile("^(?:make|turn|colou?r|paint) " + IT + " (?:in )?([a-z#0-9]+)$");

    public static ShapeCommand parse(String said) {
        if (said == null) {
            return null;
        }
        String t = said.toLowerCase(Locale.US).trim().replaceAll("[.!?]+$", "").replaceAll("^(please|now|okay|ok|can you|could you) ", "")
                .replaceAll(" please$", "").trim();
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
