package ai.ibiko.agent;

/**
 * A coding agent's saved work, kept small enough to carry into the next round.
 * {@link String#trim()} copies the whole log; a log of about 30 MB does not fit in the phone's 256 MB heap twice,
 * which is the crash in {@code CodeAgent.startBlueprint}. These methods copy only the short pieces they keep.
 */
public final class WorkLog {
    private WorkLog() {
    }

    /** True when there is nothing but whitespace. Does not copy {@code s}. */
    public static boolean blank(CharSequence s) {
        if (s == null || s.length() == 0) {
            return true;
        }
        for (int i = 0, n = s.length(); i < n; i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The task line and the latest steps, inside {@code budget} characters. A longer log is not copied whole:
     * only those two pieces are.
     */
    public static String kept(CharSequence all, int budget) {
        if (all == null || all.length() == 0) {
            return "";
        }
        int n = all.length();
        int cap = Math.max(1000, budget);
        if (n <= cap) {
            return blank(all) ? "" : all.toString();
        }
        int at = taskAt(all);
        int headStart = 0;
        int headEnd = 0;
        if (at >= 0) {
            int limit = Math.min(n, at + cap / 3);
            int nl = indexOf(all, '\n', at + 6, limit);
            int lineEnd = nl < 0 ? limit : nl + 1;
            if (at <= cap / 5) {
                headEnd = Math.min(lineEnd, cap / 3);
            } else {
                headStart = at;
                headEnd = lineEnd;
            }
        }
        int headLen = headEnd - headStart;
        int keep = cap - headLen - 32;
        if (keep < 400) {
            keep = Math.min(400, cap / 2);
        }
        int tailFrom = n - keep;
        if (tailFrom < headEnd) {
            tailFrom = headEnd;
        }
        String head = headEnd > headStart ? all.subSequence(headStart, headEnd).toString() : "";
        String tail = tailFrom < n ? all.subSequence(tailFrom, n).toString() : "";
        if (head.isEmpty()) {
            return tail;
        }
        if (tailFrom <= headEnd) {
            return head;
        }
        return head + "\n…(earlier steps left out)…\n" + tail;
    }

    /** The first "Task: " at the start of a line, or -1. */
    private static int taskAt(CharSequence all) {
        int n = all.length();
        for (int i = 0; i + 6 <= n; i++) {
            if ((i == 0 || all.charAt(i - 1) == '\n') && starts(all, "Task: ", i)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean starts(CharSequence s, String p, int i) {
        if (i + p.length() > s.length()) {
            return false;
        }
        for (int k = 0; k < p.length(); k++) {
            if (s.charAt(i + k) != p.charAt(k)) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(CharSequence s, char c, int from, int to) {
        int end = Math.min(s.length(), to);
        for (int i = from; i < end; i++) {
            if (s.charAt(i) == c) {
                return i;
            }
        }
        return -1;
    }
}
