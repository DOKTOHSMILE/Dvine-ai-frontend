package ai.dvine.ops.ssh;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Allow-listed remote operations only. */
public final class SshCommandExecutor {
    public static final class Command {
        public final List<String> argv;
        public final boolean destructive;
        public final String refusal;

        public Command(List<String> argv, boolean destructive, String refusal) {
            this.argv = argv;
            this.destructive = destructive;
            this.refusal = refusal;
        }
    }

    public Command plan(String utterance, String logPath) {
        String n = utterance == null ? "" : utterance.toLowerCase(Locale.US);
        if (n.matches(".*(delete|drop |wipe|rm |shutdown|reboot|passwd|overwrite).*")) {
            return new Command(null, true, "I need an explicit confirmation before any destructive remote change, and this wording is not an approved command.");
        }
        if (n.contains("storage") || n.contains("disk")) {
            return new Command(Arrays.asList("df", "-h", "."), false, null);
        }
        if (n.contains("log") || n.contains("why") && n.contains("fail")) {
            String path = logPath == null || logPath.isEmpty() ? "/var/log/app.log" : logPath;
            if (path.contains("..") || path.contains(";") || path.contains("|")) {
                return new Command(null, false, "That log path is not allowed.");
            }
            return new Command(Arrays.asList("tail", "-n", "80", path), false, null);
        }
        if (n.contains("restart")) {
            return new Command(Arrays.asList("restart-app"), true, null);
        }
        return new Command(null, false, "I don't have an approved remote command for that.");
    }
}
