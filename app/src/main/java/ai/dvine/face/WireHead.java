package ai.dvine.face;

import java.util.ArrayList;
import java.util.List;

/**
 * Procedural wireframe head. Nothing is hand-typed: rings are generated from
 * profile curves, a feature field pushes the nose, brow, cheeks, lips and chin
 * forward, and eyes, brows, lips, ears and (for Ivie) hair are added as their
 * own wire loops. Pure Java so it can be tested off the device.
 *
 * Model space: x right, y up, z toward the viewer. Crown y=+1, chin y≈-1.
 */
public final class WireHead {
    public static final int SKIN = 0;
    public static final int FEATURE = 1;
    public static final int HAIR = 2;

    static final int T_SKIN = 0;
    static final int T_EYE = 1;
    static final int T_BROW = 2;
    static final int T_LIP_UP = 3;
    static final int T_LIP_LO = 4;
    static final int T_LIP_MID = 5;
    static final int T_HAIR = 6;
    static final int T_EAR = 7;
    static final int T_IRIS = 8;

    public final boolean ivie;
    /** Rest positions, xyz per vertex. */
    public final float[] rest;
    /** Edge pairs, two vertex indices each. */
    public final int[] edges;
    /** Draw group per edge: SKIN, FEATURE or HAIR. */
    public final int[] edgeGroup;
    final int[] tag;
    /** Per-vertex parameter: eye upper(1)/lower(0), lip arch, hair t, brow side. */
    final float[] pa;
    /** Second parameter: eye centre x / hair phase / brow side sign. */
    final float[] pb;

    // profile constants
    private final float crownW;
    private final float crownD;
    private final float squareness;
    private final float flatTop;

    private WireHead(boolean ivie) {
        this.ivie = ivie;
        crownW = ivie ? 0.74f : 0.80f;
        crownD = ivie ? 0.88f : 0.92f;
        squareness = ivie ? 2.15f : 2.7f;
        flatTop = ivie ? 2.0f : 3.4f;
        Builder b = new Builder();
        buildSkin(b);
        buildEyes(b);
        buildBrows(b);
        buildLips(b);
        buildNose(b);
        buildEars(b);
        if (ivie) {
            buildHair(b);
        }
        rest = b.positions();
        tag = b.tags();
        pa = b.paramA();
        pb = b.paramB();
        edges = b.edgeArray();
        edgeGroup = b.groupArray();
    }

    public static WireHead create(boolean ivie) {
        return new WireHead(ivie);
    }

    public int vertexCount() {
        return rest.length / 3;
    }

    public int edgeCount() {
        return edges.length / 2;
    }

    // ------------------------------------------------------------------ shape

    /** Half width of the head at height y. */
    float width(float y) {
        if (y >= 0.2f) {
            float s = Math.min(1f, (y - 0.2f) / 0.8f);
            return crownW * superRoot(s);
        }
        if (ivie) {
            return table(y, new float[]{0.2f, 0.74f, -0.1f, 0.72f, -0.45f, 0.66f, -0.72f, 0.52f, -0.92f, 0.26f, -1.0f, 0.13f});
        }
        return table(y, new float[]{0.2f, 0.80f, -0.1f, 0.78f, -0.45f, 0.73f, -0.72f, 0.62f, -0.92f, 0.36f, -1.0f, 0.22f});
    }

    /** Half depth of the head at height y. */
    float depth(float y) {
        if (y >= 0.2f) {
            float s = Math.min(1f, (y - 0.2f) / 0.8f);
            return crownD * superRoot(s);
        }
        return table(y, new float[]{0.2f, crownD, -0.4f, crownD * 0.92f, -0.8f, crownD * 0.72f, -1.0f, crownD * 0.52f});
    }

    private float superRoot(float s) {
        float v = 1f - (float) Math.pow(s, flatTop);
        return (float) Math.pow(Math.max(0f, v), 1f / flatTop);
    }

    /** Forward push of facial structure at (x, y). Only the front of the head uses it. */
    float feature(float x, float y) {
        float ax = Math.abs(x);
        float nose = gauss(x, 0.085f) * (0.05f * band(y, 0.08f, -0.42f) + (ivie ? 0.15f : 0.18f) * gauss(y + 0.38f, 0.09f));
        float sockets = -0.085f * gauss(ax - 0.30f, 0.13f) * gauss(y - 0.02f, 0.08f);
        float brow = (ivie ? 0.035f : 0.065f) * gauss(y - 0.17f, 0.055f) * gauss(x, 0.55f);
        float cheeks = (ivie ? 0.06f : 0.045f) * gauss(ax - 0.40f, 0.14f) * gauss(y + 0.24f, 0.12f);
        float lips = (ivie ? 0.075f : 0.055f) * gauss(x, 0.17f) * gauss(y + 0.63f, 0.06f);
        float groove = -0.025f * gauss(x, 0.14f) * gauss(y + 0.75f, 0.03f);
        float chin = (ivie ? 0.04f : 0.065f) * gauss(x, ivie ? 0.12f : 0.17f) * gauss(y + 0.88f, 0.08f);
        return nose + sockets + brow + cheeks + lips + groove + chin;
    }

    /** z of the front surface at (x, y), used to seat features on the skin. */
    public float surfaceZ(float x, float y) {
        float w = width(y);
        float d = depth(y);
        float u = Math.min(1f, Math.abs(x) / Math.max(0.01f, w));
        float z = d * (float) Math.pow(Math.max(0f, 1f - (float) Math.pow(u, squareness)), 1f / squareness);
        return z + feature(x, y) * frontWeight(z / Math.max(0.01f, d));
    }

    private static float frontWeight(float zn) {
        if (zn <= 0f) {
            return 0f;
        }
        return (float) Math.pow(zn, 1.5);
    }

    // ------------------------------------------------------------------ build

    private void buildSkin(Builder b) {
        int around = ivie ? 30 : 22;
        int faceRings = ivie ? 24 : 18;
        int neckRings = ivie ? 5 : 7;
        float neckBottom = ivie ? -1.6f : -1.8f;
        float neckW = ivie ? 0.33f : 0.42f;
        int apex = b.vertex(0f, 1f, 0f, T_SKIN, 0f, 0f);
        int[] prev = null;
        int total = faceRings + neckRings;
        for (int r = 1; r <= total; r++) {
            float y;
            float w;
            float d;
            float cz = 0f;
            boolean face = r <= faceRings;
            if (face) {
                y = 1f - 2f * r / faceRings;
                w = width(y);
                d = depth(y);
            } else {
                float t = (r - faceRings) / (float) neckRings;
                y = -1f + (neckBottom + 1f) * t;
                float flare = (float) Math.pow(t, 3) * (ivie ? 0.10f : 0.20f);
                w = lerp(ivie ? 0.24f : 0.32f, neckW, Math.min(1f, t * 2f)) + flare;
                d = lerp(0.30f, 0.40f, Math.min(1f, t * 2f)) + flare * 0.3f;
                cz = lerp(-0.06f, -0.18f, Math.min(1f, t * 2f));
            }
            int[] ring = new int[around];
            float baseY = y;
            for (int j = 0; j < around; j++) {
                double th = (j / (double) around) * Math.PI * 2.0 - Math.PI;
                float sx = (float) Math.sin(th);
                float czz = (float) Math.cos(th);
                float x = w * sgnPow(sx, 2f / squareness);
                float zn = sgnPow(czz, 2f / squareness);
                float z = d * zn + cz;
                if (r < total) {
                    float jx = jitter(r * 131 + j * 17) * (w * 0.09f);
                    float jy = jitter(r * 71 + j * 29) * (face ? 0.025f : 0.04f);
                    x += jx * Math.abs(czz);
                    y += jy;
                }
                if (face) {
                    z += feature(x, y) * frontWeight(zn);
                }
                ring[j] = b.vertex(x, y, z, T_SKIN, 0f, 0f);
                y = baseY;
            }
            for (int j = 0; j < around; j++) {
                b.edge(ring[j], ring[(j + 1) % around], SKIN);
                if (prev == null) {
                    b.edge(apex, ring[j], SKIN);
                } else {
                    b.edge(prev[j], ring[j], SKIN);
                    if (((r + j) & 1) == 0) {
                        b.edge(prev[j], ring[(j + 1) % around], SKIN);
                    } else {
                        b.edge(prev[(j + 1) % around], ring[j], SKIN);
                    }
                }
            }
            prev = ring;
        }
    }

    private void buildEyes(Builder b) {
        int pts = 14;
        float rx = ivie ? 0.15f : 0.14f;
        float ry = ivie ? 0.065f : 0.052f;
        for (int side = -1; side <= 1; side += 2) {
            float ex = side * 0.30f;
            float ey = 0.02f;
            int[] last = null;
            float[] scales = {1.25f, 1.0f, 0.55f};
            for (int k = 0; k < scales.length; k++) {
                float s = scales[k];
                int[] loop = new int[pts];
                for (int i = 0; i < pts; i++) {
                    double ph = i / (double) pts * Math.PI * 2.0;
                    float c = (float) Math.cos(ph);
                    float sn = (float) Math.sin(ph);
                    float x = ex + rx * s * c;
                    float lift = side * c > 0.3f ? (ivie ? 0.012f : 0.0f) : 0f; // outer lift
                    float y = ey + ry * s * sn * (sn > 0 ? 1f : 0.7f) + lift;
                    float z = surfaceZ(x, y) + 0.012f;
                    int t = k == 2 ? T_IRIS : T_EYE;
                    loop[i] = b.vertex(x, y, z, t, sn > 0 ? 1f : 0f, ex);
                }
                for (int i = 0; i < pts; i++) {
                    b.edge(loop[i], loop[(i + 1) % pts], k == 0 ? SKIN : FEATURE);
                    if (last != null && (i & 1) == 0) {
                        b.edge(last[i], loop[i], SKIN);
                    }
                }
                last = loop;
            }
        }
    }

    private void buildBrows(Builder b) {
        int n = 7;
        for (int side = -1; side <= 1; side += 2) {
            int prev = -1;
            for (int i = 0; i < n; i++) {
                float t = i / (float) (n - 1);
                float x = side * lerp(0.12f, 0.50f, t);
                float arch = (float) Math.sin(t * Math.PI);
                float y = 0.15f + arch * (ivie ? 0.05f : 0.02f) + (ivie ? 0f : -t * 0.01f);
                float z = surfaceZ(x, y) + 0.015f;
                int v = b.vertex(x, y, z, T_BROW, side, 0f);
                if (prev >= 0) {
                    b.edge(prev, v, FEATURE);
                }
                prev = v;
            }
        }
    }

    private void buildLips(Builder b) {
        int n = 11;
        float half = ivie ? 0.20f : 0.21f;
        int[] up = new int[n];
        int[] lo = new int[n];
        int[] mid = new int[n];
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            float arch = (float) Math.sin(t * Math.PI);
            float x = lerp(-half, half, t);
            float cupid = ivie ? 0.012f * (float) Math.cos(t * Math.PI * 4) * arch : 0f;
            float yu = -0.60f + arch * (ivie ? 0.045f : 0.032f) + cupid;
            float ym = -0.625f;
            float yl = -0.65f - arch * (ivie ? 0.055f : 0.04f);
            up[i] = b.vertex(x, yu, surfaceZ(x, yu) + 0.012f, T_LIP_UP, arch, 0f);
            mid[i] = b.vertex(x, ym, surfaceZ(x, ym) + 0.006f, T_LIP_MID, arch, 0f);
            lo[i] = b.vertex(x, yl, surfaceZ(x, yl) + 0.012f, T_LIP_LO, arch, 0f);
        }
        b.edge(up[0], mid[0], FEATURE);
        b.edge(mid[0], lo[0], FEATURE);
        b.edge(up[n - 1], mid[n - 1], FEATURE);
        b.edge(mid[n - 1], lo[n - 1], FEATURE);
        for (int i = 0; i < n - 1; i++) {
            b.edge(up[i], up[i + 1], FEATURE);
            b.edge(mid[i], mid[i + 1], FEATURE);
            b.edge(lo[i], lo[i + 1], FEATURE);
        }
    }

    private void buildNose(Builder b) {
        float[][] lines = {
                {-0.07f, 0.0f, -0.05f, -0.2f, -0.06f, -0.36f, -0.12f, -0.43f, -0.08f, -0.47f, 0f, -0.45f,
                        0.08f, -0.47f, 0.12f, -0.43f, 0.06f, -0.36f, 0.05f, -0.2f, 0.07f, 0.0f},
                {0f, -0.30f, 0f, -0.40f},
                {-0.05f, -0.45f, -0.09f, -0.44f},
                {0.05f, -0.45f, 0.09f, -0.44f}
        };
        for (int l = 0; l < lines.length; l++) {
            float[] pts = lines[l];
            int prev = -1;
            for (int i = 0; i < pts.length; i += 2) {
                float x = pts[i] * (ivie ? 0.9f : 1.05f);
                float y = pts[i + 1];
                int v = b.vertex(x, y, surfaceZ(x, y) + 0.01f, T_SKIN, 0f, 0f);
                if (prev >= 0) {
                    b.edge(prev, v, FEATURE);
                }
                prev = v;
            }
        }
    }

    private void buildEars(Builder b) {
        int n = 10;
        for (int side = -1; side <= 1; side += 2) {
            float base = width(-0.1f);
            int[] outer = new int[n];
            int[] inner = new int[n];
            for (int i = 0; i < n; i++) {
                double ph = i / (double) n * Math.PI * 2.0;
                float c = (float) Math.cos(ph);
                float s = (float) Math.sin(ph);
                float y = -0.08f + s * 0.20f;
                float z = -0.05f + c * 0.10f;
                outer[i] = b.vertex(side * (base + 0.05f + 0.06f * (1f + c) * 0.5f), y, z, T_EAR, 0f, 0f);
                inner[i] = b.vertex(side * (base + 0.04f), -0.08f + s * 0.11f, -0.05f + c * 0.05f, T_EAR, 0f, 0f);
            }
            for (int i = 0; i < n; i++) {
                b.edge(outer[i], outer[(i + 1) % n], SKIN);
                b.edge(inner[i], inner[(i + 1) % n], SKIN);
                if ((i & 1) == 0) {
                    b.edge(outer[i], inner[i], SKIN);
                }
            }
        }
    }

    private void buildHair(Builder b) {
        int strands = 14;
        int pts = 18;
        for (int side = -1; side <= 1; side += 2) {
            int[] prev = null;
            for (int s = 0; s < strands; s++) {
                float a = (s + 0.5f) / strands;
                double ang = a * Math.PI * 0.5;
                float rootY = 0.58f + 0.47f * (float) Math.cos(ang);
                float rootX = side * width(rootY) * 0.96f * (float) Math.sin(ang) + side * 0.02f;
                float rootZ = Math.max(surfaceZ(rootX, rootY), 0f) * (1f - a * 0.6f) + 0.07f;
                float phase = s * 0.9f + (side > 0 ? 1.7f : 0f);
                float length = 2.75f + 0.35f * a + 0.15f * (float) Math.sin(s * 2.3f);
                int[] col = new int[pts];
                for (int k = 0; k < pts; k++) {
                    float t = k / (float) (pts - 1);
                    float y = rootY - t * length;
                    float out = t * (0.62f + 0.40f * a) + 0.16f * t * (float) Math.sin(t * Math.PI * 2.6f + phase);
                    float x = Math.abs(rootX) + out;
                    if (y < 0.95f && y > -1.05f) {
                        x = Math.max(x, width(y) + 0.07f + 0.10f * a);
                    }
                    float ease = t < 0.25f ? t / 0.25f : 1f;
                    float z = lerp(rootZ, 0.08f - 0.42f * a, ease * ease * (3f - 2f * ease));
                    col[k] = b.vertex(side * x, y, z, T_HAIR, t, phase);
                    if (k > 0) {
                        b.edge(col[k - 1], col[k], HAIR);
                    }
                    if (prev != null) {
                        b.edge(prev[k], col[k], HAIR);
                        if (k > 0) {
                            b.edge(((s + k) & 1) == 0 ? prev[k - 1] : prev[k], ((s + k) & 1) == 0 ? col[k] : col[k - 1], HAIR);
                        }
                    }
                }
                prev = col;
            }
        }
    }

    // ------------------------------------------------------------------ motion

    /** Expression input. All values come from {@link FaceRig}. */
    public static final class Pose {
        public float jaw;
        public float smile;
        public float browL;
        public float browR;
        public float blink;
        public float lookX;
        public float lookY;
        public float time;
        public float breathe;
    }

    /** Writes deformed positions into {@code out} (same layout as {@link #rest}). */
    public void deform(Pose p, float[] out) {
        float jaw = clamp(p.jaw, 0f, 1f);
        float smile = clamp(p.smile, 0f, 1.2f);
        float blink = clamp(p.blink, 0f, 1f);
        float breathe = 1f + p.breathe;
        int n = vertexCount();
        for (int i = 0; i < n; i++) {
            float x = rest[i * 3];
            float y = rest[i * 3 + 1];
            float z = rest[i * 3 + 2];
            switch (tag[i]) {
                case T_SKIN: {
                    if (y < -0.55f && y > -1.15f && z > 0f) {
                        float w = smooth((-0.55f - y) / 0.35f) * Math.min(1f, z / 0.5f);
                        y -= jaw * 0.13f * w;
                    }
                    if (y < -0.1f && y > -0.5f && z > 0.3f) {
                        y += smile * 0.012f * gauss(Math.abs(x) - 0.38f, 0.15f);
                    }
                    break;
                }
                case T_EYE:
                case T_IRIS: {
                    float ey = 0.02f;
                    if (pa[i] > 0.5f) {
                        y = ey + (y - ey) * (1f - 0.95f * blink) * (1f - smile * (ivie ? 0.12f : 0.06f));
                    } else {
                        y += smile * 0.008f;
                    }
                    if (tag[i] == T_IRIS) {
                        x += p.lookX * 0.045f;
                        y += p.lookY * 0.025f;
                    }
                    break;
                }
                case T_BROW: {
                    y += (pa[i] < 0f ? p.browL : p.browR) * 0.06f;
                    break;
                }
                case T_LIP_UP: {
                    float corner = 1f - pa[i];
                    y += smile * 0.045f * corner + jaw * 0.01f * pa[i];
                    x *= 1f + smile * 0.08f;
                    break;
                }
                case T_LIP_MID: {
                    float corner = 1f - pa[i];
                    y += smile * 0.04f * corner - jaw * 0.06f * pa[i];
                    x *= 1f + smile * 0.08f - jaw * 0.08f;
                    break;
                }
                case T_LIP_LO: {
                    float corner = 1f - pa[i];
                    y += smile * 0.04f * corner - jaw * 0.13f * (0.4f + 0.6f * pa[i]);
                    x *= 1f + smile * 0.08f - jaw * 0.05f;
                    break;
                }
                case T_HAIR: {
                    float t = pa[i];
                    float amp = t * (float) Math.sqrt(t);
                    float sign = x < 0f ? -1f : 1f;
                    x += sign * 0.055f * amp * (float) Math.sin(p.time * 1.05f + pb[i] + t * 3.1f);
                    y += 0.02f * amp * (float) Math.sin(p.time * 0.8f + pb[i] * 1.3f);
                    z += 0.04f * amp * (float) Math.cos(p.time * 0.9f + pb[i]);
                    break;
                }
                default:
                    break;
            }
            out[i * 3] = x * breathe;
            out[i * 3 + 1] = y * breathe;
            out[i * 3 + 2] = z * breathe;
        }
    }

    /**
     * Rotates and projects deformed positions. Writes screen x, screen y and
     * view-space z (larger is nearer) per vertex into {@code screen}.
     */
    public static void project(float[] pos, int count, float yawDeg, float pitchDeg, float rollDeg,
                               float cx, float cy, float unit, float[] screen) {
        float yaw = (float) Math.toRadians(yawDeg);
        float pitch = (float) Math.toRadians(pitchDeg);
        float roll = (float) Math.toRadians(rollDeg);
        float cr = (float) Math.cos(roll);
        float sr = (float) Math.sin(roll);
        float cp = (float) Math.cos(pitch);
        float sp = (float) Math.sin(pitch);
        float cyw = (float) Math.cos(yaw);
        float syw = (float) Math.sin(yaw);
        for (int i = 0; i < count; i++) {
            float x = pos[i * 3];
            float y = pos[i * 3 + 1] + 0.35f; // pivot near the jaw hinge
            float z = pos[i * 3 + 2];
            float x1 = x * cr - y * sr;
            float y1 = x * sr + y * cr;
            float y2 = y1 * cp - z * sp;
            float z2 = y1 * sp + z * cp;
            float x3 = x1 * cyw + z2 * syw;
            float z3 = -x1 * syw + z2 * cyw;
            float s = unit * (4.4f / (4.4f - z3));
            screen[i * 3] = cx + x3 * s;
            screen[i * 3 + 1] = cy - (y2 - 0.35f) * s;
            screen[i * 3 + 2] = z3;
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Deterministic -1..1 noise so every phone draws the same head. */
    static float jitter(int seed) {
        int h = seed * 0x27d4eb2d;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return ((h & 0xffff) / 32767.5f) - 1f;
    }

    static float gauss(float v, float sigma) {
        float q = v / sigma;
        return (float) Math.exp(-0.5f * q * q);
    }

    /** 1 between hi and lo (hi > lo), soft edges. */
    static float band(float y, float hi, float lo) {
        float a = smooth((hi - y) / 0.06f);
        float c = smooth((y - lo) / 0.06f);
        return a * c;
    }

    static float smooth(float t) {
        t = clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    static float sgnPow(float v, float e) {
        float a = (float) Math.pow(Math.abs(v), e);
        return v < 0f ? -a : a;
    }

    static float table(float y, float[] pairs) {
        if (y >= pairs[0]) {
            return pairs[1];
        }
        for (int i = 0; i + 3 < pairs.length; i += 2) {
            float y0 = pairs[i];
            float y1 = pairs[i + 2];
            if (y <= y0 && y >= y1) {
                float t = (y0 - y) / (y0 - y1);
                t = t * t * (3f - 2f * t);
                return lerp(pairs[i + 1], pairs[i + 3], t);
            }
        }
        return pairs[pairs.length - 1];
    }

    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static final class Builder {
        private final List<float[]> verts = new ArrayList<float[]>();
        private int[] edgeBuf = new int[4096];
        private int[] groupBuf = new int[2048];
        private int edgeCount;

        int vertex(float x, float y, float z, int tag, float a, float b) {
            verts.add(new float[]{x, y, z, tag, a, b});
            return verts.size() - 1;
        }

        void edge(int a, int b, int group) {
            if (edgeCount * 2 + 2 > edgeBuf.length) {
                int[] e = new int[edgeBuf.length * 2];
                System.arraycopy(edgeBuf, 0, e, 0, edgeBuf.length);
                edgeBuf = e;
                int[] g = new int[groupBuf.length * 2];
                System.arraycopy(groupBuf, 0, g, 0, groupBuf.length);
                groupBuf = g;
            }
            edgeBuf[edgeCount * 2] = a;
            edgeBuf[edgeCount * 2 + 1] = b;
            groupBuf[edgeCount] = group;
            edgeCount++;
        }

        float[] positions() {
            float[] out = new float[verts.size() * 3];
            for (int i = 0; i < verts.size(); i++) {
                float[] v = verts.get(i);
                out[i * 3] = v[0];
                out[i * 3 + 1] = v[1];
                out[i * 3 + 2] = v[2];
            }
            return out;
        }

        int[] tags() {
            int[] out = new int[verts.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = (int) verts.get(i)[3];
            }
            return out;
        }

        float[] paramA() {
            float[] out = new float[verts.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = verts.get(i)[4];
            }
            return out;
        }

        float[] paramB() {
            float[] out = new float[verts.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = verts.get(i)[5];
            }
            return out;
        }

        int[] edgeArray() {
            int[] out = new int[edgeCount * 2];
            System.arraycopy(edgeBuf, 0, out, 0, out.length);
            return out;
        }

        int[] groupArray() {
            int[] out = new int[edgeCount];
            System.arraycopy(groupBuf, 0, out, 0, out.length);
            return out;
        }
    }
}
