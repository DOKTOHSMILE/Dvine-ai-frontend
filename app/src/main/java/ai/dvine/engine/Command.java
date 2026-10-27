package ai.dvine.engine;

/**
 * A typed request to the stage. Nothing is drawn or moved except by commands that
 * pass the {@link Supervisor}: the brain proposes, the engine decides.
 */
public final class Command {
    /** Each channel has one owner at a time. */
    public enum Channel {
        /** The movement engine: a designed body's poses, exercises, scenes, a turn. */
        BODY,
        /** The drawing space above the pod: sketches and 3D objects. */
        DRAW
    }

    public enum Status {
        QUEUED, RUNNING, COMPLETED, REJECTED, CANCELLED, EXPIRED, PREEMPTED
    }

    /** Who asked: the user (through the brain) outranks a touch reaction, which outranks idle behaviour. */
    public static final int PRIORITY_IDLE = 10;
    public static final int PRIORITY_REACTION = 30;
    public static final int PRIORITY_USER = 60;
    public static final int PRIORITY_STOP = 100;

    private static int counter;

    public final String id;
    public final String action;
    public final String value;
    public final int priority;
    public final long createdAt;
    /** Must start within this many ms of being created, or it is stale and expires. */
    public final long deadlineMs;
    /** Extra data: a scene's poses and seconds, or a sketch. */
    public String[] poses;
    public float[] seconds;
    public Object payload;

    public Command(String action, String value, int priority, long now) {
        this(action, value, priority, now, 4000L);
    }

    public Command(String action, String value, int priority, long now, long deadlineMs) {
        synchronized (Command.class) {
            counter++;
            this.id = "cmd-" + now + "-" + counter;
        }
        this.action = action;
        this.value = value;
        this.priority = priority;
        this.createdAt = now;
        this.deadlineMs = deadlineMs;
    }

    @Override
    public String toString() {
        return action + (value == null ? "" : ":" + value);
    }

    /** What happened to a command, reported back to the engine (and remembered as an episode). */
    public static final class Result {
        public final Command command;
        public final Status status;
        public final String reason;
        public final long at;

        Result(Command command, Status status, String reason, long at) {
            this.command = command;
            this.status = status;
            this.reason = reason;
            this.at = at;
        }

        @Override
        public String toString() {
            return command + " " + status + (reason == null ? "" : " (" + reason + ")");
        }
    }
}
