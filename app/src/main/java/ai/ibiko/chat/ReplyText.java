package ai.ibiko.chat;

/**
 * A model reply, ready to show: tool tags removed, paragraph breaks kept.
 * Spaces inside a line are tightened. Line breaks are not.
 */
public final class ReplyText {
    private ReplyText() {
    }

    /**
     * Where a tool call starts in a model's streamed text (its own tag, a function or invoke tag, a DSML block, a
     * special-token marker), or -1 when there is none. The writing before it is for the user; the call is not.
     */
    public static int toolStart(String text) {
        if (text == null) {
            return -1;
        }
        int at = -1;
        for (String mark : new String[] {"<tool", "<function", "<invoke", "<|", "\uFF5C", "<\uFF5C"}) {
            int i = text.indexOf(mark);
            if (i >= 0 && (at < 0 || i < at)) {
                at = i;
            }
        }
        java.util.regex.Matcher dsml = java.util.regex.Pattern.compile("(?i)<[^>]{0,20}dsml").matcher(text);
        if (dsml.find() && (at < 0 || dsml.start() < at)) {
            at = dsml.start();
        }
        java.util.regex.Matcher open = java.util.regex.Pattern.compile("^\\s*<[a-z_]+[\\s>]").matcher(text);
        if (open.find() && !text.trim().startsWith("<br") && !text.trim().matches("(?s)^<(p|b|i|h\\d|ul|ol|li|table|div|span)\\b.*")) {
            at = 0; // a reply that opens with a tag is a tool written as its own tag
        }
        return at;
    }

    public static String prepare(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replace("\r\n", "\n").replace('\r', '\n');
        s = s.replaceAll("(?s)<think>.*?(</think>|$)", "");
        s = stripDsml(s);
        s = s.replaceAll("(?s)<tool_call>.*?(</tool_call>|$)", "");
        s = s.replaceAll("(?s)<function\\b[^>]*>.*?</function>", "");
        s = s.replaceAll("(?s)<function\\b[^>]*/>", "");
        s = s.replaceAll("(?s)<invoke\\b[^>]*>.*?</invoke>", "");
        s = s.replaceAll("(?s)<tool\\b[^>]*>.*?</tool>", "");
        s = s.replaceAll("(?s)<tool\\b[^>]*/>", "");
        s = s.replaceAll("(?s)</?final>", "");
        // a gateway's own moderation or scoring tags are not the reply ("<severity>0</severity>")
        s = s.replaceAll("(?is)<(severity|score|moderation|safety|category|flag(?:ged)?|risk|confidence)>[^<]{0,80}</\\1>", "");
        s = ai.ibiko.suit.FunctionCode.hidden(s); // the brain's command codes are for Ibiko, never for the conversation
        s = MessageParts.normalizeFences(s);
        String[] lines = s.split("\n", -1);
        StringBuilder out = new StringBuilder();
        int blanks = 0;
        boolean inCode = false;
        for (String line : lines) {
            boolean fence = line.trim().startsWith("```");
            if (inCode && !fence) {
                // inside a code block every line is kept as written: indentation is the code's own
                out.append(out.length() == 0 ? "" : "\n").append(line.replace("\t", "    ").replaceAll("\\s+$", ""));
                continue;
            }
            if (fence) {
                inCode = !inCode;
            }
            // outside code, spaces are tightened; a line that looks like code keeps its indentation
            String t = CodeLanguage.codeLine(line) && !fence
                    ? line.replace("\t", "    ").replaceAll("\\s+$", "")
                    : line.replaceAll("[\\t ]+", " ").trim();
            if (t.trim().isEmpty()) {
                blanks++;
                continue;
            }
            if (out.length() > 0) {
                out.append(blanks > 0 ? "\n\n" : "\n");
            }
            blanks = 0;
            out.append(t);
        }
        String ready = out.toString().replaceAll("^\\s*\\n", "").replaceAll("\\s+$", "");
        if (ai.ibiko.linux.CloneFolder.progressLines(ready) >= 1) {
            ready = ai.ibiko.linux.CloneFolder.quiet(ready);
        }
        // a cut-off stream joined as "logic.Found": a sentence end then a capital is a new sentence
        if (!ready.contains("```")) {
            ready = ready.replaceAll("([a-z][.!?])([A-Z])", "$1\n\n$2");
        }
        return dropRepeat(ready);
    }

    /**
     * A reply that says itself twice ("A B C D A B C D…", the second copy often cut off): only the first copy is
     * kept. The paragraphs from the repeat on must match the opening ones in order (the last may be cut short).
     */
    public static String dropRepeat(String text) {
        if (text == null || text.length() < 80) {
            return text == null ? "" : text;
        }
        String[] p = text.split("\n{2,}");
        for (int k = 1; k < p.length; k++) {
            if (!same(p[k], p[0])) {
                continue;
            }
            boolean repeat = true;
            for (int j = 0; k + j < p.length; j++) {
                String a = p[k + j].trim(), b = j < k ? p[j].trim() : "";
                boolean last = k + j == p.length - 1;
                if (!(same(a, b) || last && a.length() >= 20 && b.startsWith(a))) {
                    repeat = false;
                    break;
                }
            }
            if (repeat) {
                StringBuilder out = new StringBuilder();
                for (int i = 0; i < k; i++) {
                    out.append(i == 0 ? "" : "\n\n").append(p[i]);
                }
                return out.toString();
            }
        }
        return text;
    }

    private static boolean same(String a, String b) {
        return a.trim().replaceAll("\\s+", " ").equals(b.trim().replaceAll("\\s+", " "));
    }

    /**
     * Whether {@code next} only repeats {@code earlier} (the same words, or a cut-off copy of them): a second copy of a
     * reply in the same turn is not shown.
     */
    public static boolean repeats(String earlier, String next) {
        if (earlier == null || next == null) {
            return false;
        }
        String a = earlier.trim().replaceAll("\\s+", " "), b = next.trim().replaceAll("\\s+", " ");
        return !b.isEmpty() && (a.equals(b) || b.length() >= 40 && a.startsWith(b));
    }

    /**
     * DeepSeek's tool block ({@code <|DSML|calls>…}, also written with a full-width bar). It is a tool call, not
     * something to show. A half-written one, cut off when the request failed, goes too.
     */
    static String stripDsml(String s) {
        if (s == null || s.isEmpty() || !s.toUpperCase(java.util.Locale.US).contains("DSML")) {
            return s == null ? "" : s;
        }
        String t = s.replace('\uFF5C', '|');
        t = t.replaceAll("(?is)<\\s*\\|\\s*DSML\\s*\\|\\s*calls\\s*>.*?(</\\s*\\|\\s*DSML\\s*\\|\\s*calls\\s*>|$)", "");
        t = t.replaceAll("(?is)<\\s*\\|\\s*DSML\\s*\\|\\s*invoke\\b.*?(</\\s*\\|\\s*DSML\\s*\\|\\s*invoke\\s*>|$)", "");
        t = t.replaceAll("(?is)</?\\s*\\|\\s*DSML\\s*\\|[^>]*>", "");
        return t;
    }
}
