package ai.dvine.render.field;

/**
 * A picture as points for the particles: positions (x right, y up, z toward the viewer; about -1..1 across) and a
 * linear-ish RGB colour per point. Made from pixels by {@link #fromPixels}, whatever drew them: text, a progress
 * bar, a drawing the language model described, any image.
 */
public final class Shape {
    public final float[] xyz, rgb;
    public final int count;
    /** Distance between neighbouring samples, in the same units as {@code xyz}. Zero when the shape is not a skin. */
    public final float grid;

    public Shape(float[] xyz, float[] rgb, int count) {
        this(xyz, rgb, count, 0f);
    }

    public Shape(float[] xyz, float[] rgb, int count, float grid) {
        this.xyz = xyz;
        this.rgb = rgb;
        this.count = count;
        this.grid = grid;
    }

    public static final Shape EMPTY = new Shape(new float[0], new float[0], 0);

    /**
     * The picture's lit pixels as points: those with enough alpha and brightness, sampled on a grid fine enough for
     * {@code maxPoints}; the picture spans {@code width} units across, centred, y up; {@code depth} gives it a thin
     * 3D thickness (each point a little in front or behind, by its brightness).
     */
    public static Shape fromPixels(int[] argb, int w, int h, int maxPoints, float width, float depth) {
        int lit = 0;
        for (int p : argb) {
            if (litPixel(p)) {
                lit++;
            }
        }
        if (lit == 0 || maxPoints <= 0) {
            return EMPTY;
        }
        // as fine a grid as the budget allows (a fractional step, so the points sit as close as they can)
        float step = (float) Math.max(1.0, Math.sqrt(lit / (double) maxPoints) * 1.02);
        float scale = width / w;
        float[] xyz = new float[Math.min(lit, maxPoints * 2) * 3 + 3], rgb = new float[xyz.length];
        int n = 0, row = 0;
        for (float fy = 0f; fy < h; fy += step, row++) {
            int y = (int) fy;
            // stagger alternate rows so the points don't read as a grid
            for (float fx = row % 2 == 0 ? 0f : step / 2f; fx < w; fx += step) {
                int x = (int) fx;
                int p = argb[y * w + x];
                if (!litPixel(p)) {
                    continue;
                }
                if (n * 3 + 3 > xyz.length) {
                    xyz = java.util.Arrays.copyOf(xyz, xyz.length * 2);
                    rgb = java.util.Arrays.copyOf(rgb, rgb.length * 2);
                }
                float r = ((p >> 16) & 255) / 255f, g = ((p >> 8) & 255) / 255f, b = (p & 255) / 255f;
                float lum = 0.3f * r + 0.59f * g + 0.11f * b;
                xyz[n * 3] = (x - w / 2f) * scale;
                xyz[n * 3 + 1] = (h / 2f - y) * scale;
                xyz[n * 3 + 2] = (lum - 0.5f) * depth + (float) Math.sin(x * 12.9898 + y * 78.233) * depth * 0.25f;
                rgb[n * 3] = r;
                rgb[n * 3 + 1] = g;
                rgb[n * 3 + 2] = b;
                n++;
            }
        }
        return new Shape(xyz, rgb, n);
    }

    /**
     * A photograph as a continuous skin: one point per source pixel (thinned only when the budget requires it),
     * including dark hair and cloth, dropping only a near-black backdrop. No sparkle offset, so the points sit
     * on one surface.
     */
    public static Shape skin(int[] argb, int w, int h, int maxPoints, float width) {
        int lit = 0;
        for (int p : argb) {
            if (subjectPixel(p)) {
                lit++;
            }
        }
        if (lit == 0 || maxPoints <= 0 || w <= 0 || h <= 0) {
            return EMPTY;
        }
        float step = lit <= maxPoints ? 1f : (float) Math.sqrt(lit / (double) maxPoints);
        float scale = width / w;
        int cap = Math.min(lit, maxPoints + 8);
        float[] xyz = new float[cap * 3], rgb = new float[cap * 3];
        int n = 0;
        for (float fy = 0f; fy < h && n < cap; fy += step) {
            int y = Math.min(h - 1, (int) fy);
            for (float fx = 0f; fx < w && n < cap; fx += step) {
                int x = Math.min(w - 1, (int) fx);
                int p = argb[y * w + x];
                if (!subjectPixel(p)) {
                    continue;
                }
                float r = ((p >> 16) & 255) / 255f, g = ((p >> 8) & 255) / 255f, b = (p & 255) / 255f;
                float lum = 0.3f * r + 0.59f * g + 0.11f * b;
                xyz[n * 3] = (x - w / 2f) * scale;
                xyz[n * 3 + 1] = (h / 2f - y) * scale;
                xyz[n * 3 + 2] = (lum - 0.42f) * 0.045f;
                rgb[n * 3] = r;
                rgb[n * 3 + 1] = g;
                rgb[n * 3 + 2] = b;
                n++;
            }
        }
        return new Shape(xyz, rgb, n, step * scale);
    }

    /** Keeps hair, cloth and shadow. Drops a transparent or near-black backdrop. */
    static boolean subjectPixel(int p) {
        int a = (p >>> 24) & 255, r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
        return a > 16 && (r + g + b) > 18;
    }

    /** A drawing's ink: bright enough to read. Photographs use {@link #subjectPixel} instead. */
    static boolean litPixel(int p) {
        int a = (p >>> 24) & 255, r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
        return a > 60 && (r + g + b) > 90;
    }

    /** The shape moved by (dx, dy, dz). */
    public Shape offset(float dx, float dy, float dz) {
        float[] p = new float[count * 3];
        for (int i = 0; i < count; i++) {
            p[i * 3] = xyz[i * 3] + dx;
            p[i * 3 + 1] = xyz[i * 3 + 1] + dy;
            p[i * 3 + 2] = xyz[i * 3 + 2] + dz;
        }
        return new Shape(p, rgb, count, grid);
    }
}
