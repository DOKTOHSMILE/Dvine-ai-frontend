package ai.dvine.linux;

/** What one command finished as. The exit code is the check: a command is not a success without one. */
public final class CommandResult {
    public final String command;
    public final int exit;
    public final String stdout;
    public final String stderr;
    public final long millis;

    public CommandResult(String command, int exit, String stdout, String stderr, long millis) {
        this.command = command == null ? "" : command;
        this.exit = exit;
        this.stdout = stdout == null ? "" : stdout;
        this.stderr = stderr == null ? "" : stderr;
        this.millis = millis;
    }

    /** The text a person or the brain reads, with the exit code on the last line. */
    public String text() {
        StringBuilder b = new StringBuilder();
        if (!stdout.isEmpty()) {
            b.append(stdout);
            if (!stdout.endsWith("\n")) {
                b.append('\n');
            }
        }
        if (!stderr.isEmpty()) {
            b.append(stderr);
            if (!stderr.endsWith("\n")) {
                b.append('\n');
            }
        }
        b.append("[exit ").append(exit).append(']');
        return b.toString();
    }
}
