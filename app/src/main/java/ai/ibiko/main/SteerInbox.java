package ai.ibiko.main;

import java.util.ArrayDeque;

/**
 * Messages the user sends while a task is still running.
 *
 * <p>They are shown in the chat at once and taken, in order, by the running task before its next step
 * (or by the reply once the current model call returns). Nothing here stops the task. The same note is
 * taken only once, so a late flush cannot also start a second turn with it.
 */
public final class SteerInbox {
    private final ArrayDeque<String> notes = new ArrayDeque<String>();

    public synchronized void add(String text) {
        if (text == null) {
            return;
        }
        String t = text.trim();
        if (!t.isEmpty()) {
            notes.add(t);
        }
    }

    /** Everything waiting, oldest first, or null when there is nothing. Cleared. */
    public synchronized String drain() {
        if (notes.isEmpty()) {
            return null;
        }
        StringBuilder b = new StringBuilder();
        String n;
        while ((n = notes.poll()) != null) {
            if (b.length() > 0) {
                b.append("\n\n");
            }
            b.append(n);
        }
        return b.toString();
    }

    public synchronized boolean pending() {
        return !notes.isEmpty();
    }

    public synchronized int size() {
        return notes.size();
    }
}
