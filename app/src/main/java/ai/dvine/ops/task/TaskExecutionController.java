package ai.dvine.ops.task;

import java.util.ArrayList;
import java.util.List;

/** Runs planned steps, skipping ones already verified, and stops at a pause checkpoint. */
public final class TaskExecutionController {
    public interface StepAction {
        Result run(String step, TaskRecord task);
    }

    public static final class Result {
        public final boolean ok;
        public final String detail;
        public final boolean unverified;

        public Result(boolean ok, String detail, boolean unverified) {
            this.ok = ok;
            this.detail = detail == null ? "" : detail;
            this.unverified = unverified;
        }

        public static Result ok(String detail) {
            return new Result(true, detail, false);
        }

        public static Result fail(String detail) {
            return new Result(false, detail, false);
        }
    }

    private final TaskCheckpointManager checkpoints;
    private final TaskEventDispatcher events;

    public TaskExecutionController(TaskCheckpointManager checkpoints, TaskEventDispatcher events) {
        this.checkpoints = checkpoints;
        this.events = events;
    }

    public TaskRecord run(TaskRecord task, List<String> plan, StepAction action, boolean pauseRequested, long now) {
        if (task.completed.isEmpty() && task.remaining.isEmpty()) {
            task.remaining.addAll(plan);
        }
        task.state = TaskState.RUNNING;
        task.updatedAt = now;
        List<String> pending = new ArrayList<String>(task.remaining);
        for (String step : pending) {
            if (task.stepDone(step)) {
                task.remaining.remove(step);
                continue;
            }
            if (pauseRequested || task.state == TaskState.PAUSE_REQUESTED || task.state == TaskState.CANCEL_REQUESTED) {
                return stopEarly(task, now);
            }
            Result r = action.run(step, task);
            if (r == null || !r.ok) {
                task.state = TaskState.FAILED;
                task.lastError = r == null ? "no result" : r.detail;
                task.updatedAt = now;
                checkpoints.save(task, task.checkpoint, now);
                events.emit(TaskEventDispatcher.Channel.FAILURE, task.id, task.lastError, now);
                return task;
            }
            if (r.unverified) {
                task.state = TaskState.FAILED;
                task.lastError = "submitted but not verified: " + r.detail;
                task.updatedAt = now;
                checkpoints.save(task, task.checkpoint, now);
                return task;
            }
            task.completed.add(step);
            task.remaining.remove(step);
            task.checkpoint = step;
            task.facts.put("last_step", step);
            if (!r.detail.isEmpty()) {
                task.facts.put("finding." + step, r.detail);
            }
            checkpoints.save(task, step, now);
            events.emit(TaskEventDispatcher.Channel.PROGRESS, task.id, step, now);
            if (task.state == TaskState.PAUSE_REQUESTED || task.state == TaskState.CANCEL_REQUESTED) {
                return stopEarly(task, now);
            }
        }
        task.state = TaskState.VERIFYING;
        task.state = TaskState.COMPLETED;
        task.updatedAt = now;
        checkpoints.save(task, "done", now);
        events.emit(TaskEventDispatcher.Channel.RESULT, task.id, "completed", now);
        return task;
    }

    private TaskRecord stopEarly(TaskRecord task, long now) {
        if (task.state == TaskState.CANCEL_REQUESTED) {
            task.state = TaskState.CANCELLED;
            task.cancelStatus = "cancelled";
            events.emit(TaskEventDispatcher.Channel.CANCEL, task.id, "cancelled", now);
        } else {
            task.state = TaskState.PAUSED;
            events.emit(TaskEventDispatcher.Channel.PAUSE, task.id, task.checkpoint, now);
        }
        task.updatedAt = now;
        checkpoints.save(task, task.checkpoint, now);
        return task;
    }
}
