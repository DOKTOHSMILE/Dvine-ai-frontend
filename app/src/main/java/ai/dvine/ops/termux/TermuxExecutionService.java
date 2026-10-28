package ai.dvine.ops.termux;

import java.util.List;

/** Runs an already-approved argument list. It never receives a raw shell string from the model. */
public final class TermuxExecutionService {
    public interface Runner {
        Result run(List<String> argv, long timeoutMs);
    }

    public static final class Result {
        public final int exit;
        public final String stdout;
        public final String stderr;
        public final boolean timedOut;
        public final boolean cancelled;

        public Result(int exit, String stdout, String stderr, boolean timedOut, boolean cancelled) {
            this.exit = exit;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
            this.timedOut = timedOut;
            this.cancelled = cancelled;
        }
    }

    private final Runner runner;

    public TermuxExecutionService(Runner runner) {
        this.runner = runner;
    }

    public Result execute(List<String> argv, long timeoutMs) {
        if (runner == null) {
            return new Result(127, "", "no termux bridge", false, false);
        }
        if (argv == null || argv.isEmpty()) {
            return new Result(127, "", "empty command", false, false);
        }
        return runner.run(argv, timeoutMs);
    }
}
