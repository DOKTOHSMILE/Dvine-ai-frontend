package ai.ibiko.agent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

/**
 * A project folder as a .tar.gz, to hand to Termux (which can't see DVINE's own files): every file under it except
 * what is rebuilt or kept elsewhere (.git, node_modules, build output, virtual environments), within a size limit.
 * Plain POSIX ustar, which Termux's tar reads. Pure Java.
 */
public final class TarGz {
    /** Folders never copied: rebuilt in Termux, or not the project's. */
    static final String[] SKIP = {".git", "node_modules", "build", ".gradle", "__pycache__", ".venv", "venv", "target", "dist", ".idea"};

    private TarGz() {
    }

    /** The folder's files as .tar.gz, or null when they come to more than {@code most} bytes compressed. */
    public static byte[] pack(File folder, int most) throws IOException {
        List<File> files = new ArrayList<File>();
        collect(folder, files);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(b)) {
            String root = folder.getCanonicalPath();
            for (File f : files) {
                String name = f.getCanonicalPath().substring(root.length() + 1).replace('\\', '/');
                byte[] data = Files.readAllBytes(f.toPath());
                if (!header(gz, name, data.length, f.canExecute(), f.lastModified())) {
                    continue; // a path too long for a tar header: left out
                }
                gz.write(data);
                int pad = (512 - data.length % 512) % 512;
                gz.write(new byte[pad]);
                if (b.size() > most) {
                    return null;
                }
            }
            gz.write(new byte[1024]); // the end: two empty blocks
        }
        return b.size() > most ? null : b.toByteArray();
    }

    private static void collect(File dir, List<File> out) throws IOException {
        File[] in = dir.listFiles();
        if (in == null) {
            return;
        }
        java.util.Arrays.sort(in);
        for (File f : in) {
            if (java.nio.file.Files.isSymbolicLink(f.toPath())) {
                continue;
            }
            if (f.isDirectory()) {
                if (!skipped(f.getName())) {
                    collect(f, out);
                }
            } else if (f.isFile()) {
                out.add(f);
            }
        }
    }

    static boolean skipped(String folder) {
        String n = folder.toLowerCase(Locale.ROOT);
        for (String s : SKIP) {
            if (n.equals(s)) {
                return true;
            }
        }
        return false;
    }

    /** A ustar header; false when the name can't be stored (over 255 characters, or no split fits). */
    private static boolean header(OutputStream out, String name, long size, boolean exec, long mtime) throws IOException {
        byte[] h = new byte[512];
        byte[] full = name.getBytes(StandardCharsets.UTF_8);
        String prefix = "", base = name;
        if (full.length > 100) {
            int cut = name.lastIndexOf('/', Math.min(name.length() - 1, 155));
            while (cut > 0 && name.substring(cut + 1).getBytes(StandardCharsets.UTF_8).length > 100) {
                cut = name.lastIndexOf('/', cut - 1);
            }
            if (cut <= 0) {
                return false;
            }
            prefix = name.substring(0, cut);
            base = name.substring(cut + 1);
            if (prefix.getBytes(StandardCharsets.UTF_8).length > 155) {
                return false;
            }
        }
        put(h, 0, 100, base);
        put(h, 100, 8, octal(exec ? 0755 : 0644, 7));
        put(h, 108, 8, octal(0, 7));
        put(h, 116, 8, octal(0, 7));
        put(h, 124, 12, octal(size, 11));
        put(h, 136, 12, octal(Math.max(0, mtime / 1000), 11));
        for (int i = 148; i < 156; i++) {
            h[i] = ' '; // the checksum counts itself as spaces
        }
        h[156] = '0';
        put(h, 257, 6, "ustar");
        h[262] = 0;
        h[263] = '0';
        h[264] = '0';
        put(h, 345, 155, prefix);
        long sum = 0;
        for (byte x : h) {
            sum += x & 0xff;
        }
        put(h, 148, 8, octal(sum, 6) + "\0 ");
        out.write(h);
        return true;
    }

    private static String octal(long v, int digits) {
        String s = Long.toOctalString(v);
        StringBuilder b = new StringBuilder();
        for (int i = s.length(); i < digits; i++) {
            b.append('0');
        }
        return b.append(s).toString();
    }

    private static void put(byte[] h, int at, int len, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(b, 0, h, at, Math.min(len, b.length));
    }
}
