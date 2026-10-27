package ai.dvine.geom;

/**
 * An indexed triangle mesh with everything the digital human needs per
 * vertex: position, normal, body-part region and two-bone skin weights.
 * Triangles wind counter-clockwise seen from outside. Edges are the unique
 * undirected triangle edges, sorted most important first (sharp creases and
 * feature lines before flat interior edges) so a renderer can draw fewer of
 * them at a distance.
 */
public final class TriMesh {
    public float[] pos;
    public float[] nor;
    public int[] tri;
    /** Unique edges, two vertex indices each, most important first. */
    public int[] edge;
    /** Region id per vertex (see {@code ai.dvine.human.Regions}). */
    public byte[] region;
    /** Skin: two bones per vertex and the weight of the first (the second gets 1 − w). */
    public byte[] bone0;
    public byte[] bone1;
    public float[] weight0;
    /** For a simplified mesh: the source vertex each vertex was kept from (not saved). */
    public int[] origin;

    public int vertexCount() {
        return pos.length / 3;
    }

    public int triangleCount() {
        return tri.length / 3;
    }

    public int edgeCount() {
        return edge == null ? 0 : edge.length / 2;
    }

    /** Recomputes area-weighted vertex normals from the triangles. */
    public void computeNormals() {
        int n = vertexCount();
        if (nor == null || nor.length != n * 3) {
            nor = new float[n * 3];
        }
        normals(pos, tri, nor);
    }

    public static void normals(float[] pos, int[] tri, float[] out) {
        java.util.Arrays.fill(out, 0f);
        for (int t = 0; t < tri.length; t += 3) {
            int a = tri[t] * 3, b = tri[t + 1] * 3, c = tri[t + 2] * 3;
            float ux = pos[b] - pos[a], uy = pos[b + 1] - pos[a + 1], uz = pos[b + 2] - pos[a + 2];
            float vx = pos[c] - pos[a], vy = pos[c + 1] - pos[a + 1], vz = pos[c + 2] - pos[a + 2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            for (int k : new int[]{a, b, c}) {
                out[k] += nx;
                out[k + 1] += ny;
                out[k + 2] += nz;
            }
        }
        for (int i = 0; i < out.length; i += 3) {
            float l = (float) Math.sqrt(out[i] * out[i] + out[i + 1] * out[i + 1] + out[i + 2] * out[i + 2]);
            if (l > 1e-12f) {
                out[i] /= l;
                out[i + 1] /= l;
                out[i + 2] /= l;
            }
        }
    }

    /**
     * Builds the unique edge list, ordered by importance: the angle between
     * the two faces that share an edge (creases, silhouettes of small
     * features) plus a boost where the two ends lie in different regions.
     */
    public void buildEdges() {
        int nt = triangleCount();
        java.util.HashMap<Long, int[]> map = new java.util.HashMap<Long, int[]>(nt * 2);
        float[] fn = new float[nt * 3];
        for (int t = 0; t < nt; t++) {
            int a = tri[t * 3] * 3, b = tri[t * 3 + 1] * 3, c = tri[t * 3 + 2] * 3;
            float ux = pos[b] - pos[a], uy = pos[b + 1] - pos[a + 1], uz = pos[b + 2] - pos[a + 2];
            float vx = pos[c] - pos[a], vy = pos[c + 1] - pos[a + 1], vz = pos[c + 2] - pos[a + 2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-20f;
            fn[t * 3] = nx / l;
            fn[t * 3 + 1] = ny / l;
            fn[t * 3 + 2] = nz / l;
            for (int k = 0; k < 3; k++) {
                int i = tri[t * 3 + k], j = tri[t * 3 + (k + 1) % 3];
                long key = i < j ? ((long) i << 32) | j : ((long) j << 32) | i;
                int[] e = map.get(key);
                if (e == null) {
                    map.put(key, new int[]{Math.min(i, j), Math.max(i, j), t, -1});
                } else {
                    e[3] = t;
                }
            }
        }
        int m = map.size();
        long[] order = new long[m];
        int[][] list = new int[m][];
        int k = 0;
        for (int[] e : map.values()) {
            float score;
            if (e[3] < 0) {
                score = 2f; // open boundary: always drawn first
            } else {
                float d = fn[e[2] * 3] * fn[e[3] * 3] + fn[e[2] * 3 + 1] * fn[e[3] * 3 + 1] + fn[e[2] * 3 + 2] * fn[e[3] * 3 + 2];
                score = 1f - d; // 0 flat .. 2 folded back
                if (region != null && region[e[0]] != region[e[1]]) {
                    score += 0.15f;
                }
            }
            list[k] = e;
            // sort key: score descending, then index for stability
            order[k] = ((long) (Integer.MAX_VALUE - (int) (score * 1e6f)) << 32) | k;
            k++;
        }
        java.util.Arrays.sort(order);
        edge = new int[m * 2];
        for (int i = 0; i < m; i++) {
            int[] e = list[(int) (order[i] & 0xffffffffL)];
            edge[i * 2] = e[0];
            edge[i * 2 + 1] = e[1];
        }
    }

    /** Axis-aligned bounds: minX, minY, minZ, maxX, maxY, maxZ. */
    public float[] bounds() {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i = 0; i < pos.length; i += 3) {
            for (int k = 0; k < 3; k++) {
                b[k] = Math.min(b[k], pos[i + k]);
                b[k + 3] = Math.max(b[k + 3], pos[i + k]);
            }
        }
        return b;
    }

    /** Appends another mesh (indices shifted). Both must carry the same per-vertex arrays. */
    public static TriMesh merge(TriMesh... parts) {
        int nv = 0, nt = 0;
        for (TriMesh p : parts) {
            nv += p.vertexCount();
            nt += p.tri.length;
        }
        TriMesh m = new TriMesh();
        m.pos = new float[nv * 3];
        m.nor = new float[nv * 3];
        m.tri = new int[nt];
        m.region = new byte[nv];
        m.bone0 = new byte[nv];
        m.bone1 = new byte[nv];
        m.weight0 = new float[nv];
        int vo = 0, to = 0;
        for (TriMesh p : parts) {
            int n = p.vertexCount();
            System.arraycopy(p.pos, 0, m.pos, vo * 3, n * 3);
            if (p.nor != null) {
                System.arraycopy(p.nor, 0, m.nor, vo * 3, n * 3);
            }
            for (int i = 0; i < p.tri.length; i++) {
                m.tri[to + i] = p.tri[i] + vo;
            }
            if (p.region != null) {
                System.arraycopy(p.region, 0, m.region, vo, n);
            }
            if (p.bone0 != null) {
                System.arraycopy(p.bone0, 0, m.bone0, vo, n);
                System.arraycopy(p.bone1, 0, m.bone1, vo, n);
                System.arraycopy(p.weight0, 0, m.weight0, vo, n);
            } else {
                java.util.Arrays.fill(m.weight0, vo, vo + n, 1f);
            }
            vo += n;
            to += p.tri.length;
        }
        return m;
    }
}
