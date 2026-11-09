package ai.dvine.linux;

import java.util.function.Function;

/**
 * One Linux shell for the whole app. When the shell screen is open, chat runs its command there
 * instead of starting a second copy.
 */
public final class LinuxHub {
    /** Runs a command on the open shell and returns what it printed, or null when no shell is open. */
    private static volatile Function<String, String> shell;

    private LinuxHub() {
    }

    public static void attach(Function<String, String> run) {
        shell = run;
    }

    public static void detach(Function<String, String> run) {
        if (shell == run) {
            shell = null;
        }
    }

    public static String ask(String command) {
        Function<String, String> run = shell;
        if (run == null || command == null || command.trim().isEmpty()) {
            return null;
        }
        try {
            return run.apply(command);
        } catch (Throwable e) {
            return null;
        }
    }
}
