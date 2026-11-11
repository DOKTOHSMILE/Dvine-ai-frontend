package ai.ibiko.dvine1.comprehend;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A large table kept on the device and never loaded into memory: a UTF-8 text file, one row a line, its first column
 * the key, sorted by the key's bytes. A key's rows are found by binary search over the file's bytes, a few reads of a
 * few kilobytes, however large the file (WordNet: 22 MB; ConceptNet: many more). Thread-safe. Pure Java.
 */
public final class SortedTable {
    private final File file;
    private final long size;

    public SortedTable(File file) {
        this.file = file;
        this.size = file != null && file.isFile() ? file.length() : 0;
    }

    public boolean present() {
        return size > 0;
    }

    /** Every row whose key is exactly {@code key} (lower case is the tables' convention), split on tabs. */
    public synchronized List<String[]> rows(String key) {
        List<String[]> out = new ArrayList<String[]>();
        if (size == 0 || key == null || key.isEmpty()) {
            return out;
        }
        byte[] want = key.getBytes(StandardCharsets.UTF_8);
        try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
            // the first line whose key is not below the wanted one: lo is always the start of a line
            long lo = 0, hi = size;
            while (lo < hi) {
                long start = lineStart(f, (lo + hi) >>> 1);
                if (start < lo) {
                    start = lo;
                }
                if (compare(keyAt(f, start), want) < 0) {
                    lo = nextLine(f, start);
                } else {
                    hi = start;
                }
            }
            long at = lo;
            while (at < size) {
                f.seek(at);
                String line = readLine(f);
                if (line == null) {
                    break;
                }
                String[] cols = line.split("\t", -1);
                int c = compare(cols[0].getBytes(StandardCharsets.UTF_8), want);
                if (c > 0) {
                    break;
                }
                if (c == 0) {
                    out.add(cols);
                }
                at = f.getFilePointer();
                if (out.size() > 400) {
                    break; // enough for anything said about one key
                }
            }
        } catch (IOException e) {
            // unreadable: nothing found
        }
        return out;
    }

    private long lineStart(RandomAccessFile f, long pos) throws IOException {
        if (pos <= 0) {
            return 0;
        }
        long p = Math.min(pos, size) - 1;
        byte[] buf = new byte[512];
        while (p >= 0) {
            long from = Math.max(0, p - buf.length + 1);
            int n = (int) (p - from + 1);
            f.seek(from);
            f.readFully(buf, 0, n);
            for (int i = n - 1; i >= 0; i--) {
                if (buf[i] == '\n') {
                    return from + i + 1;
                }
            }
            p = from - 1;
        }
        return 0;
    }

    private long nextLine(RandomAccessFile f, long start) throws IOException {
        f.seek(start);
        int b;
        while ((b = f.read()) != -1) {
            if (b == '\n') {
                return f.getFilePointer();
            }
        }
        return size;
    }

    private byte[] keyAt(RandomAccessFile f, long start) throws IOException {
        f.seek(start);
        byte[] buf = new byte[160];
        int n = 0, b;
        while (n < buf.length && (b = f.read()) != -1 && b != '\t' && b != '\n') {
            buf[n++] = (byte) b;
        }
        byte[] k = new byte[n];
        System.arraycopy(buf, 0, k, 0, n);
        return k;
    }

    private static String readLine(RandomAccessFile f) throws IOException {
        java.io.ByteArrayOutputStream line = new java.io.ByteArrayOutputStream(256);
        int b;
        boolean any = false;
        while ((b = f.read()) != -1) {
            any = true;
            if (b == '\n') {
                break;
            }
            line.write(b);
        }
        return any ? new String(line.toByteArray(), StandardCharsets.UTF_8) : null;
    }

    static int compare(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int x = a[i] & 0xff, y = b[i] & 0xff;
            if (x != y) {
                return x - y;
            }
        }
        return a.length - b.length;
    }
}
