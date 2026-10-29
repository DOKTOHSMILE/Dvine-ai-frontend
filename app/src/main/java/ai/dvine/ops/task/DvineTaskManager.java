package ai.dvine.ops.task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The one task lifecycle. Tools do not keep a second manager. */
public final class DvineTaskManager {
    private final TaskStateRepository repository;
    private final TaskEventDispatcher events;
    private final TaskCheckpointManager checkpoints;
    private final TaskPriorityManager priority = new TaskPriorityManager();
    private final TaskResumeManager resume = new TaskResumeManager();
    private MicState mic = MicState.MICROPHONE_OFF;
    private String activeId;
    private final Map<String, TaskControl> controls = new LinkedHashMap<String, TaskControl>();

    public DvineTaskManager(TaskStateRepository repository, TaskEventDispatcher events) {
        this.repository = repository;
        this.events = events;
        this.checkpoints = new TaskCheckpointManager(repository);
    }

    public MicState mic() {
        return mic;
    }

    /** Never changes a task's state. */
    public void microphone(MicState state, long now) {
        mic = state == null ? MicState.MICROPHONE_OFF : state;
        events.emit(TaskEventDispatcher.Channel.MICROPHONE, activeId, mic.name(), now);
    }

    public TaskRecord active() {
        if (activeId == null) {
            return null;
        }
        TaskRecord t = repository.load(activeId);
        if (t == null || !t.state.open() || t.state == TaskState.PAUSED) {
            return t != null && t.state.runningLike() ? t : null;
        }
        return t.state.runningLike() || t.state == TaskState.WAITING_FOR_USER || t.state == TaskState.QUEUED ? t : null;
    }

    public boolean hasActiveWork() {
        TaskRecord t = active();
        return t != null && t.state.runningLike();
    }

    public TaskRecord create(String instruction, String description, String kind, List<String> steps, boolean destructive, long now) {
        return create(instruction, description, kind, steps, destructive, now, null);
    }

    /**
     * {@code requestId} is stable across retries of one request. A different request gets its own id.
     * The key is not a hash of the words.
     */
    public TaskRecord create(String instruction, String description, String kind, List<String> steps, boolean destructive, long now, String requestId) {
        String kindSafe = kind == null || kind.isEmpty() ? "general" : kind;
        String rid = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId.trim();
        String key = rid + ":" + kindSafe + ":1";
        TaskRecord prior = repository.byIdempotency(key);
        if (prior != null) {
            return prior;
        }
        TaskRecord t = new TaskRecord();
        t.id = UUID.randomUUID().toString();
        t.requestId = rid;
        t.instruction = instruction == null ? "" : instruction;
        t.description = description == null || description.isEmpty() ? t.instruction : description;
        t.kind = kindSafe;
        t.state = TaskState.QUEUED;
        if (steps != null) {
            t.remaining.addAll(steps);
        }
        t.destructive = destructive;
        t.createdAt = now;
        t.updatedAt = now;
        t.idempotencyKey = key;
        repository.save(t);
        events.emit(TaskEventDispatcher.Channel.INSTRUCTION, t.id, t.description, now);
        return t;
    }

    /** The step calls this while it is running, and checks {@link TaskControl#isCancelled()}. */
    public TaskControl attach(TaskRecord t) {
        TaskControl c = new TaskControl();
        if (t != null && t.id != null) {
            controls.put(t.id, c);
        }
        return c;
    }

    public void markRunning(TaskRecord t, long now) {
        TaskRecord current = active();
        if (current != null && !current.id.equals(t.id) && !priority.canStart(t, current)) {
            requestPause(current, now);
        }
        t.state = TaskState.RUNNING;
        t.updatedAt = now;
        activeId = t.id;
        repository.save(t);
    }

    public TaskRecord requestPause(TaskRecord t, long now) {
        if (t == null) {
            return null;
        }
        t.state = TaskState.PAUSE_REQUESTED;
        checkpoints.save(t, t.checkpoint.isEmpty() ? "paused" : t.checkpoint, now);
        t.state = TaskState.PAUSED;
        t.updatedAt = now;
        if (t.id.equals(activeId)) {
            activeId = null;
        }
        repository.save(t);
        events.emit(TaskEventDispatcher.Channel.PAUSE, t.id, "paused", now);
        return t;
    }

    public TaskRecord cancel(TaskRecord t, long now) {
        if (t == null) {
            return null;
        }
        TaskControl c = controls.get(t.id);
        if (c != null) {
            c.cancel();
        }
        t.state = TaskState.CANCEL_REQUESTED;
        if (c == null || !c.busy()) {
            t.state = TaskState.CANCELLED;
            t.cancelStatus = "cancelled";
        } else {
            t.cancelStatus = "cancel requested";
        }
        t.updatedAt = now;
        if (t.id.equals(activeId) && t.state == TaskState.CANCELLED) {
            activeId = null;
        }
        repository.save(t);
        events.emit(TaskEventDispatcher.Channel.CANCEL, t.id, t.cancelStatus, now);
        return t;
    }

    /** The step has stopped. A cancel that was waiting can now be reported as cancelled. */
    public TaskRecord noteStopped(TaskRecord t, long now) {
        if (t == null) {
            return null;
        }
        TaskControl c = controls.get(t.id);
        if (c != null) {
            c.stopped();
        }
        if (t.state == TaskState.CANCEL_REQUESTED) {
            t.state = TaskState.CANCELLED;
            t.cancelStatus = "cancelled";
            t.updatedAt = now;
            if (t.id.equals(activeId)) {
                activeId = null;
            }
            repository.save(t);
        }
        return t;
    }

    public TaskResumeManager.Choice chooseResume(String utterance) {
        return resume.choose(utterance, paused());
    }

    public void markResuming(TaskRecord t, long now) {
        t.state = TaskState.RESUMING;
        t.updatedAt = now;
        activeId = t.id;
        repository.save(t);
        events.emit(TaskEventDispatcher.Channel.RESUME, t.id, t.checkpoint, now);
        t.state = TaskState.RUNNING;
        repository.save(t);
    }

    public List<TaskRecord> paused() {
        List<TaskRecord> out = new ArrayList<TaskRecord>();
        for (TaskRecord t : repository.all()) {
            if (t.state == TaskState.PAUSED || t.state == TaskState.WAITING_FOR_USER) {
                out.add(t);
            }
        }
        return out;
    }

    public List<TaskRecord> all() {
        return repository.all();
    }

    public TaskCheckpointManager checkpoints() {
        return checkpoints;
    }

    public TaskEventDispatcher events() {
        return events;
    }

    public String activityLine() {
        TaskRecord t = active();
        if (t != null) {
            String step = t.facts.getOrDefault("last_step", t.remaining.isEmpty() ? "working" : t.remaining.get(0));
            return t.description + " — " + step.replace('_', ' ');
        }
        List<TaskRecord> p = paused();
        if (!p.isEmpty()) {
            return p.get(0).description + " paused. Your progress has been saved.";
        }
        return null;
    }

    public String restoredSummary() {
        List<TaskRecord> p = paused();
        if (p.isEmpty()) {
            return null;
        }
        if (p.size() == 1) {
            return p.get(0).description + " is paused. Say resume to continue.";
        }
        return p.size() + " tasks are paused. Say resume and the task's name.";
    }
}
