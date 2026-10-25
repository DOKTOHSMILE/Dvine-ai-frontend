package ai.dvine.face;

/** Sparse wireframe skull. Brows, eyes, and lips are rigged on top of this. */
public final class LowPolyHead {
    public final float[] rest;
    public final int[] tris;
    public final int rings;
    public final int sectors;
    public final boolean obi;

    private LowPolyHead(float[] rest, int[] tris, int rings, int sectors, boolean obi) {
        this.rest = rest;
        this.tris = tris;
        this.rings = rings;
        this.sectors = sectors;
        this.obi = obi;
    }

    public static LowPolyHead create(boolean obi) {
        int rings = obi ? 8 : 9;
        int sectors = obi ? 8 : 12;
        float[] rest = new float[rings * sectors * 3];
        int n = 0;
        for (int i = 0; i < rings; i++) {
            float t = rings == 1 ? 0f : i / (float) (rings - 1);
            float phi = (float) (Math.PI * (0.10 + 0.80 * t));
            for (int j = 0; j < sectors; j++) {
                float theta = (float) (j * Math.PI * 2.0 / sectors);
                float x = (float) (Math.sin(phi) * Math.sin(theta));
                float y = (float) Math.cos(phi);
                float z = (float) (Math.sin(phi) * Math.cos(theta));
                if (z > 0f && Math.abs(x) < 0.20f && y < 0.30f && y > -0.16f) {
                    float nose = (1f - Math.abs(x) / 0.20f);
                    float along = (float) Math.cos((y - 0.06f) * 3.4f);
                    if (along > 0f) {
                        z += (obi ? 0.30f : 0.24f) * nose * along;
                    }
                }
                if (z > 0.05f && y > 0.02f && y < 0.30f && Math.abs(Math.abs(x) - 0.32f) < 0.14f) {
                    z -= 0.10f;
                }
                if (z > 0f && y < 0.12f && y > -0.28f && Math.abs(x) > 0.28f && Math.abs(x) < 0.62f) {
                    z += obi ? 0.02f : 0.07f;
                }
                if (obi && y > 0.52f) {
                    y = 0.52f + (y - 0.52f) * 0.22f;
                }
                if (y < -0.12f) {
                    x *= obi ? 1.10f : 0.86f;
                }
                if (t > 0.72f) {
                    float neck = (t - 0.72f) / 0.28f;
                    y -= neck * 0.28f;
                    x *= 1f - neck * 0.38f;
                    z *= 1f - neck * 0.30f;
                }
                if (!obi) {
                    x *= 0.96f;
                    y *= 1.03f;
                }
                rest[n++] = x;
                rest[n++] = y;
                rest[n++] = z;
            }
        }
        int quads = (rings - 1) * sectors;
        int[] tris = new int[quads * 6];
        int p = 0;
        for (int i = 0; i < rings - 1; i++) {
            for (int j = 0; j < sectors; j++) {
                int a = i * sectors + j;
                int b = i * sectors + (j + 1) % sectors;
                int c = (i + 1) * sectors + j;
                int d = (i + 1) * sectors + (j + 1) % sectors;
                tris[p++] = a;
                tris[p++] = d;
                tris[p++] = b;
                tris[p++] = a;
                tris[p++] = c;
                tris[p++] = d;
            }
        }
        return new LowPolyHead(rest, tris, rings, sectors, obi);
    }

    public void deform(float smile, float jaw, float browL, float browR, float[] out) {
        System.arraycopy(rest, 0, out, 0, rest.length);
        int count = rest.length / 3;
        float hingeY = -0.04f;
        float ang = jaw * 0.40f;
        float ca = (float) Math.cos(ang);
        float sa = (float) Math.sin(ang);
        for (int i = 0; i < count; i++) {
            int o = i * 3;
            float x = out[o];
            float y = out[o + 1];
            float z = out[o + 2];
            float ax = Math.abs(x);
            if (y > 0.20f && y < 0.50f && z > 0.02f && ax > 0.05f && ax < 0.62f) {
                y += (x < 0f ? browL : browR) * 0.055f;
            }
            if (z > 0.08f && y < 0.08f && y > -0.42f && ax > 0.10f && ax < 0.55f) {
                float w = 1f - Math.min(1f, Math.abs(ax - 0.28f) / 0.30f);
                y += smile * 0.055f * w;
                x += Math.copySign(smile * 0.018f * w, x == 0f ? 1f : x);
            }
            if (y < hingeY && z > -0.35f) {
                float dy = y - hingeY;
                float dz = z - 0.05f;
                y = hingeY + dy * ca - dz * sa;
                z = 0.05f + dy * sa + dz * ca;
            }
            out[o] = x;
            out[o + 1] = y;
            out[o + 2] = z;
        }
    }

    public int frontFaceCount() {
        int faces = 0;
        for (int t = 0; t < tris.length; t += 3) {
            int a = tris[t] * 3;
            int b = tris[t + 1] * 3;
            int c = tris[t + 2] * 3;
            float ax = rest[b] - rest[a];
            float ay = rest[b + 1] - rest[a + 1];
            float az = rest[b + 2] - rest[a + 2];
            float bx = rest[c] - rest[a];
            float by = rest[c + 1] - rest[a + 1];
            float bz = rest[c + 2] - rest[a + 2];
            float nz = ax * by - ay * bx;
            float cx = (rest[a] + rest[b] + rest[c]) / 3f;
            float cz = (rest[a + 2] + rest[b + 2] + rest[c + 2]) / 3f;
            if (cz > 0.15f && nz > 0f && Math.abs(cx) < 1.2f) {
                faces++;
            }
        }
        return faces;
    }
}
