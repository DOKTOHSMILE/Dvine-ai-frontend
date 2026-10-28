package ai.dvine.ops.pipe;

import java.util.List;

public final class ActionResultVerifier {
    public boolean accept(SemanticTypes.Decomposition asked, SemanticTypes.ToolResult result, List<String> stillPresent) {
        if (result == null || !result.ok || !result.verified) {
            return false;
        }
        if (asked == null) {
            return false;
        }
        if ("explain".equals(asked.action) && result.evidence.contains("deleted")) {
            return false;
        }
        if (stillPresent != null) {
            for (String keep : asked.preserve) {
                if ("unrelated_geometry".equals(keep)) {
                    continue;
                }
                if (!stillPresent.contains(keep)) {
                    return false;
                }
            }
        }
        return true;
    }
}
