package ai.dvine.ops.recon;

/** One photograph as pixels. Width times height ARGB ints, row 0 at the top. */
public final class RasterImage {
    public final int width;
    public final int height;
    public final int[] argb;

    public RasterImage(int width, int height, int[] argb) {
        if (width < 1 || height < 1 || argb == null || argb.length != width * height) {
            throw new IllegalArgumentException("image size");
        }
        this.width = width;
        this.height = height;
        this.argb = argb;
    }

    public static RasterImage fill(int width, int height, int color) {
        int[] px = new int[width * height];
        for (int i = 0; i < px.length; i++) {
            px[i] = color;
        }
        return new RasterImage(width, height, px);
    }

    public void dot(int cx, int cy, int radius, int color) {
        for (int y = cy - radius; y <= cy + radius; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                if (x < 0 || y < 0 || x >= width || y >= height) {
                    continue;
                }
                int dx = x - cx, dy = y - cy;
                if (dx * dx + dy * dy <= radius * radius) {
                    argb[y * width + x] = color;
                }
            }
        }
    }

    /** Longest side at most {@code maxEdge}. Same object when it already fits. */
    public RasterImage limit(int maxEdge) {
        int edge = Math.max(width, height);
        if (edge <= maxEdge) {
            return this;
        }
        double s = maxEdge / (double) edge;
        return scaled(Math.max(1, (int) Math.round(width * s)), Math.max(1, (int) Math.round(height * s)));
    }

    public RasterImage scaled(int w, int h) {
        if (w == width && h == height) {
            return this;
        }
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            int sy = Math.min(height - 1, y * height / h);
            for (int x = 0; x < w; x++) {
                int sx = Math.min(width - 1, x * width / w);
                px[y * w + x] = argb[sy * width + sx];
            }
        }
        return new RasterImage(w, h, px);
    }
}
