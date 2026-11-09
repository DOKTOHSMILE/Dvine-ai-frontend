package ai.dvine.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A file read as the agent sees it ("Lines 1–130 of 861 …" then "1&lt;tab&gt;package …" per line), split into what a
 * code viewer draws: the note above, the line numbers for the gutter, and the code itself. A line the reader
 * didn't number (one that ran on) gets no number. Pure Java.
 */
public final class NumberedCode {
    private static final Pattern NUMBERED = Pattern.compile("^\\s*(\\d{1,7})\\t(.*)$");

    /** The words above the code ("Lines 1–130 of 861 in this file…"), or "" when there are none. */
    public final String note;
    /** Each line's number, or 0 for a line that had none. */
    public final List<Integer> numbers = new ArrayList<Integer>();
    public final List<String> code = new ArrayList<String>();

    private NumberedCode(String note) {
        this.note = note;
    }

    /** True when at least one line carried its number. */
    public boolean numbered() {
        for (int n : numbers) {
            if (n > 0) {
                return true;
            }
        }
        return false;
    }

    /** The gutter as one block: each number right-aligned to the widest, one per line. */
    public String gutter() {
        int widest = 1;
        for (int n : numbers) {
            widest = Math.max(widest, String.valueOf(n).length());
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < numbers.size(); i++) {
            String s = numbers.get(i) > 0 ? String.valueOf(numbers.get(i)) : "";
            for (int pad = s.length(); pad < widest; pad++) {
                b.append(' ');
            }
            b.append(s);
            if (i < numbers.size() - 1) {
                b.append('\n');
            }
        }
        return b.toString();
    }

    /** The code as one block, tabs as four spaces so columns line up. */
    public String source() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < code.size(); i++) {
            b.append(code.get(i).replace("\t", "    "));
            if (i < code.size() - 1) {
                b.append('\n');
            }
        }
        return b.toString();
    }

    /**
     * Splits the lines. {@code prefixed}: each line starts with the one-character mark a step detail keeps (a space
     * for an unchanged line), which is dropped first. At most {@code most} code lines are kept (0 = all).
     */
    public static NumberedCode of(List<String> lines, boolean prefixed, int most) {
        StringBuilder note = new StringBuilder();
        List<String> rest = new ArrayList<String>();
        boolean started = false;
        for (String raw : lines == null ? new ArrayList<String>() : lines) {
            String l = raw == null ? "" : raw;
            if (prefixed && !l.isEmpty()) {
                l = l.substring(1);
            }
            if (!started && !NUMBERED.matcher(l).matches()) {
                if (note.length() > 0) {
                    note.append(' ');
                }
                note.append(l.trim());
                continue;
            }
            started = true;
            rest.add(l);
        }
        NumberedCode c = new NumberedCode(started ? note.toString().trim() : "");
        if (!started) {
            rest.clear(); // nothing numbered: the lines are the code, unnumbered
            for (String raw : lines == null ? new ArrayList<String>() : lines) {
                String l = raw == null ? "" : raw;
                rest.add(prefixed && !l.isEmpty() ? l.substring(1) : l);
            }
        }
        for (String l : rest) {
            if (most > 0 && c.code.size() >= most) {
                break;
            }
            Matcher m = NUMBERED.matcher(l);
            if (m.matches()) {
                c.numbers.add(Integer.parseInt(m.group(1)));
                c.code.add(m.group(2));
            } else {
                c.numbers.add(0);
                c.code.add(l);
            }
        }
        return c;
    }

    /** The number of code lines in {@code lines} (the note left out). */
    public static int count(List<String> lines, boolean prefixed) {
        return of(lines, prefixed, 0).code.size();
    }
}
