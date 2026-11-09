package ai.dvine.linux;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One Alpine shell that stays open, so {@code cd}, installed packages and variables last.
 * Output is delivered as it is written. Stop sends interrupt to the whole session.
 */
public final class ProotSession {
    private final Process proc;
    private final OutputStream stdin;
    private volatile boolean closed;

    private ProotSession(Process proc) {
        this.proc = proc;
        this.stdin = proc.getOutputStream();
    }

    public static ProotSession open(Context c, File rootfs) throws IOException {
        File proot = LinuxRoot.proot(c);
        if (!proot.isFile()) {
            throw new IOException("The Linux starter is not in this build.");
        }
        File tmp = new File(c.getCacheDir(), "proot-tmp");
        tmp.mkdirs();
        List<String> cmd = ProotCommand.line(proot, rootfs);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.directory(new File(c.getFilesDir(), "linux"));
        Map<String, String> env = pb.environment();
        env.put("PROOT_TMP_DIR", tmp.getAbsolutePath());
        env.put("PROOT_NO_SECCOMP", "1");
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
        return new ProotSession(pb.start());
    }

    public void send(String line) throws IOException {
        if (closed) {
            throw new IOException("The shell has stopped.");
        }
        stdin.write(((line == null ? "" : line) + "\n").getBytes(StandardCharsets.UTF_8));
        stdin.flush();
    }

    /** CTRL-C. If the phone has no kill program, the session is closed instead. */
    public void interrupt() {
        try {
            new ProcessBuilder("/system/bin/kill", "-INT", Long.toString(proc.pid())).start();
        } catch (IOException e) {
            proc.destroy();
        }
    }

    public void pump(Consumer<String> out) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[4096];
            try {
                int n;
                while ((n = proc.getInputStream().read(buf)) > 0) {
                    out.accept(new String(buf, 0, n, StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
                // the session ended
            }
            closed = true;
            out.accept(null);
        }, "dvine-linux");
        t.setDaemon(true);
        t.start();
    }

    public boolean alive() {
        return !closed && proc.isAlive();
    }

    public void close() {
        closed = true;
        proc.destroy();
    }
}
