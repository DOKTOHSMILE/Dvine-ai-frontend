package ai.dvine.ops.task;

import java.util.concurrent.atomic.AtomicBoolean;

/** A cancel flag a running step can see. Changing the task record alone does not stop the step. */
public final class TaskControl {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean busy = new AtomicBoolean();

    public void working() {
        busy.set(true);
    }

    public void stopped() {
        busy.set(false);
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get() || Thread.currentThread().isInterrupted();
    }

    public boolean busy() {
        return busy.get();
    }
}
