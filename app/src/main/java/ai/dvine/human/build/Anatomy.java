package ai.dvine.human.build;

import ai.dvine.face.Look;
import ai.dvine.face.nano.DotBody;
import ai.dvine.face.nano.Sculpt;
import ai.dvine.human.rig.Skeleton;

/**
 * The anatomical sculpts the meshes are built from, as smooth signed
 * distance fields. Three pieces per variant, each meshed at the resolution
 * it needs: the head (fine: eyes, nose, lips, ears, jaw), the body (torso,
 * neck, arms to the wrists, legs and feet) and the hands (fingers and
 * thumbs, finest). Pieces overlap inside the body at the neck and wrists so
 * there is no visible seam.
 *
 * Head pieces use head units (crown y = +1, chin y = -1, face toward +z);
 * the body uses body units; hands use arm-local units (x along the arm).
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
                .capsule(0f, -0.55f, -0.12f, female ? 0.31f : 0.38f, 0f, -1.7f, -0.06f, female ? 0.34f : 0.42f); // neck
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

    // ------------------------------------------------------------ body

    /** Torso, neck, arms to the wrists, legs and feet, tagged by bone. */
    public static Sculpt body(Skeleton sk) {
        boolean f = sk.female;
        float sh = sk.sh, hx = sk.hx;
        Sculpt s = new Sculpt();
        s.blend(0.16f).tag(DotBody.CHEST)
                .ellipsoid(0f, -0.44f, 0f, sh * (f ? 0.84f : 0.9f), 0.52f, f ? 0.27f : 0.29f)            // rib cage
                .ellipsoid(0f, -0.2f, 0.02f, sh * (f ? 0.74f : 0.8f), 0.2f, 0.22f)                        // upper chest: broad shoulders
                .ellipsoid(0f, -0.04f, -0.05f, sh * 0.7f, 0.12f, 0.16f);                                // trapezius
        if (f) {
            s.blend(0.1f).ellipsoid(0.16f, -0.42f, 0.16f, 0.14f, 0.13f, 0.13f).ellipsoid(-0.16f, -0.42f, 0.16f, 0.14f, 0.13f, 0.13f);
        } else {
            s.blend(0.12f).ellipsoid(0.2f, -0.3f, 0.14f, 0.22f, 0.14f, 0.13f).ellipsoid(-0.2f, -0.3f, 0.14f, 0.22f, 0.14f, 0.13f); // pectorals
        }
        s.blend(0.08f)
                .capsule(0.05f, -0.05f, 0.13f, 0.03f, sh * 0.92f, -0.01f, 0.04f, 0.035f)                  // collarbones
                .capsule(-0.05f, -0.05f, 0.13f, 0.03f, -sh * 0.92f, -0.01f, 0.04f, 0.035f);
        s.blend(0.2f).tag(DotBody.SPINE)
                .ellipsoid(0f, -1.04f, 0.03f, f ? 0.28f : 0.37f, 0.42f, 0.21f)                                // a tapered waist
                .blend(0.12f).ellipsoid(0f, -0.95f, 0.12f, f ? 0.17f : 0.2f, 0.32f, 0.1f);                 // abdomen
        s.blend(0.18f).tag(DotBody.PELVIS)
                .ellipsoid(0f, -1.5f, 0f, f ? 0.42f : 0.44f, 0.27f, 0.24f)
                .ellipsoid(0.15f, -1.6f, -0.11f, f ? 0.17f : 0.15f, 0.19f, 0.16f)                         // glutes
                .ellipsoid(-0.15f, -1.6f, -0.11f, f ? 0.17f : 0.15f, 0.19f, 0.16f);
        s.blend(0.12f).tag(DotBody.NECK).capsule(0f, -0.12f, -0.02f, f ? 0.13f : 0.155f, 0f, 0.16f, 0f, f ? 0.125f : 0.15f);
        for (int side = -1; side <= 1; side += 2) {
            int o = side > 0 ? 0 : 3;
            float a = f ? 0.86f : 1f;
            s.blend(f ? 0.1f : 0.16f).tag(DotBody.ARM_L + o);
            armBall(s, side, sh, 0.03f, -0.03f, 0f, (f ? 0.145f : 0.135f) * a);                              // deltoid, blended into the shoulder
            armCapsule(s, side, sh, 0f, 0f, 0f, (f ? 0.12f : 0.1f) * a, 0.82f, 0f, 0f, 0.075f * a);
            armCapsule(s, side, sh, 0.2f, 0.02f, 0.03f, 0.1f * a, 0.62f, 0.01f, 0.03f, 0.085f * a);           // biceps
            armCapsule(s, side, sh, 0.25f, -0.01f, -0.04f, 0.095f * a, 0.7f, 0f, -0.04f, 0.075f * a);         // triceps
            s.blend(0.08f).tag(DotBody.FORE_L + o);
            armCapsule(s, side, sh, 0.82f, 0f, 0f, 0.075f * a, Skeleton.WRIST, 0f, 0f, 0.046f * a);
            armCapsule(s, side, sh, 0.9f, 0.01f, 0f, 0.085f * a, 1.25f, 0.005f, 0f, 0.064f * a);             // forearm muscle
            s.blend(0.12f).tag(DotBody.THIGH_L + o)
                    .capsule(side * (f ? hx : hx - 0.01f), -1.62f, 0f, f ? 0.21f : 0.21f, side * (hx + 0.02f), -2.88f, 0.03f, 0.115f)
                    .ellipsoid(side * (f ? hx + 0.02f : hx - 0.01f), -2.18f, 0.06f, f ? 0.17f : 0.16f, 0.48f, f ? 0.17f : 0.17f)   // quadriceps
                    .ellipsoid(side * (hx + 0.01f), -2.2f, -0.07f, 0.15f, 0.45f, 0.13f);                      // hamstrings
            s.blend(0.06f).ellipsoid(side * (hx + 0.02f), -2.86f, 0.11f, 0.065f, 0.07f, 0.05f);              // kneecap
            s.blend(0.1f).tag(DotBody.SHIN_L + o)
                    .capsule(side * (hx + 0.02f), -2.9f, 0.03f, 0.105f, side * (hx + 0.03f), -4.0f, -0.02f, 0.056f)
                    .ellipsoid(side * (hx + 0.03f), -3.22f, -0.07f, f ? 0.1f : 0.11f, 0.34f, f ? 0.11f : 0.12f);  // calf
            s.blend(0.04f).sphere(side * (hx + 0.08f), -3.98f, -0.02f, 0.035f).sphere(side * (hx - 0.02f), -3.98f, -0.02f, 0.035f);
            s.blend(0.06f).tag(DotBody.FOOT_L + o)
                    .sphere(side * (hx + 0.03f), -4.1f, -0.05f, 0.065f)                                      // heel
                    .box(side * (hx + 0.04f), -4.13f, 0.12f, 0.07f, 0.05f, 0.18f, 0.04f)                     // foot
                    .box(side * (hx + 0.05f), -4.16f, 0.31f, 0.075f, 0.03f, 0.06f, 0.025f);                  // toes
        }
        return s;
    }

    // ------------------------------------------------------------ hands

    /**
     * One hand in arm-local coordinates (the left side's frame), palm down,
     * fingers straight, tagged by finger bone (thumb 0, index 2, …; segment 2
     * adds 1). The wrist end (x < 1.49) is cut away inside the forearm.
     */
    public static Sculpt hand(boolean female) {
        float a = female ? 0.9f : 1f;
        Sculpt s = new Sculpt();
        s.blend(0.03f).tag(100)
                .capsule(1.46f, -0.005f, 0f, 0.045f * a, 1.56f, -0.008f, 0f, 0.048f * a)                    // wrist
                .box(1.655f, -0.01f, 0f, 0.11f, 0.026f * a, 0.075f * a, 0.022f);                             // palm
        s.blend(0.02f).ellipsoid(1.62f, -0.018f, 0.055f * a, 0.07f, 0.03f, 0.04f);                           // thumb muscle
        for (int f = 1; f < 5; f++) {
            float z = Skeleton.FINGER_Z[f] * a, len = Skeleton.FINGER_LEN[f] * a, r = Skeleton.FINGER_R0[f] * a * 0.9f;
            float mid = Skeleton.KNUCKLE + len * Skeleton.SPLIT;
            // a small blend: webbed at the knuckles, separate fingers beyond
            s.blend(0.005f).tag(f * 2)
                    .capsule(Skeleton.KNUCKLE - 0.03f, -0.012f, z, r, mid, -0.016f, z * Skeleton.SPREAD_MID, r * 0.88f);
            s.tag(f * 2 + 1)
                    .capsule(mid, -0.016f, z * Skeleton.SPREAD_MID, r * 0.88f, Skeleton.KNUCKLE + len, -0.02f,
                            z * Skeleton.SPREAD_TIP, r * 0.72f);
        }
        float tl = Skeleton.FINGER_LEN[0] * a;
        float[] b = Skeleton.THUMB_BASE, d = Skeleton.THUMB_DIR;
        float mx = b[0] + d[0] * tl * Skeleton.SPLIT, my = b[1] + d[1] * tl * Skeleton.SPLIT, mz = b[2] * a + d[2] * tl * Skeleton.SPLIT;
        s.blend(0.015f).tag(0).capsule(b[0], b[1], b[2] * a, 0.027f * a, mx, my, mz, 0.022f * a);
        s.tag(1).capsule(mx, my, mz, 0.022f * a, b[0] + d[0] * tl, b[1] + d[1] * tl, b[2] * a + d[2] * tl, 0.017f * a);
        return s;
    }

    private static void armCapsule(Sculpt s, int side, float sh, float ax, float ay, float az, float ra,
                                   float bx, float by, float bz, float rb) {
        float[] p = Skeleton.armToBody(side, sh, ax, ay, az), q = Skeleton.armToBody(side, sh, bx, by, bz);
        s.capsule(p[0], p[1], p[2], ra, q[0], q[1], q[2], rb);
    }

    private static void armBall(Sculpt s, int side, float sh, float x, float y, float z, float r) {
        float[] p = Skeleton.armToBody(side, sh, x, y, z);
        s.sphere(p[0], p[1], p[2], r);
    }
}
