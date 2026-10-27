package ai.dvine.face;

import java.util.ArrayList;
import java.util.List;

/**
 * The personas' line-art faces (the DVINE screen design): Obi an angular,
 * faceted mask, Ivie a soft drawing with long side-swept hair. Each face is a
 * set of polylines in face units (x right, y up, about -1.1 .. 1.2), rebuilt
 * every frame from the rig so it blinks, speaks, smiles, raises its brows,
 * looks about and turns its head a little. Pure Java (the stage draws the
 * strokes with a glow).
 */
public final class LineFace {
    private LineFace() {
    }

    /** One frame's face pose (all from {@link FaceRig}). */
    public static final class Pose {
        /** 0 open .. 1 closed. */
        public float blink;
        /** 0 closed .. about 1 wide open (speech). */
        public float jaw;
        /** -1 frown .. 1 smile. */
        public float smile;
        /** -1 lowered .. 1 raised, per side. */
        public float browL, browR;
        /** Gaze, -1 .. 1. */
        public float lookX, lookY;
        /** Head turn in degrees (yaw left/right, pitch up/down, roll tilt). */
        public float yaw, pitch, roll;
    }

    /** The strokes of one face; closed strokes repeat their first point at the end. */
    public static List<float[]> strokes(boolean ivie, Pose p) {
        Strokes out = new Strokes();
        if (ivie) {
            ivie(out, p);
        } else {
            obi(out, p);
        }
        turn(out, p);
        return out.lines;
    }

    // ------------------------------------------------------------ Obi

    private static void obi(Strokes out, Pose p) {
        // the mask: a flat brow line, straight sides, a strong angular jaw and a narrow chin
        float[] right = {0.2f, 1.08f, 0.55f, 1.0f, 0.68f, 0.72f, 0.7f, 0.35f, 0.66f, 0f, 0.6f, -0.32f, 0.5f, -0.6f,
                0.32f, -0.85f, 0.12f, -0.98f, 0f, -1.0f};
        out.add(closedMirror(right), 0f);
        // facets: down from the temple to the cheekbone, under the cheekbone, along the jaw, the chin plate
        out.pair(new float[]{0.55f, 1.0f, 0.6f, 0.55f, 0.5f, 0.1f, 0.56f, -0.24f}, 0f);
        out.pair(new float[]{0.5f, 0.1f, 0.3f, -0.08f}, 0.05f);
        out.pair(new float[]{0.6f, -0.32f, 0.36f, -0.56f, 0.22f, -0.82f}, 0f);
        out.add(new float[]{-0.22f, -0.82f, 0f, -0.86f, 0.22f, -0.82f}, 0.04f);
        // ears and neck
        out.pair(new float[]{0.7f, 0.32f, 0.8f, 0.3f, 0.83f, 0.1f, 0.78f, -0.12f, 0.66f, -0.18f}, -0.05f);
        out.pair(new float[]{0.32f, -0.86f, 0.34f, -1.25f}, -0.05f);
        // brows: straight and angled, raised by the rig
        for (int s = -1; s <= 1; s += 2) {
            float b = (s < 0 ? p.browL : p.browR) * 0.05f;
            out.add(side(s, new float[]{0.1f, 0.36f + b, 0.28f, 0.44f + b * 1.3f, 0.48f, 0.4f + b}), 0.1f);
        }
        eyes(out, p, 0.12f, 0.47f, 0.21f, 0.07f, 0.06f, false);
        // the nose: one bridge line and its base
        out.add(new float[]{0.06f, 0.18f, 0.08f, -0.1f, 0.15f, -0.24f, 0.06f, -0.29f, -0.06f, -0.29f, -0.15f, -0.24f}, 0.2f);
        mouth(out, p, 0.24f, -0.5f, 0.05f, 0.08f);
    }

    // ------------------------------------------------------------ Ivie

    private static void ivie(Strokes out, Pose p) {
        // a soft oval face narrowing to a gentle chin, from temple to temple under the hair
        int n = 30;
        float[] face = new float[(n + 1) * 2];
        for (int i = 0; i <= n; i++) {
            double a = Math.toRadians(-118 + 236.0 * i / n) + Math.PI;
            float x = (float) (0.6 * Math.sin(a)), y = (float) (0.95 * Math.cos(a));
            if (y < 0f) {
                x *= 1f - 0.3f * (float) Math.pow(-Math.cos(a), 1.6);
                y *= 1.03f;
            }
            face[i * 2] = x;
            face[i * 2 + 1] = y;
        }
        out.add(face, 0f);
        // long hair: over the crown, down past the shoulders, a fringe swept across the forehead
        out.add(curve(-0.62f, 0.55f, -0.7f, 1.2f, 0.25f, 1.28f, 0.78f, 0.75f), -0.04f);
        out.add(curve(0.78f, 0.75f, 0.92f, 0.1f, 0.78f, -0.6f, 0.86f, -1.25f), -0.04f);
        out.add(curve(-0.62f, 0.55f, -0.8f, -0.1f, -0.7f, -0.6f, -0.82f, -1.2f), -0.04f);
        out.add(curve(0.42f, 1.0f, 0.1f, 0.88f, -0.32f, 0.82f, -0.58f, 0.35f), 0.02f);
        out.add(curve(0.5f, 0.95f, 0.66f, 0.5f, 0.64f, -0.1f, 0.7f, -0.75f), -0.02f);
        out.add(curve(0.62f, 0.9f, 0.8f, 0.3f, 0.7f, -0.4f, 0.78f, -1.0f), -0.03f);
        // soft arched brows
        for (int s = -1; s <= 1; s += 2) {
            float b = (s < 0 ? p.browL : p.browR) * 0.05f;
            out.add(side(s, new float[]{0.1f, 0.33f + b, 0.22f, 0.4f + b * 1.3f, 0.36f, 0.41f + b * 1.2f, 0.45f, 0.36f + b}), 0.1f);
        }
        eyes(out, p, 0.12f, 0.42f, 0.2f, 0.06f, 0.035f, true);
        // a small soft nose
        out.add(new float[]{0.03f, 0.08f, 0.06f, -0.16f, 0.1f, -0.22f, 0.02f, -0.26f, -0.06f, -0.24f}, 0.18f);
        mouth(out, p, 0.2f, -0.48f, 0.07f, 0.07f);
    }

    // ------------------------------------------------------------ shared features

    /** Both eyes: almond lids that close with the blink, an iris that follows the gaze; lashes for Ivie. */
    private static void eyes(Strokes out, Pose p, float inner, float outer, float y, float up, float down, boolean lashes) {
        float open = 1f - clamp(p.blink, 0f, 1f);
        float happy = Math.max(0f, p.smile) * 0.35f; // a smile narrows the eyes from below
        for (int s = -1; s <= 1; s += 2) {
            int k = 8;
            float[] upper = new float[(k + 1) * 2], lower = new float[(k + 1) * 2];
            for (int i = 0; i <= k; i++) {
                float t = (float) i / k;
                float x = inner + (outer - inner) * t;
                float bump = (float) Math.sin(Math.PI * Math.pow(t, 0.85));
                upper[i * 2] = x;
                upper[i * 2 + 1] = y + up * bump * open;
                lower[i * 2] = x;
                lower[i * 2 + 1] = y - down * bump * open * (1f - happy);
            }
            out.add(side(s, upper), 0.12f);
            out.add(side(s, lower), 0.12f);
            if (lashes) {
                out.add(side(s, new float[]{outer, y, outer + 0.06f, y + 0.04f * open + 0.01f}), 0.12f);
            }
            if (open > 0.35f) {
                float cx = (inner + outer) * 0.5f + p.lookX * 0.05f, cy = y + p.lookY * 0.02f;
                float r = Math.min(up, down + 0.01f) * 0.75f * open;
                out.add(side(s, circle(cx, cy, r, 12)), 0.13f);
            }
        }
    }

    /** The lips: an upper lip with a cupid's bow, the parting that opens with the jaw, a lower lip; corners follow the smile. */
    private static void mouth(Strokes out, Pose p, float halfW, float y, float upper, float lower) {
        float open = clamp(p.jaw, 0f, 1.2f);
        float corner = p.smile * 0.06f;
        float cy = y + corner;
        out.add(new float[]{-halfW, cy, -halfW * 0.45f, y + upper * 0.8f, 0f, y + upper * 0.55f, halfW * 0.45f, y + upper * 0.8f, halfW, cy}, 0.15f);
        float part = y - 0.02f - open * 0.09f;
        out.add(new float[]{-halfW, cy, -halfW * 0.5f, part + corner * 0.4f, 0f, part, halfW * 0.5f, part + corner * 0.4f, halfW, cy}, 0.15f);
        if (open > 0.15f) {
            float top = y - 0.01f;
            out.add(new float[]{-halfW * 0.8f, cy - 0.005f, 0f, top, halfW * 0.8f, cy - 0.005f}, 0.15f);
        }
        float low = part - lower;
        out.add(new float[]{-halfW * 0.85f, cy - 0.02f, -halfW * 0.4f, low + 0.01f, 0f, low, halfW * 0.4f, low + 0.01f, halfW * 0.85f, cy - 0.02f}, 0.15f);
    }

    // ------------------------------------------------------------ helpers

    /**
     * The head turns a little: each stroke carries a depth,
     * so near features (nose, lips) shift more than the outline. Then the tilt (roll) about the centre.
     */
    private static void turn(Strokes out, Pose p) {
        float sx = (float) Math.sin(Math.toRadians(clamp(p.yaw, -35f, 35f)));
        float sy = (float) Math.sin(Math.toRadians(clamp(p.pitch, -25f, 25f)));
        float cr = (float) Math.cos(Math.toRadians(p.roll)), sr = (float) Math.sin(Math.toRadians(p.roll));
        for (int i = 0; i < out.lines.size(); i++) {
            float[] s = out.lines.get(i);
            float depth = out.depth.get(i);
            float shift = 0.25f + depth * 2.2f;
            for (int j = 0; j + 1 < s.length; j += 2) {
                float x = s[j] * (1f - 0.12f * Math.abs(sx)) + sx * shift * 0.35f;
                float y = s[j + 1] - sy * shift * 0.25f;
                s[j] = x * cr - y * sr;
                s[j + 1] = x * sr + y * cr;
            }
        }
    }

    /** Strokes and how near each is (0 the outline .. 0.2 the nose), for the head turn. */
    private static final class Strokes {
        final List<float[]> lines = new ArrayList<float[]>();
        final List<Float> depth = new ArrayList<Float>();

        void add(float[] stroke, float d) {
            lines.add(stroke);
            depth.add(d);
        }

        /** A right-side stroke and its mirror on the left. */
        void pair(float[] right, float d) {
            add(right, d);
            add(mirror(right), d);
        }
    }

    private static float[] side(int s, float[] right) {
        return s > 0 ? right : mirror(right);
    }

    private static float[] mirror(float[] a) {
        float[] m = a.clone();
        for (int i = 0; i < m.length; i += 2) {
            m[i] = -m[i];
        }
        return m;
    }

    /** A closed outline from its right half (top centre to bottom centre), mirrored for the left. */
    private static float[] closedMirror(float[] right) {
        int n = right.length / 2;
        float[] out = new float[(n * 2 + 1) * 2];
        int k = 0;
        out[k++] = -right[0];
        out[k++] = right[1];
        for (int i = 0; i < n; i++) {
            out[k++] = right[i * 2];
            out[k++] = right[i * 2 + 1];
        }
        for (int i = n - 2; i >= 0; i--) {
            out[k++] = -right[i * 2];
            out[k++] = right[i * 2 + 1];
        }
        out[k++] = -right[0];
        out[k++] = right[1];
        return java.util.Arrays.copyOf(out, k);
    }

    /** A cubic Bézier through four control points, as a polyline. */
    static float[] curve(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3) {
        int n = 16;
        float[] out = new float[(n + 1) * 2];
        for (int i = 0; i <= n; i++) {
            float t = (float) i / n, u = 1f - t;
            out[i * 2] = u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3;
            out[i * 2 + 1] = u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3;
        }
        return out;
    }

    private static float[] circle(float cx, float cy, float r, int n) {
        float[] out = new float[(n + 1) * 2];
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * 2 * i / n;
            out[i * 2] = cx + r * (float) Math.cos(a);
            out[i * 2 + 1] = cy + r * (float) Math.sin(a);
        }
        return out;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
