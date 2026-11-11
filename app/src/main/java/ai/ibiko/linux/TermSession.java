package ai.ibiko.linux;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * DVINE's Linux terminal: one Alpine shell on a real terminal device, and the screen it draws on. It belongs to the
 * app, not to the shell screen, so leaving the screen leaves everything running ({@code vi} is still open when you
 * come back) and chat runs its commands in this same shell.
 */
public final class TermSession {
    /** The prompt, set for the shell, so the session can tell when the shell is waiting for a command. */
    static final String HOST = "localhost:";
    /** The standard prompt: the folder in green, then " $ " ("~ $"). */
    static final String PS1 = "\\[\\e[32m\\]\\w\\[\\e[0m\\] $ ";

    /** Shown once when the terminal starts, as a standard terminal greets you. */
    static final String WELCOME = "Welcome to Ibiko Linux (Alpine " + LinuxRoot.RELEASE + ")!\r\n\r\n"
            + "Working with packages:\r\n\r\n"
            + " - Search:  pkg search <query>\r\n"
            + " - Install: pkg install <package>\r\n"
            + " - Upgrade: pkg upgrade\r\n\r\n"
            + "Folders:\r\n\r\n"
            + " - /workspace  Ibiko's projects (the same files chat sees)\r\n"
            + " - /work       Ibiko's shell files\r\n"
            + " - /sdcard     the device's storage, when allowed\r\n\r\n";

    private final Process proc;
    private final Pty pty;
    private final OutputStream out;
    private final File rootfs;
    private final TermEmulator screen = new TermEmulator(80, 24, 3000);
    private volatile Runnable onChange;
    private volatile boolean closed;
    private final Object chatLock = new Object();
    private ChatCapture chat;

    private TermSession(Process proc, Pty pty, File rootfs) {
        this.proc = proc;
        this.pty = pty;
        this.out = pty.out();
        this.rootfs = rootfs;
    }

    /** Starts the shell. Null when this phone will not open a terminal device. */
    public static TermSession open(Context c, File rootfs) throws IOException {
        File proot = LinuxRoot.proot(c);
        if (!proot.isFile()) {
            throw new IOException("The Linux starter is not in this build.");
        }
        Pty pty = Pty.tryOpen();
        if (pty == null) {
            return null;
        }
        File tmp = new File(c.getCacheDir(), "proot-tmp");
        tmp.mkdirs();
        LinuxRoot.refreshDns(c);
        new File(c.getFilesDir(), "ibiko").mkdirs(); // the coding workspace, seen inside as /workspace
        pty.resize(24, 80);
        List<String> cmd = ProotCommand.terminal(proot, rootfs, new File(c.getFilesDir(), "linux"), pty.slave);
        ProcessBuilder pb = ProotSession.builder(c, cmd, tmp);
        pb.environment().put("PS1", PS1);
        pb.environment().put("HISTFILE", "/root/.ash_history");
        pb.environment().put("COLORTERM", "truecolor");
        pb.redirectErrorStream(true);
        Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            pty.close();
            throw e;
        }
        TermSession s = new TermSession(proc, pty, rootfs);
        s.screen.write(WELCOME);
        s.pump(pty.in());
        s.drain(proc.getInputStream());
        return s;
    }

    public TermEmulator screen() {
        return screen;
    }

    /** Called (on a background thread) each time the screen changed. */
    public void onChange(Runnable r) {
        onChange = r;
    }

    private void pump(InputStream in) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[8192];
            CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
            ByteBuffer bytes = ByteBuffer.allocate(8192 + 8);
            CharBuffer chars = CharBuffer.allocate(8192 + 8);
            try {
                int n;
                while ((n = in.read(buf)) > 0) {
                    bytes.put(buf, 0, n);
                    bytes.flip();
                    chars.clear();
                    utf8.decode(bytes, chars, false); // a letter cut in half waits for its other bytes
                    bytes.compact();
                    chars.flip();
                    if (chars.hasRemaining()) {
                        feed(chars.toString());
                    }
                }
            } catch (IOException ignored) {
                // the shell ended
            }
            closed = true;
            synchronized (chatLock) {
                chatLock.notifyAll();
            }
            changed();
        }, "dvine-terminal");
        t.setDaemon(true);
        t.start();
    }

    /** Anything proot itself prints (not through the terminal) is read so it never blocks. */
    private void drain(InputStream in) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[1024];
            try {
                int n;
                while ((n = in.read(buf)) > 0) {
                    feed(new String(buf, 0, n, StandardCharsets.UTF_8).replace("\n", "\r\n"));
                }
            } catch (IOException ignored) {
                // closed
            }
        }, "dvine-terminal-err");
        t.setDaemon(true);
        t.start();
    }

    /** Downloads in this terminal, whoever typed the command, shown in the notification bar. */
    private final DownloadWatch downloads = new DownloadWatch();
    private Thread downloadClock;

    private void feed(String text) {
        downloads.feed(text, System.currentTimeMillis());
        if (downloadClock == null) {
            // once a second: a download the terminal stopped talking about is finished
            downloadClock = new Thread(() -> {
                while (!closed) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        return;
                    }
                    downloads.tick(System.currentTimeMillis());
                }
            }, "ibiko-download-watch");
            downloadClock.setDaemon(true);
            downloadClock.start();
        }
        String show = text;
        synchronized (chatLock) {
            if (chat != null) {
                show = chat.feed(text);
                if (chat.done()) {
                    chatLock.notifyAll();
                }
            }
        }
        screen.write(show);
        String reply = screen.takeReply();
        if (!reply.isEmpty()) {
            write(reply);
        }
        changed();
    }

    private void changed() {
        Runnable r = onChange;
        if (r != null) {
            r.run();
        }
    }

    /** Keys to the program in the terminal. */
    public void write(String keys) {
        if (closed || keys == null || keys.isEmpty()) {
            return;
        }
        try {
            synchronized (out) {
                out.write(keys.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (IOException e) {
            // the shell ended; the reader notices
        }
    }

    public void resize(int cols, int rows) {
        if (cols < 2 || rows < 2 || cols == screen.cols() && rows == screen.rows()) {
            return;
        }
        screen.resize(cols, rows);
        pty.resize(rows, cols);
    }

    public boolean alive() {
        if (closed) {
            return false;
        }
        try {
            proc.exitValue();
            return false;
        } catch (IllegalThreadStateException still) {
            return true;
        }
    }

    public void close() {
        closed = true;
        proc.destroy();
        pty.close();
    }

    /** The shell is showing its prompt with nothing typed: a command can be given to it. */
    public boolean atPrompt() {
        synchronized (screen) {
            return atPrompt(screen.altScreen(), screen.beforeCursor(), screen.cursorAtLineEnd());
        }
    }

    static boolean atPrompt(boolean altScreen, String beforeCursor, boolean nothingAfter) {
        // "~ $ ", "/workspace/app $ " (and the older "localhost:~# ")
        return !altScreen && nothingAfter && (beforeCursor.matches("^[~/]\\S* \\$ $")
                || beforeCursor.startsWith(HOST) && beforeCursor.endsWith("# "));
    }

    /** Waits for the first prompt after the shell starts. False when it never came. */
    public boolean waitForPrompt(long ms) {
        long until = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < until && alive()) {
            if (atPrompt()) {
                return true;
            }
            try {
                Thread.sleep(60);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return atPrompt();
    }

    /**
     * Runs chat's command in this shell and returns what it printed. The screen shows the command and its output as
     * if it had been typed. Null when the shell is busy (a program is running, or something is half typed): the
     * caller then runs the command on its own.
     */
    public String run(String command, long waitMs) {
        if (command == null || command.trim().isEmpty() || !alive()) {
            return null;
        }
        ChatCapture mine;
        synchronized (chatLock) {
            if (chat != null && !chat.done()) {
                return null;
            }
            String prompt;
            synchronized (screen) {
                if (!atPrompt(screen.altScreen(), screen.beforeCursor(), screen.cursorAtLineEnd())) {
                    return null;
                }
                prompt = screen.beforeCursor();
            }
            String first = command.trim();
            int nl = first.indexOf('\n');
            String shown = prompt + (nl < 0 ? first : first.substring(0, nl) + " …");
            try {
                Files.write(new File(rootfs, "root/.dv").toPath(), ChatCapture.script(command, shown).getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                return null;
            }
            mine = new ChatCapture();
            chat = mine;
            write(" . ~/.dv\r");
            long until = System.currentTimeMillis() + waitMs;
            while (!mine.done() && !closed && System.currentTimeMillis() < until) {
                try {
                    chatLock.wait(Math.max(1, Math.min(500, until - System.currentTimeMillis())));
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
        String text = mine.text();
        if (!mine.done()) {
            return text + (text.endsWith("\n") || text.isEmpty() ? "" : "\n") + "[still running in the shell: open Shell to see it or stop it]";
        }
        return mine.exit() == 0 ? text : text + (text.endsWith("\n") || text.isEmpty() ? "" : "\n") + "[exit " + mine.exit() + "]";
    }
}
