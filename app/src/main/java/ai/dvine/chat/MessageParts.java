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

    public static List<Part> split(String text) {
        List<Part> out = new ArrayList<Part>();
        if (text == null || text.trim().isEmpty()) {
            return out;
        }
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
            code = code.trim();
            if (!code.isEmpty()) {
                String kind = htmlLanguage(lang) || looksLikeDocument(code) ? "html" : "code";
                out.add(new Part(kind, code, lang));
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
        if (n.equals("sh")) {
            return "bash";
        }
        return n;
    }

    private static void addProse(List<Part> out, String text) {
        String rest = text == null ? "" : text.trim();
        if (rest.isEmpty()) {
            return;
        }
        if (looksLikeDocument(rest)) {
            out.add(new Part("html", rest, "html"));
        } else {
            out.add(new Part("text", rest, ""));
        }
    }

    private static boolean htmlLanguage(String lang) {
        return "html".equals(lang) || "htm".equals(lang);
    }

    static boolean looksLikeDocument(String text) {
        String t = text == null ? "" : text.trim().toLowerCase(Locale.US);
        return t.startsWith("<!doctype html") || t.startsWith("<html");
    }
}
