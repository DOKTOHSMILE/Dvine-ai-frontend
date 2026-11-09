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
     * The script Alpine runs. It starts in {@code cwd}, runs the line, and writes the new folder and the
     * exit code to files. The command's own text is the only thing printed, so it can be shown as it arrives.
     */
    public static String wrap(String cwd, String line) {
        String dir = cwd != null && cwd.startsWith("/") ? cwd : "/root";
        return "cd " + quote(dir) + " || cd /root\n"
                + (line == null ? "" : line) + "\n"
                + "status=$?\n"
                + "pwd > /root/.dvine_cwd\n"
                + "echo $status > /root/.dvine_status\n"
                + "exit $status\n";
    }

    /**
     * A command for a shell that stays open but has no terminal device. The text goes to a file the phone
     * can read while it runs. The status file is written last, so its appearance means the command ended.
     */
    public static String pipe(String line) {
        return "eval " + quote(line) + " > /root/.dvine_out 2>&1\n"
                + "pwd > /root/.dvine_cwd\n"
                + "echo $? > /root/.dvine_status\n";
    }

    /**
     * Take finished lines out of text that is still arriving. A line {@code DVINE_STATUS:code__folder}
     * is the shell saying the command ended. A tail that might be the start of that line is kept in
     * {@link Stream#rest} and not shown yet.
     */
    public static Stream drain(String buffered, String previousCwd) {
        String previous = previousCwd == null || previousCwd.isEmpty() ? "/root" : previousCwd;
        if (buffered == null || buffered.isEmpty()) {
            return new Stream("", "", null, false, 0);
        }
        StringBuilder show = new StringBuilder();
        String cwd = null;
        boolean done = false;
        int exit = 0;
        int i = 0;
        while (true) {
            int nl = buffered.indexOf('\n', i);
            if (nl < 0) {
                String tail = buffered.substring(i);
                if (tail.startsWith(MARK) || MARK.startsWith(tail)) {
                    return new Stream(show.toString(), tail, cwd, done, exit);
                }
                show.append(tail);
                return new Stream(show.toString(), "", cwd, done, exit);
            }
            String line = buffered.substring(i, nl);
            i = nl + 1;
            if (line.startsWith(MARK)) {
                Answer answer = marker(line, previous);
                cwd = answer.cwd;
                exit = answer.exit;
                done = true;
            } else {
                show.append(line).append('\n');
            }
        }
    }

    /** {@code DVINE_STATUS:0__/usr} */
    static Answer marker(String line, String previous) {
        String body = line.substring(MARK.length());
        int sep = body.indexOf("__");
        int code = 1;
        String cwd = previous;
        if (sep >= 0) {
            try {
                code = Integer.parseInt(body.substring(0, sep).trim());
            } catch (NumberFormatException ignored) {
                code = 1;
            }
            String dir = body.substring(sep + 2).trim();
            if (dir.startsWith("/")) {
                cwd = dir;
            }
        }
        return new Answer("", cwd, code);
    }

    public static final class Stream {
        public final String show;
        public final String rest;
        public final String cwd;
        public final boolean done;
        public final int exit;

        Stream(String show, String rest, String cwd, boolean done, int exit) {
            this.show = show;
            this.rest = rest;
            this.cwd = cwd;
            this.done = done;
            this.exit = exit;
        }
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
