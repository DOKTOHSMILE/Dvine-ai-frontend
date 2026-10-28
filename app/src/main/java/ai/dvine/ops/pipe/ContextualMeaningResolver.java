package ai.dvine.ops.pipe;

/** Applies a stored correction only in a matching context. */
public final class ContextualMeaningResolver {
    public String retarget(String target, String context, SemanticKnowledgeRepository memory) {
        if (memory == null || target == null) {
            return target;
        }
        String corrected = memory.correction(context, target);
        return corrected == null ? target : corrected;
    }
}
