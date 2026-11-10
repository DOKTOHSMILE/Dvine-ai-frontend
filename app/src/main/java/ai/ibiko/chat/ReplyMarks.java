package ai.ibiko.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a reply into the words the user should read, plus where those words are bold, italic, code, a heading,
 * a quote, or a link. The chat paints the marks. Nothing here loads a page or runs a tag.
 */
public final class ReplyMarks {
    public static final int BOLD = 1;
    public static final int CODE = 2;
    public static final int ITALIC = 3;
    public static final int LINK = 4;
    public static final int HEAD = 5;
    public static final int QUOTE = 6;

    private static final Pattern BARE = Pattern.compile("https?://[^\\s<>\"]+");
    private static final int FULL = 16000;

    public static final class Mark {
        public final int from;
        public final int to;
        public final int kind;
        /** Set for a link. Null otherwise. */
        public final String url;

        public Mark(int from, int to, int kind, String url) {
            this.from = from;
            this.to = to;
            this.kind = kind;
            this.url = url;
        }
    }

    public static final class Result {
        public final String text;
        public final List<Mark> marks;

        Result(String text, List<Mark> marks) {
            this.text = text;
            this.marks = marks;
        }
    }

    private ReplyMarks() {
    }

    public static Result of(String text) {
        if (text == null || text.isEmpty()) {
            return new Result("", new ArrayList<Mark>());
        }
        if (text.length() > FULL) {
            return bareOnly(text);
        }
        StringBuilder raw = new StringBuilder(text.length());
        List<Mark> marks = new ArrayList<Mark>();
        boolean inCode = false;
        String[] lines = text.split("\n", -1);
        for (int n = 0; n < lines.length; n++) {
            String line = lines[n];
            if (line.trim().startsWith("```")) {
                inCode = !inCode;
                continue;
            }
            if (raw.length() > 0) {
                raw.append('\n');
            }
            if (inCode) {
                int from = raw.length();
                raw.append(line);
                if (raw.length() > from) {
                    marks.add(new Mark(from, raw.length(), CODE, null));
                }
                continue;
            }
            int heading = 0;
            while (heading < line.length() && line.charAt(heading) == '#') {
                heading++;
            }
            boolean head = heading > 0 && heading <= 3 && heading < line.length() && line.charAt(heading) == ' ';
            if (head) {
                line = line.substring(heading + 1);
            }
            boolean quote = line.startsWith("> ");
            if (quote) {
                line = line.substring(2);
            }
            if (line.matches("\\s*[-*]\\s+.*")) {
                line = line.replaceFirst("^\\s*[-*]\\s+", "\u2022 ");
            }
            int from = raw.length();
            inline(raw, marks, line);
            if (raw.length() > from) {
                if (head) {
                    marks.add(new Mark(from, raw.length(), HEAD, null));
                } else if (quote) {
                    marks.add(new Mark(from, raw.length(), QUOTE, null));
                }
            }
        }
        return new Result(raw.toString(), marks);
    }

    /** A very long reply only gets its web addresses marked, so laying it out cannot stall the phone. */
    private static Result bareOnly(String text) {
        List<Mark> marks = new ArrayList<Mark>();
        Matcher m = BARE.matcher(text);
        while (m.find()) {
            String url = trimEnd(m.group());
            if (!url.isEmpty()) {
                marks.add(new Mark(m.start(), m.start() + url.length(), LINK, url));
            }
        }
        return new Result(text, marks);
    }

    private static void inline(StringBuilder raw, List<Mark> marks, String line) {
        int i = 0;
        while (i < line.length()) {
            if (line.startsWith("**", i) || line.startsWith("__", i)) {
                String token = line.substring(i, i + 2);
                int end = line.indexOf(token, i + 2);
                if (end > i + 2) {
                    mark(raw, marks, line.substring(i + 2, end), BOLD, null);
                    i = end + 2;
                    continue;
                }
            }
            if ((line.charAt(i) == '*' || line.charAt(i) == '_') && opener(line, i)) {
                char token = line.charAt(i);
                int end = closer(line, i + 1, token);
                if (end > i + 1) {
                    mark(raw, marks, line.substring(i + 1, end), ITALIC, null);
                    i = end + 1;
                    continue;
                }
            }
            if (line.charAt(i) == '`') {
                int end = line.indexOf('`', i + 1);
                if (end > i) {
                    mark(raw, marks, line.substring(i + 1, end), CODE, null);
                    i = end + 1;
                    continue;
                }
            }
            if (line.charAt(i) == '[') {
                int label = line.indexOf(']', i + 1);
                if (label > i + 1 && label + 1 < line.length() && line.charAt(label + 1) == '(') {
                    int close = line.indexOf(')', label + 2);
                    if (close > label + 2) {
                        String url = line.substring(label + 2, close).trim();
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            mark(raw, marks, line.substring(i + 1, label), LINK, url);
                            i = close + 1;
                            continue;
                        }
                    }
                }
            }
            int next = line.length();
            int star = line.indexOf("**", i);
            int under = line.indexOf("__", i);
            int tick = line.indexOf('`', i);
            int bracket = line.indexOf('[', i);
            int http = indexOfEither(line, i);
            if (star >= 0) {
                next = Math.min(next, star);
            }
            if (under >= 0) {
                next = Math.min(next, under);
            }
            if (tick >= 0) {
                next = Math.min(next, tick);
            }
            if (bracket >= 0) {
                next = Math.min(next, bracket);
            }
            if (http >= 0) {
                next = Math.min(next, http);
            }
            int single = singleMark(line, i);
            if (single >= 0) {
                next = Math.min(next, single);
            }
            if (next == i && http == i) {
                Matcher m = BARE.matcher(line);
                if (m.find(i) && m.start() == i) {
                    String url = trimEnd(m.group());
                    mark(raw, marks, url, LINK, url);
                    i += url.length();
                    continue;
                }
            }
            if (next == i) {
                raw.append(line.charAt(i));
                i++;
            } else {
                raw.append(line, i, next);
                i = next;
            }
        }
    }

    private static void mark(StringBuilder raw, List<Mark> marks, String words, int kind, String url) {
        int from = raw.length();
        raw.append(words);
        if (raw.length() > from) {
            marks.add(new Mark(from, raw.length(), kind, url));
        }
    }

    private static boolean opener(String line, int i) {
        if (i + 1 >= line.length()) {
            return false;
        }
        if (line.charAt(i + 1) == line.charAt(i) || line.charAt(i + 1) == ' ') {
            return false;
        }
        return i == 0 || !Character.isLetterOrDigit(line.charAt(i - 1));
    }

    private static int closer(String line, int from, char token) {
        for (int j = from; j < line.length(); j++) {
            if (line.charAt(j) != token) {
                continue;
            }
            if (j + 1 < line.length() && line.charAt(j + 1) == token) {
                continue;
            }
            if (j == from || line.charAt(j - 1) == ' ') {
                continue;
            }
            if (j + 1 < line.length() && Character.isLetterOrDigit(line.charAt(j + 1))) {
                continue;
            }
            return j;
        }
        return -1;
    }

    private static int singleMark(String line, int from) {
        for (int j = from; j < line.length(); j++) {
            char c = line.charAt(j);
            if ((c == '*' || c == '_') && opener(line, j)) {
                return j;
            }
        }
        return -1;
    }

    private static int indexOfEither(String line, int from) {
        int a = line.indexOf("http://", from);
        int b = line.indexOf("https://", from);
        if (a < 0) {
            return b;
        }
        if (b < 0) {
            return a;
        }
        return Math.min(a, b);
    }

    private static String trimEnd(String url) {
        return url.replaceAll("[),.;]+$", "");
    }
}
