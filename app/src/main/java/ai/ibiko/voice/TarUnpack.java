package ai.ibiko.voice;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Unpacks a tar archive (ustar and GNU, with GNU long names and pax headers) into a folder. Only files and folders
 * are made; links are skipped and a name that would land outside the folder is refused. Pure Java.
 */
public final class TarUnpack {
    private TarUnpack() {
    }

    /** How far the unpacking is, in bytes of the archive's files. */
    public interface Progress {
        void at(long done);
    }

    /** Unpacks every entry into {@code into}; the number of files made. */
    public static int unpack(InputStream in, File into, Progress progress) throws IOException {
        byte[] h = new byte[512];
        String longName = null;
        int files = 0;
        long done = 0;
        String root = into.getCanonicalPath() + File.separator;
        while (true) {
            if (!full(in, h)) {
                return files; // the end, without the zero blocks
            }
            if (zero(h)) {
                return files;
            }
            boolean named = longName != null;
            String name = named ? longName : text(h, 0, 100);
            longName = null;
            long size = number(h, 124, 12);
            char type = (char) h[156];
            String magic = text(h, 257, 6);
            if ("ustar".equals(magic) && !named) {
                String prefix = text(h, 345, 155);
                if (!prefix.isEmpty()) {
                    name = prefix + "/" + name;
                }
            }
            if (type == 'L') {
                longName = text(read(in, size), 0, (int) size);
                continue;
            }
            if (type == 'x') {
                String path = paxPath(new String(read(in, size), StandardCharsets.UTF_8));
                if (path != null) {
                    longName = path;
                }
                continue;
            }
            if (type == 'g') {
                read(in, size);
                continue;
            }
            File out = new File(into, name);
            String where = out.getCanonicalPath();
            if (!(where + File.separator).startsWith(root) || name.startsWith("/")) {
                throw new IOException("A name in the archive points outside its folder: " + name);
            }
            if (type == '5') {
                out.mkdirs();
                continue;
            }
            if (type != '0' && type != '\0' && type != '7') {
                skip(in, size); // links and devices
                continue;
            }
            out.getParentFile().mkdirs();
            try (OutputStream o = new FileOutputStream(out)) {
                byte[] buf = new byte[65536];
                long left = size;
                while (left > 0) {
                    int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                    if (n < 0) {
                        throw new IOException("The archive ends too soon.");
                    }
                    o.write(buf, 0, n);
                    left -= n;
                    done += n;
                    if (progress != null) {
                        progress.at(done);
                    }
                }
            }
            skip(in, (512 - size % 512) % 512);
            files++;
        }
    }

    private static boolean full(InputStream in, byte[] b) throws IOException {
        int got = 0;
        while (got < b.length) {
            int n = in.read(b, got, b.length - got);
            if (n < 0) {
                if (got == 0) {
                    return false;
                }
                throw new IOException("The archive ends too soon.");
            }
            got += n;
        }
        return true;
    }

    private static boolean zero(byte[] h) {
        for (byte b : h) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static String text(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && end < b.length && b[end] != 0) {
            end++;
        }
        return new String(b, off, end - off, StandardCharsets.UTF_8).trim();
    }

    private static long number(byte[] b, int off, int len) throws IOException {
        if ((b[off] & 0x80) != 0) {
            long v = b[off] & 0x7f;
            for (int i = 1; i < len; i++) {
                v = (v << 8) | (b[off + i] & 0xff);
            }
            return v;
        }
        String s = text(b, off, len);
        try {
            return s.isEmpty() ? 0 : Long.parseLong(s, 8);
        } catch (NumberFormatException e) {
            throw new IOException("A size in the archive can't be read.");
        }
    }

    private static byte[] read(InputStream in, long size) throws IOException {
        if (size > 1 << 20) {
            throw new IOException("A header in the archive is too long.");
        }
        byte[] b = new byte[(int) size];
        int got = 0;
        while (got < b.length) {
            int n = in.read(b, got, b.length - got);
            if (n < 0) {
                throw new IOException("The archive ends too soon.");
            }
            got += n;
        }
        skip(in, (512 - size % 512) % 512);
        return b;
    }

    private static void skip(InputStream in, long n) throws IOException {
        byte[] buf = new byte[8192];
        while (n > 0) {
            int r = in.read(buf, 0, (int) Math.min(buf.length, n));
            if (r < 0) {
                throw new IOException("The archive ends too soon.");
            }
            n -= r;
        }
    }

    /** "path=" in a pax header ("30 path=some/long/name\n"). */
    static String paxPath(String pax) {
        for (String line : pax.split("\n")) {
            int sp = line.indexOf(' ');
            if (sp > 0 && line.startsWith("path=", sp + 1)) {
                return line.substring(sp + 6);
            }
        }
        return null;
    }
}
