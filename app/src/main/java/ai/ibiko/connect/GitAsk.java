package ai.ibiko.connect;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Git and {@code gh} inside the built-in Linux, talking to a private repository. The token stays in a file git's
 * ask-pass program and credential helper read. It is never written into the command, so a step, a log or an approval
 * card cannot show it. Pure Java apart from {@link #install}, which only writes those files.
 */
public final class GitAsk {
    /** Where Alpine finds the ask-pass program (the rootfs, not a chat's private home). */
    public static final String ASK = "/usr/local/bin/ibiko-git-ask";
    /** Git's credential helper: more reliable than ask-pass alone, and it still reads the same file. */
    public static final String CRED = "/usr/local/bin/ibiko-git-cred";
    /** The token file, same rootfs, gone from the command line. */
    public static final String TOKEN_FILE = "/etc/ibiko-git-token";

    private GitAsk() {
    }

    /** A command that talks to a remote: clone, fetch, pull, push, or {@code gh}. Status and diff do not. */
    public static boolean network(String command) {
        if (command == null || command.isEmpty()) {
            return false;
        }
        String s = command.toLowerCase(Locale.US);
        boolean git = s.contains("git ") || s.contains("git\n") || s.startsWith("git");
        boolean gh = s.contains("gh ") || s.startsWith("gh");
        if (!git && !gh) {
            return false;
        }
        return s.contains("clone") || s.contains("fetch") || s.contains("pull") || s.contains("push")
                || s.contains("ls-remote") || s.contains("submodule") || gh;
    }

    /**
     * Lines prepended to a command. They name the ask-pass program and the credential helper, and let {@code gh}
     * read the same file. The token itself is not in this string.
     */
    public static String prefix() {
        return "export GIT_ASKPASS=" + ASK + " GIT_TERMINAL_PROMPT=0"
                + " GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=credential.helper GIT_CONFIG_VALUE_0=" + CRED
                + " GH_TOKEN=\"$(cat " + TOKEN_FILE + " 2>/dev/null)\"\n";
    }

    /** The ask-pass program: the username git asks for, then the token, and nothing else. */
    public static String script() {
        return "#!/bin/sh\n"
                + "case \"$1\" in\n"
                + "*[Uu]sername*) printf '%s\\n' 'x-access-token' ;;\n"
                + "*) cat " + TOKEN_FILE + " ;;\n"
                + "esac\n";
    }

    /**
     * Git's credential helper. {@code get} answers for github.com only; store and erase do nothing, so the token is
     * never written into a second file.
     */
    public static String cred() {
        return "#!/bin/sh\n"
                + "[ \"$1\" = get ] || exit 0\n"
                + "host=\n"
                + "while IFS= read -r line; do\n"
                + "  case \"$line\" in\n"
                + "    host=*) host=${line#host=} ;;\n"
                + "    \"\") break ;;\n"
                + "  esac\n"
                + "done\n"
                + "case \"$host\" in\n"
                + "  github.com|*.github.com)\n"
                + "    printf '%s\\n' 'username=x-access-token'\n"
                + "    printf 'password=%s\\n' \"$(cat " + TOKEN_FILE + " 2>/dev/null)\"\n"
                + "    ;;\n"
                + "esac\n"
                + "exit 0\n";
    }

    /** Writes the programs and the token into the Alpine root. A later call replaces the token. */
    public static void install(File rootfs, String token) throws IOException {
        if (rootfs == null || token == null || token.isEmpty()) {
            return;
        }
        File bin = new File(rootfs, "usr/local/bin");
        bin.mkdirs();
        writeExec(new File(bin, "ibiko-git-ask"), script());
        writeExec(new File(bin, "ibiko-git-cred"), cred());
        File etc = new File(rootfs, "etc");
        etc.mkdirs();
        File secret = new File(etc, "ibiko-git-token");
        Files.write(secret.toPath(), token.trim().getBytes(StandardCharsets.UTF_8));
        secret.setReadable(false, false);
        secret.setReadable(true, true);
        secret.setWritable(false, false);
        secret.setWritable(true, true);
    }

    private static void writeExec(File file, String text) throws IOException {
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        file.setExecutable(true, false);
    }

    /** Takes the token out of a command's output, if git echoed an address that contained it. */
    public static String quiet(String text, String token) {
        if (text == null || token == null || token.isEmpty() || !text.contains(token)) {
            return text;
        }
        return text.replace(token, "(token)");
    }

    /**
     * A short note when a remote git command comes back looking like a private repository the token could not open.
     * {@code signedIn} is whether a token was given to this command, not the token.
     */
    public static String note(String text, boolean signedIn) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String low = text.toLowerCase(Locale.US);
        boolean denied = low.contains("authentication failed")
                || low.contains("could not read username")
                || low.contains("could not read password")
                || low.contains("repository not found")
                || low.contains("terminal prompts disabled")
                || low.contains("password authentication was removed")
                || low.contains("invalid username or password")
                || low.contains("http basic: access denied");
        if (!denied || low.contains("connectors")) {
            return text;
        }
        return text + (signedIn
                ? "\n(Private GitHub needs a token that can read this repository: classic scope repo, or fine-grained Contents. Connectors, GitHub.)"
                : "\n(This looks like a private repository. Sign in under Connectors, GitHub, then clone or pull again.)");
    }
}
