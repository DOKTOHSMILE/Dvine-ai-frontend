package ai.dvine.linux;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A cloned repository belongs in {@code memory/projects}, the folder the workspace screen shows, not in Linux's
 * home directory.
 */
public final class CloneFolder {
    public static final String RELATIVE = "memory/projects";
    /** That folder inside Alpine, where the coding workspace is mounted. */
    public static final String GUEST = "/workspace/" + RELATIVE;
    private static final Pattern CLONE = Pattern.compile("(?i)git\\s+clone\\b([^\\n]*)");
    private static final Pattern PROGRESS = Pattern.compile(
            "(?i)^(remote:\\s*)?(counting|compressing|receiving|resolving|enumerating) objects:.*");

    private CloneFolder() {
    }

    /** The repository's name from its address: {@code owner/Dvine-ai-frontend.git} is {@code Dvine-ai-frontend}. */
    public static String nameOf(String url) {
        if (url == null) {
            return "repo";
        }
        String u = url.trim().replaceAll("\\.git$", "").replaceAll("/+$", "");
        int slash = Math.max(u.lastIndexOf('/'), u.lastIndexOf(':'));
        String name = slash < 0 ? u : u.substring(slash + 1);
        name = name.replaceAll("[^A-Za-z0-9._-]", "");
        return name.isEmpty() ? "repo" : name;
    }

    /**
     * The same command, with every {@code git clone} writing into {@link #GUEST}. A destination the model invented
     * (such as {@code /root/workspace/...}) is replaced.
     */
    public static String place(String command) {
        if (command == null || !command.toLowerCase(Locale.US).contains("git clone")
                || command.contains(RELATIVE)) {
            return command;
        }
        Matcher m = CLONE.matcher(command);
        StringBuffer out = new StringBuffer();
        boolean changed = false;
        while (m.find()) {
            String fixed = retarget(m.group(1));
            changed |= !fixed.equals(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement("git clone" + fixed));
        }
        m.appendTail(out);
        return changed ? "mkdir -p " + GUEST + "\n" + out.toString().trim() : command;
    }

    private static String retarget(String args) {
        String[] bits = args.trim().isEmpty() ? new String[0] : args.trim().split("\\s+");
        List<String> flags = new ArrayList<String>();
        List<String> words = new ArrayList<String>();
        for (int i = 0; i < bits.length; i++) {
            String bit = bits[i];
            if (bit.startsWith("-") && !bit.equals("-")) {
                flags.add(bit);
                if (bit.matches("(?i)--(branch|depth|origin|reference|config|template|separate-git-dir|jobs)")
                        || bit.matches("(?i)-[bcjo]")) {
                    if (i + 1 < bits.length) {
                        flags.add(bits[++i]);
                    }
                }
                continue;
            }
            words.add(bit);
        }
        if (words.isEmpty()) {
            return args;
        }
        String url = words.get(0);
        String dest = GUEST + "/" + nameOf(url);
        StringBuilder b = new StringBuilder();
        for (String f : flags) {
            b.append(' ').append(f);
        }
        b.append(' ').append(url).append(' ').append(dest);
        return b.toString();
    }

    /** Git's progress lines ("Counting objects: 37%") become one line. The rest of the text stays. */
    public static String quiet(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String[] lines = text.split("\n", -1);
        StringBuilder b = new StringBuilder();
        int n = 0;
        String last = null;
        for (String line : lines) {
            String t = line.trim();
            if (PROGRESS.matcher(t).matches() || t.matches("(?i)remote:\\s+.*\\d+%.*")) {
                n++;
                last = t.replaceFirst("(?i)^remote:\\s*", "");
                continue;
            }
            if (!t.isEmpty()) {
                b.append(line).append('\n');
            }
        }
        if (n > 0 && last != null) {
            b.append("Cloning: ").append(last).append('\n');
        }
        String out = b.toString().trim();
        return out.isEmpty() ? text.trim() : out;
    }

    /** True when a block is almost only that progress, so the chat should not open it as code. */
    public static boolean mostlyProgress(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        int lines = 0;
        int progress = 0;
        for (String line : text.split("\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            lines++;
            if (PROGRESS.matcher(line.trim()).matches() || line.trim().matches("(?i)(remote:\\s+.*|cloning into\\s+.*)")) {
                progress++;
            }
        }
        return lines >= 4 && progress * 100 / lines >= 60;
    }
}
