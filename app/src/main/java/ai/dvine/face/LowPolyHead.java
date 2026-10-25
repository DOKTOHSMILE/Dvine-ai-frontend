package ai.dvine.face;

/**
 * Authored low-poly busts, not a dented sphere.
 * Obi is an angular masculine profile. Ivie is the same scaffold mirrored,
 * with a smaller jaw and long hair trailing behind her.
 */
public final class LowPolyHead {
    public final float[] rest;
    public final int[] tris;
    public final boolean obi;

    private LowPolyHead(float[] rest, int[] tris, boolean obi) {
        this.rest = rest;
        this.tris = tris;
        this.obi = obi;
    }

    public static LowPolyHead create(boolean obi) {
        MeshBuilder b = new MeshBuilder();
        float[][] rings = obi ? obiRings() : ivieRings();
        int sectors = 10;
        int[][] id = new int[rings.length][sectors];
        int[] pos = new int[rings.length];
        int[] neg = new int[rings.length];
        float[] posAng = new float[rings.length];
        float[] negAng = new float[rings.length];
        for (int i = 0; i < rings.length; i++) {
            posAng[i] = 99f;
            negAng[i] = 99f;
        }
        for (int i = 0; i < rings.length; i++) {
            for (int j = 0; j < sectors; j++) {
                float ang = (float) (j * Math.PI * 2.0 / sectors);
                float nx = (float) Math.cos(ang);
                float nz = (float) Math.sin(ang);
                float nose = (float) Math.pow(Math.max(0f, nx), 2.2f) * rings[i][3];
                float flare = nz * nz * rings[i][4];
                float x = nx * rings[i][1] + nose;
                float z = nz * (rings[i][2] + flare);
                float y = rings[i][0];
                if (i == 3 && nx > 0.15f && Math.abs(nz) > 0.28f && Math.abs(nz) < 0.92f) {
                    x -= obi ? 0.07f : 0.09f;
                }
                if (obi && i == 0) {
                    y = 0.84f;
                }
                id[i][j] = b.add(x, y, z);
                float dp = Math.abs(ang - (float) (Math.PI * 0.5));
                float dn = Math.abs(ang - (float) (Math.PI * 1.5));
                if (dn > Math.PI) {
                    dn = (float) (Math.PI * 2.0 - dn);
                }
                if (dp < posAng[i]) {
                    posAng[i] = dp;
                    pos[i] = id[i][j];
                }
                if (dn < negAng[i]) {
                    negAng[i] = dn;
                    neg[i] = id[i][j];
                }
            }
        }
        for (int i = 0; i < rings.length - 1; i++) {
            for (int j = 0; j < sectors; j++) {
                int j2 = (j + 1) % sectors;
                b.tri(id[i][j], id[i + 1][j2], id[i][j2]);
                b.tri(id[i][j], id[i + 1][j], id[i + 1][j2]);
            }
        }
        ear(b, pos, obi, 1f);
        ear(b, neg, obi, -1f);
        if (!obi) {
            hair(b);
        }
        if (!obi) {
            b.flipX();
        }
        return new LowPolyHead(b.verts(), b.tris(), obi);
    }

    private static void ear(MeshBuilder b, int[] side, boolean obi, float sign) {
        float out = (obi ? 0.72f : 0.60f) * sign;
        int tip = b.add(0.04f, 0.20f, out);
        int lobe = b.add(-0.08f, -0.06f, out * 0.92f);
        b.tri(side[2], tip, side[4]);
        b.tri(side[4], tip, lobe);
        b.tri(side[4], lobe, side[6]);
    }

    private static void hair(MeshBuilder b) {
        int strands = 7;
        for (int s = 0; s < strands; s++) {
            float z0 = -0.46f + s * (0.92f / (strands - 1));
            boolean edge = s == 0 || s == strands - 1;
            int prevL = -1;
            int prevR = -1;
            int steps = 8;
            for (int k = 0; k < steps; k++) {
                float t = k / (float) (steps - 1);
                float x;
                float y;
                float z;
                if (edge) {
                    float side = s == 0 ? -1f : 1f;
                    x = -0.08f - t * 0.08f;
                    y = 0.78f - t * 1.12f;
                    z = side * (0.50f + t * 0.20f);
                } else {
                    x = -0.18f - t * 0.62f;
                    y = 0.96f - t * 1.05f - Math.abs(z0) * 0.18f;
                    z = z0 * (0.75f + t * 0.55f);
                }
                int left = b.add(x, y, z - 0.045f);
                int right = b.add(x * 0.98f, y, z + 0.045f);
                if (prevL >= 0) {
                    b.tri(prevL, left, prevR);
                    b.tri(prevR, left, right);
                }
                prevL = left;
                prevR = right;
            }
        }
    }

    /** y, forward radius, side radius, nose, jaw flare. */
    private static float[][] obiRings() {
        return new float[][]{
                {0.90f, 0.26f, 0.34f, 0.00f, 0.00f},
                {0.70f, 0.42f, 0.48f, 0.02f, 0.00f},
                {0.50f, 0.46f, 0.52f, 0.08f, 0.02f},
                {0.30f, 0.40f, 0.50f, 0.14f, 0.02f},
                {0.10f, 0.32f, 0.48f, 0.40f, 0.06f},
                {-0.08f, 0.38f, 0.50f, 0.18f, 0.10f},
                {-0.26f, 0.40f, 0.48f, 0.10f, 0.14f},
                {-0.46f, 0.30f, 0.40f, 0.04f, 0.08f},
                {-0.70f, 0.16f, 0.22f, 0.00f, 0.00f},
                {-1.05f, 0.22f, 0.30f, 0.00f, 0.00f}
        };
    }

    private static float[][] ivieRings() {
        return new float[][]{
                {0.96f, 0.24f, 0.32f, 0.00f, 0.00f},
                {0.74f, 0.38f, 0.44f, 0.02f, 0.00f},
                {0.52f, 0.42f, 0.46f, 0.06f, 0.00f},
                {0.32f, 0.36f, 0.44f, 0.12f, 0.00f},
                {0.12f, 0.28f, 0.42f, 0.32f, 0.02f},
                {-0.06f, 0.32f, 0.42f, 0.14f, 0.04f},
                {-0.22f, 0.34f, 0.40f, 0.08f, 0.04f},
                {-0.40f, 0.24f, 0.32f, 0.03f, 0.02f},
                {-0.64f, 0.14f, 0.20f, 0.00f, 0.00f},
                {-1.00f, 0.18f, 0.24f, 0.00f, 0.00f}
        };
    }

    public void deform(float smile, float jaw, float browL, float browR, float time, float[] out) {
        System.arraycopy(rest, 0, out, 0, rest.length);
        int count = rest.length / 3;
        float hingeY = -0.06f;
        float hingeX = 0.02f;
        float ang = jaw * (obi ? 0.42f : 0.36f);
        float ca = (float) Math.cos(ang);
        float sa = (float) Math.sin(ang);
        for (int i = 0; i < count; i++) {
            int o = i * 3;
            float x = out[o];
            float y = out[o + 1];
            float z = out[o + 2];
            float faceX = obi ? x : -x;
            if (y > 0.36f && y < 0.62f && faceX > 0.12f) {
                y += (z > 0f ? browR : browL) * 0.045f;
            }
            if (y < -0.02f && y > -0.36f && faceX > 0.08f && Math.abs(z) > 0.10f && Math.abs(z) < 0.42f) {
                y += smile * (obi ? 0.035f : 0.05f);
            }
            boolean hair = !obi && x > 0.20f && y < 0.90f;
            if (!hair && y < hingeY && y > -0.78f && faceX > -0.20f) {
                float dy = y - hingeY;
                float dx = x - hingeX;
                float along = obi ? dx : -dx;
                float turned = hingeX + (obi ? 1f : -1f) * (along * ca + dy * sa);
                y = hingeY + (-along * sa + dy * ca);
                x = turned;
            }
            if (hair) {
                float fall = Math.min(1f, Math.max(0f, (x - 0.15f) / 0.7f));
                float sway = (float) Math.sin(time * 0.7f + z * 3f) * 0.03f * fall;
                z += sway;
                y += (float) Math.sin(time * 0.45f + x) * 0.012f * fall;
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
            float cz = (rest[a + 2] + rest[b + 2] + rest[c + 2]) / 3f;
            if (cz > 0f && nz != 0f) {
                faces++;
            }
        }
        return faces;
    }
}

final class MeshBuilder {
        private final float[] data = new float[900];
        private final int[] idx = new int[1800];
        private int n;
        private int t;

        int add(float x, float y, float z) {
            int id = n / 3;
            data[n++] = x;
            data[n++] = y;
            data[n++] = z;
            return id;
        }

        void tri(int a, int b, int c) {
            idx[t++] = a;
            idx[t++] = b;
            idx[t++] = c;
        }

        void flipX() {
            for (int i = 0; i < n; i += 3) {
                data[i] = -data[i];
            }
        }

        float[] verts() {
            float[] out = new float[n];
            System.arraycopy(data, 0, out, 0, n);
            return out;
        }

        int[] tris() {
            int[] out = new int[t];
            System.arraycopy(idx, 0, out, 0, t);
            return out;
        }
}
