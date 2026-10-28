package ai.dvine.ops.pipe;

/** Decides what the user wants done. A question is not a command. */
public final class IntentDeterminationEngine {
    public String intent(SemanticTypes.Interpretation interpretation) {
        if (interpretation == null) {
            return "clarify";
        }
        if ("invalid".equals(interpretation.status)) {
            return "reject";
        }
        if (interpretation.clarification || "ambiguous".equals(interpretation.status)) {
            return "clarify";
        }
        if ("explain".equals(interpretation.intent) || "explain".equals(interpretation.operation)) {
            return "explain";
        }
        return interpretation.operation == null || interpretation.operation.isEmpty() ? "chat" : interpretation.operation;
    }
}
