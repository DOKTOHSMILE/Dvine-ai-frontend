package ai.ibiko.linux;

import java.io.File;
import java.util.function.Function;

/**
 * One Linux shell for the whole app. Leaving the shell screen does not stop it, and chat uses that
 * same shell instead of starting a second copy. The shell is a {@link TermSession} (a real terminal) when the phone
 * opens a terminal device, else a {@link ProotSession} on a pipe.
 */
public final class LinuxHub {
    /** Runs a command on the open shell screen and returns what it printed. */
    private static volatile Function<String, String> shell;
    private static final Object LOCK = new Object();
    private static ProotSession kept;
    private static boolean keptPipe;
    private static File keptRoot;
    private static final StringBuilder unseen = new StringBuilder();
    private static volatile boolean headlessBusy;

    private static TermSession terminal;
    private static boolean noTerminal;

    private LinuxHub() {
    }

    /**
     * The terminal, started the first time it is asked for. Null when this phone will not open a terminal device, or
     * the shell never showed its prompt: the caller then uses the pipe shell.
     */
    public static TermSession terminal(android.content.Context c) {
        synchronized (LOCK) {
            if (terminal != null && terminal.alive()) {
                return terminal;
            }
            terminal = null;
            if (noTerminal) {
                return null;
            }
        }
        TermSession opened;
        try {
            opened = TermSession.open(c.getApplicationContext(), LinuxRoot.root(c));
        } catch (Exception e) {
            opened = null;
        }
        if (opened != null && !opened.waitForPrompt(8000)) {
            opened.close();
            opened = null;
        }
        synchronized (LOCK) {
            if (opened == null) {
                noTerminal = true; // not tried again until the app restarts
                return null;
            }
            if (terminal != null && terminal.alive()) {
                opened.close();
                return terminal;
            }
            terminal = opened;
            return opened;
        }
    }

    /** The terminal if one is running; never starts one. */
    public static TermSession running() {
        synchronized (LOCK) {
            return terminal != null && terminal.alive() ? terminal : null;
        }
    }

    public static void attach(Function<String, String> run) {
        shell = run;
    }

    public static void detach(Function<String, String> run) {
        if (shell == run) {
            shell = null;
        }
    }

    /** The screen is going away. The shell stays open for chat and for the next time the screen opens. */
    public static void park(ProotSession session, boolean pipe, File rootfs) {
        synchronized (LOCK) {
            kept = session;
            keptPipe = pipe;
            keptRoot = rootfs;
            if (session != null) {
                session.pump(LinuxHub::parked);
            }
        }
    }

    public static boolean headlessBusy() {
        return headlessBusy;
    }

    /** The shell that was left running, or null. */
    public static ProotSession unpark() {
        synchronized (LOCK) {
            ProotSession session = kept;
            kept = null;
            return session;
        }
    }

    public static String takeUnseen() {
        synchronized (LOCK) {
            String text = unseen.toString();
            unseen.setLength(0);
            return text;
        }
    }

    private static void parked(String chunk) {
        synchronized (LOCK) {
            if (chunk == null) {
                kept = null;
                LOCK.notifyAll();
                return;
            }
            unseen.append(chunk);
            LOCK.notifyAll();
        }
    }

    /**
     * The GitHub token, only when this command talks to a remote and Connectors has GitHub signed in. Null otherwise
     * (a public repository needs nothing). The token is not returned to the caller of {@link #run}.
     */
    private static String gitToken(android.content.Context c, String command) {
        if (c == null || !ai.ibiko.connect.GitAsk.network(command)) {
            return null;
        }
        try {
            ai.ibiko.connect.ConnectorStore store = new ai.ibiko.connect.ConnectorStore(c);
            if (!store.ready(ai.ibiko.connect.ConnectorStore.Kind.GITHUB)) {
                return null;
            }
            String token = store.token(ai.ibiko.connect.ConnectorStore.Kind.GITHUB);
            return token == null || token.trim().isEmpty() ? null : token.trim();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The one way every part of the app runs a Linux command: chat, Ibiko, the task system. It is always the same
     * Alpine (the one the Shell screen shows): installed the first time it is needed, then run in the open terminal at
     * its prompt, or on its own in the same Alpine when a program is busy there. {@code where} is the folder on the
     * device to start in (it is mapped to /workspace or /work); null keeps the folder the last command left.
     * Only when Alpine cannot be installed at all (no internet the first time) does the small built-in shell answer,
     * and the reply says so. Call it off the main thread.
     */
    public static String run(android.content.Context c, String command, File where) {
        if (command == null || command.trim().isEmpty()) {
            return "(no command)";
        }
        File linux = new File(c.getFilesDir(), "linux");
        WorkspaceManager.ensure(linux);
        String why = null;
        if (!AlpineRun.ready(c)) {
            try {
                LinuxRoot.install(c, null); // the first command installs Linux (about 4 MB), once
            } catch (Exception e) {
                why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
        }
        if (AlpineRun.ready(c)) {
            try {
                CloneFolder.installGuard(LinuxRoot.root(c)); // git clone cannot land outside repos
            } catch (Exception ignored) {
                // the command's own rewrite still sends the clone to the right folder
            }
            command = CloneFolder.settle() + "\n" + CloneFolder.place(command);
            String placed = where == null ? command : GuestPath.at(where, command);
            String token = gitToken(c, placed);
            if (token != null) {
                try {
                    ai.ibiko.connect.GitAsk.install(LinuxRoot.root(c), token);
                } catch (Exception ignored) {
                    token = null; // no ask-pass: a public repository still clones, a private one says so
                }
            }
            if (token != null) {
                placed = ai.ibiko.connect.GitAsk.prefix() + placed;
            }
            if (where != null && ai.ibiko.main.ChatSpace.chatOf(where) != null) {
                // a chat's command: its own Alpine run with only that chat's folder as /workspace, so it can't see,
                // change or delete another chat's files (the shared terminal sees every chat)
                LinuxEngine.Result own = AlpineRun.run(c, placed, where);
                if (own != null) {
                    return finish(own.text, token, placed);
                }
                // never handed to the shared shell instead: it sees every chat's folder
                return "(the command couldn't start in this chat's own Linux; nothing else ran it)";
            }
            String shared = ask(c, placed);
            if (shared != null) {
                return finish(shared, token, placed);
            }
            // a program is running in the terminal: the same Alpine, its own shell for this one command
            String script = where == null ? LinuxCwd.script(LinuxRoot.root(c), command) : placed;
            if (token != null && script != null && !script.startsWith("export GIT_ASKPASS=")) {
                script = ai.ibiko.connect.GitAsk.prefix() + script;
            }
            LinuxEngine.Result alone = AlpineRun.run(c, script, where == null ? linux : where);
            if (alone != null) {
                return finish(alone.text, token, script == null ? placed : script);
            }
        }
        LinuxEngine.Result own = LinuxEngine.run(command, where == null ? linux : where, null);
        String plain = (own == null ? "The shell didn't run that." : own.text)
                + "\n(Linux isn't installed yet" + (why == null ? "" : ": " + why) + ". Ibiko's small built-in shell ran this;"
                + " open Shell when you're online to install Alpine.)";
        return ai.ibiko.connect.GitAsk.network(command) ? ai.ibiko.connect.GitAsk.note(plain, false) : plain;
    }

    /** The command's text, with a token taken out and a private-repository note when the remote refused it. */
    private static String finish(String text, String token, String command) {
        String out = ai.ibiko.connect.GitAsk.quiet(CloneFolder.quiet(text), token);
        return ai.ibiko.connect.GitAsk.network(command) ? ai.ibiko.connect.GitAsk.note(out, token != null) : out;
    }

    /**
     * A command from chat or Ibiko. Once Alpine is installed it runs in the one terminal (started here if it is not
     * running yet), so the folder, variables and installed packages are the same ones the shell screen shows.
     */
    public static String ask(android.content.Context c, String command) {
        if (c != null && running() == null && android.os.Looper.myLooper() != android.os.Looper.getMainLooper()
                && AlpineRun.ready(c)) {
            terminal(c);
        }
        return ask(command);
    }

    public static String ask(String command) {
        if (command == null || command.trim().isEmpty()) {
            return null;
        }
        TermSession term = running();
        if (term != null) {
            return term.run(command, CommandWait.ms(command)); // null while a program is running there: the caller runs it alone
        }
        Function<String, String> run = shell;
        if (run != null) {
            try {
                return run.apply(command);
            } catch (Throwable e) {
                return null;
            }
        }
        return headless(command);
    }

    /** A command while the shell screen is closed. Null when no shell is running. */
    private static String headless(String command) {
        ProotSession session;
        boolean pipe;
        File root;
        synchronized (LOCK) {
            session = kept;
            pipe = keptPipe;
            root = keptRoot;
        }
        if (session == null || !session.alive() || root == null) {
            return null;
        }
        headlessBusy = true;
        try {
            synchronized (session) {
                return pipe ? readPipe(session, root, command) : null;
            }
        } catch (Exception e) {
            return null;
        } finally {
            headlessBusy = false;
        }
    }

    private static String readPipe(ProotSession session, File root, String command) throws Exception {
        File out = new File(root, "root/.ibiko_out");
        File status = new File(root, "root/.ibiko_status");
        out.delete();
        status.delete();
        session.send(Guest.pipe(command));
        long began = System.currentTimeMillis();
        long deadline = began + CommandWait.ms(command);
        long seen = 0;
        StringBuilder text = new StringBuilder();
        while (System.currentTimeMillis() < deadline) {
            if (out.isFile()) {
                java.io.RandomAccessFile raf = new java.io.RandomAccessFile(out, "r");
                try {
                    if (raf.length() > seen) {
                        raf.seek(seen);
                        byte[] buf = new byte[(int) Math.min(8192, raf.length() - seen)];
                        int n = raf.read(buf);
                        if (n > 0) {
                            seen += n;
                            deadline = Math.min(began + CommandWait.LONG_MS,
                                    Math.max(deadline, System.currentTimeMillis() + CommandWait.SHORT_MS));
                            text.append(new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8));
                        }
                    }
                } finally {
                    raf.close();
                }
            }
            if (status.isFile() && status.length() > 0) {
                break;
            }
            if (!session.alive()) {
                break;
            }
            Thread.sleep(120);
        }
        return text.toString();
    }
}
