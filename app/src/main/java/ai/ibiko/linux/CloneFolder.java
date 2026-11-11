package ai.ibiko.linux;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A cloned repository belongs in {@code repos} (the chat's own folder: pulled data, apart from its memories), the
 * folder the workspace screen shows, not in Linux's home directory.
 */
public final class CloneFolder {
    public static final String RELATIVE = "repos";
    /** That folder inside Alpine, where the coding workspace is mounted. */
    public static final String GUEST = "/workspace/" + RELATIVE;
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
     * (such as {@code /root/workspace/...}) is replaced, even when the same command also mentions {@code repos}.
     * Flags between {@code git} and {@code clone} ({@code git -c key=value clone}) are kept.
     */
    public static String place(String command) {
        if (command == null || !command.toLowerCase(Locale.US).contains("clone")) {
            return command;
        }
        Matcher m = CLONE.matcher(command);
        StringBuffer out = new StringBuffer();
        boolean changed = false;
        while (m.find()) {
            String args = m.group(2) == null ? "" : m.group(2);
            String fixed = retarget(args);
            String dest = destOf(fixed);
            String replacement = m.group(1) + "git clone" + fixed;
            if (dest != null && !command.contains("checkout -f HEAD") && !fixed.equals(args)) {
                replacement = replacement + "\n" + checkout(dest);
            }
            changed |= !fixed.equals(args);
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        if (!changed) {
            return command;
        }
        return "mkdir -p " + GUEST + "\n" + out.toString().trim();
    }

    /**
     * Moves a repository that landed in Linux's home ({@code /root/workspace/<name>}) into {@link #GUEST}, where the
     * workspace screen looks. Quiet: it prints nothing and does not fail the command that follows.
     */
    public static String settle() {
        return "mkdir -p " + GUEST + "\n"
                + "for d in /root/workspace/* /workspace/*; do\n"
                + "  [ -d \"$d/.git\" ] || continue\n"
                + "  case \"$d\" in\n"
                + "    " + GUEST + "|" + GUEST + "/*|/workspace/memory|/workspace/memory/*) continue ;;\n"
                + "  esac\n"
                + "  name=$(basename \"$d\")\n"
                + "  dest=\"" + GUEST + "/$name\"\n"
                + "  if [ ! -e \"$dest\" ]; then\n"
                + "    mv \"$d\" \"$dest\" 2>/dev/null || { cp -a \"$d\" \"$dest\" && rm -rf \"$d\"; }\n"
                + "  fi\n"
                + "done\n"
                + "true";
    }

    /** A {@code git} on PATH that refuses to clone anywhere but {@link #GUEST}. The real git stays {@code /usr/bin/git}. */
    public static void installGuard(java.io.File rootfs) throws java.io.IOException {
        if (rootfs == null) {
            return;
        }
        java.io.File bin = new java.io.File(rootfs, "usr/local/bin");
        if (!bin.isDirectory() && !bin.mkdirs()) {
            return;
        }
        java.io.File git = new java.io.File(bin, "git");
        java.nio.file.Files.write(git.toPath(), GUARD.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        git.setExecutable(true, false);
    }

    private static final String GUARD = "#!/bin/sh\n"
            + "REAL=/usr/bin/git\n"
            + "if [ ! -x \"$REAL\" ]; then echo \"git is not installed in Linux yet\" >&2; exit 127; fi\n"
            + "cmd=\n"
            + "skip=0\n"
            + "for a in \"$@\"; do\n"
            + "  if [ \"$skip\" = 1 ]; then skip=0; continue; fi\n"
            + "  case \"$a\" in\n"
            + "    -c|-C|--git-dir|--work-tree|--namespace|--exec-path) skip=1 ;;\n"
            + "    -*) ;;\n"
            + "    *) cmd=$a; break ;;\n"
            + "  esac\n"
            + "done\n"
            + "if [ \"$cmd\" != clone ]; then exec \"$REAL\" \"$@\"; fi\n"
            + "mkdir -p /workspace/repos\n"
            + "url=\n"
            + "skip=0\n"
            + "for a in \"$@\"; do\n"
            + "  if [ \"$skip\" = 1 ]; then skip=0; continue; fi\n"
            + "  case \"$a\" in\n"
            + "    clone) continue ;;\n"
            + "    --branch|--depth|--origin|--reference|--config|--template|--separate-git-dir|--jobs|-b|-c|-j|-o) skip=1; continue ;;\n"
            + "    -*) continue ;;\n"
            + "    *) [ -z \"$url\" ] && url=$a ;;\n"
            + "  esac\n"
            + "done\n"
            + "name=${url%.git}\n"
            + "name=${name%/}\n"
            + "name=${name##*/}\n"
            + "name=${name##*:}\n"
            + "case \"$name\" in ''|*[!A-Za-z0-9._-]*) name=repo ;; esac\n"
            + "list=/tmp/dvine-git-$$\n"
            + ": > \"$list\"\n"
            + "skip=0\n"
            + "seen=0\n"
            + "for a in \"$@\"; do\n"
            + "  if [ \"$skip\" = 1 ]; then printf '%s\\n' \"$a\" >> \"$list\"; skip=0; continue; fi\n"
            + "  case \"$a\" in\n"
            + "    --branch|--depth|--origin|--reference|--config|--template|--separate-git-dir|--jobs|-b|-c|-j|-o)\n"
            + "      printf '%s\\n' \"$a\" >> \"$list\"; skip=1; continue ;;\n"
            + "    -*) printf '%s\\n' \"$a\" >> \"$list\"; continue ;;\n"
            + "    clone) printf '%s\\n' \"$a\" >> \"$list\"; continue ;;\n"
            + "    *) if [ \"$seen\" = 0 ]; then printf '%s\\n' \"$a\" >> \"$list\"; seen=1; fi ;;\n"
            + "  esac\n"
            + "done\n"
            + "printf '%s\\n' \"/workspace/repos/$name\" >> \"$list\"\n"
            + "set --\n"
            + "while IFS= read -r line; do set -- \"$@\" \"$line\"; done < \"$list\"\n"
            + "rm -f \"$list\"\n"
            + "\"$REAL\" \"$@\"\n"
            + "status=$?\n"
            + "dest=\"/workspace/repos/$name\"\n"
            + "if [ -d \"$dest/.git\" ]; then\n"
            + "  left=$(find \"$dest\" -mindepth 1 -maxdepth 1 ! -name .git -print -quit)\n"
            + "  if [ -z \"$left\" ]; then \"$REAL\" -C \"$dest\" checkout -f HEAD; fi\n"
            + "fi\n"
            + "exit $status\n";

    /** {@code git} with options, then {@code clone}, then the clone's own arguments. Group 2 is those arguments. */
    private static final Pattern CLONE = Pattern.compile(
            "(?i)((?:^|\\n|&&|\\|\\||[;&])\\s*)(?:\\S*/)?git\\b(?:\\s+(?:-[cC]\\s+\\S+|-[^\\s]+))*\\s+clone\\b([^\\n]*)");

    private static String destOf(String args) {
        Matcher d = Pattern.compile("\\s+(\\S+)\\s*$").matcher(args == null ? "" : args);
        return d.find() ? d.group(1) : null;
    }

    /** An empty work tree (only {@code .git}) is checked out, so a cut-off clone still has its files. */
    private static String checkout(String dest) {
        String q = Guest.quote(dest);
        return "if [ -d " + q + "/.git ] && [ -z \"$(find " + q
                + " -mindepth 1 -maxdepth 1 ! -name .git -print -quit)\" ]; then /usr/bin/git -C " + q
                + " checkout -f HEAD; fi";
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
        String url = words.get(0).replaceAll("^['\"]|['\"]$", "");
        String dest = GUEST + "/" + nameOf(url);
        if (args.contains(dest)) {
            return args;
        }
        StringBuilder b = new StringBuilder();
        for (String f : flags) {
            b.append(' ').append(f);
        }
        b.append(' ').append(url).append(' ').append(dest);
        return b.toString();
    }

    private static boolean isProgress(String t) {
        return t != null && (PROGRESS.matcher(t).matches() || t.matches("(?i)remote:\\s+.*\\d+%.*"));
    }

    /** How many git object-counting lines a reply contains. */
    public static int progressLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (String line : text.split("\n")) {
            if (isProgress(line.trim())) {
                n++;
            }
        }
        return n;
    }

    /** Git's progress ("Counting objects", "Cloning into") is not shown. Other lines stay. */
    public static String quiet(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String[] lines = text.split("\n", -1);
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (String line : lines) {
            String t = line.trim();
            if (isProgress(t) || t.matches("(?i)remote:\\s+.*\\d+%.*") || t.matches("(?i)cloning into\\s+.*")) {
                n++;
                continue;
            }
            if (!t.isEmpty()) {
                b.append(line).append('\n');
            }
        }
        String out = collapseEmptyFences(b.toString());
        if (out.isEmpty()) {
            return n > 0 ? "Cloning the repository." : text.trim();
        }
        return out;
    }

    /** A code fence whose body was only the clone log (or a leftover "done") is not a code card. */
    private static String collapseEmptyFences(String text) {
        String s = text.replaceAll("(?i)```[a-z0-9_+#.\\-]*[ \\t]*\\n(?:done\\.?[ \\t]*\\n)?```", "");
        s = s.replaceAll("(?m)^```[a-zA-Z0-9_+#.\\-]*[ \\t]*$", "");
        return s.trim();
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
