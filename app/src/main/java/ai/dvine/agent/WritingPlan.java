package ai.dvine.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Blueprint for long writing: Ivie's stories and scripts, Obi's reports. The piece is planned before it is
 * written, as code is:
 *
 * <pre>
 * 1 UNDERSTAND  what is asked: the kind of piece, who reads it, the tone, the length
 * 2 PLAN        an outline: the title, then numbered sections, each with what it must do
 * 3 CHECK       the outline scrutinised for loopholes (a gap in the story or the argument, a section out of order,
 *               something asked that no section covers) and corrected
 * 4 WRITE       each section in order, with the outline and what is already written, so nothing is said twice
 * 5 JOIN        the title and the sections, one piece
 * </pre>
 *
 * Pure Java: the prompts, and reading the outline back.
 */
public final class WritingPlan {
    private WritingPlan() {
    }

    public static final int MOST_SECTIONS = 8;

    /** A request for a long piece (a story, a script, a report) rather than a line or two. */
    public static boolean applies(String text) {
        String t = text == null ? "" : text.toLowerCase(Locale.US).trim();
        if (t.matches("(?s)^(what|why|how|who|when|where|is|are|explain|define)\\b.*")) {
            return false;
        }
        boolean longKind = t.matches("(?s).*\\b(story|short story|novel|chapter|script|screenplay|skit|play|report|analysis|business plan"
                + "|proposal|essay|article|white ?paper|case study|feasibility study|research paper|book)\\b.*");
        boolean asked = t.matches("(?s).*\\b(write|create|make|generate|compose|draft|prepare|produce)\\b.*");
        boolean shortOne = t.matches("(?s).*\\b(one line|one sentence|a line|short summary|tweet|caption|title)\\b.*");
        return longKind && asked && !shortOne;
    }

    /** Steps 1–2: the outline. */
    public static String outlinePrompt(String task) {
        return "Plan this piece before writing it. The request: " + task.trim() + "\n\nFirst understand it: the kind of piece,"
                + " who reads it, the tone and the length. Then give the outline in exactly this form and nothing else:\n"
                + "Title: <the title>\nReader: <who it is for>\nTone: <the tone>\nLength: <about how many words in all>\n"
                + "1. <section name>: <what this section must do or cover>\n2. …\n"
                + "At most " + MOST_SECTIONS + " sections. Cover everything the request asks for.";
    }

    /** Step 3: the outline checked for loopholes and given back corrected. */
    public static String checkPrompt(String task, String outline) {
        return "Scrutinise this outline before it is written. The request: " + task.trim() + "\n\nThe outline:\n" + outline.trim()
                + "\n\nLook for loopholes: anything the request asks that no section covers, a gap in the story or the"
                + " argument, a section out of order, two sections that repeat each other, a length that doesn't fit. Fix them"
                + " and give the corrected outline in the same form and nothing else.";
    }

    /** Step 4: one section, written with the whole plan and what is already written. */
    public static String sectionPrompt(String task, String outline, String written, int number, String section) {
        return "Write section " + number + " of this piece now: " + section + "\n\nThe request: " + task.trim() + "\n\nThe outline"
                + " (follow it; do not write other sections):\n" + outline.trim()
                + (written == null || written.trim().isEmpty() ? "" : "\n\nWhat is already written (carry on from it; do not repeat it):\n"
                        + tail(written, 6000))
                + "\n\nWrite only this section's text, with its heading if the piece uses headings.";
    }

    /**
     * Step 4 by several writers at once: one section, written from the whole plan while the other sections are written
     * by others at the same time. Each keeps to its own section so the joined piece reads straight through.
     */
    public static String sectionAlone(String task, String outline, int number, int of, String section) {
        return "Write section " + number + " of " + of + " of this piece now: " + section + "\n\nThe request: " + task.trim()
                + "\n\nThe outline (follow it; the other sections are being written at the same time by other writers, so"
                + " cover only yours, do not introduce or sum up the whole piece unless your section is the opening or the"
                + " close, and do not repeat what the outline gives to another section):\n" + outline.trim()
                + "\n\nWrite only this section's text, with its heading if the piece uses headings.";
    }

    private static final Pattern SECTION = Pattern.compile("(?m)^\\s*(\\d+)[.)]\\s*(.+)$");

    /** The sections of an outline, in order ("Opening: Ada finds the letter"). */
    public static List<String> sections(String outline) {
        List<String> out = new ArrayList<String>();
        Matcher m = SECTION.matcher(outline == null ? "" : outline);
        while (m.find() && out.size() < MOST_SECTIONS) {
            String s = m.group(2).replaceAll("\\*\\*", "").trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    /** The title in an outline, or null. */
    public static String title(String outline) {
        Matcher m = Pattern.compile("(?im)^\\s*\\**title\\**\\s*:\\s*(.+)$").matcher(outline == null ? "" : outline);
        return m.find() ? m.group(1).replaceAll("\\*\\*", "").trim() : null;
    }

    /** Step 5: one piece. */
    public static String join(String title, List<String> parts) {
        StringBuilder b = new StringBuilder();
        if (title != null && !title.isEmpty()) {
            b.append("# ").append(title).append("\n\n");
        }
        for (String p : parts) {
            if (p != null && !p.trim().isEmpty()) {
                b.append(p.trim()).append("\n\n");
            }
        }
        return b.toString().trim();
    }

    private static String tail(String s, int n) {
        return s.length() <= n ? s : "…" + s.substring(s.length() - n);
    }
}
