package ai.ibiko.connect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What changed between two versions of a text file, as git shows it: lines added and removed, and the changed
 * places with three lines around them ("+ new", "- old", "  same"). A longest-common-subsequence diff; for very large
 * files only the counts are worked out (by line). Pure Java.
 */
public final class LineDiff {
    /** One file's changes: the counts and the lines to show. */
    public static final class Result {
        public final int added, removed;
        /** Each line starts with '+', '-', ' ' (context) or '@' (a jump between places). */
        public final List<String> lines;

        Result(int added, int removed, List<String> lines) {
            this.added = added;
            this.removed = removed;
            this.lines = lines;
        }
    }

    static final int CONTEXT = 3;
    static final long MAX_CELLS = 4_000_000L;

    private LineDiff() {
    }

    public static Result of(String before, String after) {
        String[] a = split(before), b = split(after);
        // the same start and end are left out first: most edits touch a small part
        int start = 0;
        while (start < a.length && start < b.length && a[start].equals(b[start])) {
            start++;
        }
        int endA = a.length, endB = b.length;
        while (endA > start && endB > start && a[endA - 1].equals(b[endB - 1])) {
            endA--;
            endB--;
        }
        int n = endA - start, m = endB - start;
        if ((long) n * m > MAX_CELLS) {
            return counts(a, b, start, endA, endB);
        }
        // LCS table on the middle
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = a[start + i].equals(b[start + j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<String> all = new ArrayList<String>();
        for (int i = 0; i < start; i++) {
            all.add(" " + a[i]);
        }
        int i = 0, j = 0, added = 0, removed = 0;
        while (i < n || j < m) {
            if (i < n && j < m && a[start + i].equals(b[start + j])) {
                all.add(" " + a[start + i]);
                i++;
                j++;
            } else if (j < m && (i == n || lcs[i][j + 1] >= lcs[i + 1][j])) {
                all.add("+" + b[start + j]);
                j++;
                added++;
            } else {
                all.add("-" + a[start + i]);
                i++;
                removed++;
            }
        }
        for (int k = endA; k < a.length; k++) {
            all.add(" " + a[k]);
        }
        return new Result(added, removed, hunks(all));
    }

    /** Only the changed places, with {@link #CONTEXT} lines either side. */
    private static List<String> hunks(List<String> all) {
        boolean[] keep = new boolean[all.size()];
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).charAt(0) != ' ') {
                for (int k = Math.max(0, i - CONTEXT); k <= Math.min(all.size() - 1, i + CONTEXT); k++) {
                    keep[k] = true;
                }
            }
        }
        List<String> out = new ArrayList<String>();
        boolean gap = false;
        int line = 0;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).charAt(0) != '+') {
                line++;
            }
            if (keep[i]) {
                if (gap || (out.isEmpty() && i > 0)) {
                    out.add("@ line " + line);
                }
                out.add(all.get(i));
                gap = false;
            } else {
                gap = true;
            }
        }
        return out;
    }

    /** Too big to diff in full: which lines are new and which are gone, counted. */
    private static Result counts(String[] a, String[] b, int start, int endA, int endB) {
        Map<String, Integer> seen = new HashMap<String, Integer>();
        for (int i = start; i < endA; i++) {
            seen.merge(a[i], 1, Integer::sum);
        }
        int added = 0;
        for (int j = start; j < endB; j++) {
            Integer c = seen.get(b[j]);
            if (c == null || c == 0) {
                added++;
            } else {
                seen.put(b[j], c - 1);
            }
        }
        int removed = 0;
        for (int c : seen.values()) {
            removed += c;
        }
        List<String> note = new ArrayList<String>();
        note.add("@ too large to show line by line");
        return new Result(added, removed, note);
    }

    private static String[] split(String s) {
        if (s == null || s.isEmpty()) {
            return new String[0];
        }
        String t = s.endsWith("\n") ? s.substring(0, s.length() - 1) : s;
        return t.split("\n", -1);
    }
}
