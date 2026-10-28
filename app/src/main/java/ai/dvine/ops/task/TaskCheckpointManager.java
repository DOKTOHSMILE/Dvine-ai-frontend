package ai.dvine.ops.task;

/** Saves the last verified point a task can resume from. Does not claim a dead process survived. */
public final class TaskCheckpointManager {
    private final TaskStateRepository repository;

    public TaskCheckpointManager(TaskStateRepository repository) {
        this.repository = repository;
    }

    public void save(TaskRecord task, String checkpoint, long now) {
        if (task == null) {
            return;
        }
        task.checkpoint = checkpoint == null ? "" : checkpoint;
        task.updatedAt = now;
        repository.save(task);
    }
}
