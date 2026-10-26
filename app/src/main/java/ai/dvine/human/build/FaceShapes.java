package ai.dvine.human.build;

import java.util.ArrayList;
import java.util.List;

import ai.dvine.human.HumanAsset;
import ai.dvine.human.Regions;
import ai.dvine.human.mesh.TriMesh;

/**
 * Facial blend shapes, computed once from the rest mesh: sparse vertex
 * offsets for the jaw (a rotation about the jaw hinge with a small forward
 * glide), smiles and frowns per side, brows, blinks, wide eyes and lip
 * shapes. Expressions and speech visemes are mixes of these; nothing is
 * regenerated while the face moves, so its identity stays fixed.
 */
public final class FaceShapes {
    private FaceShapes() {
    }

    public static final String[] NAMES = {"jawOpen", "smileL", "smileR", "frownL", "frownR", "browUpL", "browUpR",
            "browDown", "blinkL", "blinkR", "wideL", "wideR", "lipsRound", "lipsWide", "lipsPress"};

    static HumanAsset.Morph[] build(TriMesh m, int headCount, int solidCount, int teethStart, int teethCount,
                                    int irisStart, int irisEnd) {
        List<HumanAsset.Morph> out = new ArrayList<HumanAsset.Morph>();
        int n = m.vertexCount();
        for (String name : NAMES) {
            List<Integer> idx = new ArrayList<Integer>();
            List<float[]> d = new ArrayList<float[]>();
            for (int v = 0; v < n; v++) {
                boolean head = v < headCount;
                boolean teeth = v >= teethStart && v < teethStart + teethCount;
                boolean line = v >= solidCount;
                int region = m.region[v];
                if (!head && !teeth && !line || region == Regions.HAIR || v >= irisStart && v < irisEnd) {
                    continue;
                }
                float hs = Anatomy.HEAD_SCALE;
                float x = m.pos[v * 3] / hs, y = (m.pos[v * 3 + 1] - Anatomy.HEAD_Y) / hs, z = m.pos[v * 3 + 2] / hs;
                float[] o = offset(name, x, y, z, teeth);
                if (o == null) {
                    continue;
                }
                if (Math.abs(o[0]) + Math.abs(o[1]) + Math.abs(o[2]) < 1e-5f) {
                    continue;
                }
                idx.add(v);
                d.add(new float[]{o[0] * hs, o[1] * hs, o[2] * hs});
            }
            int[] index = new int[idx.size()];
            float[] delta = new float[idx.size() * 3];
            for (int i = 0; i < index.length; i++) {
                index[i] = idx.get(i);
                System.arraycopy(d.get(i), 0, delta, i * 3, 3);
            }
            out.add(new HumanAsset.Morph(name, index, delta));
        }
        return out.toArray(new HumanAsset.Morph[0]);
    }

    /** The offset (head units) of a rest point for one shape, or null when it does not move. */
    static float[] offset(String name, float x, float y, float z, boolean teeth) {
        float ax = Math.abs(x);
        float my = Anatomy.MOUTH_Y;
        switch (name) {
            case "jawOpen": {
                if (!teeth && (z < -0.3f || y > my + 0.004f || y < -1.4f)) {
                    return null;
                }
                // weight: everything below the lip parting (and the lower teeth), fading toward the neck and the ears
                float w = teeth ? (y < my ? 1f : 0f) : smooth((my + 0.004f - y) / 0.03f);
                w *= smooth((y + 1.4f) / 0.3f) * smooth((0.72f - ax) / 0.2f) * smooth((z + 0.3f) / 0.4f);
                if (w <= 0f) {
                    return null;
                }
                // rotate about the jaw hinge (just in front of the ears) and glide slightly forward
                double a = Math.toRadians(17f) * w;
                float hy = -0.05f, hz = -0.02f;
                float ry = y - hy, rz = z - hz;
                float ny = (float) (ry * Math.cos(a) - rz * Math.sin(a)) + hy;
                float nz = (float) (ry * Math.sin(a) + rz * Math.cos(a)) + hz;
                return new float[]{0f, ny - y, nz - z + 0.02f * w};
            }
            case "smileL":
            case "smileR": {
                float side = name.endsWith("L") ? 1f : -1f;
                if (x * side < -0.05f || z < 0.3f) {
                    return null;
                }
                float corner = gauss(x - side * 0.2f, y - my, 0.1f, 0.08f);
                float cheek = gauss(x - side * 0.38f, y + 0.3f, 0.12f, 0.12f);
                return new float[]{side * 0.05f * corner, 0.07f * corner + 0.04f * cheek, -0.03f * corner + 0.015f * cheek};
            }
            case "frownL":
            case "frownR": {
                float side = name.endsWith("L") ? 1f : -1f;
                if (x * side < -0.05f || z < 0.3f) {
                    return null;
                }
                float corner = gauss(x - side * 0.2f, y - my, 0.08f, 0.06f);
                return new float[]{0f, -0.035f * corner, 0f};
            }
            case "browUpL":
            case "browUpR": {
                float side = name.endsWith("L") ? 1f : -1f;
                if (x * side < 0f || z < 0.25f || y < 0.08f || y > 0.6f) {
                    return null;
                }
                float w = gauss(x - side * 0.3f, y - 0.2f, 0.18f, 0.12f);
                return new float[]{0f, 0.055f * w, 0f};
            }
            case "browDown": {
                if (z < 0.4f || y < 0.06f || y > 0.4f) {
                    return null;
                }
                float w = gauss(ax - 0.17f, y - 0.19f, 0.1f, 0.07f);
                return new float[]{-Math.signum(x) * 0.015f * w, -0.035f * w, 0.01f * w};
            }
            case "blinkL":
            case "blinkR": {
                float side = name.endsWith("L") ? 1f : -1f;
                float ex = side * Anatomy.EYE_X;
                float u = (x - ex) / 0.2f, v = (y - Anatomy.EYE_Y) / 0.15f;
                if (x * side < 0f || y <= Anatomy.EYE_Y || z < 0.5f || u * u + v * v >= 1f) {
                    return null;
                }
                // the upper lid comes down over the eye: to the eye line, and out over the eyeball's front
                float w = smooth(1f - (float) Math.sqrt(u * u + v * v)) ;
                float targetY = Anatomy.EYE_Y - 0.008f + (y - Anatomy.EYE_Y) * 0.06f;
                float dxe = x - ex, dye = targetY - Anatomy.EYE_Y;
                float r = Anatomy.EYE_R + 0.012f;
                float front = Anatomy.EYE_Z + (float) Math.sqrt(Math.max(0f, r * r - dxe * dxe - dye * dye));
                float dz = Math.max(0f, front - z);
                return new float[]{0f, (targetY - y) * Math.min(1f, w * 1.6f), dz * Math.min(1f, w * 1.6f)};
            }
            case "wideL":
            case "wideR": {
                float side = name.endsWith("L") ? 1f : -1f;
                float ex = side * Anatomy.EYE_X;
                float u = (x - ex) / 0.22f, v = (y - Anatomy.EYE_Y) / 0.2f;
                if (x * side < 0f || y <= Anatomy.EYE_Y || z < 0.5f || u * u + v * v >= 1f) {
                    return null;
                }
                return new float[]{0f, 0.028f * smooth(1f - (float) Math.sqrt(u * u + v * v)), 0f};
            }
            case "lipsRound": {
                float w = lips(x, y, z);
                return w <= 0f ? null : new float[]{-x * 0.3f * w, (my - y) * 0.15f * w, 0.045f * w};
            }
            case "lipsWide": {
                float w = lips(x, y, z);
                return w <= 0f ? null : new float[]{x * 0.14f * w, (my - y) * 0.2f * w, -0.01f * w};
            }
            case "lipsPress": {
                float w = lips(x, y, z);
                return w <= 0f ? null : new float[]{0f, (my - y) * 0.35f * w, -0.012f * w};
            }
            default:
                return null;
        }
    }

    /** Weight of the lip area. */
    private static float lips(float x, float y, float z) {
        if (z < 0.55f) {
            return 0f;
        }
        return gauss(x, y - Anatomy.MOUTH_Y, 0.2f, 0.08f);
    }

    private static float gauss(float dx, float dy, float sx, float sy) {
        return (float) Math.exp(-(dx * dx) / (2 * sx * sx) - (dy * dy) / (2 * sy * sy));
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }
}
