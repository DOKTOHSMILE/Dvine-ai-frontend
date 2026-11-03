package ai.dvine.chat;

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
            {"xml", "^\\s*<\\?xml\\b", "\\bxmlns(:\\w+)?=\"", "android:\\w+=\"", "^\\s*</?[A-Za-z][\\w.:-]*(\\s+[\\w.:-]+=\"[^\"]*\")*\\s*/?>\\s*$"},
            {"html", "(?i)<!doctype html", "(?i)<(html|head|body|div|span|p|a|ul|li|script|style)\\b[^>]*>", "(?i)</(div|p|span|body|html)>"},
            {"java", "\\bpublic\\s+(final\\s+)?(class|interface|enum)\\s+\\w+", "\\b(private|public|protected)\\s+(static\\s+)?(final\\s+)?[\\w<>\\[\\]]+\\s+\\w+\\s*\\(",
                    "^\\s*import\\s+(java|javax|android|androidx|org|com)\\.[\\w.]+;", "System\\.out\\.println", "@Override",
                    "^\\s*(private\\s+|public\\s+|protected\\s+)?(final\\s+)?(int|long|String|boolean|double|float|char|void|List<\\w+>)\\s+\\w+\\s*[;=]"},
            {"kotlin", "\\bfun\\s+\\w+\\s*\\(", "\\b(val|var)\\s+\\w+\\s*(:\\s*\\w+)?\\s*=", "\\bdata\\s+class\\b", "^\\s*package\\s+[\\w.]+\\s*$", "\\bcompanion\\s+object\\b"},
            {"python", "^\\s*def\\s+\\w+\\s*\\(.*\\)\\s*(->\\s*[\\w\\[\\], ]+)?:\\s*$", "^\\s*(from\\s+[\\w.]+\\s+)?import\\s+[\\w., ]+\\s*$", "\\bself\\.", "^\\s*if\\s+__name__\\s*==",
                    "\\bprint\\(", "^\\s*(elif|except|with)\\b.*:\\s*$"},
            {"javascript", "\\bfunction\\s+\\w*\\s*\\(", "\\b(const|let)\\s+\\w+\\s*=", "=>\\s*[{(]?", "\\bconsole\\.log\\(", "\\brequire\\(['\"]", "\\bdocument\\.\\w+"},
            {"typescript", "\\binterface\\s+\\w+\\s*\\{", ":\\s*(string|number|boolean|void|any)\\b", "\\bexport\\s+(type|interface)\\b"},
            {"json", "^\\s*[\\[{]\\s*$", "^\\s*\"[^\"]+\"\\s*:\\s*", "^\\s*[}\\]],?\\s*$"},
            {"sql", "(?i)^\\s*(select|insert\\s+into|update|delete\\s+from|create\\s+table|alter\\s+table|drop\\s+table)\\b", "(?i)\\bfrom\\s+\\w+\\s+(where|join|group by|order by)\\b"},
            {"bash", "^#!/bin/(ba)?sh", "^\\s*(sudo|apt|apt-get|pip|npm|git|cd|ls|mkdir|chmod|export|echo|curl|wget)\\s", "\\$\\{?\\w+\\}?", "^\\s*fi\\s*$", "\\bthen\\s*$"},
            {"c", "^\\s*#include\\s*<\\w+\\.h>", "\\bint\\s+main\\s*\\(", "\\bprintf\\s*\\(", "\\bmalloc\\s*\\("},
            {"cpp", "^\\s*#include\\s*<(iostream|vector|string|map|memory)>", "\\bstd::", "\\bcout\\s*<<", "\\btemplate\\s*<"},
            {"csharp", "^\\s*using\\s+System[\\w.]*;", "\\bnamespace\\s+\\w+", "\\bConsole\\.WriteLine\\(", "\\bpublic\\s+async\\s+Task\\b"},
            {"go", "^\\s*package\\s+main\\s*$", "\\bfunc\\s+\\w+\\s*\\(", ":=", "\\bfmt\\.Print"},
            {"rust", "\\bfn\\s+\\w+\\s*\\(", "\\blet\\s+mut\\b", "\\bprintln!\\(", "\\bimpl\\b", "->\\s*Result<"},
            {"swift", "\\bfunc\\s+\\w+\\s*\\(", "\\bimport\\s+(UIKit|SwiftUI|Foundation)\\b", "\\bguard\\s+let\\b", "\\bvar\\s+\\w+\\s*:\\s*\\w+"},
            {"php", "<\\?php", "\\$\\w+\\s*=", "\\becho\\s+", "->\\w+\\("},
            {"ruby", "^\\s*def\\s+\\w+[?!]?\\s*$", "^\\s*end\\s*$", "\\bputs\\s+", "\\brequire\\s+'"},
            {"yaml", "^\\s*[\\w.-]+:\\s+[^{}\\[\\];]+$", "^\\s*-\\s+[\\w.-]+:\\s", "^---\\s*$"},
            {"css", "^\\s*[.#]?[\\w-]+(\\s*[.#:>\\w-]*)*\\s*\\{\\s*$", "^\\s*[\\w-]+\\s*:\\s*[^;]+;\\s*$", "@media\\b"},
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

    /** The language the code is most likely in, or "" when nothing says. */
    public static String detect(String code) {
        String c = code == null ? "" : code;
        if (c.trim().isEmpty()) {
            return "";
        }
        String best = "";
        int bestScore = 0, second = 0;
        for (int i = 0; i < RULES.length; i++) {
            int score = 0;
            for (Pattern p : COMPILED[i]) {
                java.util.regex.Matcher m = p.matcher(c);
                int hits = 0;
                while (m.find() && hits < 5) {
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
        // an Android layout is XML even though it also looks like HTML tags
        if ("html".equals(best) && Pattern.compile("android:\\w+=\"|<\\?xml").matcher(c).find()) {
            best = "xml";
        }
        // two signs, or one sign no other language shares
        return bestScore >= 2 || (bestScore == 1 && second == 0) ? best : "";
    }

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

    /** Whether a line looks like code rather than prose (for code written without fences). */
    public static boolean codeLine(String line) {
        String t = line == null ? "" : line.trim();
        if (t.isEmpty()) {
            return false;
        }
        if (t.matches("^</?[A-Za-z][\\w.:-]*([\\s/>].*)?$") || t.matches("^[\\w:.-]+=\"[^\"]*\"/?>?$")) {
            return true; // XML / HTML tags and their attribute lines
        }
        if (t.matches(".*[;{}]\\s*$") && !t.matches(".*[.!?]\\s*$")) {
            return true;
        }
        return t.matches("^(import|package|def|class|fun|val|var|const|let|function|public|private|return|#include|using)\\b.*")
                && !t.matches(".*\\b(the|and|is|are|you|your)\\b.*\\b(the|and|is|are|you|your)\\b.*");
    }

    /** Lower case, as a fence's language would be. */
    static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.US);
    }
}
