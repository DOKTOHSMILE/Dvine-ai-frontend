package ai.dvine.human.build;

/**
 * Head units ↔ body units. Head units are the standard head layout the face
 * code is written in (eyes at ±{@link Anatomy#EYE_X}, {@link Anatomy#EYE_Y},
 * {@link Anatomy#EYE_Z}; the lips part at {@link Anatomy#MOUTH_Y}; the chin at
 * {@link #CHIN_Y}; crown near y = +1; face toward +z). A frame is fitted to a
 * real head from its eyes, lip line and chin, so blinks, the jaw, smiles,
 * teeth, face regions, hair and the inner head all land on that head's own
 * features. Height is mapped in two pieces that meet at the lip line: one
 * scale above it (eyes to lips), one below (lips to chin).
 */
public final class HeadFrame {
    /** The chin's height in head units. */
    public static final float CHIN_Y = -1f;

    public final float ox, oy, oz, sx, sy, sz;
    /** The vertical scale below the lip line. */
    public final float syLow;

    public HeadFrame(float ox, float oy, float oz, float sx, float sy, float sz, float syLow) {
        if (!(sx > 0f) || !(sy > 0f) || !(sz > 0f) || !(syLow > 0f)) {
            throw new IllegalArgumentException("head frame scales must be positive");
        }
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.syLow = syLow;
    }

    /**
     * The frame of a head with these eyeball centres (left at +x), lip line
     * and chin heights: across from the eye spacing, up from eye to lips and
     * from lips to chin, depth as across.
     */
    public static HeadFrame fit(float[] eyeL, float[] eyeR, float lipY, float chinY) {
        float half = (eyeL[0] - eyeR[0]) * 0.5f;
        float eyeY = (eyeL[1] + eyeR[1]) * 0.5f, eyeZ = (eyeL[2] + eyeR[2]) * 0.5f;
        if (!(half > 0f) || !(eyeY > lipY) || !(lipY > chinY)) {
            throw new IllegalArgumentException("eyes, lips and chin must be apart and in order");
        }
        float sx = half / Anatomy.EYE_X;
        float sy = (eyeY - lipY) / (Anatomy.EYE_Y - Anatomy.MOUTH_Y);
        float low = (lipY - chinY) / (Anatomy.MOUTH_Y - CHIN_Y);
        float sz = sx;
        return new HeadFrame((eyeL[0] + eyeR[0]) * 0.5f, eyeY - Anatomy.EYE_Y * sy, eyeZ - Anatomy.EYE_Z * sz, sx, sy, sz, low);
    }

    /** The frame an asset carries. */
    public static HeadFrame of(ai.dvine.human.HumanAsset a) {
        float[] f = a.headFrame;
        return new HeadFrame(f[0], f[1], f[2], f[3], f[4], f[5], f.length > 6 ? f[6] : f[4]);
    }

    /** As stored in an asset. */
    public float[] toArray() {
        return new float[]{ox, oy, oz, sx, sy, sz, syLow};
    }

    /** This frame scaled by {@code k} about the point (px, py, pz). */
    public HeadFrame scaled(float k, float px, float py, float pz) {
        return new HeadFrame(px + (ox - px) * k, py + (oy - py) * k, pz + (oz - pz) * k, sx * k, sy * k, sz * k, syLow * k);
    }

    /** The vertical scale at a head-units height. */
    public float scaleY(float y) {
        return y >= Anatomy.MOUTH_Y ? sy : syLow;
    }

    public float[] toBody(float x, float y, float z) {
        float by = y >= Anatomy.MOUTH_Y ? oy + y * sy : oy + Anatomy.MOUTH_Y * sy + (y - Anatomy.MOUTH_Y) * syLow;
        return new float[]{ox + x * sx, by, oz + z * sz};
    }

    public float[] toHead(float x, float y, float z) {
        float lip = oy + Anatomy.MOUTH_Y * sy;
        float hy = y >= lip ? (y - oy) / sy : Anatomy.MOUTH_Y + (y - lip) / syLow;
        return new float[]{(x - ox) / sx, hy, (z - oz) / sz};
    }

    /** A length in head units across → body units (for sizes given in head units). */
    public float across(float headUnits) {
        return headUnits * sx;
    }
}
