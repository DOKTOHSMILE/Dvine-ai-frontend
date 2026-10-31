package ai.dvine.render.field;

/** The middle of a photograph. This is a crop, not an object cut-out. */
public final class PhotoFrame {
    private PhotoFrame() {
    }

    /** x, y, width, height of the middle {@code keep} fraction. */
    public static int[] middle(int width, int height, float keep) {
        float k = keep < 0.4f ? 0.4f : keep > 1f ? 1f : keep;
        int cw = Math.max(1, Math.round(width * k));
        int ch = Math.max(1, Math.round(height * k));
        if (cw > width) {
            cw = width;
        }
        if (ch > height) {
            ch = height;
        }
        return new int[] { (width - cw) / 2, (height - ch) / 2, cw, ch };
    }
}
