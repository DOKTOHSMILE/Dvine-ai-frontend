package ai.dvine.human;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Point-in-mesh for a skin made of several closed pieces (head, body, eyes,
 * teeth): a point is inside when a ray along +x crosses one piece's surface an
 * odd number of times. Triangles are bucketed by (y, z) so rays are cheap.
 */
final class MeshInside {
    private final float[] p;
    private final int[] tri;
    private final int[] piece;
    final int pieces;
    private final float cell = 0.04f;
    private final Map<Long, List<Integer>> grid = new HashMap<Long, List<Integer>>();

    MeshInside(float[] pos, int[] tri) {
        this.p = pos;
        this.tri = tri;
        int nt = tri.length / 3;
        // pieces: triangles connected through shared vertices
        int nv = pos.length / 3;
        int[] parent = new int[nv];
        for (int i = 0; i < nv; i++) {
            parent[i] = i;
        }
        for (int t = 0; t < nt; t++) {
            union(parent, tri[t * 3], tri[t * 3 + 1]);
            union(parent, tri[t * 3], tri[t * 3 + 2]);
        }
        Map<Integer, Integer> id = new HashMap<Integer, Integer>();
        piece = new int[nt];
        for (int t = 0; t < nt; t++) {
            int r = find(parent, tri[t * 3]);
            Integer k = id.get(r);
            if (k == null) {
                k = id.size();
                id.put(r, k);
            }
            piece[t] = k;
        }
        pieces = id.size();
        for (int t = 0; t < nt; t++) {
            float y0 = Float.MAX_VALUE, y1 = -Float.MAX_VALUE, z0 = Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
            for (int k = 0; k < 3; k++) {
                int v = tri[t * 3 + k];
                y0 = Math.min(y0, p[v * 3 + 1]);
                y1 = Math.max(y1, p[v * 3 + 1]);
                z0 = Math.min(z0, p[v * 3 + 2]);
                z1 = Math.max(z1, p[v * 3 + 2]);
            }
            for (int a = (int) Math.floor(y0 / cell); a <= (int) Math.floor(y1 / cell); a++) {
                for (int b = (int) Math.floor(z0 / cell); b <= (int) Math.floor(z1 / cell); b++) {
                    grid.computeIfAbsent(key(a, b), k -> new ArrayList<Integer>()).add(t);
                }
            }
        }
    }

    /** The piece vertex {@code v} belongs to (-1 for a vertex on no triangle). */
    int pieceOfVertex(int v) {
        for (int t = 0; t < piece.length; t++) {
            if (tri[t * 3] == v || tri[t * 3 + 1] == v || tri[t * 3 + 2] == v) {
                return piece[t];
            }
        }
        return -1;
    }

    /** Which pieces contain the point (bit k set: inside piece k; up to 63 pieces). */
    long insideMask(float x, float y, float z) {
        // a slightly tilted ray avoids passing exactly through edges
        y += 1.3e-5f;
        z += 0.7e-5f;
        List<Integer> c = grid.get(key((int) Math.floor(y / cell), (int) Math.floor(z / cell)));
        long mask = 0L;
        if (c == null) {
            return 0L;
        }
        for (int t : c) {
            int a = tri[t * 3] * 3, b = tri[t * 3 + 1] * 3, d = tri[t * 3 + 2] * 3;
            // the ray x' >= x through (y, z): barycentric test in the y-z plane
            float ay = p[a + 1] - y, az = p[a + 2] - z, by = p[b + 1] - y, bz = p[b + 2] - z, cy = p[d + 1] - y, cz = p[d + 2] - z;
            float w0 = by * cz - bz * cy, w1 = cy * az - cz * ay, w2 = ay * bz - az * by;
            if (!((w0 >= 0 && w1 >= 0 && w2 >= 0) || (w0 <= 0 && w1 <= 0 && w2 <= 0))) {
                continue;
            }
            float s = w0 + w1 + w2;
            if (Math.abs(s) < 1e-12f) {
                continue;
            }
            float hit = (w0 * p[a] + w1 * p[b] + w2 * p[d]) / s;
            if (hit > x) {
                mask ^= 1L << piece[t];
            }
        }
        return mask;
    }

    boolean inside(float x, float y, float z) {
        return insideMask(x, y, z) != 0L;
    }

    private static long key(int a, int b) {
        return ((long) a << 32) ^ (b & 0xffffffffL);
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        parent[find(parent, a)] = find(parent, b);
    }
}
