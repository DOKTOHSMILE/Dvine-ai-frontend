package ai.dvine.main;

import android.os.Handler;
import android.view.View;

import ai.dvine.turn.Meaning;

/**
 * Reading a short message for a command DVINE carries out itself, then either doing that or passing the words on.
 *
 * <p>A working answer from the API is not a command. A trigger word inside a real task ("stop the null in the
 * login") is not {@link Meaning.Command#STOP}. The screen side lives in {@link ai.dvine.MainActivity}.
 */
public final class DirectCommands {
    /** How long the reading of a message may take before the message goes on as written. */
    public static final long WAIT_API_MS = 6000;
    public static final long WAIT_PHONE_MS = 4000;

    /** What reading a message needs from the screen. */
    public interface Host {
        /** There is work (or a paused reply) a "continue" could carry on. */
        boolean workUnderWay();

        /** A phrasing already understood, or null. */
        Meaning.Command remembered(String text);

        /** Keep what this phrasing meant. */
        void remember(String text, Meaning.Command command);

        /** An API model is the one answering (the phone's own model is not asked to classify). */
        boolean remoteModel();

        /** The message goes on as the user wrote it (it is already past the message gate). */
        void ask(String text);

        /** Run a command's own wording through the message gate ("continue", "download the model"). */
        void say(String canonical);

        /** One classification call. Blocks. Called off the main thread. */
        String classify(String prompt, String text);

        Handler handler();

        /** A view to post the "don't wait forever" clock on. */
        View clock();

        boolean alive();

        void note(String line);

        /** Carry out a command DVINE does itself (new chat, stop, settings). */
        void perform(Meaning.Command command);

        void holdReply(boolean on);

        void cancelModel();
    }

    private final Host host;
    private int token;

    public DirectCommands(Host host) {
        this.host = host;
    }

    /** Reads {@code text} and either runs the command or asks the brain. */
    public void read(final String text) {
        final boolean work = host.workUnderWay();
        Meaning.Command known = host.remembered(text);
        if (known != null) {
            act(text, known, work);
            return;
        }
        if (!host.remoteModel()) {
            host.ask(text);
            return;
        }
        final int mine = ++token;
        final boolean[] settled = {false};
        View clock = host.clock();
        clock.postDelayed(() -> {
            if (settled[0] || mine != token || !host.alive()) {
                return;
            }
            settled[0] = true;
            token++;
            host.cancelModel();
            host.ask(text);
        }, host.remoteModel() ? WAIT_API_MS : WAIT_PHONE_MS);
        new Thread(() -> {
            host.holdReply(true);
            String reply;
            try {
                reply = host.classify(Meaning.prompt(work), text);
            } finally {
                host.holdReply(false);
            }
            Meaning.Command c = Meaning.read(reply);
            if ((c == Meaning.Command.STOP || c == Meaning.Command.NEW_CHAT || c == Meaning.Command.CLEAR_WORKSPACE)
                    && !Meaning.userMeant(text, c)) {
                c = Meaning.Command.NONE; // a trigger word inside a task, or a working answer, is not that command
            }
            final Meaning.Command found = c;
            host.handler().post(() -> {
                if (settled[0] || mine != token || !host.alive()) {
                    return;
                }
                settled[0] = true;
                if (reply != null) {
                    host.remember(text, found);
                }
                act(text, found, work);
            });
        }, "dvine-meaning").start();
    }

    /** Carries out what the message meant: the command, or the message itself. */
    public void act(String text, Meaning.Command c, boolean work) {
        if (c == Meaning.Command.NONE || (c.needsWork && !work)) {
            host.ask(text);
            return;
        }
        if ((c == Meaning.Command.STOP || c == Meaning.Command.NEW_CHAT || c == Meaning.Command.CLEAR_WORKSPACE)
                && !Meaning.userMeant(text, c)) {
            host.ask(text);
            return;
        }
        host.note("Understood as \u201c" + c.canonical + "\u201d");
        if (c.direct()) {
            host.perform(c);
            return;
        }
        host.say(c.canonical);
    }
}
