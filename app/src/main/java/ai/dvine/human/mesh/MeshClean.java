package ai.dvine.human.mesh;

import java.util.HashMap;

/**
 * Small repairs every generated mesh goes through: vertices closer than a
 * tolerance are welded, triangles that collapse to a line or point are
 * dropped, duplicates are removed, and unused vertices are compacted away.
 */
public final class MeshClean {
    private MeshClean() {
    }

    public static TriMesh weld(TriMesh m, float tol) {
        int n = m.vertexCount();
        int[] map = new int[n];
        HashMap<Long, Integer> grid = new HashMap<Long, Integer>(n * 2);
        float inv = 1f / Math.max(1e-9f, tol);
        int[] rep = new int[n];
        int kept = 0;
        for (int i = 0; i < n; i++) {
            float x = m.pos[i * 3], y = m.pos[i * 3 + 1], z = m.pos[i * 3 + 2];
            long cx = (long) Math.floor(x * inv), cy = (long) Math.floor(y * inv), cz = (long) Math.floor(z * inv);
            int found = -1;
            for (long dx = -1; dx <= 1 && found < 0; dx++) {
                for (long dy = -1; dy <= 1 && found < 0; dy++) {
                    for (long dz = -1; dz <= 1 && found < 0; dz++) {
                        Integer j = grid.get(key(cx + dx, cy + dy, cz + dz));
                        if (j != null) {
                            int r = rep[j];
                            float ex = m.pos[r * 3] - x, ey = m.pos[r * 3 + 1] - y, ez = m.pos[r * 3 + 2] - z;
                            if (ex * ex + ey * ey + ez * ez <= tol * tol) {
                                found = j;
                            }
                        }
                    }
                }
            }
            if (found >= 0) {
                map[i] = found;
            } else {
                map[i] = kept;
                rep[kept] = i;
                grid.put(key(cx, cy, cz), kept);
                kept++;
            }
        }
        // drop collapsed and duplicate triangles
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        int[] tri = new int[m.tri.length];
        int t = 0;
        for (int i = 0; i < m.tri.length; i += 3) {
            int a = map[m.tri[i]], b = map[m.tri[i + 1]], c = map[m.tri[i + 2]];
            if (a == b || b == c || a == c) {
                continue;
            }
            int lo = Math.min(a, Math.min(b, c)), hi = Math.max(a, Math.max(b, c)), mid = a + b + c - lo - hi;
            if (!seen.add(lo + "," + mid + "," + hi)) {
                continue;
            }
            tri[t++] = a;
            tri[t++] = b;
            tri[t++] = c;
        }
        TriMesh out = new TriMesh();
        out.pos = new float[kept * 3];
        for (int k = 0; k < kept; k++) {
            System.arraycopy(m.pos, rep[k] * 3, out.pos, k * 3, 3);
        }
        out.tri = java.util.Arrays.copyOf(tri, t);
        if (m.region != null) {
            out.region = new byte[kept];
            for (int k = 0; k < kept; k++) {
                out.region[k] = m.region[rep[k]];
            }
        }
        return compact(out);
    }

    /** Removes vertices no triangle uses. */
    public static TriMesh compact(TriMesh m) {
        int n = m.vertexCount();
        int[] remap = new int[n];
        java.util.Arrays.fill(remap, -1);
        int k = 0;
        for (int v : m.tri) {
            if (remap[v] < 0) {
                remap[v] = k++;
            }
        }
        if (k == n) {
            return m;
        }
        TriMesh out = new TriMesh();
        out.pos = new float[k * 3];
        if (m.region != null) {
            out.region = new byte[k];
        }
        for (int v = 0; v < n; v++) {
            if (remap[v] >= 0) {
                System.arraycopy(m.pos, v * 3, out.pos, remap[v] * 3, 3);
                if (m.region != null) {
                    out.region[remap[v]] = m.region[v];
                }
            }
        }
        out.tri = new int[m.tri.length];
        for (int i = 0; i < m.tri.length; i++) {
            out.tri[i] = remap[m.tri[i]];
        }
        return out;
    }

    private static long key(long x, long y, long z) {
        return (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L);
    }
}
