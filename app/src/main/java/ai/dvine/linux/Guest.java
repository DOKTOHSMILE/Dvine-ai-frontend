package ai.dvine.linux;

/**
 * How one typed line is handed to Alpine so the folder you were in is kept, and the output comes back
 * when the command finishes instead of sitting in a pipe.
 */
public final class Guest {
    public static final String MARK = "DVINE_STATUS:";

    private Guest() {
    }

    /** A single-quoted shell word. */
    public static String quote(String text) {
        String s = text == null ? "" : text;
        return "'" + s.replace("'", "'\\''") + "'";
    }

    /**
     * The script Alpine runs. It starts in {@code cwd}, runs the line, then prints the new folder and
     * {@code DVINE_STATUS:exit}.
     */
    public static String wrap(String cwd, String line) {
        String dir = cwd != null && cwd.startsWith("/") ? cwd : "/root";
        return "cd " + quote(dir) + " || cd /root\n"
                + (line == null ? "" : line) + "\n"
                + "status=$?\n"
                + "pwd\n"
                + "echo " + MARK + "$status\n"
                + "exit $status\n";
    }

    /** What {@link #wrap} printed, split into the text to show, the folder, and the exit code. */
    public static Answer split(String raw, String previousCwd) {
        String previous = previousCwd == null || previousCwd.isEmpty() ? "/root" : previousCwd;
        if (raw == null) {
            return new Answer("", previous, 1);
        }
        int at = raw.lastIndexOf(MARK);
        if (at < 0) {
            return new Answer(raw, previous, 1);
        }
        String head = raw.substring(0, at);
        String codeText = raw.substring(at + MARK.length());
        int nl = codeText.indexOf('\n');
        if (nl >= 0) {
            codeText = codeText.substring(0, nl);
        }
        int code = 1;
        try {
            code = Integer.parseInt(codeText.trim());
        } catch (NumberFormatException ignored) {
            code = 1;
        }
        String[] lines = head.split("\n", -1);
        int last = lines.length - 1;
        while (last >= 0 && lines[last].trim().isEmpty()) {
            last--;
        }
        String cwd = previous;
        if (last >= 0 && lines[last].trim().startsWith("/")) {
            cwd = lines[last].trim();
            last--;
        }
        StringBuilder show = new StringBuilder();
        for (int i = 0; i <= last; i++) {
            if (show.length() > 0) {
                show.append('\n');
            }
            show.append(lines[i]);
        }
        if (show.length() > 0) {
            show.append('\n');
        }
        return new Answer(show.toString(), cwd, code);
    }

    /** The short folder shown beside the cursor. */
    public static String prompt(String cwd) {
        if (cwd == null || cwd.isEmpty() || "/root".equals(cwd) || "/root/".equals(cwd)) {
            return "~";
        }
        if (cwd.startsWith("/root/")) {
            return "~/" + cwd.substring("/root/".length());
        }
        return cwd;
    }

    public static final class Answer {
        public final String text;
        public final String cwd;
        public final int exit;

        Answer(String text, String cwd, int exit) {
            this.text = text;
            this.cwd = cwd;
            this.exit = exit;
        }
    }
}
