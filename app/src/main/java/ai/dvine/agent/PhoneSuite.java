package ai.dvine.agent;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The phone is the suite a model works through. Listing, reading, searching, editing and git run here, including
 * fetching main and merging it. A server model is only asked to decide, and a merge hands it the conflict pieces,
 * not the repository.
 */
public final class PhoneSuite {
    /**
     * Told to every coding model, including a small one on the phone. The tools do the work; the model does not
     * ask a server to hold the project.
     */
    public static final String NOTE = "The phone is your suite. list, read, search, edit and git run on the device,"
            + " including fetch and merge of main. Do not ask a server to read the repository or to merge it: call the"
            + " tool. A merge returns only the conflict pieces, not the whole project.\n";

    /** A git job read from the user's own words, with no model. Null when the sentence is not that job. */
    public static final class GitOrder {
        public final String action;
        public final String branch;
        public final String path;

        GitOrder(String action, String branch, String path) {
            this.action = action;
            this.branch = branch;
            this.path = path == null ? "" : path;
        }
    }

    private static final Pattern IN_FOLDER = Pattern.compile(" (?:in|into|inside|on) ([\\w./-]+)$");
    private static final Pattern BRANCH = Pattern.compile("(?:origin/)?([\\w.-]+(?:/[\\w.-]+)*)");

    private PhoneSuite() {
    }

    /** True for work the phone does itself. A chat reply is not one of these. */
    public static boolean local(String tool) {
        if (tool == null) {
            return false;
        }
        switch (tool) {
            case "list_files":
            case "read_file":
            case "search":
            case "write_file":
            case "edit_file":
            case "make_file":
            case "zip":
            case "unzip":
            case "run":
            case "termux":
            case "git":
            case "diff":
                return true;
            default:
                return false;
        }
    }

    /**
     * "fetch main and merge", "merge origin/main", "sync with main in app". Questions and long messages are left
     * for the model. The branch defaults to main.
     */
    public static GitOrder read(String text) {
        if (text == null) {
            return null;
        }
        String raw = text.trim();
        if (raw.isEmpty() || raw.indexOf('\n') >= 0 || raw.indexOf('?') >= 0 || raw.length() > 180) {
            return null;
        }
        String n = raw.toLowerCase(Locale.US).replaceAll("[.!,]+$", "").replaceAll("\\s+", " ").trim();
        n = n.replaceFirst("^(please |kindly )", "");
        n = n.replaceFirst("^git ", "");
        String path = "";
        Matcher folder = IN_FOLDER.matcher(n);
        if (folder.find()) {
            path = folder.group(1);
            if (path.contains("..")) {
                return null;
            }
            n = n.substring(0, folder.start()).trim();
        }
        String action = actionOf(n);
        if (action == null) {
            return null;
        }
        String branch = branchOf(n);
        if ("sync".equals(action) && branch == null && !n.contains("conflict") && !n.contains("merge")
                && !n.contains("main") && !n.contains("master")) {
            return null;
        }
        if (branch == null) {
            branch = "main";
        }
        if (!branch.matches("[\\w.-]+(?:/[\\w.-]+)*") || branch.contains("..")) {
            return null;
        }
        return new GitOrder(action, branch, path);
    }

    private static String actionOf(String n) {
        if (n.matches("^(fetch|pull)( origin)?( [\\w./-]+)? and merge( .*)?$")) {
            return "sync";
        }
        if (n.matches("^fix (the )?(merge )?conflicts? (with|from|on)( origin)?( [\\w./-]+)?$")) {
            return "sync";
        }
        if (n.matches("^(sync|update)( (with|from))?( origin)?( [\\w./-]+)?$")) {
            return "sync";
        }
        if (n.matches("^bring( in)?( origin)?( [\\w./-]+)?$")) {
            return "sync";
        }
        if (n.matches("^fetch( origin)?( [\\w./-]+)?$")) {
            return "fetch";
        }
        if (n.matches("^merge( origin)?( [\\w./-]+)?( into (this|the current)( branch)?)?$")) {
            return "merge";
        }
        return null;
    }

    private static String branchOf(String n) {
        Matcher m = BRANCH.matcher(n);
        String found = null;
        while (m.find()) {
            String w = m.group(1);
            if (w.equals("fetch") || w.equals("pull") || w.equals("merge") || w.equals("sync") || w.equals("update")
                    || w.equals("from") || w.equals("with") || w.equals("on") || w.equals("bring") || w.equals("in")
                    || w.equals("and") || w.equals("fix") || w.equals("the") || w.equals("conflicts") || w.equals("conflict")
                    || w.equals("into") || w.equals("this") || w.equals("current") || w.equals("branch") || w.equals("origin")
                    || w.equals("to") || w.equals("it") || w.equals("please")) {
                continue;
            }
            found = w;
        }
        return found;
    }
}
