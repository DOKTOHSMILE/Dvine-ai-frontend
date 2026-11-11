package ai.ibiko.agent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The map for a deep diagnosis (an audit, a review, a bug hunt, "why does…"): evidence first, so the report claims
 * nothing that isn't so. An earlier audit stated things that weren't true (code it hadn't opened, functions it said were
 * missing that were there); this keeps that from happening twice. The agent is given the map (D1–D10) and the report
 * format, and after it writes the report every claim that names a file and line is checked against the files: the file
 * must exist, the line must exist, a quoted line must really be there, and the file must have been read in this run. A
 * report with claims that fail is sent back once to be corrected, and what was checked is said at the end of it.
 */
public final class Diagnosis {
    private Diagnosis() {
    }

    /** An audit, review, diagnosis or bug hunt: the work this map is for. */
    public static boolean applies(String task) {
        String t = task == null ? "" : task.toLowerCase(Locale.US);
        return t.matches("(?s).*\\b(audit|review|diagnos\\w*|investigat\\w*|debug|root cause|find (all |every |the )?(bugs?|issues|problems)"
                + "|what'?s (wrong|broken)|why (does|is|did|won'?t|isn'?t|doesn'?t|can'?t)|crash(es|ing)?|broken|not working"
                + "|security (check|review)|code review|health check)\\b.*");
    }

    /** The guiding map, for the agent's instructions. */
    public static final String MAP = "\nDEEP DIAGNOSIS MAP (this is an audit, review or diagnosis: follow it exactly; a wrong claim is"
            + " worse than a missing one):\n"
            + "D1 Scope: first say what you will examine (folders, files, features) and how; at the end, what you did not.\n"
            + "D2 Evidence first: open and read the code before you say anything about it. Every finding names the file and line"
            + " you read in THIS session as path:line and quotes that line in backticks, exactly as it is in the file.\n"
            + "D3 Status: mark each finding OBSERVED (seen in code or output you read), REPRODUCED (you ran it and saw it fail,"
            + " output quoted) or SUSPECTED (inferred: say what would confirm it). Never write a SUSPECTED finding as a fact.\n"
            + "D4 Not opened, not claimed: about a file you did not read, say only \"not checked\".\n"
            + "D5 Absence needs a search: before you say something is missing, unused, never called or never set, search for it"
            + " with the search tool and say what you searched and what came back.\n"
            + "D6 Reproduce when you can: run the test, build or command, and quote the real output; never describe output you"
            + " did not see.\n"
            + "D7 Root cause: trace from the symptom to the cause through the code path, naming each step (caller to callee).\n"
            + "D8 Severity with a reason: critical, high, medium or low, from the impact and how likely it is.\n"
            + "D9 Each fix says how to check it worked (the test or command, and what it should show).\n"
            + "D10 Before the report: re-read every line you cite. Drop or correct any finding that does not match, and"
            + " downgrade any you cannot back to SUSPECTED.\n"
            + "REPORT FORMAT:\n"
            + "## Scope\n## Findings  (most severe first; each one: **[SEVERITY] title** - STATUS - `path:line` - the quoted line -"
            + " why it matters - the fix - how to verify)\n## Not checked\n## Summary\n";

    /** What the check found in a report. */
    public static final class Check {
        public int cited, verified;
        public final List<String> problems = new ArrayList<String>();
        /** Findings that name no file and line. */
        public int unsupported;

        public boolean clean() {
            return problems.isEmpty();
        }
    }

    private static final Pattern CITE = Pattern.compile("([A-Za-z0-9_./-]+\\.[A-Za-z0-9]{1,6}):(\\d+)(?:\\s*[-\u2013]\\s*(\\d+))?");
    private static final Pattern QUOTE = Pattern.compile("`([^`\\n]{6,200})`");

    /**
     * Checks each "path:line" in the report: the file is found in the workspace (by its path, or by its name when the
     * report shortened the path), the line exists, a quoted line next to it is in the file around that line, and the
     * file was read in this run ({@code read}: the paths the agent opened; empty when not known).
     */
    public static Check verify(String report, File workspace, Set<String> read) {
        Check c = new Check();
        if (report == null) {
            return c;
        }
        Matcher m = CITE.matcher(report);
        java.util.Set<String> seen = new java.util.HashSet<String>();
        while (m.find()) {
            String path = m.group(1);
            if (path.matches("(?i).*\\.(com|org|net|io|dev)$") || path.startsWith("http")) {
                continue; // an address, not a file
            }
            int line = Integer.parseInt(m.group(2));
            if (!seen.add(path + ":" + line)) {
                continue;
            }
            c.cited++;
            File f = find(workspace, path);
            if (f == null) {
                c.problems.add(path + ":" + line + " - no such file in the workspace");
                continue;
            }
            List<String> lines;
            try {
                lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                c.problems.add(path + ":" + line + " - the file couldn't be read");
                continue;
            }
            if (line < 1 || line > lines.size()) {
                c.problems.add(path + ":" + line + " - the file has only " + lines.size() + " lines");
                continue;
            }
            if (read != null && !read.isEmpty() && !wasRead(read, path, f, workspace)) {
                c.problems.add(path + ":" + line + " - this file was not opened in this session (D2, D4)");
                continue;
            }
            String near = report.substring(m.end(), Math.min(report.length(), m.end() + 240));
            Matcher q = QUOTE.matcher(near);
            if (q.find() && !CITE.matcher(q.group(1)).matches()) {
                String quoted = squash(q.group(1));
                boolean there = false;
                for (int i = Math.max(0, line - 4); i < Math.min(lines.size(), line + 3) && !there; i++) {
                    there = squash(lines.get(i)).contains(quoted) || quoted.contains(squash(lines.get(i))) && squash(lines.get(i)).length() > 8;
                }
                if (!there) {
                    c.problems.add(path + ":" + line + " - the quoted code `" + clip(q.group(1)) + "` is not at that line");
                    continue;
                }
            }
            c.verified++;
        }
        // findings that cite nothing: a line under Findings that is a finding but has no path:line
        int at = report.indexOf("## Findings");
        if (at >= 0) {
            int end = report.indexOf("\n## ", at + 5);
            String findings = report.substring(at, end < 0 ? report.length() : end);
            for (String l : findings.split("\n")) {
                if (l.matches("(?i)^\\s*(\\d+[.)]|[-*])?\\s*\\*\\*\\[(critical|high|medium|low)].*") && !CITE.matcher(l).find()
                        && !l.toUpperCase(Locale.US).contains("SUSPECTED")) {
                    c.unsupported++;
                }
            }
            if (c.unsupported > 0) {
                c.problems.add(c.unsupported + " finding" + (c.unsupported == 1 ? "" : "s") + " name no file and line and"
                        + " aren't marked SUSPECTED (D2, D3)");
            }
        }
        return c;
    }

    /** The prompt that sends a report back once to be corrected (D10). */
    public static String correction(Check c) {
        return "Before this report goes to the user, these claims were checked against the files and do not hold:\n- "
                + String.join("\n- ", c.problems) + "\nRe-read what you need with the tools, or, if you can't, correct the report"
                + " now: fix each of these claims, drop it, or mark it SUSPECTED with what would confirm it. Keep the same format."
                + " Write the whole corrected report.";
    }

    /** The note at the end of the report: what was checked, so the user knows how far to trust it. */
    public static String note(Check c) {
        if (c.cited == 0 && c.unsupported == 0) {
            return "";
        }
        StringBuilder b = new StringBuilder("\n\n## Checked against the files\n");
        b.append(c.verified).append(" of ").append(c.cited).append(" file and line reference")
                .append(c.cited == 1 ? "" : "s").append(" checked: the file exists, the line exists, the quoted code is there and"
                        + " the file was read in this session.");
        if (!c.problems.isEmpty()) {
            b.append(" Still not matching, treat these as unconfirmed:\n- ").append(String.join("\n- ", c.problems));
        }
        return b.toString();
    }

    private static File find(File workspace, String path) {
        if (workspace == null) {
            return null;
        }
        File direct = new File(workspace, path);
        if (direct.isFile()) {
            return direct;
        }
        // a shortened path ("MainActivity.java:120", "src/Foo.java"): the one file in the workspace that ends with it
        String tail = path.replace('\\', '/');
        File[] found = new File[1];
        int[] count = new int[1];
        walk(workspace, tail, found, count, 0);
        return count[0] == 1 ? found[0] : null;
    }

    private static void walk(File dir, String tail, File[] found, int[] count, int depth) {
        File[] kids = dir.listFiles();
        if (kids == null || depth > 14 || count[0] > 1) {
            return;
        }
        for (File k : kids) {
            String n = k.getName();
            if (k.isDirectory()) {
                if (!n.equals(".git") && !n.equals("build") && !n.equals("node_modules") && !n.startsWith(".gradle")) {
                    walk(k, tail, found, count, depth + 1);
                }
            } else if (k.getPath().replace('\\', '/').endsWith("/" + tail) || n.equals(tail)) {
                found[0] = k;
                count[0]++;
            }
        }
    }

    private static boolean wasRead(Set<String> read, String path, File f, File workspace) {
        String rel = workspace == null ? f.getPath() : workspace.toPath().relativize(f.toPath()).toString().replace('\\', '/');
        for (String r : read) {
            String x = r.replace('\\', '/').replaceAll("^\\./", "");
            if (x.equals(rel) || rel.endsWith("/" + x) || x.endsWith("/" + path) || x.equals(path) || rel.equals(x)) {
                return true;
            }
        }
        return false;
    }

    private static String squash(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String clip(String s) {
        return s.length() > 60 ? s.substring(0, 60) + "\u2026" : s;
    }
}
