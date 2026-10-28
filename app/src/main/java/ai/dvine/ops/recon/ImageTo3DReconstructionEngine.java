package ai.dvine.ops.recon;

import java.util.List;

import ai.dvine.ops.vision.ImageContentAnalyzer;

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
    private final AssetGenerationService assets = new AssetGenerationService();
    private final ReconstructionQualityChecker quality = new ReconstructionQualityChecker();

    public Built fromImage(String filename, ImageContentAnalyzer.Report report, int viewCount) {
        ReferenceImageAnalyzer.Reference ref = references.read(filename, report);
        if (!ref.enoughForApproximation) {
            return new Built(null, "none", "That file isn't an image I can build from. I didn't create a model.", false);
        }
        String method = views.method(Math.max(1, viewCount));
        EditableScene scene = assets.procedural(ref.label);
        scene.approximation = true;
        ReconstructionQualityChecker.Quality q = quality.check(scene);
        if (!q.renderable) {
            return new Built(null, method, "I couldn't build editable geometry from that image.", false);
        }
        String speech = "I built an editable 3D " + scene.label.toLowerCase() + " from the picture and put it on the board. "
                + ref.limitation + " " + views.note(List.of(filename));
        return new Built(scene, method, speech, true);
    }
}
