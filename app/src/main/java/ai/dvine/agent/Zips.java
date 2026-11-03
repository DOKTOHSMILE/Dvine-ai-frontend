package ai.dvine.agent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Zip and unzip for Ibiko's workspace, in pure Java. Unzipping never writes outside the folder it unzips into (an entry
 * named "../x" or "/etc/x" is refused), and stops at a size limit so a zip bomb can't fill the phone.
 */
public final class Zips {
    private Zips() {
    }

    /** Unpacked size limit (a "zip bomb" stops here). */
    static final long MOST_BYTES = 2L * 1024 * 1024 * 1024;

    /** A file or folder into a zip; how many files went in. */
    public static int zip(File from, File zip) throws IOException {
        File dir = zip.getParentFile();
        if (dir != null) {
            dir.mkdirs();
        }
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(zip))) {
            return add(z, from, from.isDirectory() ? from.getName() + "/" : "", from.isDirectory(), zip);
        }
    }

    private static int add(ZipOutputStream z, File f, String prefix, boolean isRoot, File skip) throws IOException {
        if (f.getCanonicalPath().equals(skip.getCanonicalPath()) || ".git".equals(f.getName())) {
            return 0;
        }
        if (f.isDirectory()) {
            int n = 0;
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    n += add(z, k, isRoot ? prefix : prefix + f.getName() + "/", false, skip);
                }
            }
            return n;
        }
        z.putNextEntry(new ZipEntry(prefix + f.getName()));
        try (InputStream in = new FileInputStream(f)) {
            copy(in, z, Long.MAX_VALUE);
        }
        z.closeEntry();
        return 1;
    }

    /** A zip into a folder; how many files came out. Entries that would land outside the folder are refused. */
    public static int unzip(File zip, File into) throws IOException {
        into.mkdirs();
        String root = into.getCanonicalPath() + File.separator;
        int n = 0;
        long total = 0;
        try (ZipInputStream z = new ZipInputStream(new FileInputStream(zip))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                File out = new File(into, e.getName());
                if (!out.getCanonicalPath().startsWith(root)) {
                    throw new IOException("the zip tries to write outside its folder: " + e.getName());
                }
                if (e.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                File dir = out.getParentFile();
                if (dir != null) {
                    dir.mkdirs();
                }
                try (OutputStream o = new FileOutputStream(out)) {
                    total += copy(z, o, MOST_BYTES - total);
                }
                if (total >= MOST_BYTES) {
                    throw new IOException("the zip unpacks to more than 2 GB; stopped");
                }
                n++;
            }
        }
        return n;
    }

    private static long copy(InputStream in, OutputStream out, long most) throws IOException {
        byte[] buf = new byte[64 * 1024];
        long done = 0;
        for (int r; done < most && (r = in.read(buf, 0, (int) Math.min(buf.length, most - done))) > 0; ) {
            out.write(buf, 0, r);
            done += r;
        }
        return done;
    }
}
