package ai.dvine.linux;

/**
 * Reads one chat command's output out of the terminal's stream. The command is run from a small script that prints a
 * line {@code DVINE_BEGIN:} before it and {@code DVINE_STATUS:code__folder} after it. Those two lines are taken out
 * of what the screen shows; the text between them is what chat gets. Pure Java.
 */
public final class ChatCapture {
    public static final String BEGIN = "DVINE_BEGIN:";

    private final StringBuilder hold = new StringBuilder();
    private final StringBuilder out = new StringBuilder();
    private boolean began, done;
    private int exit;
    private String cwd;

    /** The script chat's command is run from. {@code shown} is the prompt and command, drawn over the typed line. */
    public static String script(String command, String shown) {
        return "printf '\\033[1A\\r\\033[2K%s\\n' " + Guest.quote(shown == null ? "" : shown) + "\n"
                + "echo " + BEGIN + "\n"
                // no pager and no keyboard for a command chat runs: it must print and finish on its own
                + "__dvp=${PAGER-}; __dvg=${GIT_PAGER-}; export PAGER=cat GIT_PAGER=cat\n"
                + "eval " + Guest.quote(command) + " </dev/null\n"
                + "__dvs=$?\n"
                + "PAGER=$__dvp; GIT_PAGER=$__dvg\n"
                + "[ -n \"$PAGER\" ] || unset PAGER\n"
                + "[ -n \"$GIT_PAGER\" ] || unset GIT_PAGER\n"
                + "echo \"" + Guest.MARK + "${__dvs}__$(pwd)\"\n";
    }

    /** More of the stream. Returns the part the screen should show. */
    public synchronized String feed(String chunk) {
        if (done) {
            return chunk == null ? "" : chunk;
        }
        hold.append(chunk == null ? "" : chunk);
        StringBuilder show = new StringBuilder();
        while (!done) {
            String want = began ? Guest.MARK : BEGIN;
            int at = hold.indexOf(want);
            // the marker counts only at the start of a line: the typed command is echoed with it mid-line
            while (at > 0 && hold.charAt(at - 1) != '\n' && hold.charAt(at - 1) != '\r') {
                at = hold.indexOf(want, at + 1);
            }
            if (at < 0) {
                int keep = suffixThatStarts(want);
                emit(hold.substring(0, hold.length() - keep), show);
                hold.delete(0, hold.length() - keep);
                break;
            }
            int nl = hold.indexOf("\n", at);
            if (nl < 0) {
                emit(hold.substring(0, at), show);
                hold.delete(0, at);
                break;
            }
            emit(hold.substring(0, at), show);
            String line = hold.substring(at, nl).replace("\r", "");
            hold.delete(0, nl + 1);
            if (!began) {
                began = true;
            } else {
                Guest.Answer a = Guest.marker(line, "/root");
                exit = a.exit;
                cwd = a.cwd;
                done = true;
                show.append(hold);
                hold.setLength(0);
            }
        }
        return show.toString();
    }

    private void emit(String text, StringBuilder show) {
        show.append(text);
        if (began) {
            out.append(text);
        }
    }

    /** How much of the end of the held text could be the start of {@code want} at the start of a line. */
    private int suffixThatStarts(String want) {
        int max = Math.min(want.length(), hold.length());
        for (int n = max; n > 0; n--) {
            int from = hold.length() - n;
            if (want.startsWith(hold.substring(from)) && (from == 0 || hold.charAt(from - 1) == '\n' || hold.charAt(from - 1) == '\r')) {
                return n;
            }
        }
        return 0;
    }

    public synchronized boolean done() {
        return done;
    }

    public synchronized int exit() {
        return exit;
    }

    public synchronized String cwd() {
        return cwd;
    }

    /** What the command printed so far, as plain text: no colors, no cursor codes. */
    public synchronized String text() {
        return plain(out.toString());
    }

    /** Terminal codes taken out of program output. */
    public static String plain(String raw) {
        return raw.replaceAll("\u001b\\][^\u0007\u001b]*(\u0007|\u001b\\\\)", "")
                .replaceAll("\u001b\\[[0-9;?<=>:]*[ -/]*[@-~]", "")
                .replaceAll("\u001b[()][0-9A-Za-z]", "")
                .replaceAll("\u001b.", "")
                .replace("\r\n", "\n").replace("\r", "\n").replaceAll("[\u0000-\u0008\u000b-\u001f]", "");
    }
}
