package ai.dvine.render.field;

/**
 * A picture as points for the particles: positions (x right, y up, z toward the viewer; about -1..1 across) and a
 * linear-ish RGB colour per point. Made from pixels by {@link #fromPixels}, whatever drew them: text, a progress
 * bar, a drawing the language model described, any image.
 */
public final class Shape {
    public final float[] xyz, rgb;
    public final int count;

    public Shape(float[] xyz, float[] rgb, int count) {
        this.xyz = xyz;
        this.rgb = rgb;
        this.count = count;
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
        int stride = Math.max(1, (int) Math.ceil(Math.sqrt(lit / (double) maxPoints)));
        float scale = width / w;
        float[] xyz = new float[Math.min(lit, maxPoints * 2) * 3 + 3], rgb = new float[xyz.length];
        int n = 0;
        for (int y = 0; y < h; y += stride) {
            // stagger alternate rows so the points don't read as a grid
            for (int x = (y / stride) % 2 == 0 ? 0 : stride / 2; x < w; x += stride) {
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
        return new Shape(p, rgb, count);
    }
}
