package ai.dvine.ops.recon;

public final class ReconstructionQualityChecker {
    public static final class Quality {
        public final boolean renderable;
        public final boolean editable;
        public final String limitation;

        public Quality(boolean renderable, boolean editable, String limitation) {
            this.renderable = renderable;
            this.editable = editable;
            this.limitation = limitation == null ? "" : limitation;
        }
    }

    public Quality check(EditableScene scene) {
        if (scene == null || scene.nodes().isEmpty()) {
            return new Quality(false, false, "No geometry was produced.");
        }
        boolean named = true;
        for (EditableScene.Node n : scene.nodes()) {
            if (n.id == null || n.id.isEmpty() || n.semantic == null) {
                named = false;
            }
        }
        String limit = scene.approximation ? "This is an approximation, not a measured scan." : "";
        return new Quality(named, named, limit);
    }
}
