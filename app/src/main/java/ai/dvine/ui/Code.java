package ai.dvine.ui;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ai.dvine.R;

/**
 * The chat code viewer's colourer: a tiny, single-pass highlighter for the languages the agent
 * actually writes (Java, Kotlin, C/C++, Python, JS/TS, shell, JSON). No library and no parser: one
 * linear scan of a few hundred characters that colours keywords, strings, numbers, comments,
 * annotations and types, and marks a shell's own words ($vars, pipes, flags, builtins) so a command
 * reads like a terminal.
 *
 * It is deliberately cheap — O(characters), a handful of small sets — and it only ever runs on the
 * lines a message actually shows. The result is the original text with colour spans on top, so
 * callers keep working with a CharSequence and never re-parse anything.
 */
public final class Code {

    /** Which family of keywords a language needs; chosen from the filename or the first lines. */
    public enum Lang { JAVA, KOTLIN, CPP, PYTHON, JS, SHELL, JSON }

    /** Resolved once from the palette; 0 means "not resolved yet" and paints nothing. */
    private static int KEYWORD, STRING, NUMBER, COMMENT, TYPE, ANNOTATION, BUILTIN, CONSTANT, SHELL_VAR;

    private static boolean resolved;

    private Code() {
    }

    /**
     * Resolve the palette against a context. Cheap after the first call, so every screen can call
     * it before painting without a guard. Until it is called, highlighting is silently skipped.
     */
    public static void resolve(Context context) {
        if (context == null || resolved) {
            return;
        }
        KEYWORD = context.getColor(R.color.code_keyword);
        STRING = context.getColor(R.color.code_string);
        NUMBER = context.getColor(R.color.code_number);
        COMMENT = context.getColor(R.color.code_comment);
        TYPE = context.getColor(R.color.code_type);
        ANNOTATION = context.getColor(R.color.code_annotation);
        BUILTIN = context.getColor(R.color.code_builtin);
        CONSTANT = context.getColor(R.color.code_constant);
        SHELL_VAR = context.getColor(R.color.code_shell_var);
        resolved = true;
    }

    /** The first {@code maxLines} lines of a snippet, coloured (0 = all, capped at 40). */
    public static CharSequence paint(String name, String source, int maxLines, int skipLines) {
        if (TextUtils.isEmpty(source)) {
            return source == null ? "" : source;
        }
        if (!resolved) {
            return source;
        }
        Lang lang = guess(name, source);
        current = lang;
        int budget = maxLines <= 0 ? 40 : maxLines;
        if (skipLines < 0) {
            skipLines = 0;
        }
        String[] raw = source.split("\n", -1);
        SpannableStringBuilder out = new SpannableStringBuilder(source);
        int start = 0;
        int painted = 0;
        for (int i = 0; i < raw.length; i++) {
            String line = raw[i];
            int from = start;
            start += line.length() + 1;
            if (i < skipLines) {
                continue;
            }
            if (painted >= budget) {
                break;
            }
            painted++;
            color(line, from, out, lang);
        }
        return out;
    }

    /** One line, coloured in place at {@code offset} in {@code out}. */
    static void color(String line, int offset, SpannableStringBuilder out, Lang lang) {
        int n = line.length();
        int i = 0;
        boolean shell = lang == Lang.SHELL;
        while (i < n) {
            char c = line.charAt(i);
            int from = offset + i;
            if (c == ' ' || c == '\t' || c == '\r') {
                i++;
                continue;
            }
            if (c == '/' && i + 1 < n && line.charAt(i + 1) == '/') {
                mark(out, from, offset + n, COMMENT);
                break;
            }
            if (c == '#') {
                if (shell || lang == Lang.PYTHON || lang == Lang.JSON) {
                    mark(out, from, offset + n, COMMENT);
                    break;
                }
                // A lone # at the start of a line is a comment in most other languages too.
                if (i == 0 || Character.isWhitespace(line.charAt(i - 1))) {
                    int end = i + 1;
                    while (end < n && Character.isLetterOrDigit(line.charAt(end))) {
                        end++;
                    }
                    if (end >= n || Character.isWhitespace(line.charAt(end))) {
                        mark(out, from, offset + n, COMMENT);
                        break;
                    }
                }
            }
            if (shell && c == '$') {
                int j = i + 1;
                if (j < n && line.charAt(j) == '{') {
                    while (j < n && line.charAt(j) != '}') {
                        j++;
                    }
                    j = Math.min(n, j + 1);
                } else {
                    while (j < n && (Character.isLetterOrDigit(line.charAt(j)) || line.charAt(j) == '_')) {
                        j++;
                    }
                }
                mark(out, from, offset + j, SHELL_VAR);
                i = j;
                continue;
            }
            if (c == '"' || c == '\'' || c == '`') {
                int j = i + 1;
                while (j < n) {
                    if (line.charAt(j) == '\\') {
                        j += 2;
                        continue;
                    }
                    if (line.charAt(j) == c) {
                        j++;
                        break;
                    }
                    j++;
                }
                mark(out, from, offset + Math.min(n, j), STRING);
                i = Math.min(n, j);
                continue;
            }
            if (Character.isDigit(c) && (i == 0 || !Character.isJavaIdentifierPart(line.charAt(i - 1)))) {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(line.charAt(j)) || line.charAt(j) == '.'
                        || line.charAt(j) == '_')) {
                    j++;
                }
                if (j > i) {
                    mark(out, from, offset + j, NUMBER);
                    i = j;
                    continue;
                }
            }
            if (c == '@' && (lang == Lang.JAVA || lang == Lang.KOTLIN)) {
                int j = i + 1;
                while (j < n && (Character.isLetterOrDigit(line.charAt(j)) || line.charAt(j) == '_'
                        || line.charAt(j) == '.')) {
                    j++;
                }
                mark(out, from, offset + j, ANNOTATION);
                i = j;
                continue;
            }
            if (shell && (c == '|' || c == '>' || c == '<' || c == '&')) {
                mark(out, from, from + 1, KEYWORD);
                i++;
                continue;
            }
            if (shell && (c == '-' || c == '>') && i + 1 < n && (line.charAt(i + 1) == '-' || line.charAt(i + 1) == '>')) {
                int j = i;
                while (j < n && (line.charAt(j) == '-' || line.charAt(j) == '>')) {
                    j++;
                }
                mark(out, from, offset + j, KEYWORD);
                i = j;
                continue;
            }
            if (!Character.isLetter(c) && c != '_') {
                i++;
                continue;
            }
            int j = i;
            while (j < n && (Character.isLetterOrDigit(line.charAt(j)) || line.charAt(j) == '_')) {
                j++;
            }
            String word = line.substring(i, j);
            int wc = wordColour(word, shell, i > 0 && Character.isWhitespace(line.charAt(i - 1)));
            if (wc != 0) {
                mark(out, from, offset + j, wc);
            }
            i = j;
        }
    }

    /** The colour of one word: a shell keyword or builtin, a language keyword, a type, nothing. */
    private static int wordColour(String w, boolean shell, boolean atStart) {
        if (w.isEmpty()) {
            return 0;
        }
        if (shell) {
            return atStart && SHELL_FIRST.contains(w) ? KEYWORD
                    : SHELL_BUILTIN.contains(w) ? BUILTIN : 0;
        }
        Lang lang = current;
        if (KEYWORDS.get(lang).contains(w)) {
            return KEYWORD;
        }
        if (CONSTANTS.contains(w)) {
            return CONSTANT;
        }
        if (w.length() > 1 && Character.isUpperCase(w.charAt(0))) {
            return TYPE;
        }
        return 0;
    }

    /** Set by {@link #paint} so word lookups need no language parameter threaded through. */
    private static Lang current = Lang.JAVA;

    /** Language from a filename, falling back to the shape of the text. */
    public static Lang guess(String name, String source) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java")) {
            return Lang.JAVA;
        }
        if (lower.endsWith(".kt") || lower.endsWith(".kts")) {
            return Lang.KOTLIN;
        }
        if (lower.endsWith(".py") || lower.endsWith(".pyw")) {
            return Lang.PYTHON;
        }
        if (lower.endsWith(".js") || lower.endsWith(".jsx") || lower.endsWith(".ts") || lower.endsWith(".tsx")) {
            return Lang.JS;
        }
        if (lower.endsWith(".sh") || lower.endsWith(".bash") || lower.endsWith(".zsh")) {
            return Lang.SHELL;
        }
        if (lower.endsWith(".json") || lower.endsWith(".jsonc")) {
            return Lang.JSON;
        }
        if (lower.endsWith(".c") || lower.endsWith(".h") || lower.endsWith(".cpp") || lower.endsWith(".cc")
                || lower.endsWith(".hpp") || lower.endsWith(".m") || lower.endsWith(".mm")) {
            return Lang.CPP;
        }
        if (source != null) {
            String head = source.length() > 400 ? source.substring(0, 400) : source;
            if (head.contains("#include") || head.contains("int main(")) {
                return Lang.CPP;
            }
            if (head.contains("#!/bin/") || head.contains("curl ") || head.contains("sudo ")) {
                return Lang.SHELL;
            }
            if (head.contains("import ") && head.contains("from ")) {
                return Lang.PYTHON;
            }
            if (head.contains("public class") || head.contains("import java.")) {
                return Lang.JAVA;
            }
            if (head.contains("fun ") && head.contains("val ")) {
                return Lang.KOTLIN;
            }
        }
        return Lang.JAVA;
    }

    private static void mark(SpannableStringBuilder out, int from, int to, int colour) {
        if (colour == 0 || from < 0 || from >= to || to > out.length()) {
            return;
        }
        out.setSpan(new ForegroundColorSpan(colour), from, to, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static final Set<String> CONSTANTS = new HashSet<>(Arrays.asList(
            "true", "false", "null", "nil", "None", "True", "False", "undefined", "NULL", "NaN", "Infinity"));

    private static final Map<Lang, Set<String>> KEYWORDS = new HashMap<>();

    private static final Set<String> SHELL_FIRST = new HashSet<>(Arrays.asList(
            "if", "then", "elif", "else", "fi", "for", "while", "until", "do", "done", "case", "esac",
            "function", "select", "time"));

    private static final Set<String> SHELL_BUILTIN = new HashSet<>(Arrays.asList(
            "cd", "echo", "export", "source", "alias", "unalias", "set", "unset", "read", "printf",
            "test", "true", "false", "exit", "return", "shift", "eval", "exec", "trap", "wait",
            "local", "declare", "typeset", "let", "jobs", "bg", "fg", "hash", "umask", "pwd", "kill",
            "git", "npm", "node", "python", "python3", "java", "gradle", "curl", "wget", "mkdir",
            "rm", "cp", "mv", "ls", "cat", "grep", "sed", "awk", "tar", "chmod", "chown"));

    static {
        KEYWORDS.put(Lang.JAVA, words("abstract assert boolean break byte case catch char class const "
                + "continue default do double else enum extends final finally float for goto if implements "
                + "import instanceof int interface long native new package private protected public return "
                + "short static strictfp super switch synchronized this throw throws transient try void "
                + "volatile while record sealed permits var yield"));
        KEYWORDS.put(Lang.KOTLIN, words("as break class continue do else false for fun if in interface "
                + "is null object package return super this throw true try typealias typeof val var when "
                + "while by catch constructor delegate dynamic field file finally get import init "
                + "param property receiver set setparam where abstract actual annotation companion "
                + "const crossinline data enum expect external final infix inline inner internal "
                + "lateinit noinline open operator override private public reified suspend tailrec "
                + "vararg out"));
        KEYWORDS.put(Lang.CPP, words("alignas alignof and asm auto bool break case catch char class "
                + "concept const consteval constexpr constinit const_cast continue decltype default delete "
                + "do double dynamic_cast else enum explicit export extern false float for friend goto if "
                + "inline int long mutable namespace new noexcept nullptr operator private protected public "
                + "register reinterpret_cast requires return short signed sizeof static static_assert "
                + "static_cast struct switch template this thread_local throw true try typedef typeid "
                + "typename union unsigned using virtual void volatile wchar_t while"));
        KEYWORDS.put(Lang.PYTHON, words("False None True and as assert async await break class continue "
                + "def del elif else except finally for from global if import in is lambda nonlocal not "
                + "or pass raise return try while with yield match case self cls"));
        KEYWORDS.put(Lang.JS, words("abstract any as async await boolean break case catch class const "
                + "constructor continue debugger declare default delete do else enum export extends false "
                + "finally for from function get if implements import in instanceof interface is let "
                + "module namespace never new null number object of package private protected public "
                + "readonly return satisfies set static string super switch symbol this throw true try "
                + "type typeof undefined unique unknown var void while with yield"));
        KEYWORDS.put(Lang.SHELL, new HashSet<String>());
        KEYWORDS.put(Lang.JSON, new HashSet<String>());
    }

    private static Set<String> words(String list) {
        Set<String> set = new HashSet<>();
        for (String w : list.split(" ")) {
            if (!w.isEmpty()) {
                set.add(w);
            }
        }
        return set;
    }
}