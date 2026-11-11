package ai.ibiko.agent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The GitHub repositories a chat may work on: the ones the pilot ticked in Tools › GitHub, plus any the pilot names
 * in the message itself ("owner/name" or a github.com address). With a choice made, the agent lists, clones and
 * reads only those: another repository is refused with the reason, so a task never spreads to the whole account.
 * No choice made: every repository is open, as before. Pure Java.
 */
public final class RepoScope {
    private static final Pattern NAMED = Pattern.compile("(?i)(?:github\\.com/)?\\b([a-z0-9][\\w.-]*/[a-z0-9][\\w.-]*?)(?:\\.git)?(?=$|[\\s,;:)\\]'\"`]|\\.(?:\\s|$))");
    private final Set<String> chosen = new LinkedHashSet<String>();

    private RepoScope() {
    }

    /** Every repository open (nothing chosen). */
    public static RepoScope all() {
        return new RepoScope();
    }

    /** The chosen repositories ("owner/name"), and those the pilot names in {@code message}. */
    public static RepoScope of(List<String> chosenRepos, String message) {
        RepoScope s = new RepoScope();
        if (chosenRepos == null || chosenRepos.isEmpty()) {
            return s; // nothing chosen: nothing limited
        }
        for (String r : chosenRepos) {
            String k = key(r);
            if (!k.isEmpty()) {
                s.chosen.add(k);
            }
        }
        if (message != null) {
            Matcher m = NAMED.matcher(message);
            while (m.find()) {
                String k = key(m.group(1));
                if (k.contains("/") && !k.startsWith("repos/") && !k.startsWith("memory/") && !k.contains("..")) {
                    s.chosen.add(k);
                }
            }
        }
        return s;
    }

    /** Whether a choice limits the work. */
    public boolean limited() {
        return !chosen.isEmpty();
    }

    /** The repositories in scope, "owner/name", in the order chosen. */
    public List<String> repos() {
        return new ArrayList<String>(chosen);
    }

    /** "https://github.com/Me/App.git", "me/app" → "me/app"; a bare name stays a bare name. */
    static String key(String ref) {
        if (ref == null) {
            return "";
        }
        String r = ref.trim().toLowerCase(Locale.US).replaceAll("^https?://", "").replaceAll("^(www\\.)?github\\.com/", "")
                .replaceAll("(\\.git)?/*$", "");
        String[] parts = r.split("/");
        return parts.length >= 2 ? parts[0] + "/" + parts[1] : r;
    }

    /** Whether the agent may work on {@code ref} (an address, "owner/name" or a bare name). */
    public boolean allows(String ref) {
        if (!limited()) {
            return true;
        }
        String k = key(ref);
        if (k.isEmpty()) {
            return false;
        }
        for (String c : chosen) {
            if (c.equals(k) || !k.contains("/") && c.endsWith("/" + k)) {
                return true;
            }
        }
        return false;
    }

    /** What the agent is told when it reaches for another repository. */
    public String refusal(String ref) {
        return "Not done: " + ref + " isn't one of the repositories the pilot chose for this chat (" + String.join(", ", chosen)
                + "). Work only on those. Another one needs the pilot to tick it in Tools › GitHub, or to name it in a message.";
    }

    /**
     * A GitHub connector call checked against the choice: null when it may go ahead, or what the agent gets instead.
     * The account's full list is answered with the chosen ones only; a call naming another repository is refused.
     */
    public String check(String call) {
        if (!limited() || call == null) {
            return null;
        }
        String c = call.trim();
        String tool = c.split("\\s+", 2)[0].toLowerCase(Locale.US);
        if (!tool.startsWith("github.")) {
            return null;
        }
        if (tool.equals("github.repos")) {
            StringBuilder b = new StringBuilder("The repositories the pilot chose for this chat (work only on these):\n");
            for (String r : chosen) {
                b.append("- ").append(r).append('\n');
            }
            return b.toString().trim();
        }
        Matcher m = Pattern.compile("(?i)\\b([a-z0-9][\\w.-]*/[a-z0-9][\\w.-]*)").matcher(c.substring(tool.length()));
        while (m.find()) {
            if (!allows(m.group(1))) {
                return refusal(m.group(1));
            }
        }
        return null;
    }

    /**
     * A shell command checked against the choice: null when it may run, or the refusal when it clones (or
     * fetches with gh) a GitHub repository that wasn't chosen.
     */
    public String checkCommand(String command) {
        if (!limited() || command == null || !command.matches("(?is).*\\b(git\\s+clone|gh\\s+repo\\s+clone)\\b.*")) {
            return null;
        }
        Matcher m = Pattern.compile("(?i)(?:github\\.com[/:])([\\w.-]+/[\\w.-]+)|gh\\s+repo\\s+clone\\s+([\\w.-]+/[\\w.-]+)").matcher(command);
        while (m.find()) {
            String ref = m.group(1) != null ? m.group(1) : m.group(2);
            if (!allows(ref)) {
                return refusal(ref);
            }
        }
        return null;
    }

    /** The line the brain reads at the start of the task. */
    public String brief() {
        return !limited() ? "" : "The pilot chose these GitHub repositories for this chat: " + String.join(", ", chosen)
                + ". Work only on them: do not list, clone, read or change any other repository unless the pilot names it.";
    }
}
