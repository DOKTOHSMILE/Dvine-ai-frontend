package ai.dvine.linux;

import android.system.ErrnoException;
import android.system.Int32Ref;
import android.system.Os;
import android.system.OsConstants;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A real terminal device. Programs inside Linux then see a tty, so their output is not held back
 * and Ctrl-C reaches the running program. Returns null when this phone will not open one.
 */
public final class Pty {
    /** Linux _IOW('T', 0x31, int): unlock the slave. */
    private static final int TIOCSPTLCK = 0x40045431;
    /** Linux _IOR('T', 0x30, unsigned int): the slave number. */
    private static final int TIOCGPTN = 0x80045430;

    final FileDescriptor master;
    public final String slave;

    private Pty(FileDescriptor master, String slave) {
        this.master = master;
        this.slave = slave;
    }

    public static Pty tryOpen() {
        try {
            FileDescriptor master = Os.open("/dev/ptmx", OsConstants.O_RDWR | OsConstants.O_NOCTTY, 0);
            Os.ioctlInt(master, TIOCSPTLCK, 0);
            Int32Ref number = new Int32Ref(0);
            Os.ioctlInt(master, TIOCGPTN, number);
            if (number.value < 0) {
                return null;
            }
            return new Pty(master, "/dev/pts/" + number.value);
        } catch (ErrnoException e) {
            return null;
        } catch (Throwable e) {
            return null;
        }
    }

    InputStream in() {
        return new FileInputStream(master);
    }

    OutputStream out() throws IOException {
        return new FileOutputStream(master);
    }
}
