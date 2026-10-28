package ai.dvine.ops.pipe;

/** Names the tool. The orchestrator performs it so permissions stay outside the brain. */
public final class ToolExecutionRouter {
    public String route(SemanticTypes.Plan plan) {
        return plan == null || plan.tool == null ? "none" : plan.tool;
    }
}
