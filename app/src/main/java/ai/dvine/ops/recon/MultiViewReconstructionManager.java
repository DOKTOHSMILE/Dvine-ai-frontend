package ai.dvine.ops.recon;

import java.util.List;

/** Several pictures do not become a measurement. This build has no photogrammetry backend. */
public final class MultiViewReconstructionManager {
    public enum Quality {
        NONE, SINGLE_IMAGE_APPROXIMATION, MULTI_VIEW_UNSUPPORTED
    }

    public static final class Report {
        public final Quality quality;
        public final String method;
        public final String note;
        /** Always false until a real reconstruction backend exists and validates a mesh. */
        public final boolean measured;

        public Report(Quality quality, String method, String note) {
            this.quality = quality;
            this.method = method;
            this.note = note;
            this.measured = false;
        }
    }

    public Report assess(int imageCount) {
        if (imageCount <= 0) {
            return new Report(Quality.NONE, "none", "No images.");
        }
        if (imageCount == 1) {
            return new Report(Quality.SINGLE_IMAGE_APPROXIMATION, "procedural-approximation", "Only one view was available.");
        }
        return new Report(Quality.MULTI_VIEW_UNSUPPORTED, "unsupported",
                imageCount + " views were supplied. No photogrammetry backend ran. This is not measured.");
    }

    public String method(int imageCount) {
        return assess(imageCount).method;
    }

    public String note(List<String> paths) {
        return assess(paths == null ? 0 : paths.size()).note;
    }
}
