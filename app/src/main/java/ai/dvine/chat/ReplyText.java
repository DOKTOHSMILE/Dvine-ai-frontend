package ai.dvine.chat;

/**
 * A model reply, ready to show: tool tags removed, paragraph breaks kept.
 * Spaces inside a line are tightened. Line breaks are not.
 */
public final class ReplyText {
    private ReplyText() {
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
        s = ai.dvine.suit.FunctionCode.hidden(s); // the brain's command codes are for DVINE, never for the conversation
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
        if (ai.dvine.linux.CloneFolder.progressLines(ready) >= 2) {
            ready = ai.dvine.linux.CloneFolder.quiet(ready);
        }
        return ready;
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
