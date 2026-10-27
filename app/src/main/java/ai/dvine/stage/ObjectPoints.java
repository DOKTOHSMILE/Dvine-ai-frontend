package ai.dvine.stage;

import ai.dvine.render.field.Shape;

/**
 * Things to show in front of the hologram as points: a design from the 3D creation engine (its surface sampled
 * evenly, lit, tinted by material), or one of the built-in forms and sketches. About two units across, y up,
 * centred. Pure Java.
 */
public final class ObjectPoints {
    private ObjectPoints() {
    }

    /** A design's visible surface: points spread by area, lit from above-front, in its materials' tints. */
    public static Shape of(ai.dvine.create.ObjectModel m, int max) {
        if (m == null) {
            return Shape.EMPTY;
        }
        java.util.List<Integer> owner = new java.util.ArrayList<Integer>();
        ai.dvine.geom.TriMesh mesh = m.merged(owner);
        if (mesh == null || mesh.tri == null || mesh.tri.length == 0) {
            return Shape.EMPTY;
        }
        float[] p = mesh.pos;
        int[] t = mesh.tri;
        int tris = t.length / 3;
        double[] area = new double[tris];
        double total = 0;
        for (int i = 0; i < tris; i++) {
            float[] n = normal(p, t, i);
            area[i] = 0.5 * Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            total += area[i];
        }
        if (total <= 0) {
            return Shape.EMPTY;
        }
        float[] b = m.bounds();
        float cx = (b[0] + b[3]) / 2f, cy = (b[1] + b[4]) / 2f, cz = (b[2] + b[5]) / 2f;
        float span = Math.max(b[3] - b[0], Math.max(b[4] - b[1], b[5] - b[2]));
        float k = span > 0f ? 2f / span : 1f;
        float[] xyz = new float[max * 3], rgb = new float[max * 3];
        java.util.Random r = new java.util.Random(m.version * 31L + tris);
        int n = 0;
        double carry = 0;
        for (int i = 0; i < tris && n < max; i++) {
            carry += area[i] / total * max;
            float[] nn = normal(p, t, i);
            float l = (float) Math.sqrt(nn[0] * nn[0] + nn[1] * nn[1] + nn[2] * nn[2]);
            float lit = l > 0f ? 0.35f + 0.65f * Math.max(0f, (nn[1] * 0.7f + nn[2] * 0.5f + nn[0] * 0.2f) / l) : 0.6f;
            float[] tint = tint(componentMaterial(m, owner.get(i)));
            while (carry >= 1 && n < max) {
                carry -= 1;
                float u = r.nextFloat(), v = r.nextFloat();
                if (u + v > 1f) {
                    u = 1f - u;
                    v = 1f - v;
                }
                for (int c = 0; c < 3; c++) {
                    float a = p[t[i * 3] * 3 + c], bb = p[t[i * 3 + 1] * 3 + c], cc = p[t[i * 3 + 2] * 3 + c];
                    float o = c == 0 ? cx : c == 1 ? cy : cz;
                    xyz[n * 3 + c] = (a + (bb - a) * u + (cc - a) * v - o) * k;
                    rgb[n * 3 + c] = tint[c] * lit;
                }
                n++;
            }
        }
        return new Shape(xyz, rgb, n);
    }

    /** A built-in form or sketch (fully drawn), in its own light. */
    public static Shape of(ai.dvine.render.forms.Swarm.Form f, int max) {
        float[] xyz = new float[max * 3], lit = new float[max];
        f.fill(xyz, lit, max, 0f);
        int n = Math.max(0, Math.min(max, f.fill(xyz, lit, max, 40f)));
        float[] rgb = new float[n * 3];
        for (int i = 0; i < n; i++) {
            float a = Math.max(0.25f, Math.min(1f, lit[i]));
            rgb[i * 3] = 0.6f * a;
            rgb[i * 3 + 1] = 0.9f * a;
            rgb[i * 3 + 2] = 1f * a;
        }
        return new Shape(java.util.Arrays.copyOf(xyz, n * 3), rgb, n);
    }

    private static String componentMaterial(ai.dvine.create.ObjectModel m, int id) {
        ai.dvine.create.Component c = m.component(id);
        return c == null || c.material == null ? "" : c.material.toLowerCase(java.util.Locale.US);
    }

    static float[] tint(String material) {
        if (material.contains("glass") || material.contains("water")) {
            return new float[]{0.45f, 0.85f, 1f};
        }
        if (material.contains("wood") || material.contains("brick") || material.contains("roof")) {
            return new float[]{1f, 0.72f, 0.45f};
        }
        if (material.contains("plant") || material.contains("grass") || material.contains("leaf")) {
            return new float[]{0.5f, 1f, 0.6f};
        }
        if (material.contains("metal") || material.contains("steel")) {
            return new float[]{0.75f, 0.82f, 0.95f};
        }
        return new float[]{0.85f, 0.92f, 1f};
    }

    private static float[] normal(float[] p, int[] t, int i) {
        int a = t[i * 3] * 3, b = t[i * 3 + 1] * 3, c = t[i * 3 + 2] * 3;
        float ux = p[b] - p[a], uy = p[b + 1] - p[a + 1], uz = p[b + 2] - p[a + 2];
        float vx = p[c] - p[a], vy = p[c + 1] - p[a + 1], vz = p[c + 2] - p[a + 2];
        return new float[]{uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
    }
}
