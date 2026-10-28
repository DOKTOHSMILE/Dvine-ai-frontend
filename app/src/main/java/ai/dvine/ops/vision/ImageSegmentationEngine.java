package ai.dvine.ops.vision;

import java.util.Collections;
import java.util.List;

/** Segmentation is a separate step. An empty result means the boundary was not measured. */
public final class ImageSegmentationEngine {
    public static final class Region {
        public final String label;
        public final boolean measured;

        public Region(String label, boolean measured) {
            this.label = label;
            this.measured = measured;
        }
    }

    public List<Region> segment(boolean modelLoaded, String label) {
        if (!modelLoaded) {
            return Collections.emptyList();
        }
        return List.of(new Region(label, true));
    }
}
