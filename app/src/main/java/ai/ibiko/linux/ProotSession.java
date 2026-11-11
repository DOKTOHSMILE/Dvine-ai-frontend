package ai.ibiko.linux;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One Alpine shell on a pipe, for a phone that will not open a terminal device ({@link TermSession} is the real
 * terminal). It stays open, so {@code cd}, installed packages and variables last. Output is delivered as it is
 * written. Stop sends interrupt to the whole session.
 */
public final class ProotSession {
    private final Process proc;
    private final OutputStream stdin;
    private final InputStream stdout;
    private final boolean tty;
    private volatile boolean closed;
    private volatile Consumer<String> ear;
    private volatile boolean pumping;

    private ProotSession(Process proc, OutputStream stdin, InputStream stdout, boolean tty) {
        this.proc = proc;
        this.stdin = stdin;
        this.stdout = stdout;
        this.tty = tty;
    }

    public boolean tty() {
        return tty;
    }

    public static ProotSession open(Context c, File rootfs) throws IOException {
        File proot = LinuxRoot.proot(c);
        if (!proot.isFile()) {
            throw new IOException("The Linux starter is not in this build.");
        }
        File tmp = new File(c.getCacheDir(), "proot-tmp");
        tmp.mkdirs();
        File linux = new File(c.getFilesDir(), "linux");
        new File(c.getFilesDir(), "ibiko").mkdirs();
        try {
            CloneFolder.installGuard(rootfs);
        } catch (IOException ignored) {
            // a clone from this shell is still rewritten when it is run from chat
        }
        List<String> cmd = ProotCommand.line(proot, rootfs, linux);
        ProcessBuilder pb = builder(c, cmd, tmp);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        return new ProotSession(proc, proc.getOutputStream(), proc.getInputStream(), false);
    }

    static ProcessBuilder builder(Context c, List<String> cmd, File tmp) {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(c.getFilesDir(), "linux"));
        Map<String, String> env = pb.environment();
        env.put("PROOT_TMP_DIR", tmp.getAbsolutePath());
        env.remove("PROOT_NO_SECCOMP");
        env.put("PROOT_IGNORE_MISSING_BINDINGS", "1");
        File loader = LinuxRoot.loader(c);
        if (loader.isFile()) {
            env.put("PROOT_LOADER", loader.getAbsolutePath());
        }
        env.put("HOME", "/root");
        env.put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
        env.put("TERM", "xterm-256color");
        env.put("LANG", "C.UTF-8");
        env.remove("LD_PRELOAD");
        env.remove("LD_LIBRARY_PATH");
        return pb;
    }

    public void send(String line) throws IOException {
        if (closed) {
            throw new IOException("The shell has stopped.");
        }
        stdin.write(((line == null ? "" : line) + "\n").getBytes(StandardCharsets.UTF_8));
        stdin.flush();
    }

    /** CTRL-C. On a real tty this is the interrupt character. Otherwise the process is signalled. */
    public void interrupt() {
        if (tty) {
            try {
                stdin.write(3);
                stdin.flush();
                return;
            } catch (IOException ignored) {
                // fall through
            }
        }
        long pid = pidOf(proc);
        if (pid > 0) {
            try {
                new ProcessBuilder("/system/bin/kill", "-INT", Long.toString(pid)).start();
                return;
            } catch (Throwable ignored) {
                // fall through and close the session
            }
        }
        proc.destroy();
    }

    /** The process id, or -1 when this Android library does not expose it. */
    static long pidOf(Process proc) {
        if (proc == null) {
            return -1;
        }
        try {
            Object v = Process.class.getMethod("pid").invoke(proc);
            if (v instanceof Number) {
                return ((Number) v).longValue();
            }
        } catch (Throwable ignored) {
            // compiled against an Android library older than the method
        }
        try {
            java.lang.reflect.Field field = proc.getClass().getDeclaredField("pid");
            field.setAccessible(true);
            Object v = field.get(proc);
            if (v instanceof Number) {
                return ((Number) v).longValue();
            }
        } catch (Throwable ignored) {
            // a different Process implementation
        }
        return -1;
    }

    /** Starts the reader once. A later call only changes where the text is delivered. */
    public void pump(Consumer<String> out) {
        ear = out;
        if (pumping) {
            return;
        }
        pumping = true;
        Thread t = new Thread(() -> {
            byte[] buf = new byte[4096];
            try {
                int n;
                while ((n = stdout.read(buf)) > 0) {
                    Consumer<String> who = ear;
                    if (who != null) {
                        who.accept(new String(buf, 0, n, StandardCharsets.UTF_8));
                    }
                }
            } catch (IOException ignored) {
                // the session ended
            }
            closed = true;
            Consumer<String> who = ear;
            if (who != null) {
                who.accept(null);
            }
        }, "ibiko-linux");
        t.setDaemon(true);
        t.start();
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
    }
}
