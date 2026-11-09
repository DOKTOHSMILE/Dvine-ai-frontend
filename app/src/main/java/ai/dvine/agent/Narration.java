package ai.dvine.agent;

import java.util.Locale;

/**
 * What a step was looking for and what it found, in one line for the chat, written by DVINE from the step itself
 * (not by the model), so long work is never silent: "Read PLAN.md (240 lines). It starts: # DVINE plan…", "Ran
 * git status: 3 files changed, first: app/build.gradle". Pure Java.
 */
public final class Narration {
    private Narration() {
    }

    /** The line for one step: {@code doing} is the step as shown ("Read PLAN.md"), {@code result} what came back. */
    public static String of(String doing, String result) {
        String d = doing == null ? "" : doing.trim();
        if (d.isEmpty()) {
            return null;
        }
        String r = result == null ? "" : result.trim();
        if (r.startsWith("Error") || r.toLowerCase(Locale.US).startsWith("not run") || d.startsWith("Not ")) {
            return d + ". It didn't work: " + firstLine(r.replaceFirst("^Error:?\\s*", ""), 160);
        }
        int lines = r.isEmpty() ? 0 : r.split("\n").length;
        String first = firstLine(r, 140);
        String low = d.toLowerCase(Locale.US);
        if (low.startsWith("read ")) {
            return d + " (" + lines + (lines == 1 ? " line)" : " lines)") + (first.isEmpty() ? "." : ". It starts: " + first);
        }
        if (low.startsWith("listed the files")) {
            return d + ": " + lines + (lines == 1 ? " entry" : " entries") + (first.isEmpty() ? "." : ", first " + first + ".");
        }
        if (low.startsWith("searched for")) {
            return d + ": " + (r.isEmpty() || r.toLowerCase(Locale.US).startsWith("no ") ? "nothing found." : lines
                    + (lines == 1 ? " match" : " matches") + ", first " + first + ".");
        }
        if (low.startsWith("edited") || low.startsWith("created") || low.startsWith("rewrote") || low.startsWith("made ")
                || low.startsWith("zipped") || low.startsWith("unzipped")) {
            return d + ".";
        }
        if (r.isEmpty()) {
            return d + ": nothing came back.";
        }
        return d + ": " + first + (lines > 1 ? " (" + lines + " lines)" : "");
    }

    static String firstLine(String text, int most) {
        for (String l : (text == null ? "" : text).split("\n")) {
            String t = l.trim().replaceAll("\\s+", " ");
            if (!t.isEmpty() && !t.matches("[-=*#`~_|+]+")) {
                return t.length() > most ? t.substring(0, most) + "…" : t;
            }
        }
        return "";
    }
}
