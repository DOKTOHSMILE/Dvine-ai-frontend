package ai.dvine.human.build;

/**
 * Head units ↔ body units. Head units are the standard head layout the face
 * code is written in (eyes at ±{@link Anatomy#EYE_X}, {@link Anatomy#EYE_Y},
 * {@link Anatomy#EYE_Z}; the lips part at {@link Anatomy#MOUTH_Y}; crown
 * near y = +1, chin near −1, face toward +z). A frame is fitted to a real head
 * from its eyes and chin, so blinks, the jaw, smiles, teeth, face regions,
 * hair and the inner head all land on that head's own features.
 */
public final class HeadFrame {
    public final float ox, oy, oz, sx, sy, sz;

    public HeadFrame(float ox, float oy, float oz, float sx, float sy, float sz) {
        if (!(sx > 0f) || !(sy > 0f) || !(sz > 0f)) {
            throw new IllegalArgumentException("head frame scales must be positive");
        }
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
    }

    /** The chin's height in head units. */
    public static final float CHIN_Y = -1f;

    /**
     * The frame of a head with these eyeball centres (left at +x) and chin
     * height: across from the eye spacing, up from eye to chin, depth as
     * across.
     */
    public static HeadFrame fit(float[] eyeL, float[] eyeR, float chinY) {
        float half = (eyeL[0] - eyeR[0]) * 0.5f;
        float eyeY = (eyeL[1] + eyeR[1]) * 0.5f, eyeZ = (eyeL[2] + eyeR[2]) * 0.5f;
        if (!(half > 0f) || !(eyeY > chinY)) {
            throw new IllegalArgumentException("eyes must be apart and above the chin");
        }
        float sx = half / Anatomy.EYE_X;
        float sy = (eyeY - chinY) / (Anatomy.EYE_Y - CHIN_Y);
        float sz = sx;
        return new HeadFrame((eyeL[0] + eyeR[0]) * 0.5f, eyeY - Anatomy.EYE_Y * sy, eyeZ - Anatomy.EYE_Z * sz, sx, sy, sz);
    }

    public float[] toBody(float x, float y, float z) {
        return new float[]{ox + x * sx, oy + y * sy, oz + z * sz};
    }

    public float[] toHead(float x, float y, float z) {
        return new float[]{(x - ox) / sx, (y - oy) / sy, (z - oz) / sz};
    }

    /** A length in head units across → body units (for sizes given in head units). */
    public float across(float headUnits) {
        return headUnits * sx;
    }
}
