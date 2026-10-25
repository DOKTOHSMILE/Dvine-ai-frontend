package ai.dvine.face;

/**
 * Faceted volumetric busts. Obi is an angular geometric wireframe profile (Cyber Cyan #00E5FF).
 * Ivie features flowing low-poly cascading hair and warm gold facial geometry (#FFB300).
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
        bust(b, obi);
        if (!obi) {
            flowingHair(b);
            b.flipX();
        } else {
            geometricCrown(b);
        }
        return new LowPolyHead(b.verts(), b.tris(), obi);
    }

    private static void bust(MeshBuilder b, boolean obi) {
        float n = obi ? 1f : 0.90f;
        float jawScale = obi ? 1f : 0.82f;
        float top = obi ? 0.82f : 0.94f;
        int crown = b.add(0.00f, top, 0f);
        int fore = b.add(0.18f, obi ? 0.60f : 0.66f, 0f);
        int brow = b.add(0.32f, 0.42f, 0f);
        int bridge = b.add(0.42f * n, 0.20f, 0f);
        int tip = b.add(0.80f * n, 0.02f, 0f);
        int under = b.add(0.48f * n, -0.08f, 0f);
        int lip = b.add(0.42f, -0.20f, 0f);
        int chin = b.add(0.30f * jawScale, -0.46f, 0f);
        int throat = b.add(0.08f, -0.68f, 0f);
        int neck = b.add(0.03f, -1.00f, 0f);
        int occiput = b.add(-0.36f, obi ? 0.64f : 0.72f, 0f);
        int back = b.add(-0.42f, 0.18f, 0f);
        int nape = b.add(-0.20f, -0.20f, 0f);
        int neckBack = b.add(-0.09f, -0.90f, 0f);

        int temple = b.add(0.05f, 0.54f, 0.44f);
        int browSide = b.add(0.24f, 0.36f, 0.38f);
        int cheek = b.add(0.20f, 0.04f, 0.42f);
        int jaw = b.add(0.08f, -0.28f, 0.36f * jawScale);
        int ear = b.add(-0.05f, 0.10f, 0.60f);
        int rear = b.add(-0.28f, 0.30f, 0.32f);
        int neckSide = b.add(0.01f, -0.84f, 0.20f);

        int templeL = b.add(0.05f, 0.54f, -0.44f);
        int browSideL = b.add(0.24f, 0.36f, -0.38f);
        int cheekL = b.add(0.20f, 0.04f, -0.42f);
        int jawL = b.add(0.08f, -0.28f, -0.36f * jawScale);
        int earL = b.add(-0.05f, 0.10f, -0.60f);
        int rearL = b.add(-0.28f, 0.30f, -0.32f);
        int neckSideL = b.add(0.01f, -0.84f, -0.20f);

        side(b, crown, fore, brow, bridge, tip, under, lip, chin, throat, neck,
                occiput, back, nape, neckBack, temple, browSide, cheek, jaw, ear, rear, neckSide, false);
        side(b, crown, fore, brow, bridge, tip, under, lip, chin, throat, neck,
                occiput, back, nape, neckBack, templeL, browSideL, cheekL, jawL, earL, rearL, neckSideL, true);
        b.tri(neck, neckBack, neckSide);
        b.tri(neck, neckSideL, neckBack);
    }

    private static void side(MeshBuilder b,
                             int crown, int fore, int brow, int bridge, int tip, int under, int lip,
                             int chin, int throat, int neck, int occiput, int back, int nape, int neckBack,
                             int temple, int browSide, int cheek, int jaw, int ear, int rear, int neckSide,
                             boolean far) {
        int[][] faces = new int[][]{
                {crown, fore, temple},
                {fore, brow, browSide},
                {fore, browSide, temple},
                {brow, bridge, browSide},
                {bridge, tip, cheek},
                {bridge, cheek, browSide},
                {tip, under, cheek},
                {under, lip, jaw},
                {under, jaw, cheek},
                {lip, chin, jaw},
                {chin, throat, jaw},
                {throat, neck, neckSide},
                {jaw, throat, neckSide},
                {crown, occiput, temple},
                {occiput, rear, temple},
                {occiput, back, rear},
                {back, nape, rear},
                {nape, neckBack, neckSide},
                {rear, nape, jaw},
                {temple, cheek, ear},
                {cheek, jaw, ear},
                {temple, ear, rear}
        };
        for (int i = 0; i < faces.length; i++) {
            int a = faces[i][0];
            int c = faces[i][1];
            int d = faces[i][2];
            if (far) {
                b.tri(a, d, c);
            } else {
                b.tri(a, c, d);
            }
        }
    }

    private static void flowingHair(MeshBuilder b) {
        // Cascading low-poly volumetric hair strands for Ivie
        float[] depths = new float[]{-0.42f, -0.15f, 0.15f, 0.42f};
        for (int s = 0; s < depths.length; s++) {
            int prevL = -1;
            int prevR = -1;
            for (int k = 0; k < 8; k++) {
                float t = k / 7f;
                float x = -0.08f - t * 0.72f + (s * 0.04f);
                float y = 0.90f - t * 1.25f - (s * 0.05f);
                float z = depths[s] * (0.9f + t * 0.4f);
                int left = b.add(x, y, z - 0.06f);
                int right = b.add(x, y - 0.03f, z + 0.06f);
                if (prevL >= 0) {
                    b.tri(prevL, left, prevR);
                    b.tri(prevR, left, right);
                }
                prevL = left;
                prevR = right;
            }
        }
    }

    private static void geometricCrown(MeshBuilder b) {
        // Additional geometric facets for Obi's sharp angular crown
        int c1 = b.add(0.00f, 0.92f, 0.18f);
        int c2 = b.add(0.00f, 0.92f, -0.18f);
        int f1 = b.add(0.12f, 0.78f, 0.22f);
        int f2 = b.add(0.12f, 0.78f, -0.22f);
        b.tri(c1, f1, f2);
        b.tri(c1, f2, c2);
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
            boolean hair = !obi && x > 0.15f && y < 0.95f;
            if (!hair && y < hingeY && y > -0.78f && faceX > -0.20f) {
                float dy = y - hingeY;
                float dx = x - hingeX;
                float along = obi ? dx : -dx;
                float turned = hingeX + (obi ? 1f : -1f) * (along * ca + dy * sa);
                y = hingeY + (-along * sa + dy * ca);
                x = turned;
            }
            if (hair) {
                float fall = Math.min(1f, Math.max(0f, (x - 0.10f) / 0.8f));
                z += (float) Math.sin(time * 0.8f + z * 3.2f) * 0.035f * fall;
                y += (float) Math.sin(time * 0.5f + x) * 0.015f * fall;
            }
            out[o] = x;
            out[o + 1] = y;
            out[o + 2] = z;
        }
    }
}

final class MeshBuilder {
    private final float[] data = new float[1200];
    private final int[] idx = new int[2400];
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

