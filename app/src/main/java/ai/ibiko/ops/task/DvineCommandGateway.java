package ai.ibiko.ops.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The sole typed authorization boundary for task control. Raw text and model-provided intents are never accepted.
 * Voice commands require a short-lived, explicitly opened command-mode capability; buttons use a separate event path.
 */
public final class DvineCommandGateway {
    public enum Action { PAUSE, RESUME, CANCEL, STOP }
    public enum Source { USER_BUTTON, AUTHORIZED_USER_VOICE }

    /** Structured command data. The constructor is private so callers cannot assert source/authorization fields. */
    public static final class CommandIntent {
        public final String type = "TASK_CONTROL";
        public final Action action;
        public final String targetTaskId;
        public final Source source;
        public final boolean authorized = true;
        public final boolean requiresConfirmation;

        private CommandIntent(Action action, String targetTaskId, Source source, boolean requiresConfirmation) {
            this.action = action;
            this.targetTaskId = targetTaskId;
            this.source = source;
            this.requiresConfirmation = requiresConfirmation;
        }
    }

    /** Capability returned only when the UI explicitly enters voice-command mode. One command, short lifetime. */
    public static final class VoiceSession {
        private final DvineCommandGateway owner;
        private final long expiresAt;
        private boolean consumed;

        private VoiceSession(DvineCommandGateway owner, long expiresAt) {
            this.owner = owner;
            this.expiresAt = expiresAt;
        }
    }

    public static final class Result {
        public final boolean accepted;
        public final String reason;
        public final CommandIntent intent;

        private Result(boolean accepted, String reason, CommandIntent intent) {
            this.accepted = accepted;
            this.reason = reason;
            this.intent = intent;
        }
    }

    public interface Controller {
        void execute(CommandIntent intent, TaskRecord target);
    }

    private static final long VOICE_SESSION_MS = 60_000L;
    private final DvineTaskManager tasks;
    private final Controller controller;

    public DvineCommandGateway(DvineTaskManager tasks, Controller controller) {
        if (tasks == null || controller == null) throw new IllegalArgumentException("tasks and controller are required");
        this.tasks = tasks;
        this.controller = controller;
    }

    /** Called only by an explicit UI command-mode activation, never merely by opening the microphone. */
    public VoiceSession beginVoiceCommandMode() {
        return new VoiceSession(this, System.currentTimeMillis() + VOICE_SESSION_MS);
    }

    /** Dedicated control buttons send typed events; they do not pass through conversational text. */
    public Result submitButtonCommand(Action action, String taskId, boolean confirmed) {
        return dispatch(action, taskId, Source.USER_BUTTON, confirmed);
    }

    /** Ends a voice-command session when the utterance was conversation, not a control. */
    public boolean closeVoiceCommand(VoiceSession session) {
        if (!live(session)) {
            return false;
        }
        session.consumed = true;
        return true;
    }

    /** Accepts a structured intent only while an explicitly activated voice-command session is valid. */
    public Result submitVoiceCommand(VoiceSession session, Action action, String taskId, boolean confirmed) {
        if (!live(session)) {
            return rejected("Voice command mode is not active.");
        }
        session.consumed = true;
        return dispatch(action, taskId, Source.AUTHORIZED_USER_VOICE, confirmed);
    }

    private boolean live(VoiceSession session) {
        return session != null && session.owner == this && !session.consumed && System.currentTimeMillis() <= session.expiresAt;
    }

    /** Model output, generated code, documents and other external content are never command authorities. */
    public Result rejectModelCommand(Action ignoredAction, String ignoredTaskId) {
        return rejected("Model output is not an authorized command source.");
    }

    private Result dispatch(Action action, String taskId, Source source, boolean confirmed) {
        if (action == null || !registered(action)) return rejected("Unknown task-control action.");
        if (taskId == null || taskId.trim().isEmpty()) return rejected("A specific task target is required.");
        TaskRecord target = find(taskId.trim());
        if (target == null) return rejected("The selected task does not exist.");

        boolean paused = target.state == TaskState.PAUSED || target.state == TaskState.WAITING_FOR_USER;
        if ((action == Action.PAUSE || action == Action.CANCEL || action == Action.STOP) && !target.state.runningLike()) {
            return rejected("The selected task is not running.");
        }
        if (action == Action.RESUME && !paused) return rejected("The selected task is not paused.");

        boolean needsConfirmation = action == Action.CANCEL || action == Action.STOP || target.destructive;
        CommandIntent intent = new CommandIntent(action, target.id, source, needsConfirmation);
        if (needsConfirmation && !confirmed) return new Result(false, "Confirmation is required for this task-control action.", intent);
        // STOP has no distinct lifecycle semantics in the existing task state machine; fail closed rather than alias it.
        if (action == Action.STOP) return new Result(false, "STOP is unavailable until the task controller defines its semantics.", intent);
        controller.execute(intent, target);
        return new Result(true, "Command authorized.", intent);
    }

    private static boolean registered(Action action) {
        for (Action allowed : Action.values()) if (allowed == action) return true;
        return false;
    }

    private TaskRecord find(String id) {
        for (TaskRecord task : tasks.all()) if (id.equals(task.id)) return task;
        return null;
    }

    private static Result rejected(String reason) {
        return new Result(false, reason, null);
    }
}
