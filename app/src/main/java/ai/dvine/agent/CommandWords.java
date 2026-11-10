package ai.dvine.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a shell command does, in words, for the steps the user sees: "ls -la src" is "Listed the files in src",
 * "grep -rn TODO app" is "Searched for “TODO” in app", "cat a; wc -l a" is "Read a, then counted the lines in a": every command named. The command itself
 * stays one tap away (the step's detail). Pure Java.
 */
public final class CommandWords {
    private CommandWords() {
    }

    public static String describe(String command) {
        if (command == null || command.trim().isEmpty()) {
            return "Ran a command";
        }
        List<String> parts = new ArrayList<String>();
        for (String p : grouped(split(command))) {
            String t = p.trim();
            if (t.isEmpty() || t.matches("^cd\\s.*") || label(t)) {
                continue; // directory changes and "=== section ===" labels say nothing on their own
            }
            parts.add(t);
        }
        if (parts.isEmpty()) {
            return label(command.trim()) ? "Printed a line" : "Ran a command";
        }
        // every command named, none counted away: "Listed the files in app, then read app/Main.java"
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            String said = one(parts.get(i));
            if (i > 0) {
                b.append(", then ").append(Character.toLowerCase(said.charAt(0))).append(said.substring(1));
            } else {
                b.append(said);
            }
        }
        return b.toString();
    }

    /** An echo or printf to the screen only: a label between commands ("=== git config"), not work. */
    static boolean label(String t) {
        return t.matches("(?s)^(echo|printf)(\\s.*)?$") && !t.matches("(?s).*[^2]>.*");
    }

    /**
     * The commands of a line, split at ; && || and line ends, but never inside quotes, $(...), or a here-document:
     * {@code python3 -c "import json; print(1)"} is one command, not two.
     */
    static List<String> split(String line) {
        List<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        char quote = 0;
        int paren = 0;
        String s = line;
        int heredoc = s.indexOf("<<");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (heredoc >= 0 && i == heredoc) {
                cur.append(s.substring(i)); // a here-document runs to the end: the rest is its text
                break;
            }
            if (quote != 0) {
                cur.append(c);
                if (c == '\\' && quote == '"' && i + 1 < s.length()) {
                    cur.append(s.charAt(++i));
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                cur.append(c);
                continue;
            }
            if (c == '(' ) {
                paren++;
            } else if (c == ')' && paren > 0) {
                paren--;
            }
            boolean cut = paren == 0 && (c == ';' || c == '\n'
                    || (c == '&' || c == '|') && i + 1 < s.length() && s.charAt(i + 1) == c);
            if (cut) {
                out.add(cur.toString());
                cur.setLength(0);
                if (c == '&' || c == '|') {
                    i++;
                }
                continue;
            }
            cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }

    /** A loop or an if, from its first word to its done or fi, kept as one command. */
    static List<String> grouped(List<String> parts) {
        List<String> out = new ArrayList<String>();
        StringBuilder block = null;
        int depth = 0;
        for (String p : parts) {
            String t = p.trim();
            String first = t.split("\\s+", 2)[0];
            if (first.matches("for|while|until|if|case")) {
                depth++;
                if (block == null) {
                    block = new StringBuilder();
                }
            }
            if (block != null) {
                block.append(block.length() == 0 ? "" : "; ").append(t);
                if (first.matches("done|fi|esac") || t.matches("(?s).*\\b(done|fi|esac)$")) {
                    depth--;
                }
                if (depth <= 0) {
                    out.add(block.toString());
                    block = null;
                    depth = 0;
                }
                continue;
            }
            out.add(t);
        }
        if (block != null) {
            out.add(block.toString());
        }
        return out;
    }

    /** A loop, an if, or a script given inline, in words: what it goes through and what it does each time. */
    static String script(String cmd) {
        String t = cmd.trim();
        java.util.regex.Matcher inline = java.util.regex.Pattern.compile("^(python3?|node|perl|ruby|php|sh|bash|ash)\\b.*?\\s-[ce]\\s").matcher(t);
        if (inline.find()) {
            String lang = inline.group(1).replaceAll("\\d", "");
            String name = lang.equals("python") ? "Python" : lang.equals("node") ? "JavaScript" : lang.equals("perl") ? "Perl"
                    : lang.equals("ruby") ? "Ruby" : lang.equals("php") ? "PHP" : "shell";
            return "Ran a short " + name + " script";
        }
        if (t.contains("<<")) {
            return t.matches("(?s)^cat\\s*>.*") ? "Wrote " + clean(t.replaceAll("(?s)^cat\\s*>+\\s*(\\S+).*", "$1")) : "Ran a script";
        }
        java.util.regex.Matcher loop = java.util.regex.Pattern.compile("(?s)^(for|while|until)\\s+(\\w+)?(?:\\s+in\\s+(.*?))?;\\s*do\\s*(.*?);?\\s*done.*$").matcher(t);
        if (loop.matches()) {
            String over = loop.group(3) == null ? "" : loop.group(3).trim();
            String body = loop.group(4) == null ? "" : loop.group(4);
            List<String> inner = new ArrayList<String>();
            for (String p : grouped(split(body))) {
                String q = p.trim();
                if (!q.isEmpty() && !label(q) && !q.matches("^(if|then|else|elif|fi|do|done)\\b.*") && inner.size() < 2) {
                    inner.add(lower(one(q)));
                }
            }
            String what = over.isEmpty() ? "each item" : over.startsWith("$(") ? "each file found" : over.split("\\s+").length == 1
                    ? clean(over) : "each of " + over.split("\\s+").length + " items";
            return "Went through " + what + (inner.isEmpty() ? "" : " and " + String.join(" and ", inner));
        }
        if (t.startsWith("if ")) {
            return "Checked a condition and acted on it";
        }
        return null;
    }

    private static String lower(String said) {
        return said.isEmpty() ? said : Character.toLowerCase(said.charAt(0)) + said.substring(1);
    }

    /** One command (with its pipes) in words. */
    static String one(String cmd) {
        String scripted = script(cmd);
        if (scripted != null) {
            return scripted;
        }
        String[] piped = cmd.split("\\s*\\|\\s*");
        String main = piped[0].trim();
        String[] w = words(main);
        if (w.length == 0) {
            return "Ran a command";
        }
        String verb = w[0].replaceAll("^.*/", "");
        List<String> args = new ArrayList<String>();
        List<String> flags = new ArrayList<String>();
        for (int i = 1; i < w.length; i++) {
            if (w[i].startsWith("-")) {
                flags.add(w[i]);
            } else if (!w[i].startsWith(">") && !w[i].startsWith("2>")) {
                args.add(w[i]);
            }
        }
        String target = args.isEmpty() ? "the workspace" : args.get(args.size() - 1).replaceAll("/+$", "");
        if (target.equals(".") || target.equals("./")) {
            target = "the workspace";
        }
        String said;
        switch (verb) {
            case "ls":
            case "tree":
                said = "Listed the files in " + target;
                break;
            case "cat":
            case "less":
            case "more":
                said = "Read " + list(args);
                break;
            case "head":
                said = "Read the start of " + target;
                break;
            case "tail":
                said = "Read the end of " + target;
                break;
            case "wc":
                said = "Counted the lines in " + list(args);
                break;
            case "find":
                String name = after(w, "-name");
                said = name != null ? "Looked for files named " + q(name) + " in " + (args.isEmpty() ? "the workspace" : clean(args.get(0)))
                        : "Looked through the files in " + (args.isEmpty() ? "the workspace" : clean(args.get(0)));
                break;
            case "grep":
            case "rg":
            case "ag":
                said = "Searched for " + q(args.isEmpty() ? "text" : args.get(0)) + (args.size() > 1 ? " in " + clean(args.get(args.size() - 1)) : "");
                break;
            case "mkdir":
                said = "Made the folder " + target;
                break;
            case "rm":
                said = "Deleted " + list(args);
                break;
            case "cp":
                said = args.size() >= 2 ? "Copied " + clean(args.get(0)) + " to " + clean(args.get(args.size() - 1)) : "Copied files";
                break;
            case "mv":
                said = args.size() >= 2 ? "Moved " + clean(args.get(0)) + " to " + clean(args.get(args.size() - 1)) : "Moved files";
                break;
            case "touch":
                said = "Created " + list(args);
                break;
            case "echo":
            case "printf":
                said = main.contains(">") ? "Wrote to " + clean(main.replaceAll(".*>+\\s*", "").split("\\s")[0]) : "Printed a line";
                break;
            case "sed":
                said = flags.contains("-i") ? "Changed text in " + target : "Read part of " + target;
                break;
            case "diff":
                said = "Compared " + list(args);
                break;
            case "du":
            case "df":
                said = "Checked the space used" + (args.isEmpty() ? "" : " by " + target);
                break;
            case "pwd":
                said = "Checked the current folder";
                break;
            case "chmod":
                said = "Changed who can run " + target;
                break;
            case "unzip":
                said = "Unzipped " + target;
                break;
            case "zip":
            case "tar":
                said = "Packed files into " + (args.isEmpty() ? "an archive" : clean(args.get(0)));
                break;
            case "curl":
            case "wget":
                said = "Downloaded " + host(args);
                break;
            case "python":
            case "python3":
            case "node":
            case "sh":
            case "bash":
            case "java":
            case "ruby":
            case "php":
                said = args.isEmpty() ? "Ran " + verb : "Ran " + clean(args.get(0));
                break;
            case "git":
                said = "Git: " + (args.isEmpty() ? "status" : String.join(" ", args.subList(0, Math.min(2, args.size()))));
                break;
            case "npm":
            case "yarn":
            case "pip":
            case "pip3":
                said = (args.isEmpty() ? verb : verb + " " + args.get(0)).replaceAll("^(npm|yarn|pip3?) install.*", "Installed packages")
                        .replaceAll("^(npm|yarn) (test|run test).*", "Ran the tests");
                break;
            case "date":
                said = "Checked the date";
                break;
            case "whoami":
            case "uname":
            case "env":
                said = "Checked the system";
                break;
            default:
                said = "Ran " + verb;
        }
        if (piped.length > 1) {
            String last = words(piped[piped.length - 1].trim()).length == 0 ? "" : words(piped[piped.length - 1].trim())[0];
            if (last.equals("head")) {
                said += " (the first lines)";
            } else if (last.equals("wc")) {
                said += " and counted them";
            } else if (last.equals("grep")) {
                said += " and filtered them";
            } else if (last.equals("sort")) {
                said += ", sorted";
            }
        }
        return said;
    }

    private static String[] words(String s) {
        List<String> out = new ArrayList<String>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]*)\"|'([^']*)'|(\\S+)").matcher(s);
        while (m.find()) {
            out.add(m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3));
        }
        return out.toArray(new String[0]);
    }

    private static String after(String[] w, String flag) {
        for (int i = 0; i < w.length - 1; i++) {
            if (w[i].equals(flag)) {
                return w[i + 1];
            }
        }
        return null;
    }

    private static String list(List<String> args) {
        if (args.isEmpty()) {
            return "a file";
        }
        List<String> names = new ArrayList<String>();
        for (String a : args) {
            names.add(clean(a));
        }
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    private static String clean(String s) {
        String t = s.replaceAll("/+$", "");
        return t.equals(".") ? "the workspace" : t;
    }

    private static String q(String s) {
        return "“" + (s.length() > 40 ? s.substring(0, 40) + "…" : s) + "”";
    }

    private static String host(List<String> args) {
        for (String a : args) {
            if (a.toLowerCase(Locale.US).startsWith("http")) {
                return a.replaceAll("^https?://", "").replaceAll("/.*$", "");
            }
        }
        return "a file";
    }
}
