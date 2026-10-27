package ai.dvine.face;

import java.util.Locale;

/**
 * How a persona looks. The user changes it by voice ("make your face slimmer",
 * "darker skin", "dot mode", "remove your hair"); the head is rebuilt only when
 * it changes, so drawing stays as cheap as before.
 */
public final class Look {
    public enum Style {
        SKIN,
        WIRE,
        DOTS
    }

    /** SKIN: lifelike lit skin (default); WIRE: the particle hologram; DOTS: the nano-dot swarm. */
    public Style style = Style.SKIN;
    /** 0 light .. 1 deep brown. */
    public float tone = 0.85f;
    public float width = 1f;
    public float height = 1f;
    public float jaw = 1f;
    public float eyes = 1f;
    public float nose = 1f;
    public float lips = 1f;
    /** 0 none, 1 default, up to 2 long. Obi starts at 0. */
    public float hair;
    /** The hairstyle (from the hair library): see {@link #HAIR_STYLES}. */
    public String hairStyle = FADE;

    public static final String PUFFS = "afro puffs", SHORT_AFRO = "short afro", CORNROWS = "cornrows",
            BANTU = "bantu knots", FADE = "fade", BALD = "bald";
    /** Every style, in the shader's order (index = its number there). */
    public static final String[] HAIR_STYLES = {PUFFS, FADE, SHORT_AFRO, CORNROWS, BANTU, BALD};

    /** The style's number for the renderer (0 afro puffs .. 5 bald). */
    public int hairStyleIndex() {
        for (int i = 0; i < HAIR_STYLES.length; i++) {
            if (HAIR_STYLES[i].equals(hairStyle)) {
                return i;
            }
        }
        return 1;
    }

    public static Look defaults(boolean ivie) {
        Look l = new Look();
        l.hair = ivie ? 1f : 0f;
        l.tone = ivie ? 0.80f : 0.88f;
        l.hairStyle = ivie ? PUFFS : FADE;
        return l;
    }

    public Look copy() {
        Look l = new Look();
        l.style = style;
        l.tone = tone;
        l.width = width;
        l.height = height;
        l.jaw = jaw;
        l.eyes = eyes;
        l.nose = nose;
        l.lips = lips;
        l.hair = hair;
        l.hairStyle = hairStyle;
        return l;
    }

    /** Skin colour from the tone: a warm brown ramp from caramel to deep espresso. */
    public int skinColor() {
        float t = clamp(tone, 0f, 1f);
        int r = (int) lerp(0xB0, 0x3E, t);
        int g = (int) lerp(0x7A, 0x24, t);
        int b = (int) lerp(0x52, 0x14, t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** True when geometry changed between two looks (needs a rebuild). */
    public boolean shapeDiffers(Look o) {
        return o == null || width != o.width || height != o.height || jaw != o.jaw || eyes != o.eyes
                || nose != o.nose || lips != o.lips || hair != o.hair || !hairStyle.equals(o.hairStyle);
    }

    public String serialize() {
        return String.format(Locale.US, "%s;%.2f;%.2f;%.2f;%.2f;%.2f;%.2f;%.2f;%.2f;%s",
                style.name(), tone, width, height, jaw, eyes, nose, lips, hair, hairStyle);
    }

    public static Look parse(String s, boolean ivie) {
        Look l = defaults(ivie);
        if (s == null) {
            return l;
        }
        String[] p = s.split(";");
        if (p.length != 9 && p.length != 10) {
            return l;
        }
        try {
            l.style = Style.valueOf(p[0]);
            l.tone = Float.parseFloat(p[1]);
            l.width = Float.parseFloat(p[2]);
            l.height = Float.parseFloat(p[3]);
            l.jaw = Float.parseFloat(p[4]);
            l.eyes = Float.parseFloat(p[5]);
            l.nose = Float.parseFloat(p[6]);
            l.lips = Float.parseFloat(p[7]);
            l.hair = Float.parseFloat(p[8]);
            if (p.length == 10) {
                for (String h : HAIR_STYLES) {
                    if (h.equals(p[9])) {
                        l.hairStyle = h;
                    }
                }
            }
        } catch (IllegalArgumentException bad) {
            return defaults(ivie);
        }
        l.clampAll();
        return l;
    }

    /**
     * Applies a spoken style request. Returns a short spoken confirmation,
     * or null when the text isn't a look request.
     */
    public String apply(String lower) {
        String t = lower.toLowerCase(Locale.US);
        String styleAsked = hairStyleIn(t);
        boolean about = t.contains("your") || t.contains("you look") || t.startsWith("make ") || t.startsWith("change ")
                || t.endsWith(" mode") || t.contains("skin") || t.contains("look") || styleAsked != null;
        if (!about) {
            return null;
        }
        boolean more = has(t, "bigger", "larger", "wider", "longer", "fuller", "more", "darker", "deeper", "taller");
        boolean less = has(t, "smaller", "slimmer", "thinner", "narrower", "shorter", "less", "lighter", "flatter", "smaller");
        String said = null;
        if (has(t, "dot mode", "dots mode", "nano dot", "dotted", "made of dots", "dot look")) {
            style = Style.DOTS;
            said = "Now I'm drawn in tiny dots.";
        } else if (has(t, "wireframe", "wire frame", "mesh mode", "hologram", "lines mode", "polygon", "triangle mode",
                "particle mode")) {
            style = Style.WIRE;
            said = "Back to my hologram.";
        } else if (t.contains("skin") && has(t, "darker", "deeper", "lighter", "brighter", "fairer")) {
            style = Style.SKIN;
            boolean darker = has(t, "darker", "deeper");
            tone += darker ? 0.08f : -0.08f;
            said = darker ? "A deeper skin tone." : "A lighter skin tone.";
        } else if (has(t, "skin mode", "solid", "with skin", "add skin", "show skin", "skin on", "lifelike", "realistic",
                "real skin", "human skin", "like a real person", "look human", "look real")) {
            style = Style.SKIN;
            said = "Real skin on. Ask me to change my skin tone, eyes, nose, lips, jaw or hair, or say hologram mode.";
        } else if (styleAsked != null) {
            hairStyle = styleAsked;
            if (hair < 0.5f) {
                hair = 1f;
            }
            said = BALD.equals(styleAsked) ? "Clean shaven head." : "New hairstyle: " + styleAsked + ".";
        } else if (t.contains("hair") && has(t, "remove", "no hair", "bald", "take off", "without")) {
            hair = 0f;
            hairStyle = BALD;
            said = "Hair off.";
        } else if (t.contains("hair") && (has(t, "add", "give", "grow", "with hair") || more || less)) {
            hair = less ? Math.max(0.5f, hair - 0.35f) : (hair < 0.5f ? 1f : hair + 0.35f);
            said = less ? "Shorter hair." : "Longer hair.";
        } else if (t.contains("eye") && (more || less)) {
            eyes += more ? 0.12f : -0.12f;
            said = more ? "Bigger eyes." : "Smaller eyes.";
        } else if (t.contains("nose") && (more || less)) {
            nose += more ? 0.2f : -0.2f;
            said = more ? "A stronger nose." : "A smaller nose.";
        } else if (t.contains("lip") && (more || less)) {
            lips += more ? 0.2f : -0.2f;
            said = more ? "Fuller lips." : "Thinner lips.";
        } else if ((t.contains("jaw") || t.contains("chin")) && (more || less)) {
            jaw += more ? 0.1f : -0.1f;
            said = more ? "A stronger jaw." : "A softer jaw.";
        } else if ((t.contains("face") || t.contains("head")) && has(t, "slimmer", "thinner", "narrower")) {
            width -= 0.06f;
            said = "Slimmer.";
        } else if ((t.contains("face") || t.contains("head")) && has(t, "wider", "fuller", "rounder")) {
            width += 0.06f;
            said = "A little fuller.";
        } else if ((t.contains("face") || t.contains("head")) && has(t, "longer", "taller")) {
            height += 0.06f;
            said = "A little longer.";
        } else if ((t.contains("face") || t.contains("head")) && has(t, "shorter")) {
            height -= 0.06f;
            said = "A little shorter.";
        } else if (has(t, "reset your look", "default look", "original look", "go back to normal", "reset your face")) {
            return null; // handled by the caller, which knows the persona
        }
        if (said != null) {
            clampAll();
        }
        return said;
    }

    private void clampAll() {
        tone = clamp(tone, 0f, 1f);
        width = clamp(width, 0.82f, 1.18f);
        height = clamp(height, 0.85f, 1.15f);
        jaw = clamp(jaw, 0.7f, 1.3f);
        eyes = clamp(eyes, 0.7f, 1.4f);
        nose = clamp(nose, 0.4f, 1.8f);
        lips = clamp(lips, 0.5f, 1.8f);
        hair = clamp(hair, 0f, 2f);
    }

    /** The hairstyle a request names ("give Ivie cornrows", "short afro", "bantu knots"), or null. */
    static String hairStyleIn(String t) {
        if (has(t, "afro puff", "puffs", "space bun", "two buns")) {
            return PUFFS;
        }
        if (has(t, "cornrow", "corn row", "braids", "braided back")) {
            return CORNROWS;
        }
        if (has(t, "bantu", "knots")) {
            return BANTU;
        }
        if (has(t, "short afro", "afro")) {
            return SHORT_AFRO;
        }
        if (has(t, "fade", "low cut", "lowcut", "line up", "line-up")) {
            return FADE;
        }
        if (has(t, "shave your head", "bald head", "go bald", "clean shaven head")) {
            return BALD;
        }
        return null;
    }

    private static boolean has(String t, String... words) {
        for (String w : words) {
            if (t.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
