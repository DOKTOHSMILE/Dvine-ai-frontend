package ai.dvine.face;

/**
 * Procedural wireframe hand. A palm grid and five three-segment fingers built
 * as small four-sided tubes. The topology is fixed; {@link #pose} recomputes
 * positions from finger curls, spread and wrist angles every frame.
 *
 * Local space: wrist at the origin, fingers along +y, palm facing +z (the
 * viewer), thumb on +x. Mirror x for the other hand.
 */
public final class WireHand {
    private static final float[][] LEN = {
            {0.30f, 0.24f, 0.20f}, // thumb
            {0.36f, 0.24f, 0.18f},
            {0.40f, 0.26f, 0.19f},
            {0.37f, 0.24f, 0.18f},
            {0.29f, 0.19f, 0.15f}
    };
    private static final float[] BASE_X = {0.30f, 0.26f, 0.09f, -0.09f, -0.26f};
    private static final float[] BASE_Y = {0.14f, 0.86f, 0.90f, 0.87f, 0.80f};
    private static final float[] WIDTH = {0.085f, 0.075f, 0.078f, 0.074f, 0.064f};
    private static final int RING = 4;
    private static final int PALM_ROWS = 4;
    private static final int PALM_COLS = 4;

    public final int[] edges;
    /** Skin triangles: palm grid and the four faces of every finger segment. */
    public final int[] tris;
    private final int vertexCount;

    public WireHand() {
        int palm = PALM_ROWS * PALM_COLS;
        int fingerVerts = 5 * 4 * RING; // 4 rings per finger (base + 3 joints)
        vertexCount = palm + fingerVerts;
        int[] e = new int[4096];
        int n = 0;
        for (int r = 0; r < PALM_ROWS; r++) {
            for (int c = 0; c < PALM_COLS; c++) {
                int i = r * PALM_COLS + c;
                if (c + 1 < PALM_COLS) {
                    e[n++] = i;
                    e[n++] = i + 1;
                }
                if (r + 1 < PALM_ROWS) {
                    e[n++] = i;
                    e[n++] = i + PALM_COLS;
                    if (c + 1 < PALM_COLS) {
                        e[n++] = ((r + c) & 1) == 0 ? i : i + 1;
                        e[n++] = ((r + c) & 1) == 0 ? i + PALM_COLS + 1 : i + PALM_COLS;
                    }
                }
            }
        }
        for (int f = 0; f < 5; f++) {
            int first = palm + f * 4 * RING;
            for (int j = 0; j < 4; j++) {
                int ring = first + j * RING;
                for (int k = 0; k < RING; k++) {
                    e[n++] = ring + k;
                    e[n++] = ring + (k + 1) % RING;
                    if (j < 3) {
                        e[n++] = ring + k;
                        e[n++] = ring + RING + k;
                        if ((k & 1) == 0) {
                            e[n++] = ring + k;
                            e[n++] = ring + RING + (k + 1) % RING;
                        }
                    }
                }
            }
            // tie finger base to the nearest palm points
            int anchor = f == 0 ? PALM_COLS : (PALM_ROWS - 1) * PALM_COLS + (4 - f) * (PALM_COLS - 1) / 3;
            e[n++] = first;
            e[n++] = Math.min(palm - 1, anchor);
            e[n++] = first + 2;
            e[n++] = Math.min(palm - 1, anchor);
        }
        edges = new int[n];
        System.arraycopy(e, 0, edges, 0, n);
        int[] t = new int[4096];
        int m = 0;
        for (int r = 0; r + 1 < PALM_ROWS; r++) {
            for (int c = 0; c + 1 < PALM_COLS; c++) {
                int i = r * PALM_COLS + c;
                t[m++] = i; t[m++] = i + 1; t[m++] = i + PALM_COLS + 1;
                t[m++] = i; t[m++] = i + PALM_COLS + 1; t[m++] = i + PALM_COLS;
            }
        }
        for (int f = 0; f < 5; f++) {
            int first = palm + f * 4 * RING;
            for (int j = 0; j < 3; j++) {
                int ring = first + j * RING;
                for (int k = 0; k < RING; k++) {
                    int a = ring + k;
                    int b = ring + (k + 1) % RING;
                    t[m++] = a; t[m++] = b; t[m++] = b + RING;
                    t[m++] = a; t[m++] = b + RING; t[m++] = a + RING;
                }
            }
            int tip = first + 3 * RING;
            t[m++] = tip; t[m++] = tip + 1; t[m++] = tip + 2;
            t[m++] = tip; t[m++] = tip + 2; t[m++] = tip + 3;
        }
        tris = new int[m];
        System.arraycopy(t, 0, tris, 0, m);
    }

    public int vertexCount() {
        return vertexCount;
    }

    /** Joint state for one hand. */
    public static final class Pose {
        /** thumb, index, middle, ring, pinky: 0 straight .. 1 fully curled. */
        public final float[] curl = new float[5];
        /** Per-finger sideways angle in radians (index..pinky use 1..4). */
        public final float[] yaw = new float[5];
        public float thumbOut = 1f;
        public float rollDeg;
        public float pitchDeg;
        public float yawDeg;

        public void set(Pose o) {
            System.arraycopy(o.curl, 0, curl, 0, 5);
            System.arraycopy(o.yaw, 0, yaw, 0, 5);
            thumbOut = o.thumbOut;
            rollDeg = o.rollDeg;
            pitchDeg = o.pitchDeg;
            yawDeg = o.yawDeg;
        }
    }

    /**
     * Writes posed positions (xyz per vertex) in hand space, scaled by
     * {@code scale}, mirrored when {@code mirror}, then offset by (ox, oy, oz).
     */
    public void pose(Pose p, boolean mirror, float scale, float ox, float oy, float oz, float[] out) {
        int v = 0;
        float palmCurl = 0.12f * (p.curl[1] + p.curl[2] + p.curl[3] + p.curl[4]) / 4f;
        for (int r = 0; r < PALM_ROWS; r++) {
            float ty = r / (float) (PALM_ROWS - 1);
            float halfW = 0.24f + 0.10f * ty;
            for (int c = 0; c < PALM_COLS; c++) {
                float tx = c / (float) (PALM_COLS - 1);
                float x = -halfW + 2f * halfW * tx;
                float y = ty * 0.82f;
                float z = -0.04f * (1f - (float) Math.cos((tx - 0.5f) * Math.PI)) + palmCurl * ty;
                v = put(out, v, x, y, z);
            }
        }
        for (int f = 0; f < 5; f++) {
            float bx = BASE_X[f];
            float by = BASE_Y[f];
            float bz = 0.02f;
            float phi;
            float theta;
            float[] bend;
            float c = clamp(p.curl[f], 0f, 1f);
            if (f == 0) {
                phi = (0.7f * p.thumbOut) - c * 0.95f;
                theta = 0.25f + c * 0.7f;
                bend = new float[]{c * 0.3f, c * 0.55f, c * 0.5f};
            } else {
                phi = p.yaw[f];
                theta = 0f;
                bend = new float[]{c * 1.45f, c * 1.65f, c * 1.1f};
            }
            float w = WIDTH[f];
            float px = bx;
            float py = by;
            float pz = bz;
            for (int j = 0; j < 4; j++) {
                if (j > 0) {
                    theta += bend[j - 1];
                    float len = LEN[f][j - 1];
                    px += len * (float) (Math.sin(phi) * Math.cos(theta));
                    py += len * (float) (Math.cos(phi) * Math.cos(theta));
                    pz += len * (float) Math.sin(theta);
                }
                float dx = (float) (Math.sin(phi) * Math.cos(theta));
                float dy = (float) (Math.cos(phi) * Math.cos(theta));
                float dz = (float) Math.sin(theta);
                float ux = (float) Math.cos(phi);
                float uy = (float) -Math.sin(phi);
                float uz = 0f;
                float vx = dy * uz - dz * uy;
                float vy = dz * ux - dx * uz;
                float vz = dx * uy - dy * ux;
                float taper = w * (1f - j * 0.12f);
                for (int k = 0; k < RING; k++) {
                    double a = k * Math.PI * 2 / RING + Math.PI / 4;
                    float ca = (float) Math.cos(a) * taper;
                    float sa = (float) Math.sin(a) * taper * 0.8f;
                    v = put(out, v, px + ux * ca + vx * sa, py + uy * ca + vy * sa, pz + uz * ca + vz * sa);
                }
            }
        }
        // wrist rotation, mirror, scale, offset
        float roll = (float) Math.toRadians(p.rollDeg);
        float pitch = (float) Math.toRadians(p.pitchDeg);
        float yaw = (float) Math.toRadians(p.yawDeg);
        float cr = (float) Math.cos(roll), sr = (float) Math.sin(roll);
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        for (int i = 0; i < vertexCount; i++) {
            float x = out[i * 3];
            float y = out[i * 3 + 1];
            float z = out[i * 3 + 2];
            float x1 = x * cr - y * sr;
            float y1 = x * sr + y * cr;
            float y2 = y1 * cp - z * sp;
            float z2 = y1 * sp + z * cp;
            float x3 = x1 * cy + z2 * sy;
            float z3 = -x1 * sy + z2 * cy;
            if (mirror) {
                x3 = -x3;
            }
            out[i * 3] = ox + x3 * scale;
            out[i * 3 + 1] = oy + y2 * scale;
            out[i * 3 + 2] = oz + z3 * scale;
        }
    }

    private static int put(float[] out, int v, float x, float y, float z) {
        out[v * 3] = x;
        out[v * 3 + 1] = y;
        out[v * 3 + 2] = z;
        return v + 1;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
