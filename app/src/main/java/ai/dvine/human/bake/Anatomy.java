package ai.dvine.human.bake;

import ai.dvine.face.Look;
import ai.dvine.face.nano.Sculpt;

/**
 * The persona's own head, as a smooth signed distance field: eyes, nose,
 * lips, ears, jaw and the upper neck, in head units (crown y = +1, chin
 * y = -1, face toward +z). The body below it is the standard body
 * ({@link StandardBody}), whose neck runs up into this head.
 */
public final class Anatomy {
    private Anatomy() {
    }

    /** Head units → body units. */
    public static final float HEAD_SCALE = 0.34f;
    public static final float HEAD_Y = 0.66f;
    /** Eye centres and radius, head units. */
    public static final float EYE_X = 0.31f, EYE_Y = 0.02f, EYE_Z = 0.6f, EYE_R = 0.17f;
    public static final float MOUTH_Y = -0.61f;
    /** The head piece stops here (inside the body's neck). */
    public static final float HEAD_CUT_Y = -1.5f;

    // ------------------------------------------------------------ head

    /** The head and upper neck. Eyeballs are separate meshes, so the sockets are left open. */
    public static Sculpt head(boolean female, Look l) {
        float w = l.width, j = l.jaw, n = l.nose, e = l.eyes, lip = l.lips;
        Sculpt s = new Sculpt();
        s.blend(0.3f).ellipsoid(0f, 0.2f, -0.08f, (female ? 0.72f : 0.76f) * w, 0.82f, female ? 0.9f : 0.94f);   // cranium
        if (!female) {
            s.blend(0.24f).box(0f, 0.4f, -0.08f, 0.62f * w, 0.56f, 0.8f, 0.3f);                                 // squared crown
        }
        s.blend(0.28f).ellipsoid(0f, 0.3f, 0.4f, 0.6f * w, 0.5f, 0.45f);                                         // forehead
        s.blend(0.3f).ellipsoid(0f, -0.32f, 0.22f, (female ? 0.58f : 0.64f) * w, 0.62f, 0.66f);                  // midface
        if (female) {
            s.blend(0.26f).ellipsoid(0f, -0.62f, 0.05f, 0.5f * w * j, 0.34f, 0.66f)                             // jaw
                    .blend(0.16f).ellipsoid(0f, -0.87f, 0.44f, 0.16f * j, 0.12f, 0.19f);                        // chin
        } else {
            s.blend(0.2f).box(0f, -0.6f, 0.04f, 0.58f * w * j, 0.32f, 0.6f, 0.24f)                               // square jaw
                    .blend(0.12f).box(0f, -0.86f, 0.42f, 0.22f * j, 0.13f, 0.17f, 0.11f);                        // chin
        }
        s.blend(0.15f)
                .ellipsoid(0.44f * w, -0.13f, 0.52f, 0.2f, 0.12f, 0.19f)                                          // cheekbones
                .ellipsoid(-0.44f * w, -0.13f, 0.52f, 0.2f, 0.12f, 0.19f)
                .blend(0.12f)
                .ellipsoid(0f, 0.17f, 0.7f, 0.55f * w, female ? 0.08f : 0.11f, female ? 0.13f : 0.17f)          // brow ridge
                .blend(0.08f)
                .carve(EYE_X, EYE_Y, 0.93f, 0.18f * e, 0.115f * e, 0.18f)                                          // sockets
                .carve(-EYE_X, EYE_Y, 0.93f, 0.18f * e, 0.115f * e, 0.18f)
                .blend(0.07f)
                .capsule(0f, 0.07f, 0.8f, 0.05f * n, 0f, -0.3f, 0.98f, 0.066f * n)                                // nose bridge
                .blend(0.06f)
                .ellipsoid(0f, -0.34f, 0.98f, 0.1f * n, 0.08f * n, 0.085f)                                         // nose tip
                .ellipsoid((female ? 0.085f : 0.1f) * n, -0.38f, 0.89f, 0.07f * n, 0.06f, 0.065f)                 // nostril wings
                .ellipsoid(-(female ? 0.085f : 0.1f) * n, -0.38f, 0.89f, 0.07f * n, 0.06f, 0.065f)
                .blend(0.045f)
                .ellipsoid(0f, -0.56f, 0.87f, 0.21f * lip, (female ? 0.06f : 0.052f) * lip, 0.09f)               // upper lip
                .ellipsoid(0f, -0.675f, 0.85f, 0.19f * lip, (female ? 0.07f : 0.06f) * lip, 0.09f)               // lower lip
                .blend(0.01f)
                .carve(0f, MOUTH_Y, 0.95f, 0.17f * lip, 0.011f, 0.13f)                                             // the parting of the lips
                .blend(0.05f)
                .ellipsoid(0.78f * w, 0f, -0.08f, 0.08f, 0.2f, 0.14f)                                             // ears
                .ellipsoid(-0.78f * w, 0f, -0.08f, 0.08f, 0.2f, 0.14f)
                .blend(0.03f)
                .carve(0.85f * w, 0.0f, -0.06f, 0.04f, 0.13f, 0.08f)
                .carve(-0.85f * w, 0.0f, -0.06f, 0.04f, 0.13f, 0.08f)
                .blend(0.22f)
                .capsule(0f, -0.55f, -0.12f, female ? 0.4f : 0.47f, 0f, -1.7f, -0.06f, female ? 0.44f : 0.52f); // neck, as wide as the standard body's
        if (!female) {
            s.blend(0.06f).ellipsoid(0f, -1.18f, 0.27f, 0.07f, 0.09f, 0.07f);                                     // larynx
        }
        if (female && l.hair > 0.05f) {
            // the hair volume: a shell over the skull, swept back, thicker toward the crown
            float grow = 0.05f + 0.03f * l.hair;
            s.blend(0.12f).ellipsoid(0f, 0.26f, -0.16f, 0.74f * w + grow, 0.84f + grow, 0.9f + grow * 1.6f);
        }
        return s;
    }

    /** Hair is everything of the female head above this hairline (front high, sides low, back lowest). */
    public static boolean hairArea(float x, float y, float z) {
        return y - 0.36f + (z - 0.55f) * 0.55f > 0f && y > -0.3f + Math.max(0f, z) * 0.45f;
    }
}
