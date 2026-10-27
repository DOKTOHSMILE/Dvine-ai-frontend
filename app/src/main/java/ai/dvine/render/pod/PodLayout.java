package ai.dvine.render.pod;

/**
 * Where things sit on a screen of any size and either way up (the scene being the screen less the system bars).
 * Upright (phones, and tablets held upright): drawings centred at 50% across and 40% down, one stage unit 42% of
 * the height (at most 67% of the width), the pod's centre 76% down and 76% of the width across. On its side
 * (phones turned, tablets, foldables open): the drawing space is the screen's height, so a stage unit is 38% of it,
 * the drawing sits 38% down and the pod 80% down, no wider than half the screen, so the drawing never shrinks to a
 * strip. {@link #unit} is pixels per stage unit at the stage's depth. Pure Java.
 */
public final class PodLayout {
    /** How wide a stage unit may be, relative to its height, on narrow screens. */
    public static final float STAGE_ASPECT = 0.82f;
    /** The viewer's distance, in stage units (a gentle perspective). */
    public static final float EYE = 6f;

    public float width = 1f, height = 1f;
    /** Pixels per stage unit at the stage's depth. */
    public float unit = 1f;
    /** The stage's centre (where drawings float) in pixels. */
    public float cx, cy;
    /** The pod's centre height (stage units, below the centre: negative) and its diameter. */
    public float podY = -0.86f, podDiameter = 1.8f;
    /** Column-major projection for stage-unit positions (the viewer at z = EYE, looking down -z). */
    public final float[] proj = new float[16];

    /** Lays the scene out in a {@code w} × {@code h} view with {@code top} and {@code bottom} pixels kept clear. */
    public static PodLayout compute(float w, float h, float top, float bottom) {
        PodLayout l = new PodLayout();
        l.width = Math.max(1f, w);
        l.height = Math.max(1f, h);
        float usable = Math.max(1f, l.height - top - bottom);
        l.cx = l.width * 0.5f;
        float podPx;
        if (l.landscape()) {
            l.unit = Math.min(0.38f * usable, 0.32f * l.width);
            l.cy = top + 0.38f * usable;
            podPx = top + 0.80f * usable;
            l.podDiameter = Math.min(0.5f * l.width, 2.1f * l.unit) / l.unit;
        } else {
            l.unit = Math.min(0.42f * usable, 0.55f * l.width / STAGE_ASPECT);
            l.cy = top + 0.40f * usable;
            podPx = top + 0.76f * usable;
            l.podDiameter = Math.min(0.76f * l.width, 2.3f * l.unit) / l.unit;
        }
        l.podY = -(podPx - l.cy) / l.unit;
        l.projection();
        return l;
    }

    /** Whether the screen is on its side (clearly wider than tall). */
    public boolean landscape() {
        return width > height * 1.1f;
    }

    private void projection() {
        float d = EYE, n = d * 0.05f, f = d * 4f;
        float a = unit * d / (width * 0.5f), b = unit * d / (height * 0.5f);
        float ox = (cx - width * 0.5f) / (width * 0.5f), oy = (height * 0.5f - cy) / (height * 0.5f);
        float aa = (f + n) / (f - n), bb = 2f * f * n / (f - n);
        float[] p = {a, 0, -ox, ox * d,
                0, b, -oy, oy * d,
                0, 0, -aa, aa * d - bb,
                0, 0, -1, d};
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                proj[j * 4 + i] = p[i * 4 + j];
            }
        }
    }

    /** A stage-unit point to pixels (x, y) and its depth along the view. */
    public float[] project(float x, float y, float z) {
        float[] m = proj;
        float px = m[0] * x + m[4] * y + m[8] * z + m[12];
        float py = m[1] * x + m[5] * y + m[9] * z + m[13];
        float pw = m[3] * x + m[7] * y + m[11] * z + m[15];
        return new float[]{(px / pw + 1f) * 0.5f * width, (1f - py / pw) * 0.5f * height, pw};
    }
}
