package ai.dvine.human.mesh;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Quadric-error edge-collapse simplification (Garland and Heckbert) with an
 * importance weight per vertex: where the weight is high (eyes, mouth, nose,
 * fingers) collapses cost more and triangles stay small; flat, rigid parts
 * get large triangles. That gives the irregular low-poly look of the
 * reference while keeping enough geometry where the face deforms. A collapse
 * is refused if it would flip or crush a triangle or break the mesh's
 * manifold topology (the link condition).
 */
public final class Decimator {
    /** Importance of a point: 1 is ordinary, larger keeps more detail. */
    public interface Importance {
        float at(float x, float y, float z);
    }

    private final int nv;
    private final double[] p;
    private final double[] q;
    private final float[] w;
    private final int[] tri;
    private final boolean[] faceAlive;
    private final boolean[] vertAlive;
    private final int[] version;
    private final List<int[]> vf; // faces per vertex (growable int arrays: [count, f0, f1, ...])
    private int liveFaces;
    private final double lengthWeight;

    private static final class Cand implements Comparable<Cand> {
        final double cost;
        final int a, b, va, vb;
        final double x, y, z;

        Cand(double cost, int a, int b, int va, int vb, double x, double y, double z) {
            this.cost = cost;
            this.a = a;
            this.b = b;
            this.va = va;
            this.vb = vb;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int compareTo(Cand o) {
            return Double.compare(cost, o.cost);
        }
    }

    private Decimator(TriMesh m, Importance imp, double lengthWeight) {
        nv = m.vertexCount();
        p = new double[nv * 3];
        for (int i = 0; i < p.length; i++) {
            p[i] = m.pos[i];
        }
        tri = m.tri.clone();
        int nf = tri.length / 3;
        faceAlive = new boolean[nf];
        Arrays.fill(faceAlive, true);
        liveFaces = nf;
        vertAlive = new boolean[nv];
        Arrays.fill(vertAlive, true);
        version = new int[nv];
        w = new float[nv];
        for (int i = 0; i < nv; i++) {
            w[i] = imp == null ? 1f : Math.max(0.05f, imp.at(m.pos[i * 3], m.pos[i * 3 + 1], m.pos[i * 3 + 2]));
        }
        this.lengthWeight = lengthWeight;
        vf = new ArrayList<int[]>(nv);
        for (int i = 0; i < nv; i++) {
            vf.add(new int[]{0, 0, 0, 0, 0, 0, 0, 0});
        }
        q = new double[nv * 10];
        for (int f = 0; f < nf; f++) {
            for (int k = 0; k < 3; k++) {
                addFace(tri[f * 3 + k], f);
            }
            double[] pl = plane(f);
            if (pl == null) {
                continue;
            }
            double area = pl[4];
            for (int k = 0; k < 3; k++) {
                addQuadric(tri[f * 3 + k], pl, area);
            }
        }
    }

    /**
     * Simplifies to about {@code targetTriangles}. {@code lengthWeight} adds a
     * cost for long edges (scaled by importance) so important areas keep
     * small triangles even where they are flat.
     */
    public static TriMesh simplify(TriMesh m, int targetTriangles, Importance imp, double lengthWeight) {
        Decimator d = new Decimator(m, imp, lengthWeight);
        d.run(targetTriangles);
        return d.result(m);
    }

    private void run(int target) {
        PriorityQueue<Cand> heap = new PriorityQueue<Cand>();
        int nf = tri.length / 3;
        for (int f = 0; f < nf; f++) {
            for (int k = 0; k < 3; k++) {
                int a = tri[f * 3 + k], b = tri[f * 3 + (k + 1) % 3];
                if (a < b) {
                    Cand c = candidate(a, b);
                    if (c != null) {
                        heap.add(c);
                    }
                } else if (!hasFaceWithEdge(b, a, f)) {
                    // edge seen only from this side (open boundary): still a candidate
                    Cand c = candidate(b, a);
                    if (c != null) {
                        heap.add(c);
                    }
                }
            }
        }
        while (liveFaces > target && !heap.isEmpty()) {
            Cand c = heap.poll();
            if (!vertAlive[c.a] || !vertAlive[c.b] || version[c.a] != c.va || version[c.b] != c.vb) {
                continue;
            }
            if (!canCollapse(c.a, c.b, c.x, c.y, c.z)) {
                continue;
            }
            collapse(c.a, c.b, c.x, c.y, c.z);
            int[] fs = vf.get(c.a);
            int[] seen = new int[fs[0] * 3];
            int ns = 0;
            for (int i = 1; i <= fs[0]; i++) {
                int f = fs[i];
                for (int k = 0; k < 3; k++) {
                    int o = tri[f * 3 + k];
                    if (o == c.a) {
                        continue;
                    }
                    boolean dup = false;
                    for (int s = 0; s < ns; s++) {
                        dup |= seen[s] == o;
                    }
                    if (!dup) {
                        seen[ns++] = o;
                        Cand n = candidate(Math.min(c.a, o), Math.max(c.a, o));
                        if (n != null) {
                            heap.add(n);
                        }
                    }
                }
            }
        }
    }

    private boolean hasFaceWithEdge(int a, int b, int except) {
        int[] fs = vf.get(a);
        for (int i = 1; i <= fs[0]; i++) {
            int f = fs[i];
            if (f != except && contains(f, b)) {
                return true;
            }
        }
        return false;
    }

    private Cand candidate(int a, int b) {
        double[] Q = new double[10];
        for (int i = 0; i < 10; i++) {
            Q[i] = q[a * 10 + i] * w[a] + q[b * 10 + i] * w[b];
        }
        double[] best = optimal(Q);
        double bx, by, bz, cost;
        if (best != null && distToSegment2(best, a, b) < len2(a, b) * 0.25) {
            bx = best[0];
            by = best[1];
            bz = best[2];
            cost = error(Q, bx, by, bz);
        } else {
            double[][] opts = {{p[a * 3], p[a * 3 + 1], p[a * 3 + 2]}, {p[b * 3], p[b * 3 + 1], p[b * 3 + 2]},
                    {(p[a * 3] + p[b * 3]) / 2, (p[a * 3 + 1] + p[b * 3 + 1]) / 2, (p[a * 3 + 2] + p[b * 3 + 2]) / 2}};
            cost = Double.MAX_VALUE;
            bx = by = bz = 0;
            for (double[] o : opts) {
                double e = error(Q, o[0], o[1], o[2]);
                if (e < cost) {
                    cost = e;
                    bx = o[0];
                    by = o[1];
                    bz = o[2];
                }
            }
        }
        cost += lengthWeight * len2(a, b) * (w[a] + w[b]) * 0.5;
        return new Cand(cost, a, b, version[a], version[b], bx, by, bz);
    }

    private double len2(int a, int b) {
        double dx = p[a * 3] - p[b * 3], dy = p[a * 3 + 1] - p[b * 3 + 1], dz = p[a * 3 + 2] - p[b * 3 + 2];
        return dx * dx + dy * dy + dz * dz;
    }

    private double distToSegment2(double[] x, int a, int b) {
        double mx = (p[a * 3] + p[b * 3]) / 2, my = (p[a * 3 + 1] + p[b * 3 + 1]) / 2, mz = (p[a * 3 + 2] + p[b * 3 + 2]) / 2;
        double dx = x[0] - mx, dy = x[1] - my, dz = x[2] - mz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Link condition plus no flipped or crushed triangles. */
    private boolean canCollapse(int a, int b, double x, double y, double z) {
        // vertices adjacent to both a and b must be exactly the opposite corners of the faces on edge ab
        int[] na = neighbours(a), nb = neighbours(b);
        int shared = 0;
        for (int i = 1; i <= na[0]; i++) {
            for (int j = 1; j <= nb[0]; j++) {
                if (na[i] == nb[j]) {
                    shared++;
                }
            }
        }
        int edgeFaces = 0;
        int[] fa = vf.get(a);
        for (int i = 1; i <= fa[0]; i++) {
            if (contains(fa[i], b)) {
                edgeFaces++;
            }
        }
        if (shared != edgeFaces || edgeFaces == 0) {
            return false;
        }
        return !flips(a, b, x, y, z) && !flips(b, a, x, y, z);
    }

    private boolean flips(int v, int other, double x, double y, double z) {
        int[] fs = vf.get(v);
        for (int i = 1; i <= fs[0]; i++) {
            int f = fs[i];
            if (contains(f, other)) {
                continue; // this face disappears
            }
            double[] before = normal(f, -1, 0, 0, 0);
            double[] after = normal(f, v, x, y, z);
            if (before == null || after == null) {
                return true;
            }
            double dot = before[0] * after[0] + before[1] * after[1] + before[2] * after[2];
            if (dot < 0.25 || after[3] < before[3] * 0.02) {
                return true;
            }
        }
        return false;
    }

    private void collapse(int a, int b, double x, double y, double z) {
        p[a * 3] = x;
        p[a * 3 + 1] = y;
        p[a * 3 + 2] = z;
        for (int i = 0; i < 10; i++) {
            q[a * 10 + i] = (q[a * 10 + i] * w[a] + q[b * 10 + i] * w[b]) / Math.max(w[a], w[b]);
        }
        w[a] = Math.max(w[a], w[b]);
        int[] fb = vf.get(b);
        for (int i = 1; i <= fb[0]; i++) {
            int f = fb[i];
            if (!faceAlive[f]) {
                continue;
            }
            if (contains(f, a)) {
                faceAlive[f] = false;
                liveFaces--;
                removeFace(a, f);
                for (int k = 0; k < 3; k++) {
                    int o = tri[f * 3 + k];
                    if (o != a && o != b) {
                        removeFace(o, f);
                    }
                }
            } else {
                for (int k = 0; k < 3; k++) {
                    if (tri[f * 3 + k] == b) {
                        tri[f * 3 + k] = a;
                    }
                }
                addFace(a, f);
            }
        }
        vertAlive[b] = false;
        version[a]++;
        version[b]++;
    }

    private TriMesh result(TriMesh src) {
        int[] remap = new int[nv];
        Arrays.fill(remap, -1);
        int n = 0;
        int nf = tri.length / 3;
        int live = 0;
        for (int f = 0; f < nf; f++) {
            if (faceAlive[f]) {
                live++;
                for (int k = 0; k < 3; k++) {
                    int v = tri[f * 3 + k];
                    if (remap[v] < 0) {
                        remap[v] = n++;
                    }
                }
            }
        }
        TriMesh m = new TriMesh();
        m.pos = new float[n * 3];
        if (src.region != null) {
            m.region = new byte[n];
        }
        for (int v = 0; v < nv; v++) {
            if (remap[v] >= 0) {
                int r = remap[v];
                m.pos[r * 3] = (float) p[v * 3];
                m.pos[r * 3 + 1] = (float) p[v * 3 + 1];
                m.pos[r * 3 + 2] = (float) p[v * 3 + 2];
                if (src.region != null) {
                    m.region[r] = src.region[v];
                }
            }
        }
        m.tri = new int[live * 3];
        int t = 0;
        for (int f = 0; f < nf; f++) {
            if (faceAlive[f]) {
                for (int k = 0; k < 3; k++) {
                    m.tri[t++] = remap[tri[f * 3 + k]];
                }
            }
        }
        m.computeNormals();
        return m;
    }

    // ------------------------------------------------------------ helpers

    private boolean contains(int f, int v) {
        return tri[f * 3] == v || tri[f * 3 + 1] == v || tri[f * 3 + 2] == v;
    }

    private int[] neighbours(int v) {
        int[] fs = vf.get(v);
        int[] out = new int[1 + fs[0] * 2];
        for (int i = 1; i <= fs[0]; i++) {
            int f = fs[i];
            for (int k = 0; k < 3; k++) {
                int o = tri[f * 3 + k];
                if (o == v) {
                    continue;
                }
                boolean dup = false;
                for (int j = 1; j <= out[0]; j++) {
                    dup |= out[j] == o;
                }
                if (!dup) {
                    out[++out[0]] = o;
                }
            }
        }
        return out;
    }

    private void addFace(int v, int f) {
        int[] a = vf.get(v);
        for (int i = 1; i <= a[0]; i++) {
            if (a[i] == f) {
                return;
            }
        }
        if (a[0] + 1 >= a.length) {
            a = Arrays.copyOf(a, a.length * 2);
            vf.set(v, a);
        }
        a[++a[0]] = f;
    }

    private void removeFace(int v, int f) {
        int[] a = vf.get(v);
        for (int i = 1; i <= a[0]; i++) {
            if (a[i] == f) {
                a[i] = a[a[0]];
                a[0]--;
                return;
            }
        }
    }

    /** Unit normal and area of face f, with vertex {@code moved} (or -1) placed at (x, y, z). */
    private double[] normal(int f, int moved, double x, double y, double z) {
        double[] v = new double[9];
        for (int k = 0; k < 3; k++) {
            int i = tri[f * 3 + k];
            if (i == moved) {
                v[k * 3] = x;
                v[k * 3 + 1] = y;
                v[k * 3 + 2] = z;
            } else {
                v[k * 3] = p[i * 3];
                v[k * 3 + 1] = p[i * 3 + 1];
                v[k * 3 + 2] = p[i * 3 + 2];
            }
        }
        double ux = v[3] - v[0], uy = v[4] - v[1], uz = v[5] - v[2];
        double wx = v[6] - v[0], wy = v[7] - v[1], wz = v[8] - v[2];
        double nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-18) {
            return null;
        }
        return new double[]{nx / l, ny / l, nz / l, l * 0.5};
    }

    private double[] plane(int f) {
        double[] n = normal(f, -1, 0, 0, 0);
        if (n == null) {
            return null;
        }
        int a = tri[f * 3];
        double d = -(n[0] * p[a * 3] + n[1] * p[a * 3 + 1] + n[2] * p[a * 3 + 2]);
        return new double[]{n[0], n[1], n[2], d, n[3]};
    }

    private void addQuadric(int v, double[] pl, double area) {
        double a = pl[0], b = pl[1], c = pl[2], d = pl[3];
        int o = v * 10;
        q[o] += area * a * a;
        q[o + 1] += area * a * b;
        q[o + 2] += area * a * c;
        q[o + 3] += area * a * d;
        q[o + 4] += area * b * b;
        q[o + 5] += area * b * c;
        q[o + 6] += area * b * d;
        q[o + 7] += area * c * c;
        q[o + 8] += area * c * d;
        q[o + 9] += area * d * d;
    }

    private static double error(double[] Q, double x, double y, double z) {
        return Q[0] * x * x + 2 * Q[1] * x * y + 2 * Q[2] * x * z + 2 * Q[3] * x
                + Q[4] * y * y + 2 * Q[5] * y * z + 2 * Q[6] * y
                + Q[7] * z * z + 2 * Q[8] * z + Q[9];
    }

    private static double[] optimal(double[] Q) {
        double a = Q[0], b = Q[1], c = Q[2], d = Q[4], e = Q[5], f = Q[7];
        double det = a * (d * f - e * e) - b * (b * f - c * e) + c * (b * e - c * d);
        if (Math.abs(det) < 1e-12) {
            return null;
        }
        double r0 = -Q[3], r1 = -Q[6], r2 = -Q[8];
        double x = (r0 * (d * f - e * e) - b * (r1 * f - e * r2) + c * (r1 * e - d * r2)) / det;
        double y = (a * (r1 * f - e * r2) - r0 * (b * f - c * e) + c * (b * r2 - r1 * c)) / det;
        double z = (a * (d * r2 - r1 * e) - b * (b * r2 - r1 * c) + r0 * (b * e - c * d)) / det;
        return new double[]{x, y, z};
    }
}
