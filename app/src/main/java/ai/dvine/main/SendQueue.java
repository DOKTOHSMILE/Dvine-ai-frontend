package ai.dvine.main;

/**
 * The composer's half of a send.
 *
 * <p>Tap Send and this decides what happens: a double tap is ignored, an empty box stops the reply that is going,
 * and when something is already being worked on the message is sent into that work instead of cutting it off
 * (Stop still stops it). The words show in the chat at once. The running task reads them on its next step and
 * continues, the way a message sent mid-task is folded into the same job. If the task has already finished and
 * never saw them, they go out as the next turn.
 *
 * <p>The screen side lives in {@link ai.dvine.MainActivity}: it implements {@link Host}, keeps the send button
 * enabled, and hands over the two ways a message can go out.
 */
public final class SendQueue {

    /** What the queue needs from the screen it serves. */
    public interface Host {
        /** A view of the screen by id, or null when it isn't there. */
        <T extends android.view.View> T find(int id);

        /** The main thread handler. */
        android.os.Handler handler();

        /** The note line above the composer, or null when it isn't there. */
        android.widget.TextView findNote();

        /** Something is being worked on or still being written out: a reply, an agent's run, a build. */
        boolean busy();

        /** The composer holds files (or a dictation draft) as well as words. */
        boolean hasAttachments();

        /** The dictation draft, taken so the reply can carry it; false when there is none. */
        boolean takeVoiceDraft();

        /** The dictation draft is going with this reply (the reply is spoken back as the turn it came from). */
        void markVoiceTurn();

        /** Words with no attachments: ask for the reply. */
        void answer(String text);

        /** Words that came with attachments: send those first. */
        void sendAttached(String text);

        /** Stop the reply that is running (empty box tapped while working). */
        void haltGeneration();

        /**
         * The user sent this while work is running. Show it now and hand it to that work. Do not stop the work.
         * {@code attached} means the composer is holding files that go with the words.
         */
        void steer(String text, boolean attached);

        /** The note line's own hide timer is cancelled: this beat is showing it. */
        void cancelNoteHide();
    }

    private final Host host;

    public SendQueue(Host host) {
        this.host = host;
    }

    /** After a send, taps on the button are ignored this long, so a double tap neither cancels nor sends twice. */
    static final long SEND_GUARD_MS = 900;
    private long lastSendAt = -SEND_GUARD_MS;

    public void sendComposer() {
        android.widget.EditText field = host.find(ai.dvine.R.id.et_message);
        if (field == null || field.getText() == null) {
            return;
        }
        String text = field.getText().toString().trim();
        boolean attached = host.hasAttachments();
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastSendAt < SEND_GUARD_MS) {
            return; // a double tap: the second tap would otherwise stop the reply it just asked for, or send twice
        }
        if (text.isEmpty() && !attached) {
            if (host.busy()) {
                host.haltGeneration();
            }
            return;
        }
        lastSendAt = now;
        field.setText("");
        if (host.takeVoiceDraft()) {
            host.markVoiceTurn();
        }
        if (host.busy()) {
            // work is running: the message joins it. The task is not stopped, and the model reads this on its next step.
            host.steer(text, attached);
            field.requestFocus();
            return;
        }
        if (attached) {
            host.sendAttached(text);
        } else {
            host.answer(text);
        }
        field.requestFocus(); // the keyboard keeps typing into the box after Send
    }
}