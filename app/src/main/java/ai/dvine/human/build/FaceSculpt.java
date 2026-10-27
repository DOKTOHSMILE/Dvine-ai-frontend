package ai.dvine.human.build;

import ai.dvine.face.Look;

/**
 * Shapes the standard head into the persona's face, in head units (see
 * {@link HeadFrame}). Each persona has its own likeness, after the DVINE
 * reference heads: Obi a squared, flat-topped crown, a strong straight jaw
 * and chin, defined cheekbones and brow, prominent ears and full lips; Ivie
 * an oval, delicate face with a refined, slightly upturned nose, full lips and
 * soft cheekbones. The {@link Look} the user approved (face width, jaw, nose,
 * lips) scales on top of that. Everything is a smooth offset field, faded out
 * toward the neck, so the surface stays continuous.
 */
final class FaceSculpt {
    private final boolean female;
    private final Look look;

    FaceSculpt(boolean female, Look look) {
        this.female = female;
        this.look = look;
    }

    /** The sculpted position of a head point (head units), weighted by how much it belongs to the head. */
    float[] apply(float x, float y, float z, float headWeight) {
        float w = headWeight * smooth((y + 1.45f) / 0.35f);
        if (w <= 0f) {
            return new float[]{x, y, z};
        }
        float ax = Math.abs(x), side = Math.signum(x);
        float nx = x, ny = y, nz = z;
        if (!female) {
            // a squared, flat-topped crown: the top flattened, the upper sides filled out
            float top = smooth((y - 0.45f) / 0.5f);
            ny = y - (y - 0.45f) * 0.2f * top;
            nx *= 1f + 0.1f * top * smooth((0.75f - Math.abs(z + 0.1f)) / 0.5f);
            // a strong, straight jaw and a squared chin pushed forward
            float jaw = smooth((-0.4f - y) / 0.45f) * underChin(y);
            nx *= 1f + 0.16f * jaw * smooth((z + 0.3f) / 0.4f);
            float chin = smooth((-0.72f - y) / 0.15f) * underChin(y) * smooth((0.4f - ax) / 0.2f);
            nz += 0.06f * chin * smooth(z / 0.3f);
            ny -= 0.03f * chin;
            // cheekbones and the brow ridge
            nx += side * 0.04f * gauss(ax - 0.47f, y + 0.12f, 0.12f, 0.1f) * smooth((z - 0.2f) / 0.3f);
            nz += 0.035f * gauss(ax - 0.3f, y - 0.17f, 0.18f, 0.06f) * smooth((z - 0.45f) / 0.2f);
            // prominent ears
            if (ax > 0.62f && Math.abs(y) < 0.38f && z < 0.25f) {
                nx += side * 0.04f * smooth((ax - 0.62f) / 0.12f);
            }
        } else {
            // an oval, delicate face: the jaw slimmer, the chin small
            float jaw = smooth((-0.45f - y) / 0.45f) * underChin(y);
            nx *= 1f - 0.07f * jaw;
            // a refined nose, its tip slightly turned up
            float nose = gauss(x, y + 0.25f, 0.16f, 0.2f) * smooth((z - 0.6f) / 0.2f);
            nx -= x * 0.1f * nose;
            ny += 0.025f * nose * smooth((-0.15f - y) / 0.2f);
            // soft cheekbones
            nx += side * 0.025f * gauss(ax - 0.45f, y + 0.12f, 0.12f, 0.1f) * smooth((z - 0.2f) / 0.3f);
        }
        // full lips for both
        float lips = gauss(x, y - Anatomy.MOUTH_Y, 0.2f, 0.09f) * smooth((z - 0.55f) / 0.15f);
        ny += (y - Anatomy.MOUTH_Y) * (female ? 0.25f : 0.15f) * lips;
        nz += (female ? 0.025f : 0.015f) * lips;
        // the look the user approved
        nx *= 1f + (look.width - 1f) * smooth((z + 0.6f) / 0.6f);
        float lowJaw = smooth((-0.45f - y) / 0.4f) * underChin(y);
        nx *= 1f + (look.jaw - 1f) * lowJaw;
        float noseArea = gauss(x, y + 0.25f, 0.14f, 0.18f) * smooth((z - 0.7f) / 0.15f);
        nx += x * (look.nose - 1f) * noseArea;
        nz += (look.nose - 1f) * 0.12f * noseArea;
        float lipArea = gauss(x, y - Anatomy.MOUTH_Y, 0.2f, 0.08f) * smooth((z - 0.55f) / 0.15f);
        nx += x * (look.lips - 1f) * 0.5f * lipArea;
        ny += (y - Anatomy.MOUTH_Y) * (look.lips - 1f) * lipArea;
        return new float[]{x + (nx - x) * w, y + (ny - y) * w, z + (nz - z) * w};
    }

    /** 1 down to the chin, fading to 0 just under it, so the neck is never reshaped with the jaw. */
    private static float underChin(float y) {
        return smooth((y + 1.15f) / 0.12f);
    }

    private static float gauss(float dx, float dy, float sx, float sy) {
        return (float) Math.exp(-(dx * dx) / (2 * sx * sx) - (dy * dy) / (2 * sy * sy));
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }
}
