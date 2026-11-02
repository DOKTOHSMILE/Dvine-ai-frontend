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
        s = s.replaceAll("(?s)<tool_call>.*?(</tool_call>|$)", "");
        s = s.replaceAll("(?s)<function\\b[^>]*>.*?</function>", "");
        s = s.replaceAll("(?s)<function\\b[^>]*/>", "");
        s = s.replaceAll("(?s)<invoke\\b[^>]*>.*?</invoke>", "");
        s = s.replaceAll("(?s)<tool\\b[^>]*>.*?</tool>", "");
        s = s.replaceAll("(?s)<tool\\b[^>]*/>", "");
        s = s.replaceAll("(?s)</?final>", "");
        String[] lines = s.split("\n", -1);
        StringBuilder out = new StringBuilder();
        int blanks = 0;
        for (String line : lines) {
            String t = line.replaceAll("[\\t ]+", " ").trim();
            if (t.isEmpty()) {
                blanks++;
                continue;
            }
            if (out.length() > 0) {
                out.append(blanks > 0 ? "\n\n" : "\n");
            }
            blanks = 0;
            out.append(t);
        }
        return out.toString().trim();
    }
}
