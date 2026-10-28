package ai.dvine.ops.recon;

import ai.dvine.ops.vision.ImageContentAnalyzer;

public final class ReferenceImageAnalyzer {
    public static final class Reference {
        public final String label;
        public final boolean enoughForApproximation;
        public final boolean exactDimensions;
        public final String limitation;

        public Reference(String label, boolean enoughForApproximation, boolean exactDimensions, String limitation) {
            this.label = label;
            this.enoughForApproximation = enoughForApproximation;
            this.exactDimensions = exactDimensions;
            this.limitation = limitation;
        }
    }

    public Reference read(String filename, ImageContentAnalyzer.Report report) {
        String label = "object";
        if (report != null && !report.detections.isEmpty() && !"unknown".equals(report.detections.get(0).label)) {
            label = report.detections.get(0).label;
        } else if (filename != null) {
            label = filename;
        }
        boolean enough = report != null && report.format != null && !report.format.equals("unknown");
        String limit = "One picture does not show hidden surfaces or real measurements, so the model is an approximation.";
        return new Reference(label, enough, false, limit);
    }
}
