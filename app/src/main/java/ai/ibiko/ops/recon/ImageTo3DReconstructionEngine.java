package ai.ibiko.ops.recon;

import ai.ibiko.ops.vision.ImageContentAnalyzer;

public final class ImageTo3DReconstructionEngine {
    public static final class Built {
        public final EditableScene scene;
        public final String method;
        public final String speech;
        public final boolean ok;

        public Built(EditableScene scene, String method, String speech, boolean ok) {
            this.scene = scene;
            this.method = method;
            this.speech = speech;
            this.ok = ok;
        }
    }

    private final ReferenceImageAnalyzer references = new ReferenceImageAnalyzer();
    private final MultiViewReconstructionManager views = new MultiViewReconstructionManager();

    public Built fromImage(String filename, ImageContentAnalyzer.Report report, int viewCount) {
        ReferenceImageAnalyzer.Reference ref = references.read(filename, report);
        if (!ref.enoughForApproximation) {
            return new Built(null, "none", "That file isn't an image I can build from. I didn't create a model.", false);
        }
        // there are no ready-made shapes to pick from a label: a 3D build comes from measurements the model designs
        return new Built(null, views.method(Math.max(1, viewCount)), "I don't turn a picture into one of a few ready-made shapes."
                + " Tell me what it is and its main measurements, or ask me to design it, and I'll build it from those numbers.", false);
    }
}
