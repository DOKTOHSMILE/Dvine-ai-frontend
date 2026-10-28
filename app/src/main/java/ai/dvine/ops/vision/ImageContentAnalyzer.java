package ai.dvine.ops.vision;

import java.util.ArrayList;
import java.util.List;

public final class ImageContentAnalyzer {
    public static final class Report {
        public final String format;
        public final List<ObjectDetectionEngine.Hit> detections;
        public final List<ImageSegmentationEngine.Region> regions;
        public final boolean deeplyAnalyzed;
        public final String note;

        public Report(String format, List<ObjectDetectionEngine.Hit> detections, List<ImageSegmentationEngine.Region> regions,
                      boolean deeplyAnalyzed, String note) {
            this.format = format;
            this.detections = detections;
            this.regions = regions;
            this.deeplyAnalyzed = deeplyAnalyzed;
            this.note = note;
        }
    }

    private final ImageMetadataReader meta = new ImageMetadataReader();
    private final VisualFeatureExtractor features = new VisualFeatureExtractor();
    private final ObjectDetectionEngine detector = new ObjectDetectionEngine();
    private final ImageSegmentationEngine segments = new ImageSegmentationEngine();
    private boolean modelLoaded;

    public void setModelLoaded(boolean modelLoaded) {
        this.modelLoaded = modelLoaded;
    }

    public Report analyze(String filename, byte[] bytes) {
        ImageMetadataReader.Meta m = meta.read(bytes);
        if (!m.supported) {
            return new Report(m.format, List.of(), List.of(), false, m.problem);
        }
        features.fromBytes(bytes);
        List<ObjectDetectionEngine.Hit> hits = detector.detect(filename, modelLoaded);
        String label = hits.isEmpty() ? "unknown" : hits.get(0).label;
        List<ImageSegmentationEngine.Region> regions = segments.segment(modelLoaded, label);
        boolean deep = modelLoaded && !regions.isEmpty();
        String note = deep
                ? "A detector and a segmentation pass both ran."
                : "I read the file type and a filename hint. I do not have an on-device detector loaded, so I am not claiming I can see the parts in the picture.";
        return new Report(m.format, new ArrayList<ObjectDetectionEngine.Hit>(hits), regions, deep, note);
    }
}
