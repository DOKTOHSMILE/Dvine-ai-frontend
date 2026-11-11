package ai.ibiko.linux;

import java.util.regex.Pattern;

/**
 * How long a Linux command may run. A clone or a download prints nothing when the shell is not a terminal,
 * so a three-minute clock kills it while it is still fetching. Those keep going for {@link #LONG_MS}. Anything
 * else stops after {@link #SHORT_MS} of silence, and never runs past {@link #LONG_MS}.
 */
public final class CommandWait {
    public static final long SHORT_MS = 180_000L;
    /** A clone of a real repository, on a phone, over a slow link. */
    public static final long LONG_MS = 2L * 60L * 60L * 1000L;

    private CommandWait() {
    }

    public static long ms(String command) {
        return longJob(command) ? LONG_MS : SHORT_MS;
    }

    /** A fetch that can sit quiet for a long time and still be working. */
    public static boolean longJob(String command) {
        if (command == null) {
            return false;
        }
        String s = command.toLowerCase();
        return GIT_TRANSFER.matcher(s).find()
                || s.contains("apk add") || s.contains("apk upgrade") || s.contains("pkg install")
                || s.contains("wget ") || s.contains("curl ");
    }

    /** git, then flags, then clone/fetch/pull. `git -c key=value clone` is included. */
    private static final Pattern GIT_TRANSFER = Pattern.compile(
            "(?:^|[\\n;&|])\\s*(?:\\S*/)?git\\b[^\\n]*\\b(?:clone|fetch|pull|submodule)\\b");

    /** "3 minutes", "2 hours". {@code ms} is how long the command was allowed. */
    public static String lasted(long ms) {
        long minutes = Math.max(1L, (ms + 59_999L) / 60_000L);
        if (minutes >= 60 && minutes % 60 == 0) {
            long hours = minutes / 60;
            return hours + (hours == 1 ? " hour" : " hours");
        }
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }
}
