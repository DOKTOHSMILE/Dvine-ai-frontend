package ai.ibiko.linux;

import java.io.File;
import java.util.List;

/**
 * DVINE's own shell. Every command runs in the app, in {@link ShellSession}. Termux is not asked and not required.
 * A program that is not built in comes back as exit 127. It is not handed to another app.
 */
public final class LinuxEngine {
    private LinuxEngine() {
    }

    /** Kept so older call sites still compile. The shell does not use it. */
    public interface FullShell {
        boolean ready();

        String run(String command);
    }

    /** What a command did. */
    public static final class Result {
        public final boolean ok;
        public final String text;

        public Result(boolean ok, String text) {
            this.ok = ok;
            this.text = text;
        }
    }

    /** True when every command in the line is one DVINE's shell runs itself. */
    public static boolean knows(String command) {
        return ShellSession.supported(command);
    }

    /** There is no Termux environment to update. Package installs are not this shell. */
    public static boolean updatesEnvironment(String command) {
        return false;
    }

    public static Result run(String command, File work, List<File> roots) {
        return run(command, work, roots, null);
    }

    public static Result run(String command, File work, List<File> roots, FullShell ignored) {
        if (work == null) {
            return new Result(false, "The shell has no folder.\nThis ran in Ibiko's own Linux. Termux was not opened.");
        }
        WorkspaceManager.ensure(work);
        try {
            CommandResult ran = new ShellSession(work).exec(command, roots);
            return new Result(ran.exit == 0, ran.text() + "\nThis ran in Ibiko's own Linux. Termux was not opened.");
        } catch (Throwable e) {
            return new Result(false, "The shell stopped before it could run that: " + e.getClass().getSimpleName()
                    + "\nThis ran in Ibiko's own Linux. Termux was not opened.");
        }
    }
}
