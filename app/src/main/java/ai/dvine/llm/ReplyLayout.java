package ai.dvine.llm;

/**
 * One model reply stays one answer. Shell lines are gathered into a single code block
 * so the chat can open them in the code viewer instead of a bubble each.
 */
public final class ReplyLayout {
    private ReplyLayout() {
    }

    public static String join(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace("\r\n", "\n").trim();
        if (text.isEmpty()) {
            return "";
        }
        if (text.contains("```")) {
            return text;
        }
        String[] lines = text.split("\n");
        StringBuilder prose = new StringBuilder();
        StringBuilder code = new StringBuilder();
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) {
                if (prose.length() > 0 && !prose.toString().endsWith("\n")) {
                    prose.append("\n");
                }
                continue;
            }
            if (command(t)) {
                if (code.length() > 0) {
                    code.append('\n');
                }
                code.append(t);
            } else if (prose.length() == 0) {
                prose.append(t);
            } else if (prose.toString().endsWith("\n")) {
                prose.append(t);
            } else {
                prose.append('\n').append(t);
            }
        }
        if (code.length() == 0) {
            return prose.toString();
        }
        String block = "```bash\n" + code + "\n```";
        return prose.length() == 0 ? block : prose + "\n" + block;
    }

    static boolean command(String line) {
        if (line.length() > 100 || line.endsWith(".") || line.endsWith("?")) {
            return false;
        }
        return line.matches("(?i)(bash|sh|apt|pkg|git|cd|ls|pwd|unzip|mkdir|rm|cat|echo|python3?|pip3?|npm|node|java|javac|curl|wget|sudo|chmod|termux)(\\s+\\S.*){0,1}");
    }
}
