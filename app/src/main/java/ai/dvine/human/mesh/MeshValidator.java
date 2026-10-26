package ai.dvine.human.mesh;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Checks a mesh before it is used or saved. Invalid geometry is rejected
 * with a reason, never silently "fixed". Checks: index range, finite
 * positions, degenerate triangles, closed two-manifold surface (every edge
 * shared by exactly two triangles), consistent winding, outward orientation
 * (positive volume), skin weights and bone ids, region ids, unique edges.
 */
public final class MeshValidator {
    private MeshValidator() {
    }

    public static final class Report {
        public final List<String> problems = new ArrayList<String>();
        public int boundaryEdges;
        public int nonManifoldEdges;
        public double volume;

        public boolean ok() {
            return problems.isEmpty();
        }

        void fail(String p) {
            problems.add(p);
        }

        @Override
        public String toString() {
            return ok() ? "ok" : String.join("; ", problems);
        }
    }

    /**
     * @param closed     require a closed surface (no open edges)
     * @param boneCount  bones the skin may reference (0 to skip skin checks)
     * @param regionCount regions a vertex may carry (0 to skip)
     */
    public static Report check(TriMesh m, boolean closed, int boneCount, int regionCount) {
        Report r = new Report();
        if (m.pos == null || m.tri == null || m.pos.length % 3 != 0 || m.tri.length % 3 != 0 || m.tri.length == 0) {
            r.fail("missing geometry");
            return r;
        }
        int n = m.vertexCount();
        for (float v : m.pos) {
            if (!Float.isFinite(v)) {
                r.fail("non-finite position");
                break;
            }
        }
        int degenerate = 0;
        HashMap<Long, Integer> directed = new HashMap<Long, Integer>(m.tri.length * 2);
        for (int t = 0; t < m.tri.length; t += 3) {
            int a = m.tri[t], b = m.tri[t + 1], c = m.tri[t + 2];
            if (a < 0 || b < 0 || c < 0 || a >= n || b >= n || c >= n) {
                r.fail("index out of range in triangle " + t / 3);
                return r;
            }
            if (a == b || b == c || a == c || area2(m.pos, a, b, c) < 1e-14) {
                degenerate++;
                continue;
            }
            int[] v = {a, b, c};
            for (int k = 0; k < 3; k++) {
                long key = ((long) v[k] << 32) | v[(k + 1) % 3];
                Integer seen = directed.get(key);
                directed.put(key, seen == null ? 1 : seen + 1);
            }
        }
        if (degenerate > 0) {
            r.fail(degenerate + " degenerate triangles");
        }
        int badWinding = 0;
        for (java.util.Map.Entry<Long, Integer> e : directed.entrySet()) {
            long k = e.getKey();
            int a = (int) (k >>> 32), b = (int) k;
            Integer back = directed.get(((long) b << 32) | a);
            if (e.getValue() > 1) {
                badWinding++;
            }
            if (back == null) {
                r.boundaryEdges++;
            } else if (back > 1 || e.getValue() > 1) {
                r.nonManifoldEdges++;
            }
        }
        if (badWinding > 0) {
            r.fail(badWinding + " edges wound the same way twice (inconsistent winding)");
        }
        if (closed && r.boundaryEdges > 0) {
            r.fail(r.boundaryEdges + " open boundary edges (missing geometry)");
        }
        if (r.nonManifoldEdges > 0) {
            r.fail(r.nonManifoldEdges + " non-manifold edges");
        }
        r.volume = volume(m.pos, m.tri);
        if (closed && r.volume <= 0) {
            r.fail("faces point inward (volume " + r.volume + ")");
        }
        if (boneCount > 0) {
            if (m.bone0 == null || m.bone1 == null || m.weight0 == null || m.bone0.length != n) {
                r.fail("missing skin weights");
            } else {
                for (int i = 0; i < n; i++) {
                    float w = m.weight0[i];
                    if (!(w >= 0f && w <= 1f) || (m.bone0[i] & 0xff) >= boneCount || (m.bone1[i] & 0xff) >= boneCount) {
                        r.fail("invalid skin weight at vertex " + i);
                        break;
                    }
                }
            }
        }
        if (regionCount > 0) {
            if (m.region == null || m.region.length != n) {
                r.fail("missing body-part regions");
            } else {
                for (int i = 0; i < n; i++) {
                    if ((m.region[i] & 0xff) >= regionCount) {
                        r.fail("invalid region at vertex " + i);
                        break;
                    }
                }
            }
        }
        if (m.edge != null) {
            java.util.HashSet<Long> seen = new java.util.HashSet<Long>();
            for (int i = 0; i < m.edge.length; i += 2) {
                int a = Math.min(m.edge[i], m.edge[i + 1]), b = Math.max(m.edge[i], m.edge[i + 1]);
                if (a < 0 || b >= n || a == b || !seen.add(((long) a << 32) | b)) {
                    r.fail("duplicate or invalid edge " + i / 2);
                    break;
                }
            }
        }
        return r;
    }

    /** Signed volume: positive when every face points outward. */
    public static double volume(float[] p, int[] tri) {
        double v = 0;
        for (int t = 0; t < tri.length; t += 3) {
            int a = tri[t] * 3, b = tri[t + 1] * 3, c = tri[t + 2] * 3;
            v += p[a] * (p[b + 1] * p[c + 2] - p[b + 2] * p[c + 1])
                    - p[a + 1] * (p[b] * p[c + 2] - p[b + 2] * p[c])
                    + p[a + 2] * (p[b] * p[c + 1] - p[b + 1] * p[c]);
        }
        return v / 6.0;
    }

    private static double area2(float[] p, int a, int b, int c) {
        double ux = p[b * 3] - p[a * 3], uy = p[b * 3 + 1] - p[a * 3 + 1], uz = p[b * 3 + 2] - p[a * 3 + 2];
        double vx = p[c * 3] - p[a * 3], vy = p[c * 3 + 1] - p[a * 3 + 1], vz = p[c * 3 + 2] - p[a * 3 + 2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        return nx * nx + ny * ny + nz * nz;
    }
}
