package ai.dvine.human.mesh;

/**
 * Turns a signed distance field into a closed, connected triangle mesh
 * (naive surface nets: one vertex per surface cell, one quad per crossing
 * grid edge, quads split along the shorter diagonal, vertices then pulled
 * exactly onto the surface). Deterministic and pure Java, so the same code
 * builds the shipped assets offline and can rebuild them on the phone.
 */
public final class SurfaceNets {
    private SurfaceNets() {
    }

    /** A signed distance field: negative inside. */
    public interface Field {
        float d(float x, float y, float z);
    }

    public static TriMesh extract(Field f, float[] min, float[] max, float cell) {
        int nx = (int) Math.ceil((max[0] - min[0]) / cell) + 1;
        int ny = (int) Math.ceil((max[1] - min[1]) / cell) + 1;
        int nz = (int) Math.ceil((max[2] - min[2]) / cell) + 1;
        float[] v = new float[nx * ny * nz];
        for (int k = 0; k < nz; k++) {
            float z = min[2] + k * cell;
            for (int j = 0; j < ny; j++) {
                float y = min[1] + j * cell;
                int row = (k * ny + j) * nx;
                for (int i = 0; i < nx; i++) {
                    float d = f.d(min[0] + i * cell, y, z);
                    v[row + i] = d == 0f ? 1e-7f : d;
                }
            }
        }
        int cx = nx - 1, cy = ny - 1, cz = nz - 1;
        int[] cellVert = new int[cx * cy * cz];
        java.util.Arrays.fill(cellVert, -1);
        FloatList pos = new FloatList(4096);
        int[] corner = new int[8];
        for (int k = 0; k < cz; k++) {
            for (int j = 0; j < cy; j++) {
                for (int i = 0; i < cx; i++) {
                    int base = (k * ny + j) * nx + i;
                    corner[0] = base;
                    corner[1] = base + 1;
                    corner[2] = base + nx;
                    corner[3] = base + nx + 1;
                    corner[4] = base + nx * ny;
                    corner[5] = base + nx * ny + 1;
                    corner[6] = base + nx * ny + nx;
                    corner[7] = base + nx * ny + nx + 1;
                    int inside = 0;
                    for (int c = 0; c < 8; c++) {
                        if (v[corner[c]] < 0f) {
                            inside++;
                        }
                    }
                    if (inside == 0 || inside == 8) {
                        continue;
                    }
                    // average of the 12 cube edges' zero crossings
                    float sx = 0f, sy = 0f, sz = 0f;
                    int n = 0;
                    for (int[] e : EDGES) {
                        float a = v[corner[e[0]]], b = v[corner[e[1]]];
                        if ((a < 0f) == (b < 0f)) {
                            continue;
                        }
                        float t = a / (a - b);
                        sx += ox(e[0]) + (ox(e[1]) - ox(e[0])) * t;
                        sy += oy(e[0]) + (oy(e[1]) - oy(e[0])) * t;
                        sz += oz(e[0]) + (oz(e[1]) - oz(e[0])) * t;
                        n++;
                    }
                    cellVert[(k * cy + j) * cx + i] = pos.size() / 3;
                    pos.add(min[0] + (i + sx / n) * cell, min[1] + (j + sy / n) * cell, min[2] + (k + sz / n) * cell);
                }
            }
        }
        IntList tris = new IntList(8192);
        float[] p = pos.array();
        for (int k = 1; k < cz; k++) {
            for (int j = 1; j < cy; j++) {
                for (int i = 0; i < cx; i++) {
                    edgeQuad(v, nx, ny, i, j, k, 0, cellVert, cx, cy, p, tris);
                }
            }
        }
        for (int k = 1; k < cz; k++) {
            for (int j = 0; j < cy; j++) {
                for (int i = 1; i < cx; i++) {
                    edgeQuad(v, nx, ny, i, j, k, 1, cellVert, cx, cy, p, tris);
                }
            }
        }
        for (int k = 0; k < cz; k++) {
            for (int j = 1; j < cy; j++) {
                for (int i = 1; i < cx; i++) {
                    edgeQuad(v, nx, ny, i, j, k, 2, cellVert, cx, cy, p, tris);
                }
            }
        }
        TriMesh m = new TriMesh();
        m.pos = pos.toArray();
        m.tri = tris.toArray();
        project(f, m.pos, cell);
        TriMesh clean = MeshClean.weld(m, cell * 0.08f);
        clean.computeNormals();
        return clean;
    }

    /** Pulls every vertex onto the zero surface with a few Newton steps (never further than one cell). */
    static void project(Field f, float[] p, float cell) {
        float h = cell * 0.25f;
        for (int i = 0; i < p.length; i += 3) {
            float x = p[i], y = p[i + 1], z = p[i + 2];
            float x0 = x, y0 = y, z0 = z;
            for (int it = 0; it < 3; it++) {
                float d = f.d(x, y, z);
                float gx = f.d(x + h, y, z) - f.d(x - h, y, z);
                float gy = f.d(x, y + h, z) - f.d(x, y - h, z);
                float gz = f.d(x, y, z + h) - f.d(x, y, z - h);
                float g2 = gx * gx + gy * gy + gz * gz;
                if (g2 < 1e-20f) {
                    break;
                }
                float s = d * (2f * h) / g2;
                x -= gx * s;
                y -= gy * s;
                z -= gz * s;
            }
            float dx = x - x0, dy = y - y0, dz = z - z0;
            if (dx * dx + dy * dy + dz * dz < cell * cell) {
                p[i] = x;
                p[i + 1] = y;
                p[i + 2] = z;
            }
        }
    }

    /** The quad around one grid edge along axis (0 x, 1 y, 2 z) starting at grid point (i, j, k). */
    private static void edgeQuad(float[] v, int nx, int ny, int i, int j, int k, int axis, int[] cellVert,
                                 int cx, int cy, float[] p, IntList out) {
        int a = (k * ny + j) * nx + i;
        int b = axis == 0 ? a + 1 : axis == 1 ? a + nx : a + nx * ny;
        float va = v[a], vb = v[b];
        if ((va < 0f) == (vb < 0f)) {
            return;
        }
        int c0, c1, c2, c3;
        if (axis == 0) {
            c0 = cellVert[((k - 1) * cy + (j - 1)) * cx + i];
            c1 = cellVert[((k - 1) * cy + j) * cx + i];
            c2 = cellVert[(k * cy + j) * cx + i];
            c3 = cellVert[(k * cy + (j - 1)) * cx + i];
        } else if (axis == 1) {
            c0 = cellVert[((k - 1) * cy + j) * cx + (i - 1)];
            c1 = cellVert[(k * cy + j) * cx + (i - 1)];
            c2 = cellVert[(k * cy + j) * cx + i];
            c3 = cellVert[((k - 1) * cy + j) * cx + i];
        } else {
            c0 = cellVert[(k * cy + (j - 1)) * cx + (i - 1)];
            c1 = cellVert[(k * cy + (j - 1)) * cx + i];
            c2 = cellVert[(k * cy + j) * cx + i];
            c3 = cellVert[(k * cy + j) * cx + (i - 1)];
        }
        if (c0 < 0 || c1 < 0 || c2 < 0 || c3 < 0) {
            return;
        }
        if (va > 0f) { // keep the outward winding
            int t = c1;
            c1 = c3;
            c3 = t;
        }
        // split along the shorter diagonal
        if (dist2(p, c0, c2) <= dist2(p, c1, c3)) {
            out.add(c0, c1, c2);
            out.add(c0, c2, c3);
        } else {
            out.add(c0, c1, c3);
            out.add(c1, c2, c3);
        }
    }

    private static float dist2(float[] p, int a, int b) {
        float dx = p[a * 3] - p[b * 3], dy = p[a * 3 + 1] - p[b * 3 + 1], dz = p[a * 3 + 2] - p[b * 3 + 2];
        return dx * dx + dy * dy + dz * dz;
    }

    private static final int[][] EDGES = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    private static float ox(int c) {
        return c & 1;
    }

    private static float oy(int c) {
        return (c >> 1) & 1;
    }

    private static float oz(int c) {
        return (c >> 2) & 1;
    }

    /** Growable float array. */
    static final class FloatList {
        private float[] a;
        private int n;

        FloatList(int cap) {
            a = new float[cap];
        }

        void add(float x, float y, float z) {
            if (n + 3 > a.length) {
                a = java.util.Arrays.copyOf(a, a.length * 2);
            }
            a[n++] = x;
            a[n++] = y;
            a[n++] = z;
        }

        int size() {
            return n;
        }

        float[] array() {
            return a;
        }

        float[] toArray() {
            return java.util.Arrays.copyOf(a, n);
        }
    }

    /** Growable int array. */
    static final class IntList {
        private int[] a;
        private int n;

        IntList(int cap) {
            a = new int[cap];
        }

        void add(int x, int y, int z) {
            if (n + 3 > a.length) {
                a = java.util.Arrays.copyOf(a, a.length * 2);
            }
            a[n++] = x;
            a[n++] = y;
            a[n++] = z;
        }

        int[] toArray() {
            return java.util.Arrays.copyOf(a, n);
        }
    }
}
