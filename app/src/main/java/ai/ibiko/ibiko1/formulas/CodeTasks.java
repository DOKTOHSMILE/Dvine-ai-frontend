package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Code tasks Ibiko 1 carries out itself, with no model: the request becomes one shell command for the built-in Linux
 * ("§SH ls -la /workspace"), which the pilot approves before it runs, as for every brain. A command typed as it is,
 * code pasted with "run this", files and folders, cloning, installing, versions. One line, because a function code is
 * one line: pasted code is written to /work with printf and then run. Pure Java.
 */
public final class CodeTasks {
    private CodeTasks() {
    }

    /** Words that start a shell command and nothing else in English: "git status" is a command, said as it is. */
    static final Set<String> COMMANDS = new HashSet<String>(Arrays.asList("ls", "pwd", "git", "python", "python3", "node",
            "npm", "npx", "pip", "pip3", "pkg", "apk", "gcc", "g++", "clang", "df", "du", "uname", "whoami", "cat", "mkdir",
            "cd", "grep", "wc", "tar", "curl", "wget", "javac", "java", "cargo", "rustc", "chmod", "cp", "mv", "rm", "echo",
            "ps", "ping", "ssh", "sed", "awk", "zip", "gradle", "kotlinc", "deno", "bun", "ruby", "php", "perl", "lua",
            "cmake", "head", "tail"));
    /** Of those, the ones whose plain sub-commands are commands too ("git status", "npm install", "pkg upgrade"). */
    static final Set<String> PLAIN = new HashSet<String>(Arrays.asList("ls", "pwd", "git", "npm", "npx", "pip", "pip3",
            "pkg", "apk", "cargo", "gradle", "uname", "whoami", "df", "du", "python3", "node"));

    private static final Pattern FENCE = Pattern.compile("```\\s*([A-Za-z0-9+#-]*)\\s*\\n(.*?)```", Pattern.DOTALL);
    private static final Pattern RUN_WORD = Pattern.compile("\\b(?:run|execute|exec|test|try)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TICKS = Pattern.compile("^(?:please )?(?:run|execute|exec)?(?: the command| this command| this)?:?\\s*`([^`\\n]{1,500})`$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RUN_COMMAND = Pattern.compile("^(?:please )?(?:run|execute) (?:the )?command:? (.{1,500})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOLLAR = Pattern.compile("^\\$ (.{1,500})$");
    private static final Pattern LIST = Pattern.compile("^(?:please )?(?:list|show)(?: me)? (?:all )?(?:the |my )?files(?: (?:in|inside|under) (?:the )?(?:folder )?(\\S+))?$|^what(?:'s| is) in (?:the )?(?:folder |directory )?(\\S+)$");
    private static final Pattern READ = Pattern.compile("^(?:please )?(?:show|read|open|print|display)(?: me)? (?:the )?(?:file|contents of(?: the file)?) (\\S+)$");
    private static final Pattern CLONE = Pattern.compile("^(?:please )?clone (?:the )?(?:private )?(?:github )?(?:repo(?:sitory)? )?(https://github\\.com/[\\w.-]+/[\\w.-]+?|[\\w.-]+/[\\w.-]+?)(?:\\.git)?/?$");
    private static final Pattern PULL_NAMED = Pattern.compile("^(?:please )?pull (?:my |the )?(?:private )?(?:github )?(?:repo(?:sitory)? )([\\w.-]+)$");
    private static final Pattern PULL_HERE = Pattern.compile("^(?:please )?pull$|^(?:please )?pull (?:the |my )?(?:private )?(?:github )?(?:repo(?:sitory)?)$");
    private static final Pattern INSTALL = Pattern.compile("^(?:please )?install (?:the )?(?:package )?([a-z0-9][a-z0-9.+_-]{0,60})(?: package)?$");
    private static final Pattern MKDIR = Pattern.compile("^(?:please )?(?:make|create) (?:a )?(?:new )?(?:folder|directory)(?: called| named)? (\\S+)$");
    private static final Pattern TOUCH = Pattern.compile("^(?:please )?(?:make|create) (?:a )?(?:new )?(?:empty )?file(?: called| named)? (\\S+)$");
    private static final Pattern VERSION = Pattern.compile("^(?:what(?:'s| is) the |which )?(python|node|git|java|gcc|npm|pip) version(?: is (?:installed|here))?$|^is (python|node|git|java|gcc|npm|pip) installed$");
    private static final Pattern WHERE = Pattern.compile("^(?:where am i|which folder am i in|(?:what(?:'s| is) the )?current (?:folder|directory))$");
    private static final Pattern SPACE = Pattern.compile("^(?:how much )?(?:free )?(?:disk |storage )?space(?: is)?(?: left| free)?(?: on (?:the |this )?device)?$|^how much (?:disk |storage )?space (?:is )?(?:left|free)$");

    /** The one shell command for a code task, or null when the message is not one. */
    public static String command(String message) {
        if (message == null) {
            return null;
        }
        String raw = message.trim();
        Matcher f = FENCE.matcher(raw);
        if (f.find()) {
            String outside = (raw.substring(0, f.start()) + " " + raw.substring(f.end())).trim();
            return RUN_WORD.matcher(outside).find() ? script(f.group(1), f.group(2)) : null;
        }
        if (raw.contains("\n")) {
            return null;
        }
        String one = raw.replaceAll("\\s+", " ");
        Matcher m;
        if ((m = TICKS.matcher(one)).matches() || (m = RUN_COMMAND.matcher(one)).matches() || (m = DOLLAR.matcher(one)).matches()) {
            return m.group(1).trim();
        }
        String first = one.split(" ", 2)[0];
        if (COMMANDS.contains(first) && !one.endsWith("?") && looksTyped(first, one)) {
            return one;
        }
        String low = one.toLowerCase(Locale.US).replaceAll("[.!?]+$", "");
        if ((m = LIST.matcher(low)).matches()) {
            String dir = m.group(1) != null ? m.group(1) : m.group(2);
            return "ls -la " + (dir == null ? "/workspace" : q(path(one, dir)));
        }
        if ((m = READ.matcher(low)).matches()) {
            return "cat -- " + q(path(one, m.group(1)));
        }
        if ((m = CLONE.matcher(low)).matches()) {
            String repo = path(one, m.group(1)).replaceAll("\\.git/?$|/$", "");
            String url = repo.startsWith("https://") ? repo : "https://github.com/" + repo;
            return "cd /workspace && git clone " + q(url);
        }
        if ((m = PULL_NAMED.matcher(low)).matches()) {
            return "git -C /workspace/repos/" + path(one, m.group(1)) + " pull";
        }
        if (PULL_HERE.matcher(low).matches()) {
            return "git pull";
        }
        if ((m = INSTALL.matcher(low)).matches()) {
            return "pkg install " + m.group(1);
        }
        if ((m = MKDIR.matcher(low)).matches()) {
            return "mkdir -p -- " + q(path(one, m.group(1)));
        }
        if ((m = TOUCH.matcher(low)).matches()) {
            return "touch -- " + q(path(one, m.group(1)));
        }
        if ((m = VERSION.matcher(low)).matches()) {
            String tool = m.group(1) != null ? m.group(1) : m.group(2);
            return versionOf(tool);
        }
        if (WHERE.matcher(low).matches()) {
            return "pwd";
        }
        if (SPACE.matcher(low).matches()) {
            return "df -h";
        }
        return null;
    }

    /** "git status", "ls -la", "python3 app.py": typed as a command, not a sentence that starts with the word. */
    private static boolean looksTyped(String first, String s) {
        String[] w = s.split(" ");
        if (w.length == 1) {
            return PLAIN.contains(first);
        }
        if (s.matches(".*(?:\\s-{1,2}[A-Za-z]|[/=|&>]|\\.[A-Za-z0-9]|\\$\\(|~).*")) {
            return true;
        }
        // "git status", "npm install", "pip list": a command and a sub-command, short and lower case
        return PLAIN.contains(first) && w.length <= 4 && s.equals(s.toLowerCase(Locale.US))
                && !s.matches(".*\\b(?:the|a|an|my|me|please|is|are|what|how|why|you)\\b.*");
    }

    /** The words as the pilot wrote them (a path keeps its capitals). */
    private static String path(String original, String lowered) {
        int i = original.toLowerCase(Locale.US).indexOf(lowered);
        return i < 0 ? lowered : original.substring(i, i + lowered.length());
    }

    private static String versionOf(String tool) {
        switch (tool) {
            case "python":
                return "python3 --version";
            case "java":
                return "java -version";
            case "gcc":
                return "gcc --version";
            default:
                return tool + " --version";
        }
    }

    /** Pasted code: written to /work as lines with printf, then run with the right program (installed when missing). */
    static String script(String lang, String code) {
        String l = lang == null ? "" : lang.toLowerCase(Locale.US);
        String file, run, need;
        switch (l) {
            case "python":
            case "py":
            case "python3":
                file = "/work/ibiko_run.py";
                run = "python3 " + file;
                need = "python3";
                break;
            case "js":
            case "javascript":
            case "node":
                file = "/work/ibiko_run.js";
                run = "node " + file;
                need = "nodejs";
                break;
            case "c":
                file = "/work/ibiko_run.c";
                run = "gcc " + file + " -o /work/ibiko_run && /work/ibiko_run";
                need = "gcc musl-dev";
                break;
            case "cpp":
            case "c++":
                file = "/work/ibiko_run.cpp";
                run = "g++ " + file + " -o /work/ibiko_run && /work/ibiko_run";
                need = "g++ musl-dev";
                break;
            default:
                if (!(l.isEmpty() || l.equals("sh") || l.equals("bash") || l.equals("shell") || l.equals("console"))) {
                    return null;
                }
                file = "/work/ibiko_run.sh";
                run = "sh " + file;
                need = null;
        }
        List<String> lines = new ArrayList<String>(Arrays.asList(code.replace("\r", "").split("\n", -1)));
        while (!lines.isEmpty() && lines.get(lines.size() - 1).trim().isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        if (lines.isEmpty()) {
            return null;
        }
        StringBuilder b = new StringBuilder("mkdir -p /work && printf '%s\\n'");
        for (String line : lines) {
            b.append(' ').append(q(line));
        }
        b.append(" > ").append(file).append(" && ");
        if (need != null) {
            String program = need.split(" ")[0].equals("nodejs") ? "node" : need.split(" ")[0];
            b.append("{ command -v ").append(program).append(" >/dev/null 2>&1 || apk add -q ").append(need).append("; } && ");
        }
        return b.append(run).toString();
    }

    /** A shell word in single quotes. */
    static String q(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
