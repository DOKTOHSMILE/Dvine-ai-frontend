package ai.dvine.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a shell command does, in words, for the steps the user sees: "ls -la src" is "Listed the files in src",
 * "grep -rn TODO app" is "Searched for “TODO” in app", "cat a; wc -l a" is "Read a and 1 more". The command itself
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
        for (String p : command.split("\\s*(?:;|&&|\\|\\|)\\s*")) {
            String t = p.trim();
            if (t.isEmpty() || t.matches("^(echo|printf)\\s+['\"]?-*['\"]?$") || t.matches("^cd\\s.*")) {
                continue; // separators and directory changes say nothing on their own
            }
            parts.add(t);
        }
        if (parts.isEmpty()) {
            return "Ran a command";
        }
        String first = one(parts.get(0));
        int more = parts.size() - 1;
        return more == 0 ? first : first + " and " + more + " more";
    }

    /** One command (with its pipes) in words. */
    static String one(String cmd) {
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
        String first = clean(args.get(0));
        return args.size() == 1 ? first : first + " and " + (args.size() - 1) + " more";
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
