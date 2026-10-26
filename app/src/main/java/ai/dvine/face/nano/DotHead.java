package ai.dvine.face.nano;

import ai.dvine.face.Look;
import ai.dvine.face.WireHead;

/**
 * The persona's head sculpted from smooth shapes and drawn as nano dots: no
 * mesh, so cheeks, lips, nose and skull are true curves. Eyes, brows and lips
 * are rows of brighter dots seated on the skin. Built once per look; each
 * frame only moves dots for speech, blinks, smiles and glances.
 *
 * Model space as before: x right, y up, z toward the viewer, crown y=+1, chin y=-1.
 */
public final class DotHead {
    static final int SKIN = 0;
    static final int EYEBALL = 1;
    static final int IRIS = 2;
    static final int LID_UP = 3;
    static final int LID_LO = 4;
    static final int BROW = 5;
    static final int LIP_UP = 6;
    static final int LIP_LO = 7;
    static final int MOUTH = 8;
    static final int HAIR = 9;
    static final int NOSTRIL = 10;

    static final float EYE_Y = 0.02f;
    static final float MOUTH_Y = -0.61f;

    public final boolean ivie;
    public final Look look;
    /** Rest positions, xyz per dot. */
    public final float[] rest;
    /** Rest brightness 0..1 per dot (shading already applied). */
    public final float[] lit;
    final int[] kind;
    /** Per-dot parameter: lid/brow side (-1 or +1), mouth row (0 upper, 1 lower), hair phase. */
    final float[] param;
    /** Hair only: how far out the dot sits on its strand beyond the head (it sways more). */
    final float[] reach;
    public final int size;
    private final float eyeX;
    private final float eyeRx;

    public DotHead(boolean ivie, Look look) {
        this.ivie = ivie;
        this.look = (look == null ? Look.defaults(ivie) : look).copy();
        Look l = this.look;
        eyeX = 0.31f;
        eyeRx = (ivie ? 0.15f : 0.14f) * l.eyes;
        Sculpt head = skull(ivie, l);
        Builder b = new Builder(ivie ? 9000 : 6800);

        // skin: even dots over the whole sculpted surface
        Sculpt.Cloud skin = head.sample(ivie ? 4000 : 5000, ivie ? 11 : 5, (x, y, z) -> y > neckCut(x, z, ivie));
        for (int i = 0; i < skin.size; i++) {
            float x = skin.xyz[i * 3], y = skin.xyz[i * 3 + 1], z = skin.xyz[i * 3 + 2];
            float shade = glow(skin.normal[i * 3], skin.normal[i * 3 + 1], skin.normal[i * 3 + 2]);
            float bend = Math.abs(head.curvature(x, y, z));
            shade = Math.min(1f, shade + 0.4f * clamp((bend - 4.5f) / 18f, 0f, 1f));
            if (y - neckCut(x, z, ivie) < 0.035f) {
                shade = 0.95f; // the cut edge glows
            }
            int k = SKIN;
            float p = 0f;
            if (z > 0.4f && insideEye(x, y)) {
                k = EYEBALL;
                shade = 0.22f;
            } else if (z > 0.6f && Math.abs(x) < 0.23f * l.lips && y < MOUTH_Y + 0.07f && y > MOUTH_Y - 0.11f) {
                k = y > MOUTH_Y ? LIP_UP : LIP_LO;
                shade = Math.min(1f, shade + 0.12f);
            }
            b.add(x, y, z, shade, k, p);
        }
        float front = 1.6f;
        // eyes: lids, iris ring and pupil
        for (int side = -1; side <= 1; side += 2) {
            float ex = side * eyeX;
            float ryU = (ivie ? 0.072f : 0.06f) * l.eyes;
            float ryL = (ivie ? 0.05f : 0.044f) * l.eyes;
            int steps = 34;
            for (int row = 0; row < 2; row++) {
                for (int i = 0; i <= steps; i++) {
                    double t = Math.PI * i / steps;
                    float c = (float) Math.cos(t);
                    float outer = side * c > 0f ? (ivie ? 0.012f : 0.004f) * side * c : 0f;
                    float x = ex + eyeRx * c;
                    float yu = EYE_Y + ryU * (float) Math.sin(t) + outer + row * 0.012f;
                    seat(b, head, x, yu, front, row == 0 ? 0.95f : 0.6f, LID_UP, side, 0.008f);
                    if (row == 0) {
                        float yl = EYE_Y - ryL * (float) Math.sin(t) + outer * 0.5f;
                        seat(b, head, x, yl, front, 0.62f, LID_LO, side, 0.006f);
                    }
                }
            }
            float ir = 0.056f * l.eyes;
            for (int i = 0; i < 30; i++) {
                double t = i * Math.PI * 2 / 30;
                seat(b, head, ex + ir * (float) Math.cos(t), EYE_Y + ir * (float) Math.sin(t), front, 0.55f, IRIS, side, 0.01f);
            }
            for (int i = 0; i < 14; i++) {
                double t = i * 2.399963;
                float r = 0.026f * l.eyes * (float) Math.sqrt((i + 0.5f) / 14f);
                seat(b, head, ex + r * (float) Math.cos(t), EYE_Y + r * (float) Math.sin(t), front, 0.7f, IRIS, side, 0.012f);
            }
            // brows: three rows, thicker toward the middle of the face
            for (int row = 0; row < 3; row++) {
                for (int i = 0; i <= 22; i++) {
                    float u = i / 22f;
                    float x = side * (0.13f + 0.33f * u);
                    float thick = (ivie ? 0.016f : 0.022f) * (1f - 0.55f * u);
                    float y = 0.165f + (ivie ? 0.05f : 0.035f) * (float) Math.sin(Math.PI * Math.min(1f, u * 1.15f))
                            + (row - 1) * thick;
                    seat(b, head, x, y, front, row == 1 ? 0.6f : 0.4f, BROW, side, 0.008f);
                }
            }
        }
        // nostrils
        float nw = (ivie ? 0.085f : 0.1f) * l.nose;
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 10; i++) {
                double t = Math.PI * (0.15 + 0.7 * i / 9.0);
                seat(b, head, side * (nw - 0.035f * (float) Math.cos(t)), -0.405f - 0.018f * (float) Math.sin(t), front,
                        0.8f, NOSTRIL, side, 0.004f);
            }
        }
        // lips: the parting line, the cupid's bow and the lower edge
        float mw = (ivie ? 0.2f : 0.21f) * l.lips;
        for (int i = 0; i <= 44; i++) {
            float u = -1f + 2f * i / 44f;
            float x = u * mw;
            float part = MOUTH_Y + 0.008f * (1f - u * u);
            seat(b, head, x, part, front, 1f, MOUTH, 0f, 0.004f);
            seat(b, head, x, part - 0.006f, front, 0.85f, MOUTH, 1f, 0.004f);
            float bow = 0.05f * l.lips * (1f - u * u) * (1f - 0.35f * (float) Math.exp(-u * u / 0.02f)) + 0.008f;
            seat(b, head, x * 0.96f, MOUTH_Y + bow, front, 0.8f, LIP_UP, 0f, 0.006f);
            float low = (ivie ? 0.075f : 0.062f) * l.lips * (float) Math.sqrt(Math.max(0f, 1f - u * u)) + 0.004f;
            seat(b, head, x * 0.9f, MOUTH_Y - low, front, 0.75f, LIP_LO, 0f, 0.006f);
        }
        if (l.hair > 0.05f) {
            hair(b, head, l);
        }
        rest = b.xyz();
        lit = b.lit();
        kind = b.kind();
        param = b.param();
        reach = java.util.Arrays.copyOf(b.reach, b.n);
        size = b.n;
        for (int i = 1; i < rest.length; i += 3) {
            rest[i] *= l.height;
        }
    }

    public static DotHead create(boolean ivie) {
        return new DotHead(ivie, Look.defaults(ivie));
    }

    /** The head as melted shapes. */
    static Sculpt skull(boolean ivie, Look l) {
        float w = l.width;
        float j = l.jaw;
        float n = l.nose;
        float e = l.eyes;
        float lip = l.lips;
        Sculpt s = new Sculpt();
        s.blend(0.3f).ellipsoid(0f, 0.17f, -0.06f, (ivie ? 0.74f : 0.79f) * w, 0.84f, ivie ? 0.9f : 0.93f);      // cranium
        if (!ivie) {
            s.blend(0.24f).box(0f, 0.4f, -0.06f, 0.62f * w, 0.56f, 0.8f, 0.3f);                             // squared crown
        }
        s.blend(0.3f).ellipsoid(0f, -0.36f, 0.16f, (ivie ? 0.6f : 0.66f) * w, 0.6f, 0.72f);                   // face
        if (ivie) {
            s.blend(0.26f).ellipsoid(0f, -0.62f, 0.04f, 0.52f * w * j, 0.34f, 0.7f)                           // jaw
                    .blend(0.16f).ellipsoid(0f, -0.87f, 0.44f, 0.16f * j, 0.12f, 0.2f);                        // chin
        } else {
            s.blend(0.2f).box(0f, -0.6f, 0.04f, 0.6f * w * j, 0.32f, 0.62f, 0.24f)                            // square jaw
                    .blend(0.12f).box(0f, -0.86f, 0.42f, 0.22f * j, 0.13f, 0.17f, 0.11f);                      // chin
        }
        s
                .ellipsoid(0.43f * w, -0.15f, 0.5f, 0.2f, 0.13f, 0.2f)                                           // cheekbones
                .ellipsoid(-0.43f * w, -0.15f, 0.5f, 0.2f, 0.13f, 0.2f)
                .blend(0.13f)
                .ellipsoid(0f, 0.2f, 0.66f, 0.55f * w, ivie ? 0.08f : 0.11f, ivie ? 0.13f : 0.17f)               // brow ridge
                .blend(0.09f)
                .carve(eyeXs(1), EYE_Y, 0.93f, 0.17f * e, 0.105f * e, 0.15f)                                       // sockets
                .carve(eyeXs(-1), EYE_Y, 0.93f, 0.17f * e, 0.105f * e, 0.15f)
                .blend(0.03f)
                .sphere(eyeXs(1), EYE_Y, 0.63f, 0.175f * e)                                                       // eyeballs
                .sphere(eyeXs(-1), EYE_Y, 0.63f, 0.175f * e)
                .blend(0.07f)
                .capsule(0f, 0.06f, 0.8f, 0.05f * n, 0f, -0.3f, 0.97f, 0.065f * n)                                // nose bridge
                .blend(0.06f)
                .ellipsoid(0f, -0.34f, 0.97f, 0.1f * n, 0.08f * n, 0.085f)                                        // nose tip
                .ellipsoid((ivie ? 0.085f : 0.1f) * n, -0.38f, 0.88f, 0.07f * n, 0.06f, 0.065f)                   // nostril wings
                .ellipsoid(-(ivie ? 0.085f : 0.1f) * n, -0.38f, 0.88f, 0.07f * n, 0.06f, 0.065f)
                .blend(0.045f)
                .ellipsoid(0f, -0.56f, 0.87f, 0.21f * lip, (ivie ? 0.06f : 0.05f) * lip, 0.09f)                   // upper lip
                .ellipsoid(0f, -0.675f, 0.85f, 0.19f * lip, (ivie ? 0.07f : 0.06f) * lip, 0.09f)                  // lower lip
                .blend(0.05f)
                .ellipsoid(0.79f * w, 0f, -0.08f, 0.08f, 0.2f, 0.14f)                                             // ears
                .ellipsoid(-0.79f * w, 0f, -0.08f, 0.08f, 0.2f, 0.14f)
                .blend(0.03f)
                .carve(0.86f * w, 0f, -0.06f, 0.04f, 0.13f, 0.08f)
                .carve(-0.86f * w, 0f, -0.06f, 0.04f, 0.13f, 0.08f)
                .blend(0.22f)
                .capsule(0f, -0.55f, -0.12f, ivie ? 0.31f : 0.39f, 0f, -2f, -0.08f, ivie ? 0.36f : 0.45f);         // neck
        return s;
    }

    /** The neck ends in a sculpted V: long at the front, short at the back. */
    static float neckCut(float x, float z, boolean ivie) {
        float v = Math.max(0f, 1f - Math.abs(x) / (ivie ? 0.34f : 0.42f)) * smooth((z + 0.05f) / 0.4f);
        return (ivie ? -1.2f : -1.28f) - (ivie ? 0.5f : 0.62f) * v;
    }

    private static float eyeXs(int side) {
        return side * 0.31f;
    }

    private boolean insideEye(float x, float y) {
        float ex = x > 0 ? eyeX : -eyeX;
        float u = (x - ex) / eyeRx;
        float ry = y > EYE_Y ? (ivie ? 0.072f : 0.06f) * look.eyes : (ivie ? 0.05f : 0.044f) * look.eyes;
        float v = (y - EYE_Y) / ry;
        return u * u + v * v < 0.92f;
    }

    /** Puts a feature dot on the skin at (x, y), lifted a little toward the viewer. */
    private static void seat(Builder b, Sculpt head, float x, float y, float from, float lit, int kind, float param,
                             float lift) {
        float z = head.frontZ(x, y, from);
        if (!Float.isNaN(z)) {
            b.add(x, y, z + lift, lit, kind, param);
        }
    }

    /**
     * Hair: a faint cap over the skull and long strands that sweep back from
     * the hairline over the head and stream out behind it, moving in a breeze.
     */
    private void hair(Builder b, Sculpt head, Look l) {
        float grow = 0.035f + 0.03f * l.hair;
        float rx = (ivie ? 0.74f : 0.79f) * l.width + grow, ry = 0.84f + grow, rz = (ivie ? 0.9f : 0.93f) + grow;
        Sculpt cap = new Sculpt();
        cap.ellipsoid(0f, 0.2f, -0.08f, rx, ry, rz);
        Sculpt.Cloud c = cap.sample(ivie ? 700 : 900, 23, (x, y, z) ->
                y - 0.35f + (z - 0.5f) * 0.55f > 0f && y > -0.25f + Math.max(0f, z) * 0.4f && head.d(x, y, z) > 0.012f);
        for (int i = 0; i < c.size; i++) {
            float shade = glow(c.normal[i * 3], c.normal[i * 3 + 1], c.normal[i * 3 + 2]);
            b.add(c.xyz[i * 3], c.xyz[i * 3 + 1], c.xyz[i * 3 + 2], 0.15f + 0.4f * shade, HAIR, i * 0.37f, 0f);
        }
        int strands = ivie ? 40 : 18;
        float flow = ivie ? 0.6f + 0.45f * l.hair : 0.08f * l.hair;
        for (int k = 0; k < strands; k++) {
            float u = -0.86f + 1.72f * (k + 0.5f) / strands + 0.02f * Swarm.noise(k * 5);
            float side = (float) Math.sqrt(1f - u * u);
            float start = (float) Math.toRadians(52f + 14f * Math.abs(u) + 6f * Swarm.noise(k * 5 + 1));
            float end = (float) Math.toRadians(168f + 10f * Swarm.noise(k * 5 + 2));
            float phase = k * 1.7f;
            float lift = 1.03f + 0.02f * Swarm.noise(k * 5 + 3);
            // over the skull
            int over = 46;
            float lx = 0f, ly = 0f, lz = 0f;
            for (int i = 0; i <= over; i++) {
                float a = start + (end - start) * i / over;
                float x = u * rx * lift, y = 0.2f + side * (float) Math.sin(a) * ry * lift, z = -0.08f + side * (float) Math.cos(a) * rz * lift;
                x *= 1f - 0.12f * i / over; // strands gather toward the back
                b.add(x, y, z, 0.32f + 0.3f * (i / (float) over), HAIR, phase, 0f);
                lx = x;
                ly = y;
                lz = z;
            }
            // streaming out behind the head
            float len = flow * (0.75f + 0.45f * (Swarm.noise(k * 5 + 4) + 1f) * 0.5f);
            int out = (int) (len / 0.03f);
            for (int i = 1; i <= out; i++) {
                float t = i / (float) out;
                float x = lx * (1f + 0.55f * t) + 0.05f * t * Swarm.noise(k * 7);
                float y = ly - 0.55f * len * t + 0.06f * t * Swarm.noise(k * 7 + 1);
                float z = lz - len * t * (1f - 0.25f * t);
                b.add(x, y, z, 0.7f * (1f - 0.7f * t), HAIR, phase, t * len);
            }
        }
    }

    /** Glowing-outline shading: soft key light and a bright silhouette, like the hologram heads. */
    static float glow(float nx, float ny, float nz) {
        float lambert = Math.max(0f, nx * -0.5f + ny * 0.45f + nz * 0.74f);
        float rim = 1f - Math.abs(nz);
        return Math.min(0.95f, 0.05f + 0.42f * lambert * lambert + 0.62f * rim * rim * rim);
    }

    // ------------------------------------------------------------ motion

    /** Writes this frame's dot positions and brightness. Returns the number of dots. */
    public int pose(WireHead.Pose p, float[] xyz, float[] out, int max, int stride, float cut) {
        float jaw = clamp(p.jaw, 0f, 1f);
        float smile = clamp(p.smile, 0f, 1.2f);
        float blink = clamp(p.blink, 0f, 1f);
        float breathe = 1f + p.breathe;
        int n = 0;
        for (int i = 0; i < size && n < max; i++) {
            int k = kind[i];
            // in the full body every stride-th skin dot is enough; features always stay
            if (stride > 1 && (k == SKIN || k == HAIR || k == EYEBALL ? i % stride != 0 : (i & 1) != 0)) {
                continue;
            }
            float x = rest[i * 3], y = rest[i * 3 + 1], z = rest[i * 3 + 2];
            if (y < cut) {
                continue;
            }
            float light = stride > 1 && k != SKIN && k != HAIR ? lit[i] * 0.45f : lit[i];
            float side = param[i];
            switch (k) {
                case SKIN: {
                    if (y < MOUTH_Y && z > -0.1f) {
                        float w = smooth((MOUTH_Y - y) / 0.1f) * smooth((z + 0.1f) / 0.5f) * smooth((y + 1.4f) / 0.4f);
                        y -= jaw * 0.12f * w;
                        z -= jaw * 0.03f * w;
                    }
                    if (z > 0.3f) {
                        y += smile * 0.03f * gauss(Math.abs(x) - 0.22f, 0.08f) * gauss(y - MOUTH_Y, 0.1f);
                        y += smile * 0.015f * gauss(Math.abs(x) - 0.4f, 0.12f) * gauss(y + 0.25f, 0.12f);
                    }
                    break;
                }
                case LIP_UP:
                    y += jaw * 0.012f + smile * 0.035f * Math.abs(x) / 0.2f;
                    x *= 1f + smile * 0.07f;
                    break;
                case MOUTH:
                    if (side > 0.5f) {
                        y -= jaw * 0.1f * (1f - 0.7f * x * x / 0.045f);
                    }
                    y += smile * 0.035f * Math.min(1f, x * x / 0.03f);
                    x *= 1f + smile * 0.07f - jaw * 0.06f;
                    break;
                case LIP_LO:
                    y -= jaw * 0.12f;
                    y += smile * 0.03f * Math.abs(x) / 0.2f;
                    x *= 1f + smile * 0.07f - jaw * 0.04f;
                    break;
                case LID_UP: {
                    float closed = EYE_Y - 0.012f;
                    y = y + (closed - y) * blink * 0.96f;
                    y -= smile * 0.006f;
                    break;
                }
                case LID_LO:
                    y += smile * 0.012f;
                    break;
                case IRIS:
                    x += p.lookX * 0.04f;
                    y += p.lookY * 0.022f;
                    light *= 1f - blink;
                    y = EYE_Y + (y - EYE_Y) * (1f - 0.8f * blink);
                    break;
                case EYEBALL:
                    light *= 1f - 0.9f * blink;
                    break;
                case BROW:
                    y += (side < 0f ? p.browL : p.browR) * 0.05f;
                    break;
                case HAIR: {
                    // strands ripple in a breeze, more toward their tips
                    float r = reach[i];
                    float w = r * r * 1.4f + 0.004f;
                    y += w * 0.1f * (float) Math.sin(p.time * 1.5f - r * 5f + side);
                    x += w * 0.06f * (float) Math.sin(p.time * 1.1f - r * 4f + side * 0.7f);
                    break;
                }
                default:
                    break;
            }
            xyz[n * 3] = x * breathe;
            xyz[n * 3 + 1] = y * breathe;
            xyz[n * 3 + 2] = z * breathe;
            out[n] = light;
            n++;
        }
        return n;
    }

    private static float smooth(float t) {
        t = clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float gauss(float v, float s) {
        return (float) Math.exp(-(v * v) / (2f * s * s));
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Growable dot list. */
    static final class Builder {
        float[] xyz;
        float[] lit;
        int[] kind;
        float[] param;
        float[] reach;
        int n;

        Builder(int cap) {
            xyz = new float[cap * 3];
            lit = new float[cap];
            kind = new int[cap];
            param = new float[cap];
            reach = new float[cap];
        }

        void add(float x, float y, float z, float l, int k, float p) {
            add(x, y, z, l, k, p, 0f);
        }

        void add(float x, float y, float z, float l, int k, float p, float r) {
            if (n == lit.length) {
                int c = n * 2;
                xyz = java.util.Arrays.copyOf(xyz, c * 3);
                lit = java.util.Arrays.copyOf(lit, c);
                kind = java.util.Arrays.copyOf(kind, c);
                param = java.util.Arrays.copyOf(param, c);
                reach = java.util.Arrays.copyOf(reach, c);
            }
            reach[n] = r;
            xyz[n * 3] = x;
            xyz[n * 3 + 1] = y;
            xyz[n * 3 + 2] = z;
            lit[n] = l;
            kind[n] = k;
            param[n] = p;
            n++;
        }

        float[] xyz() {
            return java.util.Arrays.copyOf(xyz, n * 3);
        }

        float[] lit() {
            return java.util.Arrays.copyOf(lit, n);
        }

        int[] kind() {
            return java.util.Arrays.copyOf(kind, n);
        }

        float[] param() {
            return java.util.Arrays.copyOf(param, n);
        }
    }
}
