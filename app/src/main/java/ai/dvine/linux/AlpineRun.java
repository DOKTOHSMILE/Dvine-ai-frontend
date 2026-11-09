package ai.dvine.linux;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * One command in Alpine, for chat and Ibiko. Returns null when Linux has not been set up yet, so the
 * small Java shell still answers. The command runs off the caller’s thread. Termux is not opened.
 */
public final class AlpineRun {
    static final int MOST = 64 * 1024;
    private static final long WAIT_MS = 180_000;

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
        if (!ready(c) || command == null || command.trim().isEmpty()) {
            return null;
        }
        File rootfs = LinuxRoot.root(c);
        File proot = LinuxRoot.proot(c);
        if (work != null && !work.isDirectory()) {
            work.mkdirs();
        }
        List<String> cmd = ProotCommand.once(proot, rootfs, work, command);
        File tmp = new File(c.getCacheDir(), "proot-tmp");
        tmp.mkdirs();
        try {
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
            env.put("TERM", "dumb");
            env.put("LANG", "C.UTF-8");
            env.remove("LD_PRELOAD");
            env.remove("LD_LIBRARY_PATH");
            Process proc = pb.start();
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            Thread pump = new Thread(() -> drain(proc.getInputStream(), captured), "dvine-linux-out");
            pump.setDaemon(true);
            pump.start();
            long deadline = System.currentTimeMillis() + WAIT_MS;
            boolean finished = false;
            while (System.currentTimeMillis() < deadline) {
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
            int code = finished ? proc.exitValue() : 124;
            if (!finished) {
                text = text + "\n[stopped: it ran longer than 3 minutes]";
            } else if (code != 0) {
                text = text + "\n[exit " + code + "]";
            }
            return new LinuxEngine.Result(code == 0, text + "\nThis ran in DVINE's Linux. Termux was not opened.");
        } catch (IOException e) {
            String why = e.getMessage() == null ? "it could not be started" : e.getMessage();
            return new LinuxEngine.Result(false, "Linux didn't start: " + why
                    + "\nThis ran in DVINE's Linux. Termux was not opened.");
        }
    }

    private static void drain(InputStream in, ByteArrayOutputStream out) {
        byte[] buf = new byte[4096];
        try {
            int n;
            while ((n = in.read(buf)) > 0) {
                synchronized (out) {
                    if (out.size() < MOST) {
                        out.write(buf, 0, (int) Math.min(n, MOST - out.size()));
                    }
                }
            }
        } catch (IOException ignored) {
            // the process closed the pipe
        }
    }
}
