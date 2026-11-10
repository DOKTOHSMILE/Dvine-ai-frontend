package ai.dvine.linux;

import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A real terminal device. Programs inside Linux then see a tty: they draw full screens, ask for its size, and CTRL-C
 * reaches the program that is running. Android 14 and later give Java no way to open one, so three small system calls
 * are made by {@code libdvpty.so} (source: {@code src/main/cpp/pty/dvpty.c}). Null when the phone will not open one.
 */
public final class Pty {
    private static final boolean LOADED = load();

    private final ParcelFileDescriptor master;
    private final int fd;
    /** The other end, opened by the shell: {@code /dev/pts/N}. */
    public final String slave;

    private Pty(ParcelFileDescriptor master, int fd, String slave) {
        this.master = master;
        this.fd = fd;
        this.slave = slave;
    }

    private static boolean load() {
        try {
            System.loadLibrary("dvpty");
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public static Pty tryOpen() {
        if (!LOADED) {
            return null;
        }
        try {
            int fd = nativeOpen();
            if (fd < 0) {
                return null;
            }
            int n = nativeNumber(fd);
            if (n < 0) {
                nativeClose(fd);
                return null;
            }
            return new Pty(ParcelFileDescriptor.adoptFd(fd), fd, "/dev/pts/" + n);
        } catch (Throwable e) {
            return null;
        }
    }

    /** Tells the programs inside how big the screen is. */
    public void resize(int rows, int cols) {
        try {
            nativeResize(fd, Math.max(1, rows), Math.max(1, cols));
        } catch (Throwable ignored) {
            // the size stays as it was
        }
    }

    InputStream in() {
        return new FileInputStream(master.getFileDescriptor());
    }

    OutputStream out() {
        return new FileOutputStream(master.getFileDescriptor());
    }

    void close() {
        try {
            master.close();
        } catch (Throwable ignored) {
            // already closed
        }
    }

    private static native int nativeOpen();

    private static native int nativeNumber(int fd);

    private static native int nativeResize(int fd, int rows, int cols);

    private static native int nativeClose(int fd);
}
