package ai.dvine.main;

import android.os.Handler;
import android.view.View;

import ai.dvine.turn.Meaning;

/**
 * Reads a short message for a command DVINE carries out itself, then either runs it or passes the words on.
 * Model classification is not authority: the explicit Stop UI control remains the only way to interrupt generation.
 */
public final class DirectCommands {
    public static final long WAIT_API_MS = 6000;
    public static final long WAIT_PHONE_MS = 4000;

    public interface Host {
        boolean workUnderWay();
        Meaning.Command remembered(String text);
        void remember(String text, Meaning.Command command);
        boolean remoteModel();
        void ask(String text);
        void say(String canonical);
        String classify(String prompt, String text);
        Handler handler();
        View clock();
        boolean alive();
        void note(String line);
        void perform(Meaning.Command command);
        void holdReply(boolean on);
        void cancelModel();
    }

    private final Host host;
    private int token;

    public DirectCommands(Host host) { this.host = host; }

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
            if (settled[0] || mine != token || !host.alive()) return;
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
            Meaning.Command command = Meaning.read(reply);
            // A model's label cannot authorize interruption. The explicit Stop UI control is separate.
            if (command == Meaning.Command.STOP) command = Meaning.Command.NONE;
            if ((command == Meaning.Command.NEW_CHAT || command == Meaning.Command.CLEAR_WORKSPACE)
                    && !Meaning.userMeant(text, command)) {
                command = Meaning.Command.NONE;
            }
            final Meaning.Command found = command;
            host.handler().post(() -> {
                if (settled[0] || mine != token || !host.alive()) return;
                settled[0] = true;
                if (reply != null) host.remember(text, found);
                act(text, found, work);
            });
        }, "dvine-meaning").start();
    }

    public void act(String text, Meaning.Command command, boolean work) {
        if (command == Meaning.Command.NONE || command == Meaning.Command.STOP || (command.needsWork && !work)) {
            host.ask(text);
            return;
        }
        if ((command == Meaning.Command.NEW_CHAT || command == Meaning.Command.CLEAR_WORKSPACE)
                && !Meaning.userMeant(text, command)) {
            host.ask(text);
            return;
        }
        host.note("Understood as “" + command.canonical + "”");
        if (command.direct()) {
            host.perform(command);
            return;
        }
        host.say(command.canonical);
    }
}
