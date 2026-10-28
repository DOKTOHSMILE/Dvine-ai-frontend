package ai.dvine.ops.task;

/** Lifecycle of one DVINE task. The microphone never writes this. */
public enum TaskState {
    QUEUED,
    RUNNING,
    LISTENING_FOR_INPUT,
    PAUSE_REQUESTED,
    PAUSED,
    WAITING_FOR_USER,
    WAITING_FOR_PERMISSION,
    RESUMING,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCEL_REQUESTED,
    CANCELLED;

    public boolean open() {
        switch (this) {
            case COMPLETED:
            case FAILED:
            case CANCELLED:
                return false;
            default:
                return true;
        }
    }

    public boolean runningLike() {
        return this == RUNNING || this == RESUMING || this == VERIFYING || this == PAUSE_REQUESTED || this == LISTENING_FOR_INPUT;
    }
}
