package ai.dvine.ops.task;

/** One heavy job at a time. A second heavy task waits; it does not run beside the first. */
public final class TaskPriorityManager {
    public enum Weight { LIGHT, HEAVY }

    public boolean canStart(TaskRecord candidate, TaskRecord active) {
        if (candidate == null) {
            return false;
        }
        if (active == null || !active.state.runningLike()) {
            return true;
        }
        return weight(candidate) == Weight.LIGHT && weight(active) == Weight.LIGHT;
    }

    public Weight weight(TaskRecord task) {
        if (task == null || task.kind == null) {
            return Weight.LIGHT;
        }
        switch (task.kind) {
            case "reconstruct":
            case "vision":
            case "ssh":
            case "research":
                return Weight.HEAVY;
            default:
                return Weight.LIGHT;
        }
    }
}
