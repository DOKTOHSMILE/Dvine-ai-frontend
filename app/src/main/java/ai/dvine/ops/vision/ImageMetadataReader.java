package ai.dvine.ops.vision;

/** Reads a header only. It does not invent objects. */
public final class ImageMetadataReader {
    public static final class Meta {
        public final String format;
        public final int width;
        public final int height;
        public final boolean supported;
        public final String problem;

        public Meta(String format, int width, int height, boolean supported, String problem) {
            this.format = format;
            this.width = width;
            this.height = height;
            this.supported = supported;
            this.problem = problem == null ? "" : problem;
        }
    }

    public Meta read(byte[] bytes) {
        if (bytes == null || bytes.length < 8) {
            return new Meta("unknown", 0, 0, false, "The file is empty or too small to be an image.");
        }
        if (bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            if (bytes.length < 24) {
                return new Meta("png", 0, 0, false, "The PNG header is cut off.");
            }
            int w = u32(bytes, 16);
            int h = u32(bytes, 20);
            return new Meta("png", w, h, w > 0 && h > 0, w > 0 && h > 0 ? "" : "The PNG size is not usable.");
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return new Meta("jpeg", 0, 0, true, "");
        }
        if (bytes.length > 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return new Meta("webp", 0, 0, true, "");
        }
        return new Meta("unknown", 0, 0, false, "That file is not a PNG, JPEG, or WebP I can read.");
    }

    private static int u32(byte[] b, int o) {
        return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }
}
