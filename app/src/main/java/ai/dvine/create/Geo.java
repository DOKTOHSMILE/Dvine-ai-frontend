package ai.dvine.create;

import ai.dvine.geom.TriMesh;

/**
 * Closed triangle-mesh primitives for the 3D creation engine, in metres,
 * y up, wound outward: boxes (optionally rotated about y), frusta, cylinders
 * along any axis, cones, spheres, tori and gable prisms. Every shape is a
 * closed two-manifold, so assemblies can be validated.
 */
public final class Geo {
    private Geo() {
    }

    /** An axis-aligned box centred at (cx, cy, cz) with full sizes w, h, d, turned by yaw degrees about y. */
    public static TriMesh box(float cx, float cy, float cz, float w, float h, float d, float yaw) {
        return frustum(cx, cy, cz, w, d, w, d, h, yaw);
    }

    public static TriMesh box(float cx, float cy, float cz, float w, float h, float d) {
        return box(cx, cy, cz, w, h, d, 0f);
    }

    /** A box whose top (tw x td) is smaller than its bottom (bw x bd): a car cabin, a lamp shade, a tapered leg. */
    public static TriMesh frustum(float cx, float cy, float cz, float bw, float bd, float tw, float td, float h, float yaw) {
        float[][] v = {
                {-bw / 2, -h / 2, -bd / 2}, {bw / 2, -h / 2, -bd / 2}, {bw / 2, -h / 2, bd / 2}, {-bw / 2, -h / 2, bd / 2},
                {-tw / 2, h / 2, -td / 2}, {tw / 2, h / 2, -td / 2}, {tw / 2, h / 2, td / 2}, {-tw / 2, h / 2, td / 2}};
        int[][] quads = {{0, 1, 2, 3}, {7, 6, 5, 4}, {3, 2, 6, 7}, {1, 0, 4, 5}, {2, 1, 5, 6}, {0, 3, 7, 4}};
        return place(v, quads, cx, cy, cz, yaw);
    }

    /** A gable roof: a triangular prism along x (ridge at the top), base w x d, height h. */
    public static TriMesh gable(float cx, float baseY, float cz, float w, float d, float h) {
        float[][] v = {{-w / 2, 0, -d / 2}, {w / 2, 0, -d / 2}, {w / 2, 0, d / 2}, {-w / 2, 0, d / 2}, {-w / 2, h, 0}, {w / 2, h, 0}};
        TriMesh m = new TriMesh();
        m.pos = new float[18];
        for (int i = 0; i < 6; i++) {
            m.pos[i * 3] = v[i][0] + cx;
            m.pos[i * 3 + 1] = v[i][1] + baseY;
            m.pos[i * 3 + 2] = v[i][2] + cz;
        }
        m.tri = new int[]{0, 1, 2, 0, 2, 3, 3, 2, 5, 3, 5, 4, 1, 0, 4, 1, 4, 5, 0, 3, 4, 2, 1, 5};
        m.computeNormals();
        return m;
    }

    /** A hip roof: a pyramid frustum (ridge along x) over base w x d. */
    public static TriMesh hip(float cx, float baseY, float cz, float w, float d, float h) {
        float ridge = Math.max(0.01f, w - d);
        return frustum(cx, baseY + h / 2, cz, w, d, ridge, 0.02f, h, 0f);
    }

    /** A cylinder from (x0,y0,z0) to (x1,y1,z1) with radius r and the given number of sides, capped. */
    public static TriMesh cylinder(float x0, float y0, float z0, float x1, float y1, float z1, float r, int sides) {
        return cone(x0, y0, z0, x1, y1, z1, r, r, sides);
    }

    /** A capped tube from radius r0 at one end to r1 at the other (r1 = 0 makes a point). */
    public static TriMesh cone(float x0, float y0, float z0, float x1, float y1, float z1, float r0, float r1, int sides) {
        float ax = x1 - x0, ay = y1 - y0, az = z1 - z0;
        float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        ax /= len;
        ay /= len;
        az /= len;
        // two directions across the axis
        float px = Math.abs(ay) < 0.9f ? 0f : 1f, py = Math.abs(ay) < 0.9f ? 1f : 0f;
        float ux = py * az, uy = -px * az, uz = px * ay - py * ax;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        float vx = ay * uz - az * uy, vy = az * ux - ax * uz, vz = ax * uy - ay * ux;
        boolean point = r1 <= 1e-6f;
        int ring = sides;
        TriMesh m = new TriMesh();
        java.util.List<float[]> pts = new java.util.ArrayList<float[]>();
        for (int e = 0; e < 2; e++) {
            float r = e == 0 ? r0 : r1;
            if (e == 1 && point) {
                break;
            }
            float bx = e == 0 ? x0 : x1, by = e == 0 ? y0 : y1, bz = e == 0 ? z0 : z1;
            for (int i = 0; i < ring; i++) {
                double a = i * Math.PI * 2 / ring;
                float c = (float) Math.cos(a), s = (float) Math.sin(a);
                pts.add(new float[]{bx + (ux * c + vx * s) * r, by + (uy * c + vy * s) * r, bz + (uz * c + vz * s) * r});
            }
        }
        int c0 = pts.size();
        pts.add(new float[]{x0, y0, z0});
        int c1 = pts.size();
        pts.add(new float[]{x1, y1, z1});
        m.pos = new float[pts.size() * 3];
        for (int i = 0; i < pts.size(); i++) {
            System.arraycopy(pts.get(i), 0, m.pos, i * 3, 3);
        }
        java.util.List<Integer> t = new java.util.ArrayList<Integer>();
        for (int i = 0; i < ring; i++) {
            int j = (i + 1) % ring;
            // bottom cap faces back along the axis
            add(t, c0, j, i);
            if (point) {
                add(t, i, j, c1);
            } else {
                add(t, i, j, ring + j);
                add(t, i, ring + j, ring + i);
                add(t, c1, ring + i, ring + j);
            }
        }
        m.tri = toArray(t);
        fixWinding(m);
        m.computeNormals();
        return m;
    }

    /** A UV sphere (slightly squashable via ry) centred at (cx, cy, cz). */
    public static TriMesh sphere(float cx, float cy, float cz, float r, float ry, int bands) {
        int seg = bands * 2;
        java.util.List<float[]> pts = new java.util.ArrayList<float[]>();
        pts.add(new float[]{cx, cy + ry, cz});
        for (int b = 1; b < bands; b++) {
            double th = Math.PI * b / bands;
            for (int s = 0; s < seg; s++) {
                double ph = Math.PI * 2 * s / seg;
                pts.add(new float[]{cx + (float) (Math.sin(th) * Math.cos(ph)) * r, cy + (float) Math.cos(th) * ry,
                        cz + (float) (Math.sin(th) * Math.sin(ph)) * r});
            }
        }
        pts.add(new float[]{cx, cy - ry, cz});
        int bottom = pts.size() - 1;
        java.util.List<Integer> t = new java.util.ArrayList<Integer>();
        for (int s = 0; s < seg; s++) {
            int n = (s + 1) % seg;
            add(t, 0, 1 + n, 1 + s);
            int last = 1 + (bands - 2) * seg;
            add(t, bottom, last + s, last + n);
        }
        for (int b = 0; b < bands - 2; b++) {
            for (int s = 0; s < seg; s++) {
                int n = (s + 1) % seg;
                int a = 1 + b * seg + s, a2 = 1 + b * seg + n, c = 1 + (b + 1) * seg + s, c2 = 1 + (b + 1) * seg + n;
                add(t, a, a2, c2);
                add(t, a, c2, c);
            }
        }
        TriMesh m = new TriMesh();
        m.pos = new float[pts.size() * 3];
        for (int i = 0; i < pts.size(); i++) {
            System.arraycopy(pts.get(i), 0, m.pos, i * 3, 3);
        }
        m.tri = toArray(t);
        fixWinding(m);
        m.computeNormals();
        return m;
    }

    /** A torus (a tyre, a ring, a mug handle) around an axis: "x", "y" or "z". */
    public static TriMesh torus(float cx, float cy, float cz, float big, float small, String axis, int seg, int sides) {
        float[] pos = new float[seg * sides * 3];
        for (int i = 0; i < seg; i++) {
            double a = Math.PI * 2 * i / seg;
            for (int j = 0; j < sides; j++) {
                double b = Math.PI * 2 * j / sides;
                float r = big + small * (float) Math.cos(b);
                float p = (float) (r * Math.cos(a)), q = (float) (r * Math.sin(a)), h = small * (float) Math.sin(b);
                int k = (i * sides + j) * 3;
                switch (axis) {
                    case "x": pos[k] = cx + h; pos[k + 1] = cy + p; pos[k + 2] = cz + q; break;
                    case "z": pos[k] = cx + p; pos[k + 1] = cy + q; pos[k + 2] = cz + h; break;
                    default: pos[k] = cx + p; pos[k + 1] = cy + h; pos[k + 2] = cz + q;
                }
            }
        }
        java.util.List<Integer> t = new java.util.ArrayList<Integer>();
        for (int i = 0; i < seg; i++) {
            for (int j = 0; j < sides; j++) {
                int a = i * sides + j, b = ((i + 1) % seg) * sides + j, c = ((i + 1) % seg) * sides + (j + 1) % sides,
                        d = i * sides + (j + 1) % sides;
                add(t, a, d, c);
                add(t, a, c, b);
            }
        }
        TriMesh m = new TriMesh();
        m.pos = pos;
        m.tri = toArray(t);
        fixWinding(m);
        m.computeNormals();
        return m;
    }

    /** Several meshes as one (each stays a closed shell). */
    public static TriMesh merge(TriMesh... parts) {
        int nv = 0, nt = 0;
        for (TriMesh p : parts) {
            nv += p.pos.length / 3;
            nt += p.tri.length;
        }
        TriMesh m = new TriMesh();
        m.pos = new float[nv * 3];
        m.tri = new int[nt];
        int vo = 0, to = 0;
        for (TriMesh p : parts) {
            System.arraycopy(p.pos, 0, m.pos, vo * 3, p.pos.length);
            for (int i = 0; i < p.tri.length; i++) {
                m.tri[to + i] = p.tri[i] + vo;
            }
            vo += p.pos.length / 3;
            to += p.tri.length;
        }
        m.computeNormals();
        return m;
    }

    /** Signed volume (positive when wound outward). */
    public static double volume(TriMesh m) {
        double v = 0;
        for (int t = 0; t < m.tri.length; t += 3) {
            int a = m.tri[t] * 3, b = m.tri[t + 1] * 3, c = m.tri[t + 2] * 3;
            v += (m.pos[a] * (m.pos[b + 1] * m.pos[c + 2] - m.pos[b + 2] * m.pos[c + 1])
                    - m.pos[a + 1] * (m.pos[b] * m.pos[c + 2] - m.pos[b + 2] * m.pos[c])
                    + m.pos[a + 2] * (m.pos[b] * m.pos[c + 1] - m.pos[b + 1] * m.pos[c])) / 6.0;
        }
        return v;
    }

    /** Turns a closed mesh inside out if it was wound inward. */
    static void fixWinding(TriMesh m) {
        if (volume(m) < 0) {
            for (int t = 0; t < m.tri.length; t += 3) {
                int x = m.tri[t + 1];
                m.tri[t + 1] = m.tri[t + 2];
                m.tri[t + 2] = x;
            }
        }
    }

    private static TriMesh place(float[][] v, int[][] quads, float cx, float cy, float cz, float yaw) {
        double a = Math.toRadians(yaw);
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        TriMesh m = new TriMesh();
        m.pos = new float[v.length * 3];
        for (int i = 0; i < v.length; i++) {
            m.pos[i * 3] = cx + v[i][0] * c + v[i][2] * s;
            m.pos[i * 3 + 1] = cy + v[i][1];
            m.pos[i * 3 + 2] = cz - v[i][0] * s + v[i][2] * c;
        }
        m.tri = new int[quads.length * 6];
        for (int q = 0; q < quads.length; q++) {
            int[] f = quads[q];
            int[] six = {f[0], f[1], f[2], f[0], f[2], f[3]};
            System.arraycopy(six, 0, m.tri, q * 6, 6);
        }
        fixWinding(m);
        m.computeNormals();
        return m;
    }

    private static void add(java.util.List<Integer> t, int a, int b, int c) {
        t.add(a);
        t.add(b);
        t.add(c);
    }

    private static int[] toArray(java.util.List<Integer> t) {
        int[] out = new int[t.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = t.get(i);
        }
        return out;
    }
}
