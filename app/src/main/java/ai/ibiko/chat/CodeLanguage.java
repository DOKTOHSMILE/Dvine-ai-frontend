package ai.ibiko.chat;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What language a piece of code is in, from the code itself: used when a reply's code block has no language, or a
 * wrong one, so the code viewer still names it ("xml", "kotlin", "python"…). Also tells code from prose, for code a
 * model wrote without fences. Pure Java, a handful of telling patterns per language, the best score wins.
 */
public final class CodeLanguage {
    private CodeLanguage() {
    }

    private static final String[][] RULES = {
            // language, then patterns that say it (each is a regex, multiline)
            {"xml", "^\\s*<\\?xml\\b", "\\bxmlns(:\\w+)?=\"", "android:\\w+=\"", "^\\s*</?[A-Za-z][\\w.:-]*"},
            {"html", "(?i)<!doctype html", "(?i)<(html|head|body|div|span|p|a|ul|li|script|style)\\b", "(?i)</(div|p|span|body|html)>"},
            {"java", "\\bpublic\\s+(final\\s+)?(class|interface|enum)\\s+\\w+", "\\b(private|public|protected)\\s+(static\\s+)?(final\\s+)?[\\w<>\\[\\]]+\\s+\\w+\\s*\\(",
                    "^\\s*import\\s+(java|javax|android|androidx|org|com)\\.[\\w.]+;", "System\\.out\\.println", "@Override",
                    "^\\s*(private\\s+|public\\s+|protected\\s+)?(final\\s+)?(int|long|String|boolean|double|float|char|void|List<\\w+>)\\s+\\w+\\s*[;=]"},
            {"kotlin", "\\bfun\\s+\\w+\\s*\\(", "\\b(val|var)\\s+\\w+\\s*(:\\s*\\w+)?\\s*=", "\\bdata\\s+class\\b", "^\\s*package\\s+[\\w.]+\\s*$", "\\bcompanion\\s+object\\b"},
            {"python", "^\\s*def\\s+\\w+\\s*\\(", "^\\s*(from\\s+[\\w.]+\\s+)?import\\s+[\\w., ]+\\s*$", "\\bself\\.", "^\\s*if\\s+__name__\\s*==",
                    "\\bprint\\(", "^\\s*(elif|except|with)\\b"},
            {"javascript", "\\bfunction\\s+\\w*\\s*\\(", "\\b(const|let)\\s+\\w+\\s*=", "=>", "\\bconsole\\.log\\(", "\\brequire\\(['\"]", "\\bdocument\\.\\w+"},
            {"typescript", "\\binterface\\s+\\w+\\s*\\{", ":\\s*(string|number|boolean|void|any)\\b", "\\bexport\\s+(type|interface)\\b"},
            {"json", "^\\s*[\\[{]\\s*$", "^\\s*\"[^\"]+\"\\s*:", "^\\s*[}\\]],?\\s*$"},
            {"sql", "(?i)^\\s*(select|insert\\s+into|update|delete\\s+from|create\\s+table|alter\\s+table|drop\\s+table)\\b", "(?i)\\bfrom\\s+\\w+\\s+(where|join|group by|order by)\\b"},
            {"bash", "^#!/bin/(ba)?sh", "^\\s*(sudo|apt|apt-get|pip|npm|git|cd|ls|mkdir|chmod|export|echo|curl|wget)\\s", "\\$\\w+", "^\\s*fi\\s*$"},
            {"c", "^\\s*#include\\s*<\\w+\\.h>", "\\bint\\s+main\\s*\\(", "\\bprintf\\s*\\(", "\\bmalloc\\s*\\("},
            {"cpp", "^\\s*#include\\s*<(iostream|vector|string|map|memory)>", "\\bstd::", "\\bcout\\s*<<", "\\btemplate\\s*<"},
            {"csharp", "^\\s*using\\s+System[\\w.]*;", "\\bnamespace\\s+\\w+", "\\bConsole\\.WriteLine\\(", "\\bpublic\\s+async\\s+Task\\b"},
            {"go", "^\\s*package\\s+main\\s*$", "\\bfunc\\s+\\w+\\s*\\(", ":=", "\\bfmt\\.Print"},
            {"rust", "\\bfn\\s+\\w+\\s*\\(", "\\blet\\s+mut\\b", "\\bprintln!\\(", "\\bimpl\\b", "->\\s*Result<"},
            {"swift", "\\bfunc\\s+\\w+\\s*\\(", "\\bimport\\s+(UIKit|SwiftUI|Foundation)\\b", "\\bguard\\s+let\\b", "\\bvar\\s+\\w+\\s*:\\s*\\w+"},
            {"php", "<\\?php", "\\$\\w+\\s*=", "\\becho\\s+", "->\\w+\\("},
            {"ruby", "^\\s*def\\s+\\w+", "^\\s*end\\s*$", "\\bputs\\s+", "\\brequire\\s+'"},
            {"yaml", "^\\s*[\\w.-]+:\\s+\\S", "^\\s*-\\s+[\\w.-]+:\\s", "^---\\s*$"},
            {"css", "^\\s*[.#]?[\\w-]+[^{\\n]{0,60}\\{\\s*$", "^\\s*[\\w-]+\\s*:\\s*[^;\\n]+;\\s*$", "@media\\b"},
            {"gradle", "\\b(implementation|api|testImplementation)\\s*[('\"]", "\\bandroid\\s*\\{", "\\bdependencies\\s*\\{", "\\bplugins\\s*\\{"},
            {"dockerfile", "^\\s*FROM\\s+\\S+", "^\\s*(RUN|COPY|WORKDIR|CMD|ENTRYPOINT|EXPOSE)\\s"},
            {"markdown", "^#{1,6}\\s+\\w", "^\\s*[-*]\\s+\\w", "\\[[^\\]]+\\]\\([^)]+\\)"},
    };

    private static final Pattern[][] COMPILED = new Pattern[RULES.length][];

    static {
        for (int i = 0; i < RULES.length; i++) {
            COMPILED[i] = new Pattern[RULES[i].length - 1];
            for (int j = 1; j < RULES[i].length; j++) {
                COMPILED[i][j - 1] = Pattern.compile(RULES[i][j], Pattern.MULTILINE);
            }
        }
    }

    /** The language the code is most likely in, or "" when nothing says. Only a sample is read: a long reply must not stall the screen. */
    public static String detect(String code) {
        String c = code == null ? "" : code;
        if (c.trim().isEmpty()) {
            return "";
        }
        if (c.length() > 6000) {
            c = c.substring(0, 4000) + "\n" + c.substring(c.length() - 1500);
        }
        String best = "";
        int bestScore = 0, second = 0;
        long deadline = System.nanoTime() + 8_000_000L;
        for (int i = 0; i < RULES.length; i++) {
            if (System.nanoTime() > deadline) {
                break;
            }
            int score = 0;
            for (Pattern p : COMPILED[i]) {
                java.util.regex.Matcher m = p.matcher(c);
                int hits = 0;
                while (hits < 3 && m.find()) {
                    hits++;
                }
                score += hits;
            }
            if (score > bestScore) {
                second = bestScore;
                bestScore = score;
                best = RULES[i][0];
            } else if (score > second) {
                second = score;
            }
        }
        if ("html".equals(best) && ANDROID_XML.matcher(c).find()) {
            best = "xml";
        }
        return bestScore >= 2 || (bestScore == 1 && second == 0) ? best : "";
    }

    private static final Pattern ANDROID_XML = Pattern.compile("android:\\w+=\"|<\\?xml");
    private static final Pattern XML_LINE = Pattern.compile("^</?[A-Za-z][\\w.:-]*|^[\\w:.-]+=\"[^\"]{0,80}\"");
    private static final Pattern ENDS_CODE = Pattern.compile(".*[;{}]\\s*$");
    private static final Pattern ENDS_SENTENCE = Pattern.compile(".*[.!?]\\s*$");
    private static final Pattern CODE_START = Pattern.compile("^(import|package|def|class|fun|val|var|const|let|function|public|private|return|#include|using)\\b");
    private static final Pattern PROSE = Pattern.compile(".*\\b(the|and|is|are|you|your)\\b.*\\b(the|and|is|are|you|your)\\b.*");

    /** The language a fence named, or detected from the code when it named none or one this doesn't know. */
    public static String of(String named, String code) {
        String n = MessageParts.languageOf(named == null ? "" : named.trim());
        if (!n.isEmpty() && known(n)) {
            return n;
        }
        String found = detect(code);
        return found.isEmpty() ? n : found;
    }

    static boolean known(String lang) {
        for (String[] r : RULES) {
            if (r[0].equals(lang)) {
                return true;
            }
        }
        return lang.matches("(?i)kotlin|kt|groovy|text|plain|properties|toml|ini|scala|dart|lua|r|perl|haskell|elixir|clojure|"
                + "objective-c|objc|powershell|ps1|bat|cmd|make|makefile|cmake|proto|graphql|vue|svelte|jsx|tsx|scss|sass|less");
    }

    /** Whether a line looks like code rather than prose (for code written without fences). A very long line is not scanned. */
    public static boolean codeLine(String line) {
        String t = line == null ? "" : line.trim();
        if (t.isEmpty() || t.length() > 240) {
            return false;
        }
        if (XML_LINE.matcher(t).find()) {
            return true;
        }
        if (ENDS_CODE.matcher(t).matches() && !ENDS_SENTENCE.matcher(t).matches()) {
            return true;
        }
        return CODE_START.matcher(t).find() && !PROSE.matcher(t).matches();
    }

    /** Lower case, as a fence's language would be. */
    static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.US);
    }
}
