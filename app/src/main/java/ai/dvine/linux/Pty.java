package ai.dvine.linux;

import android.system.ErrnoException;
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
            // Int32Ref and this ioctlInt are hidden in the public SDK: reached by reflection, so the app builds with
            // any android.jar and the phone still gets its real terminal
            Class<?> ref = Class.forName("android.system.Int32Ref");
            java.lang.reflect.Method ioctl = Os.class.getMethod("ioctlInt", FileDescriptor.class, int.class, ref);
            ioctl.invoke(null, master, TIOCSPTLCK, ref.getConstructor(int.class).newInstance(0));
            Object number = ref.getConstructor(int.class).newInstance(0);
            ioctl.invoke(null, master, TIOCGPTN, number);
            int n = ref.getField("value").getInt(number);
            if (n < 0) {
                return null;
            }
            return new Pty(master, "/dev/pts/" + n);
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
