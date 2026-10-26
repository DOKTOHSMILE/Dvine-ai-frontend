package ai.dvine.face;

import java.util.Arrays;

/** Lighting and depth order for skin triangles. Pure Java, shared by head and hands. */
public final class Shade {
    private Shade() {
    }

    /**
     * Per-vertex light 0..1 from projected positions (screen x, y and view z
     * per vertex). Normals face the viewer.
     */
    public static void light(float[] screen, int[] tris, int vertexCount, float unit, float[] normals, float[] light) {
        for (int i = 0; i < vertexCount * 3; i++) {
            normals[i] = 0f;
        }
        for (int t = 0; t < tris.length; t += 3) {
            int a = tris[t] * 3;
            int b = tris[t + 1] * 3;
            int c = tris[t + 2] * 3;
            float ax = (screen[b] - screen[a]) / unit;
            float ay = -(screen[b + 1] - screen[a + 1]) / unit;
            float az = screen[b + 2] - screen[a + 2];
            float bx = (screen[c] - screen[a]) / unit;
            float by = -(screen[c + 1] - screen[a + 1]) / unit;
            float bz = screen[c + 2] - screen[a + 2];
            float nx = ay * bz - az * by;
            float ny = az * bx - ax * bz;
            float nz = ax * by - ay * bx;
            if (nz < 0f) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            for (int k = 0; k < 3; k++) {
                int v = tris[t + k] * 3;
                normals[v] += nx;
                normals[v + 1] += ny;
                normals[v + 2] += nz;
            }
        }
        for (int i = 0; i < vertexCount; i++) {
            float nx = normals[i * 3];
            float ny = normals[i * 3 + 1];
            float nz = normals[i * 3 + 2];
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-6f) {
                light[i] = 0f;
                continue;
            }
            float d = (-0.35f * nx + 0.45f * ny + 0.82f * nz) / len;
            float rim = 1f - nz / len;
            float lit = Math.max(0f, d);
            light[i] = Math.max(0f, Math.min(1f, 0.04f + 0.9f * lit * lit + 0.45f * rim * rim * rim));
        }
    }

    /**
     * Writes the visible triangles far-to-near into {@code out} and returns how
     * many indices were written. Triangles behind {@code minZ} are dropped.
     */
    public static int order(float[] screen, int[] tris, float minZ, long[] keys, short[] out) {
        int n = 0;
        for (int t = 0, k = 0; t < tris.length; t += 3, k++) {
            float z = (screen[tris[t] * 3 + 2] + screen[tris[t + 1] * 3 + 2] + screen[tris[t + 2] * 3 + 2]) / 3f;
            if (z < minZ) {
                continue;
            }
            long depth = (long) ((z + 10f) * 100000f);
            keys[n++] = (depth << 20) | k;
        }
        Arrays.sort(keys, 0, n);
        int o = 0;
        for (int i = 0; i < n; i++) {
            int t = (int) (keys[i] & 0xFFFFF) * 3;
            out[o++] = (short) tris[t];
            out[o++] = (short) tris[t + 1];
            out[o++] = (short) tris[t + 2];
        }
        return o;
    }

    /**
     * Skin colour for one vertex: warm brown lit by {@code light}, with a rim of
     * the persona's glow colour.
     */
    public static int skin(int base, int glow, float light, float alpha) {
        float l = 0.48f + 0.95f * light;
        float rim = Math.max(0f, light - 0.75f) * 1.2f;
        int r = (int) Math.min(255f, ((base >> 16) & 0xFF) * l + ((glow >> 16) & 0xFF) * rim * 0.35f);
        int g = (int) Math.min(255f, ((base >> 8) & 0xFF) * l + ((glow >> 8) & 0xFF) * rim * 0.35f);
        int b = (int) Math.min(255f, (base & 0xFF) * l + (glow & 0xFF) * rim * 0.35f);
        int a = (int) Math.max(0f, Math.min(255f, alpha * 255f));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
