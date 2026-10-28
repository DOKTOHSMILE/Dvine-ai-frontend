package ai.dvine.ops.pipe;

/** Turns a decomposition into a structured reading and rejects a reading that drops a negation. */
public final class BrainInterpretationEngine {
    public interface ModelReading {
        SemanticTypes.Interpretation read(SemanticTypes.Decomposition decomposition);
    }

    public SemanticTypes.Interpretation interpret(SemanticTypes.Decomposition d, ModelReading model) {
        SemanticTypes.Interpretation local = baseline(d);
        if (model == null) {
            return local;
        }
        SemanticTypes.Interpretation proposed = model.read(d);
        if (proposed == null) {
            local.status = "insufficient";
            return local;
        }
        if (contradicts(d, proposed)) {
            local.status = "invalid";
            local.intent = "reject";
            local.operation = "none";
            local.clarification = false;
            local.clarify = "I won't do that, because it contradicts what you said.";
            return local;
        }
        return proposed;
    }

    private static SemanticTypes.Interpretation baseline(SemanticTypes.Decomposition d) {
        SemanticTypes.Interpretation i = new SemanticTypes.Interpretation();
        i.target = d.target;
        i.parent = d.parent;
        i.constraints.addAll(d.preserve);
        i.constraints.addAll(d.negations);
        if (d.transcriptConfidence < 0.45) {
            i.status = "insufficient";
            i.clarification = true;
            i.clarify = "I didn't catch that clearly.";
            return i;
        }
        if (d.ambiguous && d.preserve.isEmpty()) {
            i.status = "ambiguous";
            i.clarification = true;
            i.clarify = "Which thing do you mean?";
            i.intent = "clarify";
            return i;
        }
        if (d.question || "explain".equals(d.action)) {
            i.intent = "explain";
            i.operation = "explain";
            i.status = "understood";
            return i;
        }
        i.intent = d.action;
        i.operation = d.action;
        i.status = d.action.equals("chat") ? "unsupported" : "understood";
        return i;
    }

    static boolean contradicts(SemanticTypes.Decomposition d, SemanticTypes.Interpretation proposed) {
        boolean userForbidsDelete = d.original.toLowerCase().contains("do not") || d.original.toLowerCase().contains("don't")
                || d.original.toLowerCase().contains("what would happen");
        boolean proposesDelete = "delete".equals(proposed.operation) || "remove".equals(proposed.operation) && d.question;
        if (userForbidsDelete && ("delete".equals(proposed.operation) || proposesDelete && d.question)) {
            return true;
        }
        if (!d.negations.isEmpty() && "delete".equals(proposed.operation) && d.original.toLowerCase().contains("do not delete")) {
            return true;
        }
        for (String keep : d.preserve) {
            if (keep.equals(proposed.target) && ("remove".equals(proposed.operation) || "delete".equals(proposed.operation))) {
                return true;
            }
        }
        return false;
    }
}
