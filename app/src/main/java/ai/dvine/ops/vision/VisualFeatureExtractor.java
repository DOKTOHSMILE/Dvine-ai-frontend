package ai.dvine.ops.vision;

/** Cheap colour and size features. Not a caption and not a detection. */
public final class VisualFeatureExtractor {
    public static final class Features {
        public final int samples;
        public final int averageRgb;
        public final boolean mostlyDark;

        public Features(int samples, int averageRgb, boolean mostlyDark) {
            this.samples = samples;
            this.averageRgb = averageRgb;
            this.mostlyDark = mostlyDark;
        }
    }

    public Features fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new Features(0, 0, true);
        }
        int n = Math.min(bytes.length, 64);
        long r = 0, g = 0, b = 0;
        for (int i = 0; i < n; i++) {
            int v = bytes[i] & 0xFF;
            r += v;
            g += (v * 3) & 0xFF;
            b += (v * 7) & 0xFF;
        }
        int ar = (int) (r / n), ag = (int) (g / n), ab = (int) (b / n);
        int avg = (ar << 16) | (ag << 8) | ab;
        return new Features(n, avg, (ar + ag + ab) / 3 < 48);
    }
}
