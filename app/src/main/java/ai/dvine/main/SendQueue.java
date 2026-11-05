package ai.dvine.main;

/**
 * The composer's half of a send, and the queue for messages that arrive while work is still running.
 *
 * <p>Tap Send and this decides what happens: a double tap is ignored, an empty box stops the reply that is going,
 * and when something is already being worked on the message waits its turn instead of cutting it off (Stop still
 * stops it). Waiting messages are shown on the note line above the keyboard with how long they've waited, and they
 * go one at a time once the work has finished and stayed finished a moment — a reply that runs in two steps is not
 * cut between them. Tapping that note brings the waiting messages back into the box to change.
 *
 * <p>The screen side lives in {@link ai.dvine.MainActivity}: it implements {@link Host}, keeps the send button
 * enabled, and hands over the two ways a message can go out.
 */
public final class SendQueue {

    /** What the queue needs from the screen it serves. */
    public interface Host {
        /** A view of the screen by id, or null when it isn't there. */
        <T extends android.view.View> T find(int id);

        /** The main thread handler, for the half-second beat. */
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
            // work is running: the message waits its turn instead of cutting the work off (Stop still stops it)
            final String words = text;
            hold(words, attached ? () -> host.sendAttached(words) : () -> host.answer(words));
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

    /** Messages sent while work was running, in order, waiting for it to finish (with what they say, and since when). */
    private final java.util.ArrayDeque<Runnable> waiting = new java.util.ArrayDeque<Runnable>();
    private final java.util.ArrayDeque<String> waitingWords = new java.util.ArrayDeque<String>();
    private long waitingSince;
    private int idleBeats;

    private void hold(String words, Runnable send) {
        if (waiting.isEmpty()) {
            waitingSince = System.currentTimeMillis();
        }
        waiting.add(send);
        waitingWords.add(words.isEmpty() ? "your files" : words);
        idleBeats = 0;
        host.handler().removeCallbacks(releaseBeat);
        host.handler().post(releaseBeat);
    }

    /**
     * Every half second while messages wait: the waiting line shows them and how long they've waited, and once the
     * work has finished (and stayed finished a moment, so a reply that runs in two steps isn't cut between them) the
     * first goes, then the next after its own work.
     */
    private final Runnable releaseBeat = new Runnable() {
        @Override
        public void run() {
            android.widget.TextView line = host.find(ai.dvine.R.id.composer_note);
            if (waiting.isEmpty()) {
                if (line != null && line.getTag() == waiting) {
                    line.setTag(null);
                    line.setOnClickListener(null);
                    line.setVisibility(android.view.View.GONE);
                }
                return;
            }
            if (!host.busy()) {
                idleBeats++;
            } else {
                idleBeats = 0;
            }
            if (idleBeats >= 2) {
                idleBeats = 0;
                Runnable next = waiting.poll();
                waitingWords.poll();
                waitingSince = System.currentTimeMillis();
                if (next != null) {
                    next.run();
                }
            }
            if (line != null && !waiting.isEmpty()) {
                long secs = (System.currentTimeMillis() - waitingSince) / 1000;
                String first = waitingWords.peek();
                first = first == null ? "" : first.length() > 40 ? first.substring(0, 40) + "\u2026" : first;
                line.setText("Waiting to send" + (waiting.size() > 1 ? " (" + waiting.size() + ")" : "") + ": \u201c" + first + "\u201d \u00b7 "
                        + ai.dvine.stage.ChatPane.duration(secs) + " \u00b7 sends when this finishes \u00b7 tap to edit");
                line.setTag(waiting);
                line.setVisibility(android.view.View.VISIBLE);
                host.cancelNoteHide();
                line.setOnClickListener(v -> unhold());
            }
            host.handler().postDelayed(this, 500);
        }
    };

    /** The waiting line tapped: the messages come back into the box to change, and nothing more waits. */
    private void unhold() {
        android.widget.EditText field = host.find(ai.dvine.R.id.et_message);
        StringBuilder b = new StringBuilder();
        for (String w : waitingWords) {
            if (!"your files".equals(w)) {
                b.append(b.length() == 0 ? "" : "\n").append(w);
            }
        }
        waiting.clear();
        waitingWords.clear();
        if (field != null) {
            field.setText(b.toString());
            field.setSelection(field.getText().length());
        }
        android.widget.TextView line = host.find(ai.dvine.R.id.composer_note);
        if (line != null) {
            line.setOnClickListener(null);
            line.setTag(null);
            line.setVisibility(android.view.View.GONE);
        }
    }
}