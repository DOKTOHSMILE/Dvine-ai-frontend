package ai.ibiko.linux;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * One command in Alpine, for chat and Ibiko. Returns null when Linux has not been set up yet, so the
 * small Java shell still answers. The command runs off the caller’s thread. Termux is not opened.
 */
public final class AlpineRun {
    static final int MOST = 64 * 1024;

    /** A command that can be stopped. */
    public static final class Job {
        private volatile Process proc;
        private volatile boolean stopped;

        public void cancel() {
            stopped = true;
            Process running = proc;
            if (running != null) {
                running.destroy();
            }
        }

        public boolean stopped() {
            return stopped;
        }
    }

    private AlpineRun() {
    }

    public static boolean ready(Context c) {
        if (c == null) {
            return false;
        }
        File proot = LinuxRoot.proot(c);
        return LinuxRoot.ready(LinuxRoot.root(c)) && proot.isFile() && proot.length() > 10000;
    }

    /** Null when Alpine is not installed. Otherwise what the command printed. */
    public static LinuxEngine.Result run(Context c, String command, File work) {
        return run(c, command, work, null, MOST);
    }

    /**
     * As {@link #run(Context, String, File)}, with a way to stop it and a cap on how much text is kept.
     * The phone's shared storage is mounted at {@code /storage} when that folder exists.
     */
    public static LinuxEngine.Result run(Context c, String command, File work, Job job, int cap) {
        return run(c, command, work, job, cap, null);
    }

    /**
     * As {@link #run(Context, String, File, Job, int)}. {@code live} is given each piece of output as it
     * is written, from a background thread, so a long install is not silent until the end.
     */
    public static LinuxEngine.Result run(Context c, String command, File work, Job job, int cap, Consumer<String> live) {
        if (!ready(c) || command == null || command.trim().isEmpty()) {
            return null;
        }
        int limit = cap <= 0 ? MOST : cap;
        File rootfs = LinuxRoot.root(c);
        File proot = LinuxRoot.proot(c);
        if (work != null && !work.isDirectory()) {
            work.mkdirs();
        }
        List<String> cmd = ProotCommand.once(proot, rootfs, work, command);
        mount(cmd, "/storage", "/storage");
        mount(cmd, "/sdcard", "/sdcard");
        final DownloadWatch watch = new DownloadWatch();
        final String downloadTitle = DownloadWatch.began(command);
        if (downloadTitle != null) {
            ai.ibiko.net.Transfers.progress(DownloadWatch.KEY, downloadTitle, "In progress", -1);
        }
        Consumer<String> feed = piece -> {
            watch.feed(piece, System.currentTimeMillis());
            if (live != null) {
                live.accept(piece);
            }
        };
        File tmp = new File(c.getCacheDir(), "proot-tmp");
        tmp.mkdirs();
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.directory(new File(c.getFilesDir(), "linux"));
            Map<String, String> env = pb.environment();
            env.put("PROOT_TMP_DIR", tmp.getAbsolutePath());
            // Do not set PROOT_NO_SECCOMP. Setting it stops proot fixing the calls Android blocks,
            // and apk then dies with "Function not implemented".
            env.remove("PROOT_NO_SECCOMP");
            env.put("PROOT_IGNORE_MISSING_BINDINGS", "1");
            File loader = LinuxRoot.loader(c);
            if (loader.isFile()) {
                env.put("PROOT_LOADER", loader.getAbsolutePath());
            }
            env.put("HOME", "/root");
            env.put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
            env.put("TERM", "dumb");
            env.put("LANG", "C.UTF-8");
            env.remove("LD_PRELOAD");
            env.remove("LD_LIBRARY_PATH");
            LinuxRoot.refreshDns(c);
            Process proc = pb.start();
            if (job != null) {
                job.proc = proc;
                if (job.stopped) {
                    proc.destroy();
                }
            }
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            AtomicInteger received = new AtomicInteger();
            Thread pump = new Thread(() -> drain(proc.getInputStream(), captured, limit, feed, received), "ibiko-linux-out");
            pump.setDaemon(true);
            pump.start();
            long started = System.currentTimeMillis();
            long budget = CommandWait.ms(command);
            long deadline = started + budget;
            int seen = 0;
            boolean finished = false;
            while (System.currentTimeMillis() < deadline) {
                if (job != null && job.stopped) {
                    proc.destroy();
                    break;
                }
                int now = received.get();
                if (now != seen) {
                    seen = now;
                    // still writing: another three quiet minutes, but never past two hours
                    long next = System.currentTimeMillis() + CommandWait.SHORT_MS;
                    long ceiling = started + CommandWait.LONG_MS; // not "cap": that is the text limit passed in
                    if (next > ceiling) {
                        next = ceiling;
                    }
                    if (next > deadline) {
                        deadline = next;
                    }
                }
                try {
                    proc.exitValue();
                    finished = true;
                    break;
                } catch (IllegalThreadStateException still) {
                    try {
                        Thread.sleep(40);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
            if (!finished) {
                proc.destroy();
            }
            try {
                pump.join(2000);
            } catch (InterruptedException ignored) {
                // the output we already have is enough
            }
            String text;
            synchronized (captured) {
                text = new String(captured.toByteArray(), StandardCharsets.UTF_8);
            }
            boolean stopped = job != null && job.stopped;
            int code = finished ? proc.exitValue() : 124;
            if (stopped) {
                text = text + "\n[stopped]";
                code = 130;
            } else if (!finished) {
                text = text + "\n[stopped: it ran longer than " + CommandWait.lasted(System.currentTimeMillis() - started) + "]";
            } else if (code != 0) {
                text = text + "\n[exit " + code + "]";
            }
            String title = watch.current() != null ? watch.current() : downloadTitle;
            if (title != null) {
                ai.ibiko.net.Transfers.end(DownloadWatch.KEY, title, code == 0 ? "Finished" : "Stopped", code == 0 && finished);
            }
            return new LinuxEngine.Result(code == 0, text + "\nThis ran in Ibiko's Linux. Termux was not opened.");
        } catch (IOException e) {
            if (downloadTitle != null) {
                ai.ibiko.net.Transfers.end(DownloadWatch.KEY, downloadTitle, "Stopped", false);
            }
            String why = e.getMessage() == null ? "it could not be started" : e.getMessage();
            return new LinuxEngine.Result(false, "Linux didn't start: " + why
                    + "\nThis ran in Ibiko's Linux. Termux was not opened.");
        }
    }

    /** Mount a phone folder into the guest, just before the shell, when it exists. */
    private static void mount(List<String> cmd, String hostPath, String guest) {
        if (!new File(hostPath).exists() || cmd.size() < 3) {
            return;
        }
        int at = cmd.size() - 3;
        cmd.add(at, "-b");
        cmd.add(at + 1, hostPath + ":" + guest);
    }

    private static void drain(InputStream in, ByteArrayOutputStream out, int cap, Consumer<String> live,
            AtomicInteger received) {
        byte[] buf = new byte[4096];
        try {
            int n;
            while ((n = in.read(buf)) > 0) {
                received.addAndGet(n);
                synchronized (out) {
                    if (out.size() < cap) {
                        out.write(buf, 0, (int) Math.min(n, cap - out.size()));
                    }
                }
                if (live != null) {
                    live.accept(new String(buf, 0, n, StandardCharsets.UTF_8));
                }
            }
        } catch (IOException ignored) {
            // the process closed the pipe
        }
    }
}
