package ai.dvine.render.pod;

/**
 * The projection pod: a round, low platform turned on a lathe. From the middle out: a domed emitter a quarter of
 * the diameter across, a flat top (its rings, ticks and moving traces are drawn by the shader from each point's
 * radius), a bevelled rim that carries the outer light, a side band, a lip, a narrower base. Diameter 1, top at
 * y = 0. Per vertex: position, normal, radius and part (0 emitter, 1 top, 2 rim, 3 side, 4 lip, 5 base, 6 bottom).
 * Pure Java.
 */
public final class PodMesh {
    private PodMesh() {
    }

    public static final int STRIDE = 8;
    /** The profile: radius, height, part (each segment between two points is one band). */
    static final float[][] PROFILE = {
            {0.000f, 0.020f, 0}, {0.030f, 0.019f, 0}, {0.060f, 0.015f, 0}, {0.090f, 0.009f, 0}, {0.125f, 0.002f, 0},
            {0.125f, 0.000f, 1}, {0.300f, 0.000f, 1}, {0.488f, 0.000f, 1},
            {0.488f, 0.000f, 2}, {0.500f, -0.008f, 2},
            {0.500f, -0.008f, 3}, {0.500f, -0.058f, 3},
            {0.500f, -0.058f, 4}, {0.470f, -0.070f, 4},
            {0.462f, -0.072f, 5}, {0.462f, -0.118f, 5},
            {0.462f, -0.118f, 6}, {0.430f, -0.128f, 6}, {0.000f, -0.128f, 6},
    };

    /** Interleaved vertices (see {@link #STRIDE}) and triangle indices, {@code segments} around. */
    public static final class Mesh {
        public final float[] vertices;
        public final int[] indices;

        Mesh(float[] v, int[] i) {
            vertices = v;
            indices = i;
        }

        public int vertexCount() {
            return vertices.length / STRIDE;
        }
    }

    public static Mesh build(int segments) {
        java.util.List<float[]> verts = new java.util.ArrayList<float[]>();
        java.util.List<int[]> tris = new java.util.ArrayList<int[]>();
        for (int k = 0; k + 1 < PROFILE.length; k++) {
            float[] a = PROFILE[k], b = PROFILE[k + 1];
            if (a[2] != b[2]) {
                continue; // a new part starts: its first point is repeated, with its own normal
            }
            float dr = b[0] - a[0], dy = b[1] - a[1];
            float l = (float) Math.sqrt(dr * dr + dy * dy);
            if (l < 1e-6f) {
                continue;
            }
            float nr = -dy / l, ny = dr / l;
            if (a[2] == 0) { // the dome is smooth: normals from the sphere it approximates
                nr = Float.NaN;
            }
            int base = verts.size();
            for (int s = 0; s <= segments; s++) {
                double ang = 2 * Math.PI * s / segments;
                float c = (float) Math.cos(ang), sn = (float) Math.sin(ang);
                for (float[] p : new float[][]{a, b}) {
                    float vr = nr, vy = ny;
                    if (Float.isNaN(nr)) {
                        float cy = -0.35f, ry = p[1] - cy, rl = (float) Math.sqrt(p[0] * p[0] + ry * ry);
                        vr = p[0] / rl;
                        vy = ry / rl;
                    }
                    verts.add(new float[]{p[0] * c, p[1], p[0] * sn, vr * c, vy, vr * sn, p[0], p[2]});
                }
            }
            for (int s = 0; s < segments; s++) {
                int i0 = base + s * 2, i1 = base + (s + 1) * 2;
                tris.add(new int[]{i0, i0 + 1, i1 + 1});
                tris.add(new int[]{i0, i1 + 1, i1});
            }
        }
        float[] v = new float[verts.size() * STRIDE];
        for (int i = 0; i < verts.size(); i++) {
            System.arraycopy(verts.get(i), 0, v, i * STRIDE, STRIDE);
        }
        int[] idx = new int[tris.size() * 3];
        for (int i = 0; i < tris.size(); i++) {
            System.arraycopy(tris.get(i), 0, idx, i * 3, 3);
        }
        return new Mesh(v, idx);
    }
}
