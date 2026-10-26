package ai.dvine.human.build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import ai.dvine.human.mesh.TriMesh;

/** Small closed meshes: icospheres for the eyes, rotated boxes for the teeth. */
final class Shapes {
    private Shapes() {
    }

    /** A unit icosphere: level 0 has 20 triangles, each level multiplies by 4. Outward winding. */
    static TriMesh icosphere(int level) {
        float t = (float) ((1 + Math.sqrt(5)) / 2);
        List<float[]> v = new ArrayList<float[]>();
        float[][] base = {{-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0}, {0, -1, t}, {0, 1, t}, {0, -1, -t}, {0, 1, -t},
                {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1}};
        for (float[] p : base) {
            v.add(norm(p));
        }
        int[] f = {0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11, 1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
                3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9, 4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1};
        for (int l = 0; l < level; l++) {
            HashMap<Long, Integer> mid = new HashMap<Long, Integer>();
            int[] g = new int[f.length * 4];
            int k = 0;
            for (int i = 0; i < f.length; i += 3) {
                int a = f[i], b = f[i + 1], c = f[i + 2];
                int ab = middle(v, mid, a, b), bc = middle(v, mid, b, c), ca = middle(v, mid, c, a);
                int[] tris = {a, ab, ca, b, bc, ab, c, ca, bc, ab, bc, ca};
                System.arraycopy(tris, 0, g, k, 12);
                k += 12;
            }
            f = g;
        }
        TriMesh m = new TriMesh();
        m.pos = new float[v.size() * 3];
        for (int i = 0; i < v.size(); i++) {
            System.arraycopy(v.get(i), 0, m.pos, i * 3, 3);
        }
        m.tri = f;
        m.computeNormals();
        return m;
    }

    private static int middle(List<float[]> v, HashMap<Long, Integer> cache, int a, int b) {
        long key = a < b ? ((long) a << 32) | b : ((long) b << 32) | a;
        Integer got = cache.get(key);
        if (got != null) {
            return got;
        }
        float[] p = v.get(a), q = v.get(b);
        v.add(norm(new float[]{(p[0] + q[0]) / 2, (p[1] + q[1]) / 2, (p[2] + q[2]) / 2}));
        cache.put(key, v.size() - 1);
        return v.size() - 1;
    }

    private static float[] norm(float[] p) {
        float l = (float) Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
        return new float[]{p[0] / l, p[1] / l, p[2] / l};
    }

    /** A box with half sizes (hx, hy, hz) turned by {@code yaw} radians about the vertical axis. */
    static TriMesh box(float cx, float cy, float cz, float hx, float hy, float hz, float yaw) {
        float c = (float) Math.cos(yaw), s = (float) Math.sin(yaw);
        TriMesh m = new TriMesh();
        m.pos = new float[24];
        for (int i = 0; i < 8; i++) {
            float x = (i & 1) == 0 ? -hx : hx, y = (i & 2) == 0 ? -hy : hy, z = (i & 4) == 0 ? -hz : hz;
            m.pos[i * 3] = cx + x * c + z * s;
            m.pos[i * 3 + 1] = cy + y;
            m.pos[i * 3 + 2] = cz - x * s + z * c;
        }
        m.tri = new int[]{0, 2, 1, 1, 2, 3, 4, 5, 6, 5, 7, 6, 0, 1, 4, 1, 5, 4, 2, 6, 3, 3, 6, 7, 0, 4, 2, 2, 4, 6,
                1, 3, 5, 3, 7, 5};
        m.region = new byte[8];
        m.bone0 = new byte[8];
        m.bone1 = new byte[8];
        m.weight0 = new float[8];
        m.computeNormals();
        return m;
    }
}
