package ai.dvine.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Splits one reply into the pieces the chat can show: speech, code, or an HTML page.
 * A fence is trusted. A page is HTML only when the text itself is a document.
 */
public final class MessageParts {
    public static final class Part {
        public final String kind;
        public final String body;
        public final String language;

        public Part(String kind, String body, String language) {
            this.kind = kind;
            this.body = body == null ? "" : body;
            this.language = language == null ? "" : language;
        }
    }

    private MessageParts() {
    }

    /**
     * Repairs the fences small models get wrong, so the code still lands in the code viewer: one or two backticks
     * with a language (`` `xml ``, ``` ``java ```) open a block, a line of one or two backticks closes it.
     */
    public static String normalizeFences(String text) {
        if (text == null || text.indexOf('`') < 0) {
            return text;
        }
        String[] lines = text.split("\n", -1);
        StringBuilder b = new StringBuilder(text.length() + 8);
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (t.matches("`{1,2}[A-Za-z][A-Za-z0-9_+#.-]{0,19}")) {
                lines[i] = "```" + t.replaceFirst("^`+", "");
            } else if (t.matches("`{1,2}")) {
                lines[i] = "```";
            }
            b.append(i == 0 ? "" : "\n").append(lines[i]);
        }
        return b.toString();
    }

    public static List<Part> split(String text) {
        List<Part> out = new ArrayList<Part>();
        if (text == null || text.trim().isEmpty()) {
            return out;
        }
        text = normalizeFences(text);
        int i = 0;
        while (i < text.length()) {
            int fence = text.indexOf("```", i);
            if (fence < 0) {
                addProse(out, text.substring(i));
                break;
            }
            addProse(out, text.substring(i, fence));
            int close = text.indexOf("```", fence + 3);
            String raw = close < 0 ? text.substring(fence + 3) : text.substring(fence + 3, close);
            int nl = raw.indexOf('\n');
            String lang = "";
            String code = raw;
            if (nl >= 0) {
                String head = raw.substring(0, nl).trim();
                if (head.matches("[A-Za-z0-9_+#.-]{0,20}")) {
                    lang = head.toLowerCase(Locale.US);
                    code = raw.substring(nl + 1);
                }
            } else if (raw.trim().matches("[A-Za-z0-9_+#.-]{1,20}")) {
                lang = raw.trim().toLowerCase(Locale.US);
                code = "";
            }
            code = trimBlankLines(code);
            if (!code.isEmpty()) {
                String kind = htmlLanguage(lang) || looksLikeDocument(code) ? "html" : "code";
                out.add(new Part(kind, code, CodeLanguage.of(lang, code))); // named, or read from the code itself
            }
            if (close < 0) {
                break;
            }
            i = close + 3;
        }
        return out;
    }

    public static String languageOf(String name) {
        if (name == null) {
            return "";
        }
        String n = name.toLowerCase(Locale.US);
        if (n.equals("js")) {
            return "javascript";
        }
        if (n.equals("py")) {
            return "python";
        }
        if (n.equals("htm")) {
            return "html";
        }
        if (n.equals("c++") || n.equals("cc")) {
            return "cpp";
        }
        if (n.equals("sh") || n.equals("shell") || n.equals("zsh") || n.equals("console") || n.equals("terminal")) {
            return "bash";
        }
        switch (n) {
            case "kt": case "kts": return "kotlin";
            case "ts": return "typescript";
            case "yml": return "yaml";
            case "rb": return "ruby";
            case "rs": return "rust";
            case "cs": case "c#": return "csharp";
            case "golang": return "go";
            case "py3": case "python3": return "python";
            case "jsonc": return "json";
            default: return n;
        }
    }

    private static void addProse(List<Part> out, String text) {
        String rest = text == null ? "" : text.trim();
        if (rest.isEmpty()) {
            return;
        }
        if (looksLikeDocument(rest)) {
            out.add(new Part("html", rest, "html"));
            return;
        }
        // code written without fences: a run of at least three code-looking lines becomes a code block
        String[] lines = text.split("\n", -1);
        int i = 0;
        StringBuilder prose = new StringBuilder();
        while (i < lines.length) {
            int j = i;
            int code = 0;
            while (j < lines.length && (CodeLanguage.codeLine(lines[j]) || (lines[j].trim().isEmpty() && code > 0))) {
                if (CodeLanguage.codeLine(lines[j])) {
                    code++;
                }
                j++;
            }
            if (code >= MIN_CODE_LINES) {
                flushProse(out, prose);
                StringBuilder block = new StringBuilder();
                for (int k = i; k < j; k++) {
                    block.append(k == i ? "" : "\n").append(lines[k]);
                }
                String body = trimBlankLines(block.toString());
                out.add(new Part("code", body, CodeLanguage.of("", body)));
                i = j;
            } else {
                prose.append(prose.length() == 0 ? "" : "\n").append(lines[i]);
                i++;
            }
        }
        flushProse(out, prose);
    }

    /** Lines of code in a row, unfenced, that make a code block. */
    static final int MIN_CODE_LINES = 3;

    private static void flushProse(List<Part> out, StringBuilder prose) {
        String t = prose.toString().trim();
        if (!t.isEmpty()) {
            out.add(new Part("text", t, ""));
        }
        prose.setLength(0);
    }

    /** Blank lines off both ends, the indentation of the first line kept. */
    static String trimBlankLines(String s) {
        return s.replaceAll("^(\\s*\\n)+", "").replaceAll("\\s+$", "");
    }

    private static boolean htmlLanguage(String lang) {
        return "html".equals(lang) || "htm".equals(lang);
    }

    static boolean looksLikeDocument(String text) {
        String t = text == null ? "" : text.trim().toLowerCase(Locale.US);
        return t.startsWith("<!doctype html") || t.startsWith("<html");
    }
}
