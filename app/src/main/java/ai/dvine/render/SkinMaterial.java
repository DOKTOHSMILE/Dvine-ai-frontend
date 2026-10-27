package ai.dvine.render;

import ai.dvine.human.HumanAsset;
import ai.dvine.human.Regions;
import ai.dvine.human.bake.Anatomy;

/**
 * Per-vertex inputs of the lifelike skin (Mode A), four floats each:
 * material (0 skin, 2 teeth), warmth (blood close to the surface: cheeks,
 * nose, ears, lips, palms), and where face features may be drawn (1 head,
 * 0.5 ears, 0 body). The brows, hairline and lips themselves are drawn per
 * pixel in the shader from the rest position in head units, so they stay
 * crisp on a coarse mesh and follow each persona's own face. The functions
 * below are the same shapes in Java (for tests). Pure Java.
 */
public final class SkinMaterial {
    private SkinMaterial() {
    }

    public static float[] perVertex(HumanAsset a) {
        int n = a.mesh.vertexCount();
        float[] out = new float[n * 4];
        byte[] region = a.mesh.region;
        for (int v = 0; v < n; v++) {
            int r = region == null ? -1 : region[v] & 0xff;
            float mat = 0f, warm = 0f;
            switch (r) {
                case Regions.LIPS:
                    mat = 1f;
                    warm = 0.6f;
                    break;
                case Regions.TEETH:
                    mat = 2f;
                    break;
                case Regions.CHEEK_L:
                case Regions.CHEEK_R:
                    warm = 0.7f;
                    break;
                case Regions.NOSE:
                    warm = 0.55f;
                    break;
                case Regions.EAR_L:
                case Regions.EAR_R:
                    warm = 0.8f;
                    break;
                case Regions.PALM_L:
                case Regions.PALM_R:
                    warm = 0.3f;
                    break;
                default:
                    break;
            }
            boolean onHead = r >= 0 && r <= Regions.NECK && r != Regions.TEETH;
            boolean ear = r == Regions.EAR_L || r == Regions.EAR_R;
            if (r == Regions.LIPS) {
                mat = 0f; // the lips are drawn from their exact shape in the shader (the region is coarser)
            }
            out[v * 4] = mat;
            out[v * 4 + 1] = warm;
            // where the face features may be drawn: 1 on the head (brows, hairline, lips), 0.5 on the ears (no hair)
            out[v * 4 + 2] = onHead ? (ear ? 0.5f : 1f) : 0f;
            out[v * 4 + 3] = 0f;
        }
        return out;
    }

    /** An arched brow over each eye: thicker for Obi, finer and higher for Ivie. */
    static float brow(float x, float y, float z, boolean female) {
        float ax = Math.abs(x);
        if (z < 0.35f || ax < 0.07f || ax > 0.6f) {
            return 0f;
        }
        float u = (ax - 0.07f) / 0.5f; // 0 inner end .. 1 tail
        float arch = female ? 0.07f : 0.045f;
        float centre = Anatomy.EYE_Y + (female ? 0.215f : 0.195f) + arch * (float) Math.sin(Math.PI * Math.min(1f, u * 1.25f)) - 0.05f * u * u;
        float half = (female ? 0.028f : 0.042f) * (1f - 0.55f * u * u) + 0.012f * (1f - u);
        float d = Math.abs(y - centre);
        float w = smooth((half - d) / 0.02f + 0.5f);
        float ends = smooth(u / 0.08f) * smooth((1f - u) / 0.12f);
        return w * ends;
    }

    /** A short-hair cap: the hairline at the forehead, over the temples and around the ears to the nape. */
    static float hair(float x, float y, float z, boolean female) {
        float ax = Math.abs(x);
        float front = female ? 0.58f : 0.63f;
        // the hairline drops from the forehead (front) to the nape (back), lifting over the ears
        float back = smooth((0.35f - z) / 0.6f); // 0 at the face, 1 behind
        float line = front + (-0.85f - front) * back;
        float temple = smooth((ax - 0.45f) / 0.15f) * smooth((z + 0.1f) / 0.4f);
        line -= 0.35f * temple * (1f - back); // the hairline steps back a little over the temples
        float ear = smooth((ax - 0.55f) / 0.12f) * smooth((0.25f - Math.abs(z + 0.05f)) / 0.2f) * smooth((0.4f - Math.abs(y + 0.05f)) / 0.15f);
        float w = smooth((y - line) / 0.06f) * (1f - ear);
        return w;
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }
}
