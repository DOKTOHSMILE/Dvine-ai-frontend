package ai.dvine.ops.task;

import java.util.ArrayList;
import java.util.List;

/** Separate channels so a microphone event cannot be mistaken for cancellation. */
public final class TaskEventDispatcher {
    public enum Channel {
        MICROPHONE, SPEECH, INSTRUCTION, PROGRESS, PAUSE, RESUME, CANCEL, RESULT, FAILURE
    }

    public static final class Event {
        public final Channel channel;
        public final String taskId;
        public final String message;
        public final long at;

        public Event(Channel channel, String taskId, String message, long at) {
            this.channel = channel;
            this.taskId = taskId == null ? "" : taskId;
            this.message = message == null ? "" : message;
            this.at = at;
        }
    }

    private final List<Event> log = new ArrayList<Event>();

    public synchronized Event emit(Channel channel, String taskId, String message, long at) {
        Event e = new Event(channel, taskId, message, at);
        log.add(e);
        if (log.size() > 400) {
            log.remove(0);
        }
        return e;
    }

    public synchronized List<Event> snapshot() {
        return new ArrayList<Event>(log);
    }

    public synchronized int count(Channel channel) {
        int n = 0;
        for (Event e : log) {
            if (e.channel == channel) {
                n++;
            }
        }
        return n;
    }
}
