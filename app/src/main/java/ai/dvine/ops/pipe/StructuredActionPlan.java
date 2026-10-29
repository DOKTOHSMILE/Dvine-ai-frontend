package ai.dvine.ops.pipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One validated reading of a request. Free-form model text is not allowed to name an operation
 * outside {@link Operation}.
 */
public final class StructuredActionPlan {
    public enum Operation {
        ANSWER, CLOCK, MODEL, ABILITIES, PICK, LEARN, EXPLAIN,
        DETACH, DELETE, RESEARCH, STORAGE, RECONSTRUCT, CHAT, REJECT
    }

    public final String requestId;
    public final Operation operation;
    public final String subject;
    /** Stable scene id when one was found in the inventory. Empty when none was. Never invented. */
    public final String targetId;
    public final boolean reversible;
    public final boolean needsConfirmation;
    public final boolean needsClarification;
    public final float confidence;
    public final List<String> constraints;
    public final String verification;

    public StructuredActionPlan(String requestId, Operation operation, String subject, String targetId,
            boolean reversible, boolean needsConfirmation, boolean needsClarification, float confidence,
            List<String> constraints, String verification) {
        this.requestId = requestId == null ? "" : requestId;
        this.operation = operation == null ? Operation.REJECT : operation;
        this.subject = subject == null ? "" : subject;
        this.targetId = targetId == null ? "" : targetId;
        this.reversible = reversible;
        this.needsConfirmation = needsConfirmation;
        this.needsClarification = needsClarification;
        this.confidence = confidence;
        this.constraints = constraints == null ? List.of() : Collections.unmodifiableList(new ArrayList<String>(constraints));
        this.verification = verification == null ? "" : verification;
    }

    /** Null when the plan may be inspected. A sentence when it must not be executed. */
    public String problem() {
        if (requestId.isEmpty()) {
            return "The request has no id.";
        }
        if (operation == Operation.REJECT || confidence < 0f || confidence > 1f) {
            return "The request is not a plan I can carry out.";
        }
        if (operation == Operation.DELETE && !needsConfirmation) {
            return "A delete needs confirmation.";
        }
        if (!targetId.isEmpty() && !targetId.matches("[a-z0-9_]+")) {
            return "The target id is not one this scene uses.";
        }
        return null;
    }
}
