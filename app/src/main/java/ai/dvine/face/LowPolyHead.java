package ai.dvine.face;

/**
 * Faceted busts. Obi is an angular profile facing right.
 * Ivie is the same face mirrored, with hair trailing behind her.
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
            hair(b);
            b.flipX();
        }
        return new LowPolyHead(b.verts(), b.tris(), obi);
    }

    private static void bust(MeshBuilder b, boolean obi) {
        float n = obi ? 1f : 0.88f;
        float jawScale = obi ? 1f : 0.78f;
        float top = obi ? 0.78f : 0.92f;
        int crown = b.add(0.00f, top, 0f);
        int fore = b.add(0.16f, obi ? 0.58f : 0.64f, 0f);
        int brow = b.add(0.30f, 0.40f, 0f);
        int bridge = b.add(0.40f * n, 0.18f, 0f);
        int tip = b.add(0.78f * n, 0.00f, 0f);
        int under = b.add(0.46f * n, -0.10f, 0f);
        int lip = b.add(0.40f, -0.22f, 0f);
        int chin = b.add(0.28f * jawScale, -0.48f, 0f);
        int throat = b.add(0.06f, -0.70f, 0f);
        int neck = b.add(0.02f, -1.02f, 0f);
        int occiput = b.add(-0.34f, obi ? 0.62f : 0.70f, 0f);
        int back = b.add(-0.40f, 0.16f, 0f);
        int nape = b.add(-0.18f, -0.22f, 0f);
        int neckBack = b.add(-0.08f, -0.92f, 0f);

        int temple = b.add(0.04f, 0.52f, 0.42f);
        int browSide = b.add(0.22f, 0.34f, 0.36f);
        int cheek = b.add(0.18f, 0.02f, 0.40f);
        int jaw = b.add(0.06f, -0.30f, 0.34f * jawScale);
        int ear = b.add(-0.06f, 0.08f, 0.58f);
        int rear = b.add(-0.26f, 0.28f, 0.30f);
        int neckSide = b.add(0.00f, -0.86f, 0.18f);

        int templeL = b.add(0.04f, 0.52f, -0.42f);
        int browSideL = b.add(0.22f, 0.34f, -0.36f);
        int cheekL = b.add(0.18f, 0.02f, -0.40f);
        int jawL = b.add(0.06f, -0.30f, -0.34f * jawScale);
        int earL = b.add(-0.06f, 0.08f, -0.58f);
        int rearL = b.add(-0.26f, 0.28f, -0.30f);
        int neckSideL = b.add(0.00f, -0.86f, -0.18f);

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

    private static void hair(MeshBuilder b) {
        float[] depth = new float[]{-0.36f, 0f, 0.36f};
        for (int s = 0; s < depth.length; s++) {
            int prevL = -1;
            int prevR = -1;
            boolean edge = s != 1;
            for (int k = 0; k < 6; k++) {
                float t = k / 5f;
                float x = edge ? -0.05f - t * 0.12f : -0.22f - t * 0.58f;
                float y = edge ? 0.64f - t * 0.95f : 0.88f - t * 1.15f;
                float z = depth[s] * (edge ? 1.15f : 0.85f + t * 0.3f);
                int left = b.add(x, y, z - 0.05f);
                int right = b.add(x, y - 0.02f, z + 0.05f);
                if (prevL >= 0) {
                    b.tri(prevL, left, prevR);
                    b.tri(prevR, left, right);
                }
                prevL = left;
                prevR = right;
            }
        }
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
                z += (float) Math.sin(time * 0.7f + z * 3f) * 0.03f * fall;
                y += (float) Math.sin(time * 0.45f + x) * 0.012f * fall;
            }
            out[o] = x;
            out[o + 1] = y;
            out[o + 2] = z;
        }
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
