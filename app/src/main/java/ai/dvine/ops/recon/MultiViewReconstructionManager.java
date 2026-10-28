package ai.dvine.ops.recon;

import java.util.List;

public final class MultiViewReconstructionManager {
    public String method(int imageCount) {
        if (imageCount <= 0) {
            return "none";
        }
        if (imageCount == 1) {
            return "procedural-approximation";
        }
        return "multi-view-approximation";
    }

    public String note(List<String> paths) {
        int n = paths == null ? 0 : paths.size();
        if (n <= 1) {
            return "Only one view was available.";
        }
        return n + " views were available. Hidden surfaces are still not measured.";
    }
}
