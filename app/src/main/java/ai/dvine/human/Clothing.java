package ai.dvine.human;

import java.util.ArrayList;
import java.util.List;

import ai.dvine.face.nano.DotBody;

/**
 * Everyday clothes for the lifelike look. Each garment is a set of the body's
 * own triangles, chosen by body region and rest position (neckline, sleeve
 * length, hem, waist and trouser hem from the persona's joints), drawn a few
 * millimetres out along the skin's normals. Because a garment shares the
 * skinned surface, it moves, bends and turns with the body and cannot fall
 * behind it. Obi: a charcoal crew-neck T-shirt, dark jeans, white trainers;
 * Ivie: a gold short-sleeved top with a scoop neck, high-waisted black
 * trousers and dark flats. Pure Java.
 */
public final class Clothing {
    /** One garment: its triangles (vertex index triples into the body mesh) and how it is drawn. */
    public static final class Garment {
        public final String name;
        /** Linear RGB. */
        public final float[] color;
        /** How far out from the skin, body units (about 3 mm per 0.01). */
        public final float push;
        /** 0 smooth knit .. 1 coarse twill (weave strength in the shader). */
        public final float weave;
        public final int[] tri;
        /**
         * The exact edges, trimmed per pixel on the rest surface (the triangles are chosen with a margin):
         * {kind (0 top, 1 trousers, 2 shoes), neckline front, neckline back, scoop, hem or waist or top,
         * sleeve end (0..1 along the upper arm), lowest y}.
         */
        public final float[] clip;

        Garment(String name, float[] color, float push, float weave, float[] clip, int[] tri) {
            this.name = name;
            this.color = color;
            this.push = push;
            this.weave = weave;
            this.clip = clip;
            this.tri = tri;
        }
    }

    private Clothing() {
    }

    public static Garment[] design(HumanAsset a) {
        return design(a, a.female ? ai.dvine.face.Look.PUFFS : ai.dvine.face.Look.FADE);
    }

    /**
     * The outfit and, for shell hairstyles, the hair: the fade's short top or a short afro's round
     * volume (see {@link ai.dvine.face.Look#hairStyle}); a beard when the look has one. The shoes are geometry
     * of their own (see {@link HumanAsset#shoeRange}).
     */
    public static Garment[] design(HumanAsset a, String hairStyle) {
        float[] j = a.joints;
        float neck = j[DotBody.NECK * 3 + 1], pelvis = j[DotBody.PELVIS * 3 + 1];
        float ankle = (j[DotBody.FOOT_L * 3 + 1] + j[DotBody.FOOT_R * 3 + 1]) * 0.5f;
        boolean f = a.female;
        List<Garment> out = new ArrayList<Garment>();
        final float m = 0.12f; // selection margin; the shader trims the exact edge
        // after the character sheets: a cream ribbed tee for both (Ivie's cropped to meet high-waisted joggers),
        // cargo joggers with ankle cuffs (beige for Ivie, black for Obi) and socks (the trainers are geometry)
        final float neckFront = neck - (f ? 0.24f : 0.16f), neckBack = neck - 0.1f, scoop = f ? 0.1f : 0.03f;
        final float waist = pelvis + (f ? 0.48f : 0.36f);
        final float hem = f ? waist - 0.06f : pelvis + 0.03f, sleeveEnd = f ? 0.36f : 0.5f;
        float[] cream = rgb(0.66f, 0.6f, 0.5f);
        out.add(new Garment("t-shirt", cream, 0.04f, -1f, new float[]{0f, neckFront, neckBack, scoop, hem, sleeveEnd, 0f},
                select(a, (r, x, y, z, along) -> {
                    boolean torso = r == Regions.CHEST || r == Regions.BACK || r == Regions.BELLY || r == Regions.HIPS
                            || r == Regions.SHOULDER_L || r == Regions.SHOULDER_R || r == Regions.NECK;
                    boolean sleeve = (r == Regions.UPPER_ARM_L || r == Regions.UPPER_ARM_R || r == Regions.SHOULDER_L
                            || r == Regions.SHOULDER_R) && along < sleeveEnd + m;
                    float neckline = Math.max(neckFront + scoop, neckBack) + 4f * Math.max(0f, Math.abs(x) - (f ? 0.26f : 0.2f));
                    return (torso || sleeve) && y < neckline + m && y > hem - m;
                })));
        // cargo joggers: from the waist to a ribbed cuff a little above the ankle; clip {1, cargo pockets, cuff band, knee y}
        final float cuff = ankle + 0.27f;
        float knee = (a.joints[DotBody.SHIN_L * 3 + 1] + a.joints[DotBody.SHIN_R * 3 + 1]) * 0.5f;
        out.add(new Garment("cargo joggers", f ? rgb(0.33f, 0.27f, 0.21f) : rgb(0.014f, 0.014f, 0.016f), 0.07f, 0.6f,
                new float[]{1f, 1f, 0.11f, knee, waist, 0f, cuff},
                select(a, (r, x, y, z, along) -> {
                    boolean legs = r == Regions.HIPS || r == Regions.BELLY || r == Regions.BACK || r == Regions.THIGH_L
                            || r == Regions.THIGH_R || r == Regions.KNEE_L || r == Regions.KNEE_R || r == Regions.SHIN_L
                            || r == Regions.SHIN_R;
                    return legs && y < waist + m && y > cuff - m;
                })));
        // socks: from inside the trainer up under the cuff
        final float top = ankle;
        out.add(new Garment("socks", f ? cream : rgb(0.012f, 0.012f, 0.013f), 0.016f, -1f, new float[]{5f, 0f, 0f, 0f, cuff + 0.05f, 0f, top - 0.05f},
                select(a, (r, x, y, z, along) -> (r == Regions.SHIN_L || r == Regions.SHIN_R || r == Regions.FOOT_L
                        || r == Regions.FOOT_R) && y < cuff + 0.05f + m && y > top - 0.05f - m)));
        final ai.dvine.human.bake.HeadFrame head = ai.dvine.human.bake.HeadFrame.of(a);
        float[] hairColor = rgb(0.006f, 0.005f, 0.0045f);
        if (ai.dvine.face.Look.FADE.equals(hairStyle)) {
            // the fade: short coily hair on top with a sharp line-up
            out.add(new Garment("hair", hairColor, 0.07f, 0f, new float[]{3f, 0f, 0f, 0f, 0f, 0f, 0f},
                    selectHead(a, head, h -> topHair(h[0], h[1], h[2], 0.1f))));
        } else if (ai.dvine.face.Look.SHORT_AFRO.equals(hairStyle)) {
            // a short afro: a round volume of coils over the whole hairline (about 45 mm deep)
            out.add(new Garment("hair", hairColor, 0.075f, 0f, new float[]{6f, 0f, 0f, 0f, 0f, 0f, 0f},
                    selectHead(a, head, h -> h[1] > afroLine(h[0], h[2]) - 0.12f && !(Math.abs(h[0]) > 0.58f && h[1] < 0.2f && Math.abs(h[2] + 0.05f) < 0.3f))));
        }
        if (!f && a.beard) {
            // a full beard, with thickness
            out.add(new Garment("beard", rgb(0.006f, 0.005f, 0.0045f), 0.032f, 0f, new float[]{4f, 0f, 0f, 0f, 0f, 0f, 0f},
                    selectHead(a, head, h -> beard(h[0], h[1], h[2], 0.05f))));
        }
        return out.toArray(new Garment[0]);
    }

    /**
     * Obi's top hair in head units (the shader trims with the same shape, margin 0): above a straight
     * line-up at the forehead with square temple corners, and above a high fade on the sides and back.
     */
    public static boolean topHair(float x, float y, float z, float margin) {
        float ax = Math.abs(x);
        float front = z > 0.25f && ax < 0.52f ? 0.63f : 0.44f; // the line-up across the forehead; the fade's top elsewhere
        return y > front - margin;
    }

    /** A short afro's hairline in head units: rounded across the forehead, down past the temples, low at the nape. */
    public static float afroLine(float x, float z) {
        float ax = Math.abs(x);
        float back = clamp01((0.1f - z) / 0.8f);
        back *= back; // the line stays high over the ears and drops behind them to the nape
        float front = 0.6f - 0.18f * Math.min(1f, (ax / 0.55f) * (ax / 0.55f));
        return front + (-0.8f - front) * back;
    }

    /** Obi's full beard in head units: jaw, chin and cheeks below a cheek line, the moustache, not the lips. */
    public static boolean beard(float x, float y, float z, float margin) {
        float ax = Math.abs(x);
        if (z < -0.42f - margin || y < -1.13f - margin) {
            return false;
        }
        if (sideburn(ax, y, z) > 0f) {
            return true;
        }
        if (ax > 0.78f + margin) {
            return false;
        }
        // a wider margin out on the cheeks (the shader thins the edge there), a tight one by the nose
        return y < cheekLine(ax) + (ax > 0.3f ? margin * 2.5f : margin);
    }

    /** The sideburns: in front of each ear, from the beard up into the fade (0..1 density). */
    public static float sideburn(float ax, float y, float z) {
        float band = clamp01((ax - 0.53f) / 0.06f) * clamp01((0.8f - ax) / 0.06f) * clamp01((z + 0.16f) / 0.1f) * clamp01((0.3f - z) / 0.08f);
        return band * (1f - clamp01((y - 0.1f) / 0.34f)) * clamp01((y + 0.2f) / 0.1f);
    }

    private static float clamp01(float t) {
        return Math.max(0f, Math.min(1f, t));
    }

    /** Where the beard meets the bare cheek (head units): over the upper lip, down across the cheek, up to the sideburn. */
    public static float cheekLine(float ax) {
        float t = Math.max(0f, Math.min(1f, (ax - 0.12f) / 0.5f));
        float dip = (float) Math.sin(Math.PI * t) * 0.12f;
        return -0.43f + 0.42f * t * t * (3f - 2f * t) - dip;
    }

    interface HeadRule {
        boolean covers(float[] headUnits);
    }

    private static int[] selectHead(HumanAsset a, ai.dvine.human.bake.HeadFrame head, HeadRule rule) {
        int[] tri = a.mesh.tri;
        float[] p = a.mesh.pos;
        byte[] reg = a.mesh.region;
        int skin = a.eyeRange[0] > 0 ? a.eyeRange[0] : a.mesh.vertexCount();
        int[] buf = new int[tri.length];
        int n = 0;
        for (int t = 0; t < tri.length; t += 3) {
            int i0 = tri[t], i1 = tri[t + 1], i2 = tri[t + 2];
            if (i0 >= skin || i1 >= skin || i2 >= skin) {
                continue;
            }
            int r = majority(reg[i0], reg[i1], reg[i2]);
            if (r > Regions.NECK) { // ear-labelled triangles reach onto the cheek; the shader cuts the ear itself out
                continue;
            }
            float[] h = head.toHead((p[i0 * 3] + p[i1 * 3] + p[i2 * 3]) / 3f, (p[i0 * 3 + 1] + p[i1 * 3 + 1] + p[i2 * 3 + 1]) / 3f,
                    (p[i0 * 3 + 2] + p[i1 * 3 + 2] + p[i2 * 3 + 2]) / 3f);
            if (rule.covers(h)) {
                buf[n++] = i0;
                buf[n++] = i1;
                buf[n++] = i2;
            }
        }
        return java.util.Arrays.copyOf(buf, n);
    }

    interface Rule {
        /** Region, triangle centre (rest), and for arms how far along the upper arm (0 shoulder .. 1 elbow). */
        boolean covers(int region, float x, float y, float z, float alongArm);
    }

    private static int[] select(HumanAsset a, Rule rule) {
        int[] tri = a.mesh.tri;
        float[] p = a.mesh.pos, j = a.joints;
        byte[] reg = a.mesh.region;
        int skin = a.eyeRange[0] > 0 ? a.eyeRange[0] : a.mesh.vertexCount(); // body surface only (not eyes or teeth)
        int[] buf = new int[tri.length];
        int n = 0;
        for (int t = 0; t < tri.length; t += 3) {
            int i0 = tri[t], i1 = tri[t + 1], i2 = tri[t + 2];
            if (i0 >= skin || i1 >= skin || i2 >= skin) {
                continue;
            }
            float x = (p[i0 * 3] + p[i1 * 3] + p[i2 * 3]) / 3f;
            float y = (p[i0 * 3 + 1] + p[i1 * 3 + 1] + p[i2 * 3 + 1]) / 3f;
            float z = (p[i0 * 3 + 2] + p[i1 * 3 + 2] + p[i2 * 3 + 2]) / 3f;
            int r = majority(reg[i0], reg[i1], reg[i2]);
            int arm = x >= 0f ? DotBody.ARM_L : DotBody.ARM_R, fore = x >= 0f ? DotBody.FORE_L : DotBody.FORE_R;
            float ax = j[fore * 3] - j[arm * 3], ay = j[fore * 3 + 1] - j[arm * 3 + 1], az = j[fore * 3 + 2] - j[arm * 3 + 2];
            float len2 = ax * ax + ay * ay + az * az;
            float along = len2 > 0f ? ((x - j[arm * 3]) * ax + (y - j[arm * 3 + 1]) * ay + (z - j[arm * 3 + 2]) * az) / len2 : 0f;
            if (rule.covers(r, x, y, z, along)) {
                buf[n++] = i0;
                buf[n++] = i1;
                buf[n++] = i2;
            }
        }
        return java.util.Arrays.copyOf(buf, n);
    }

    private static int majority(byte a, byte b, byte c) {
        return (b == c ? b : a) & 0xff;
    }

    private static float[] rgb(float r, float g, float b) {
        return new float[]{r, g, b};
    }
}
