package ai.ibiko.agent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the errors in a command's output the way a developer does: the first real error, with its file and line, what
 * kind of error it is, what usually causes that kind, and what to look at next. Javac and Gradle, Kotlin, Python
 * tracebacks, Node and TypeScript, Go, Rust, C and C++, JUnit, the shell, npm and pip, and Ibiko's own git tool. Long
 * logs bury the one line that matters under hundreds; this finds it. Pure Java.
 */
public final class ErrorReader {
    private ErrorReader() {
    }

    /** What went wrong, by the kind of fix it needs. */
    public enum Kind {
        SYMBOL, IMPORT, SYNTAX, TYPE, NULL, INDEX, FILE, PERMISSION, COMMAND, DEPENDENCY, TEST, MEMORY, TIMEOUT,
        GIT_BEHIND, GIT_CONFLICT, GIT_AUTH, OTHER
    }

    /** One error: where (file and line, when the output says) and what. */
    public static final class Problem {
        public final Kind kind;
        public final String file, message;
        public final int line;

        Problem(Kind kind, String file, int line, String message) {
            this.kind = kind;
            this.file = file;
            this.line = line;
            this.message = message.trim();
        }

        /** "Main.java:42: cannot find symbol Foo". */
        public String where() {
            return (file == null ? "" : file + (line > 0 ? ":" + line : "") + ": ") + message;
        }
    }

    private static final Pattern JAVAC = Pattern.compile("^(\\S+\\.(?:java|kt|kts|scala|groovy)):(\\d+): (?:error|e): (.+)$");
    private static final Pattern KOTLINC = Pattern.compile("^e: (?:file://)?(\\S+\\.kts?):(\\d+):\\d+ (.+)$");
    private static final Pattern TSC = Pattern.compile("^(\\S+\\.tsx?)\\((\\d+),\\d+\\): error (TS\\d+: .+)$");
    private static final Pattern NATIVE = Pattern.compile("^(\\S+\\.(?:c|cc|cpp|cxx|h|hpp|go|swift|m|mm)):(\\d+):(?:\\d+:)? (?:fatal )?(?:error: )?(.+)$");
    private static final Pattern RUST_HEAD = Pattern.compile("^error(?:\\[(E\\d+)])?: (.+)$");
    private static final Pattern RUST_AT = Pattern.compile("^\\s*--> (\\S+?):(\\d+):\\d+");
    private static final Pattern PY_AT = Pattern.compile("^\\s*File \"(.+?)\", line (\\d+)");
    private static final Pattern PY_EXC = Pattern.compile("^([A-Za-z_][\\w.]*(?:Error|Exception|Exit|Interrupt|Warning))(?:: (.*))?$");
    private static final Pattern JAVA_EXC = Pattern.compile("(?:Exception in thread \"[^\"]*\" |Caused by: )?((?:[a-z]\\w*\\.)+(\\w*(?:Exception|Error)))(?:: (.*))?$");
    private static final Pattern JAVA_AT = Pattern.compile("^\\s*at [\\w.$<>]+\\((\\w+\\.(?:java|kt)):(\\d+)\\)");
    private static final Pattern NODE_AT = Pattern.compile("\\(?((?:/|\\.{1,2}/|[A-Za-z]:\\\\)?[\\w./\\\\-]+\\.(?:m?js|cjs|jsx|tsx?)):(\\d+):\\d+\\)?\\s*$");
    private static final Pattern JS_EXC = Pattern.compile("^(?:Uncaught )?(\\w*Error): (.+)$");
    private static final Pattern EXPECTED = Pattern.compile("expected:\\s*<(.*?)> but was:\\s*<(.*?)>");
    private static final Pattern GENERIC = Pattern.compile("^(?:error|ERROR|fatal|FATAL|npm ERR!|E:)[:!]?\\s+(.+)$");

    /** The errors in an output, first first, at most three, each once. */
    public static List<Problem> read(String output) {
        List<Problem> out = new ArrayList<Problem>();
        if (output == null || output.isEmpty()) {
            return out;
        }
        String[] lines = output.split("\\r?\\n");
        String pyFile = null, javaFile = null, nodeFile = null;
        int pyLine = 0, javaLine = 0, nodeLine = 0;
        for (int i = 0; i < lines.length && out.size() < 6; i++) {
            String l = lines[i];
            String t = l.trim();
            Matcher m;
            // Ibiko's git tool, in its own words
            if (t.contains("has newer commits on GitHub; pull first") || t.contains("non-fast-forward") || t.contains("(fetch first)")) {
                out.add(new Problem(Kind.GIT_BEHIND, null, 0, t));
                continue;
            }
            if (t.startsWith("Conflicts (") || t.startsWith("CONFLICT (") || t.contains("resolve the conflict markers in")
                    || t.contains("before pushing") && t.contains(".theirs")) {
                out.add(new Problem(Kind.GIT_CONFLICT, null, 0, t));
                continue;
            }
            if (t.matches(".*GitHub said 40[13].*") || t.contains("the token may lack access") || t.contains("Authentication failed")
                    || t.contains("could not read Username")) {
                out.add(new Problem(Kind.GIT_AUTH, null, 0, t));
                continue;
            }
            if ((m = JAVAC.matcher(t)).matches() || (m = KOTLINC.matcher(t)).matches() || (m = TSC.matcher(t)).matches()) {
                out.add(new Problem(kindOf(m.group(3)), m.group(1), Integer.parseInt(m.group(2)), m.group(3) + symbolAfter(lines, i)));
                continue;
            }
            if ((m = NATIVE.matcher(t)).matches() && !m.group(3).startsWith("warning") && !m.group(3).startsWith("note:")) {
                out.add(new Problem(kindOf(m.group(3)), m.group(1), Integer.parseInt(m.group(2)), m.group(3)));
                continue;
            }
            if ((m = RUST_HEAD.matcher(t)).matches() && !t.startsWith("error: could not compile")) {
                String file = null;
                int line = 0;
                for (int j = i + 1; j < lines.length && j <= i + 4; j++) {
                    Matcher at = RUST_AT.matcher(lines[j]);
                    if (at.find()) {
                        file = at.group(1);
                        line = Integer.parseInt(at.group(2));
                        break;
                    }
                }
                if (file != null || m.group(1) != null) {
                    out.add(new Problem(kindOf(m.group(2)), file, line, (m.group(1) == null ? "" : m.group(1) + ": ") + m.group(2)));
                    continue;
                }
            }
            if ((m = PY_AT.matcher(l)).find()) {
                pyFile = m.group(1);
                pyLine = Integer.parseInt(m.group(2));
                continue;
            }
            if (pyFile != null && (m = PY_EXC.matcher(t)).matches()) {
                String msg = m.group(1) + (m.group(2) == null ? "" : ": " + m.group(2));
                out.add(new Problem(kindOf(msg), pyFile, pyLine, msg));
                pyFile = null;
                continue;
            }
            if ((m = JAVA_AT.matcher(l)).find()) {
                if (javaFile == null) {
                    javaFile = m.group(1);
                    javaLine = Integer.parseInt(m.group(2));
                    attachWhere(out, javaFile, javaLine);
                }
                continue;
            }
            if ((m = JAVA_EXC.matcher(t)).find() && t.indexOf(m.group(1)) <= 40 && !t.startsWith("at ")) {
                String msg = m.group(2) + (m.group(3) == null ? "" : ": " + m.group(3));
                javaFile = null;
                out.add(new Problem(kindOf(msg), null, 0, msg));
                continue;
            }
            if ((m = NODE_AT.matcher(t)).find() && (t.startsWith("at ") || t.matches("^\\S+:\\d+$") || t.startsWith("/"))) {
                if (nodeFile == null && !m.group(1).contains("node_modules") && !m.group(1).startsWith("node:")) {
                    nodeFile = m.group(1);
                    nodeLine = Integer.parseInt(m.group(2));
                    attachWhere(out, nodeFile, nodeLine);
                }
                continue;
            }
            if ((m = JS_EXC.matcher(t)).matches()) {
                out.add(new Problem(kindOf(m.group(1) + ": " + m.group(2)), nodeFile, nodeLine, m.group(1) + ": " + m.group(2)));
                continue;
            }
            if ((m = EXPECTED.matcher(t)).find()) {
                out.add(new Problem(Kind.TEST, null, 0, "expected " + m.group(1) + " but got " + m.group(2)));
                continue;
            }
            if (t.matches(".*\\b(command not found|: not found)$") || t.matches("^(?:sh|bash|ash): \\S+: (?:command )?not found.*")) {
                out.add(new Problem(Kind.COMMAND, null, 0, t));
                continue;
            }
            if (t.matches(".*\\[stopped: it ran longer than .*") || t.matches("(?i).*timed? ?out.*") && t.length() < 160) {
                out.add(new Problem(Kind.TIMEOUT, null, 0, t));
                continue;
            }
            if ((m = GENERIC.matcher(t)).matches() && !t.matches("(?i).*\\b(warning|deprecat).*")) {
                out.add(new Problem(kindOf(m.group(1)), null, 0, m.group(1)));
                continue;
            }
            if (t.matches(".*(No such file or directory|Permission denied|OutOfMemoryError|MemoryError|Killed\\b).*") && t.length() < 300) {
                out.add(new Problem(kindOf(t), null, 0, t));
            }
        }
        // each error once (a log repeats them), at most three
        List<Problem> kept = new ArrayList<Problem>();
        Set<String> seen = new LinkedHashSet<String>();
        for (Problem p : out) {
            if (seen.add(p.kind + "|" + p.where()) && kept.size() < 3) {
                kept.add(p);
            }
        }
        return kept;
    }

    /** A runtime error's place, from the first frame of its stack: given to the error above it that has none. */
    private static void attachWhere(List<Problem> out, String file, int line) {
        if (!out.isEmpty() && out.get(out.size() - 1).file == null) {
            Problem p = out.remove(out.size() - 1);
            out.add(new Problem(p.kind, file, line, p.message));
        }
    }

    /** Javac names the missing symbol on the next line ("  symbol:   variable Foo"). */
    private static String symbolAfter(String[] lines, int i) {
        for (int j = i + 1; j < lines.length && j <= i + 4; j++) {
            Matcher m = Pattern.compile("^\\s*symbol:\\s+(.+)$").matcher(lines[j]);
            if (m.find()) {
                return " (" + m.group(1).trim().replaceAll("\\s+", " ") + ")";
            }
        }
        return "";
    }

    static Kind kindOf(String message) {
        String m = message.toLowerCase(Locale.US);
        if (m.matches(".*(no module named|modulenotfounderror|cannot find module|package .* does not exist|unresolved import|could not find package|importerror|cannot import name).*")) {
            return Kind.IMPORT;
        }
        if (m.matches(".*(cannot find symbol|unresolved reference|is not defined|not declared|undeclared identifier|nameerror|cannot find name|undefined:|undefined reference|cannot resolve symbol|no such method|has no attribute|does not exist on type|cannot find value).*")) {
            return Kind.SYMBOL;
        }
        if (m.matches(".*(syntaxerror|indentationerror|expected ';'|expected '.'|';' expected|unexpected token|parse error|parseerror|unexpected end|unterminated|illegal start|reached end of file|expected expression|invalid syntax|expecting).*")) {
            return Kind.SYNTAX;
        }
        if (m.matches(".*(nullpointerexception|'nonetype'|cannot read propert(y|ies) of (undefined|null)|null pointer|nil pointer|is null|undefined is not).*")) {
            return Kind.NULL;
        }
        if (m.matches(".*(incompatible types|typeerror|type mismatch|mismatched types|cannot be applied to|cannot be converted|is not assignable|ts2322|ts2345|classcastexception|wrong number of arguments|takes \\d+ positional).*")) {
            return Kind.TYPE;
        }
        if (m.matches(".*(indexerror|keyerror|indexoutofbounds|index out of range|out of bounds|no such element|nosuchelement).*")) {
            return Kind.INDEX;
        }
        if (m.matches(".*(no such file|filenotfounderror|enoent|file not found|cannot open).*")) {
            return Kind.FILE;
        }
        if (m.matches(".*(permission denied|eacces|eperm|access is denied|operation not permitted).*")) {
            return Kind.PERMISSION;
        }
        if (m.matches(".*(command not found|: not found).*")) {
            return Kind.COMMAND;
        }
        if (m.matches(".*(could not resolve|no matching distribution|eresolve|e404|version .* not found|could not find a version|unable to resolve dependency).*")) {
            return Kind.DEPENDENCY;
        }
        if (m.matches(".*(assertionerror|assertion failed|expected .* but|tests? failed|failures?:).*")) {
            return Kind.TEST;
        }
        if (m.matches(".*(outofmemoryerror|memoryerror|out of memory|killed).*")) {
            return Kind.MEMORY;
        }
        return Kind.OTHER;
    }

    /** What usually causes this kind of error, and what to look at next. */
    static String hint(Problem p) {
        String at = p.file == null ? "" : " Read " + p.file + (p.line > 0 ? " around line " + p.line : "") + " first.";
        switch (p.kind) {
            case SYMBOL:
                return "Likely: a name that doesn't exist where it's used: a typo, a missing import, or a method, field or"
                        + " variable that isn't defined (or was renamed)." + at + " Then search for the name's definition.";
            case IMPORT:
                return "Likely: a module or package that isn't there: a wrong name, or a dependency not installed or not"
                        + " declared. Check the spelling, then the dependency file (package.json, requirements.txt, build.gradle)"
                        + " and install or add it.";
            case SYNTAX:
                return "Likely: the code doesn't parse: a missing bracket, quote, colon or semicolon, usually at or just"
                        + " before the line named." + at;
            case TYPE:
                return "Likely: a value of the wrong type, or the wrong number of arguments: compare what the function takes"
                        + " with what is passed (the docs tool gives the signature)." + at;
            case NULL:
                return "Likely: something is null, None or undefined when it's used. Find where it should have been set, then"
                        + " set it or guard against it." + at;
            case INDEX:
                return "Likely: a list read past its end, or a key that isn't in the map. Check the size or the key first." + at;
            case FILE:
                return "Likely: a path that doesn't exist from where it runs. list_files shows the real path; or run from the"
                        + " right folder.";
            case PERMISSION:
                return "Likely: the file can't be read, written or run (a script needs chmod +x), or it's outside the workspace.";
            case COMMAND:
                return "Likely: the program isn't installed in Ibiko's Linux. Install it (apk add, pip install, npm install) or"
                        + " use another way.";
            case DEPENDENCY:
                return "Likely: a package name or version that can't be found, or versions that don't fit together. Check the"
                        + " name and version against the registry.";
            case TEST:
                return "Likely: the code gives a different result from what the test expects. Read the test and the code it"
                        + " calls; fix the code, unless the test itself is wrong (then say so)." + at;
            case MEMORY:
                return "Likely: the job is too big for the phone's memory. Build a smaller target, fewer parallel jobs, or use CI.";
            case TIMEOUT:
                return "Likely: it ran too long and was stopped: run a smaller target, or check it isn't waiting for input.";
            case GIT_BEHIND:
                return "Someone pushed meanwhile: sync (or pull), fix any conflicts, run the check, then push again (G4).";
            case GIT_CONFLICT:
                return "Both sides changed the same lines: in each file keep both changes, remove the <<<<<<< and >>>>>>> markers,"
                        + " delete the .theirs file, run the check, then push (G5).";
            case GIT_AUTH:
                return "The GitHub token can't reach this repository: tell the user to check the connector's token and its"
                        + " Contents permission. Retrying won't help (G6).";
            default:
                return "Read the first error: later ones are often caused by it." + at;
        }
    }

    /** The errors for the agent, the first with its likely cause; "" when the output shows none. */
    public static String summary(String output) {
        List<Problem> ps = read(output);
        if (ps.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder("Error read: ").append(clip(ps.get(0).where(), 300)).append("\n").append(hint(ps.get(0)));
        for (int i = 1; i < ps.size(); i++) {
            b.append("\nAlso: ").append(clip(ps.get(i).where(), 200));
        }
        return b.toString();
    }

    /**
     * The same error seen again: its kind and message, with numbers and paths taken out (and quoted values, except
     * for a missing module, command or name, which is the error). What lessons are filed under ({@link CodingLessons}).
     */
    public static String signature(Problem p) {
        // a missing module, command or name is the fact that matters ("No module named 'requests'"): kept; for
        // other kinds the names and quoted values are what changes from one time to the next, so they are taken out
        boolean named = p.kind == Kind.IMPORT || p.kind == Kind.COMMAND || p.kind == Kind.SYMBOL || p.kind == Kind.DEPENDENCY;
        String m = p.message.toLowerCase(Locale.US);
        if (!named) {
            m = m.replaceAll("'[^']*'|\"[^\"]*\"|`[^`]*`|<[^>]*>", "Q");
        }
        m = m.replaceAll("(?:[\\w.-]*/)+[\\w.-]+", "P").replaceAll("\\d+", "N").replaceAll("\\s+", " ").trim();
        return p.kind.name() + ":" + (m.length() > 120 ? m.substring(0, 120) : m);
    }

    private static String clip(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
