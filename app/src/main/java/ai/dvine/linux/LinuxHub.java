package ai.dvine.linux;

import java.io.File;
import java.util.function.Function;

/**
 * One Linux shell for the whole app. Leaving the shell screen does not stop it, and chat uses that
 * same shell instead of starting a second copy.
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

    private LinuxHub() {
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

    public static String ask(String command) {
        if (command == null || command.trim().isEmpty()) {
            return null;
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
                if (pipe) {
                    return readPipe(session, root, command);
                }
                return readTty(session, command);
            }
        } catch (Exception e) {
            return null;
        } finally {
            headlessBusy = false;
        }
    }

    private static String readPipe(ProotSession session, File root, String command) throws Exception {
        File out = new File(root, "root/.dvine_out");
        File status = new File(root, "root/.dvine_status");
        out.delete();
        status.delete();
        session.send(Guest.pipe(command));
        long began = System.currentTimeMillis();
        long seen = 0;
        StringBuilder text = new StringBuilder();
        while (System.currentTimeMillis() - began < 180000) {
            if (out.isFile()) {
                java.io.RandomAccessFile raf = new java.io.RandomAccessFile(out, "r");
                try {
                    if (raf.length() > seen) {
                        raf.seek(seen);
                        byte[] buf = new byte[(int) Math.min(8192, raf.length() - seen)];
                        int n = raf.read(buf);
                        if (n > 0) {
                            seen += n;
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

    private static String readTty(ProotSession session, String command) throws Exception {
        synchronized (LOCK) {
            unseen.setLength(0);
        }
        session.send(command + "\necho " + Guest.MARK + "$?__$(pwd)");
        long began = System.currentTimeMillis();
        StringBuilder got = new StringBuilder();
        while (System.currentTimeMillis() - began < 180000) {
            synchronized (LOCK) {
                if (unseen.length() > 0) {
                    got.append(unseen);
                    unseen.setLength(0);
                }
            }
            Guest.Stream part = Guest.drain(got.toString(), "/root");
            if (part.done) {
                return part.show;
            }
            if (!session.alive()) {
                break;
            }
            Thread.sleep(80);
        }
        Guest.Stream part = Guest.drain(got.toString(), "/root");
        return part.show.isEmpty() ? got.toString() : part.show;
    }
}
