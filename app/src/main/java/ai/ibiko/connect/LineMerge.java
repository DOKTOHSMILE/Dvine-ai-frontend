package ai.ibiko.connect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A three-way line merge, the way git merges a file: the common version, ours, and theirs. Edits that do not touch
 * the same lines are combined. Edits that touch the same lines are left as conflict markers. Pure Java, and it runs
 * on the phone so a server is not asked to merge the file.
 */
public final class LineMerge {
    public static final class Result {
        public final String text;
        public final int conflicts;

        Result(String text, int conflicts) {
            this.text = text;
            this.conflicts = conflicts;
        }

        public boolean clean() {
            return conflicts == 0;
        }
    }

    private static final class Seg {
        final int b0, b1, o0, o1;
        final boolean stable;

        Seg(int b0, int b1, int o0, int o1, boolean stable) {
            this.b0 = b0;
            this.b1 = b1;
            this.o0 = o0;
            this.o1 = o1;
            this.stable = stable;
        }
    }

    private LineMerge() {
    }

    public static Result of(String base, String ours, String theirs) {
        return of(base, ours, theirs, "yours", "theirs");
    }

    /** {@code oursName} and {@code theirsName} are written into the conflict markers. */
    public static Result of(String base, String ours, String theirs, String oursName, String theirsName) {
        String bText = base == null ? "" : base;
        String oText = ours == null ? "" : ours;
        String tText = theirs == null ? "" : theirs;
        boolean nl = endsNl(bText) || endsNl(oText) || endsNl(tText);
        if (oText.equals(tText)) {
            return new Result(finish(oText, nl), 0);
        }
        String[] b = split(bText), o = split(oText), t = split(tText);
        if (tooWide(b, o) || tooWide(b, t)) {
            return marked(o, t, oursName, theirsName, nl);
        }
        List<Seg> so = align(b, o);
        List<Seg> st = align(b, t);
        Set<Seg> used = new HashSet<Seg>();
        List<String> out = new ArrayList<String>();
        int conflicts = 0;
        int pos = 0;
        int guard = 0;
        int limit = b.length + o.length + t.length + 8;
        while (pos <= b.length && guard++ < limit) {
            Seg aIns = insertAt(so, pos, used);
            Seg cIns = insertAt(st, pos, used);
            if (aIns != null || cIns != null) {
                if (aIns != null) {
                    used.add(aIns);
                }
                if (cIns != null) {
                    used.add(cIns);
                }
                if (aIns != null && cIns != null) {
                    List<String> left = copy(o, aIns.o0, aIns.o1);
                    List<String> right = copy(t, cIns.o0, cIns.o1);
                    if (join(left).equals(join(right))) {
                        out.addAll(left);
                    } else {
                        conflicts++;
                        mark(out, left, right, oursName, theirsName);
                    }
                } else if (aIns != null) {
                    out.addAll(copy(o, aIns.o0, aIns.o1));
                } else {
                    out.addAll(copy(t, cIns.o0, cIns.o1));
                }
                continue;
            }
            if (pos >= b.length) {
                break;
            }
            Seg aCh = cover(so, pos);
            Seg cCh = cover(st, pos);
            if (aCh == null && cCh == null) {
                out.add(b[pos]);
                pos++;
                continue;
            }
            int lo = pos;
            int hi = pos;
            if (aCh != null) {
                hi = Math.max(hi, aCh.b1);
            }
            if (cCh != null) {
                hi = Math.max(hi, cCh.b1);
            }
            boolean grew = true;
            while (grew) {
                grew = false;
                for (Seg s : so) {
                    if (s.stable || !overlaps(s, lo, hi)) {
                        continue;
                    }
                    int nlo = Math.min(lo, s.b0);
                    int nhi = Math.max(hi, s.b1);
                    if (nlo != lo || nhi != hi) {
                        lo = nlo;
                        hi = nhi;
                        grew = true;
                    }
                }
                for (Seg s : st) {
                    if (s.stable || !overlaps(s, lo, hi)) {
                        continue;
                    }
                    int nlo = Math.min(lo, s.b0);
                    int nhi = Math.max(hi, s.b1);
                    if (nlo != lo || nhi != hi) {
                        lo = nlo;
                        hi = nhi;
                        grew = true;
                    }
                }
            }
            if (hi <= lo) {
                pos++;
                continue;
            }
            boolean touchA = touches(so, lo, hi);
            boolean touchB = touches(st, lo, hi);
            List<String> left = slice(so, o, lo, hi);
            List<String> right = slice(st, t, lo, hi);
            if (touchA && touchB) {
                if (join(left).equals(join(right))) {
                    out.addAll(left);
                } else {
                    conflicts++;
                    mark(out, left, right, oursName, theirsName);
                }
            } else if (touchA) {
                out.addAll(left);
            } else {
                out.addAll(right);
            }
            pos = hi;
        }
        return new Result(finish(join(out), nl), conflicts);
    }

    private static boolean tooWide(String[] a, String[] b) {
        return (long) a.length * Math.max(b.length, 1) > LineDiff.MAX_CELLS;
    }

    private static Result marked(String[] o, String[] t, String oursName, String theirsName, boolean nl) {
        List<String> out = new ArrayList<String>();
        mark(out, java.util.Arrays.asList(o), java.util.Arrays.asList(t), oursName, theirsName);
        return new Result(finish(join(out), nl), 1);
    }

    private static void mark(List<String> out, List<String> left, List<String> right, String oursName, String theirsName) {
        out.add("<<<<<<< " + (oursName == null || oursName.isEmpty() ? "yours" : oursName));
        out.addAll(left);
        out.add("=======");
        out.addAll(right);
        out.add(">>>>>>> " + (theirsName == null || theirsName.isEmpty() ? "theirs" : theirsName));
    }

    /** Longest-common-subsequence alignment of {@code base} with {@code other}, as stable and changed spans. */
    private static List<Seg> align(String[] base, String[] other) {
        int start = 0;
        while (start < base.length && start < other.length && base[start].equals(other[start])) {
            start++;
        }
        int endB = base.length, endO = other.length;
        while (endB > start && endO > start && base[endB - 1].equals(other[endO - 1])) {
            endB--;
            endO--;
        }
        List<Seg> segs = new ArrayList<Seg>();
        if (start > 0) {
            segs.add(new Seg(0, start, 0, start, true));
        }
        int n = endB - start, m = endO - start;
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = base[start + i].equals(other[start + j])
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        int i = 0, j = 0, cb = -1, co = -1;
        while (i < n || j < m) {
            if (i < n && j < m && base[start + i].equals(other[start + j])) {
                if (cb >= 0) {
                    segs.add(new Seg(cb, start + i, co, start + j, false));
                    cb = -1;
                }
                int b0 = start + i, o0 = start + j;
                while (i < n && j < m && base[start + i].equals(other[start + j])) {
                    i++;
                    j++;
                }
                segs.add(new Seg(b0, start + i, o0, start + j, true));
            } else if (j < m && (i == n || lcs[i][j + 1] >= lcs[i + 1][j])) {
                if (cb < 0) {
                    cb = start + i;
                    co = start + j;
                }
                j++;
            } else {
                if (cb < 0) {
                    cb = start + i;
                    co = start + j;
                }
                i++;
            }
        }
        if (cb >= 0) {
            segs.add(new Seg(cb, start + i, co, start + j, false));
        }
        if (endB < base.length) {
            segs.add(new Seg(endB, base.length, endO, other.length, true));
        }
        return segs;
    }

    private static Seg insertAt(List<Seg> segs, int pos, Set<Seg> used) {
        for (Seg s : segs) {
            if (!used.contains(s) && !s.stable && s.b0 == s.b1 && s.b0 == pos) {
                return s;
            }
        }
        return null;
    }

    private static Seg cover(List<Seg> segs, int pos) {
        for (Seg s : segs) {
            if (!s.stable && s.b0 <= pos && pos < s.b1) {
                return s;
            }
        }
        return null;
    }

    private static boolean overlaps(Seg s, int lo, int hi) {
        if (s.b0 == s.b1) {
            return s.b0 >= lo && s.b0 < hi;
        }
        return s.b0 < hi && s.b1 > lo;
    }

    private static boolean touches(List<Seg> segs, int lo, int hi) {
        for (Seg s : segs) {
            if (!s.stable && overlaps(s, lo, hi)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> slice(List<Seg> segs, String[] lines, int lo, int hi) {
        List<String> out = new ArrayList<String>();
        for (Seg s : segs) {
            if (s.b0 == s.b1) {
                if (!s.stable && s.b0 > lo && s.b0 < hi) {
                    out.addAll(copy(lines, s.o0, s.o1));
                }
                continue;
            }
            if (s.b1 <= lo || s.b0 >= hi) {
                continue;
            }
            if (s.stable) {
                int from = Math.max(s.b0, lo);
                int to = Math.min(s.b1, hi);
                if (from < to) {
                    out.addAll(copy(lines, s.o0 + (from - s.b0), s.o0 + (to - s.b0)));
                }
            } else if (s.b0 >= lo && s.b1 <= hi) {
                out.addAll(copy(lines, s.o0, s.o1));
            }
        }
        return out;
    }

    private static List<String> copy(String[] lines, int from, int to) {
        List<String> out = new ArrayList<String>();
        for (int i = from; i < to && i < lines.length; i++) {
            out.add(lines[i]);
        }
        return out;
    }

    private static String join(List<String> lines) {
        return String.join("\n", lines);
    }

    private static String finish(String text, boolean nl) {
        if (text.isEmpty()) {
            return nl ? "\n" : "";
        }
        if (nl && !text.endsWith("\n")) {
            return text + "\n";
        }
        return text;
    }

    private static boolean endsNl(String s) {
        return s.endsWith("\n");
    }

    private static String[] split(String s) {
        if (s.isEmpty()) {
            return new String[0];
        }
        String t = s.endsWith("\n") ? s.substring(0, s.length() - 1) : s;
        return t.split("\n", -1);
    }
}
