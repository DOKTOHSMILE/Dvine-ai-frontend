package ai.dvine.linux;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;

/**
 * Unpacks a {@code .tar.gz} root filesystem. Paths that climb out of the destination are skipped.
 * Symlinks are kept as symlinks, which is what a Linux root needs ({@code bin/sh} points at busybox).
 */
public final class TarGz {
    private TarGz() {
    }

    public static void extract(InputStream gzip, File dest) throws IOException {
        dest.mkdirs();
        try (GZIPInputStream in = new GZIPInputStream(gzip)) {
            untar(in, dest);
        }
    }

    static void untar(InputStream in, File dest) throws IOException {
        byte[] header = new byte[512];
        String longName = null;
        String longLink = null;
        java.util.List<String[]> later = new java.util.ArrayList<String[]>();
        while (true) {
            int n = readHeader(in, header);
            if (n == 0 || zero(header)) {
                break;
            }
            if (n < 512) {
                throw new IOException("The archive ended in the middle of a header.");
            }
            int type = header[156] & 0xff;
            long size = octal(header, 124, 12);
            if (type == 'L') {
                longName = readText(in, size);
                skipPad(in, size);
                continue;
            }
            if (type == 'K') {
                longLink = readText(in, size);
                skipPad(in, size);
                continue;
            }
            if (type == 'x' || type == 'g') {
                String pax = readText(in, size);
                skipPad(in, size);
                String path = pax(pax, "path");
                String link = pax(pax, "linkpath");
                if (path != null) {
                    longName = path;
                }
                if (link != null) {
                    longLink = link;
                }
                continue;
            }
            String name = longName != null ? longName : field(header, 0, 100);
            String prefix = field(header, 345, 155);
            if (longName == null && !prefix.isEmpty()) {
                name = prefix + "/" + name;
            }
            String link = longLink != null ? longLink : field(header, 157, 100);
            longName = null;
            longLink = null;
            if (!safe(name)) {
                skip(in, size);
                skipPad(in, size);
                continue;
            }
            File out = new File(dest, name);
            if (type == '5') {
                out.mkdirs();
                skip(in, size);
            } else if (type == '2' || type == '1') {
                File parent = out.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                Files.deleteIfExists(out.toPath());
                if (type == '2') {
                    Files.createSymbolicLink(out.toPath(), Paths.get(link));
                } else if (!copyLink(dest, out, link)) {
                    later.add(new String[] { out.getAbsolutePath(), link });
                }
                skip(in, size);
            } else if (type == '0' || type == 0) {
                File parent = out.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                try (FileOutputStream o = new FileOutputStream(out)) {
                    copy(in, o, size);
                }
                int mode = (int) octal(header, 100, 8);
                if ((mode & 0111) != 0) {
                    out.setExecutable(true, false);
                }
                skipPad(in, size);
                continue;
            } else {
                skip(in, size);
            }
            skipPad(in, size);
        }
        for (String[] link : later) {
            copyLink(dest, new File(link[0]), link[1]);
        }
    }

    /** A hard link's target is often stored later in the archive. */
    private static boolean copyLink(File dest, File out, String link) throws IOException {
        File target = new File(dest, link);
        if (!target.isFile()) {
            File beside = new File(out.getParentFile(), link);
            if (beside.isFile()) {
                target = beside;
            }
        }
        if (!target.isFile()) {
            return false;
        }
        Files.deleteIfExists(out.toPath());
        Files.copy(target.toPath(), out.toPath());
        if (target.canExecute()) {
            out.setExecutable(true, false);
        }
        return true;
    }

    /** A path that stays inside the destination. */
    static boolean safe(String name) {
        if (name == null || name.isEmpty() || name.startsWith("/") || name.indexOf('\0') >= 0) {
            return false;
        }
        for (String part : name.split("/")) {
            if ("..".equals(part)) {
                return false;
            }
        }
        return true;
    }

    private static boolean zero(byte[] header) {
        for (byte b : header) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static int readHeader(InputStream in, byte[] header) throws IOException {
        int got = 0;
        while (got < header.length) {
            int n = in.read(header, got, header.length - got);
            if (n < 0) {
                return got;
            }
            got += n;
        }
        return got;
    }

    private static String field(byte[] header, int at, int len) {
        int end = at;
        int stop = at + len;
        while (end < stop && header[end] != 0) {
            end++;
        }
        return new String(header, at, end - at, StandardCharsets.UTF_8).trim();
    }

    private static long octal(byte[] header, int at, int len) {
        long v = 0;
        for (int i = at; i < at + len; i++) {
            byte b = header[i];
            if (b < '0' || b > '7') {
                if (v > 0) {
                    break;
                }
                continue;
            }
            v = (v << 3) + (b - '0');
        }
        return v;
    }

    private static String readText(InputStream in, long size) throws IOException {
        byte[] buf = new byte[(int) Math.min(size, 1 << 20)];
        int got = 0;
        while (got < buf.length) {
            int n = in.read(buf, got, buf.length - got);
            if (n < 0) {
                break;
            }
            got += n;
        }
        if (got < size) {
            skip(in, size - got);
        }
        int end = got;
        while (end > 0 && buf[end - 1] == 0) {
            end--;
        }
        return new String(buf, 0, end, StandardCharsets.UTF_8);
    }

    private static String pax(String record, String key) {
        String mark = key + "=";
        for (String line : record.split("\n")) {
            int sp = line.indexOf(' ');
            String body = sp >= 0 ? line.substring(sp + 1) : line;
            if (body.startsWith(mark)) {
                return body.substring(mark.length());
            }
        }
        return null;
    }

    private static void copy(InputStream in, FileOutputStream out, long size) throws IOException {
        byte[] buf = new byte[8192];
        long left = size;
        while (left > 0) {
            int n = in.read(buf, 0, (int) Math.min(buf.length, left));
            if (n < 0) {
                throw new IOException("The archive ended in the middle of a file.");
            }
            out.write(buf, 0, n);
            left -= n;
        }
    }

    private static void skip(InputStream in, long size) throws IOException {
        long left = size;
        byte[] buf = new byte[8192];
        while (left > 0) {
            int n = in.read(buf, 0, (int) Math.min(buf.length, left));
            if (n < 0) {
                return;
            }
            left -= n;
        }
    }

    private static void skipPad(InputStream in, long size) throws IOException {
        long pad = (512 - (size % 512)) % 512;
        skip(in, pad);
    }
}
