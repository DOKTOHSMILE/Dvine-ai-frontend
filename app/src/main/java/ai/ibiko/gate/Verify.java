package ai.ibiko.gate;

import java.io.IOException;
import java.io.InputStream;

/**
 * Checks that a thing DVINE says it did is there before it says so: a saved file is read back, and its size must be
 * what was written. Null when it checks out, otherwise what is wrong. Pure Java (the caller opens the file).
 */
public final class Verify {
    private Verify() {
    }

    public interface Opener {
        InputStream open() throws IOException;
    }

    /** The saved file holds exactly {@code expected} bytes. */
    public static String saved(Opener file, long expected) {
        long n = 0;
        try (InputStream in = file.open()) {
            if (in == null) {
                return "the saved file can't be opened";
            }
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) {
                n += r;
            }
        } catch (IOException | RuntimeException e) {
            return "the saved file can't be read back (" + (e.getMessage() == null ? "an error" : e.getMessage()) + ")";
        }
        return n == expected ? null : "the saved file has " + n + " bytes, not the " + expected + " written";
    }
}
