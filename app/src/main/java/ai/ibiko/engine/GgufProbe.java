package ai.ibiko.engine;

import ai.ibiko.bridge.Probe;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reads a model's sizes from its GGUF header without loading the weights: the context it was trained with and the
 * layer and head widths the memory estimate needs. Only the metadata at the front of the file is read; long arrays
 * (the tokenizer's word list) are skipped. Anything unreadable gives {@link Probe#unknown()}. Also reads the free
 * memory from /proc/meminfo. Pure Java.
 */
public final class GgufProbe {
    private static final int MAGIC = 0x46554747; // "GGUF", little-endian
    /** No metadata key or string is this long in a real file: a damaged header stops here. */
    private static final long MOST_STRING = 1L << 24;

    private static volatile String lastKey;
    private static volatile Probe lastProbe;

    private GgufProbe() {
    }

    /** The file's sizes, read once per file (path, length and date) and remembered. */
    public static Probe of(File model) {
        if (model == null || !model.isFile()) {
            return Probe.unknown();
        }
        String key = model.getPath() + ':' + model.length() + ':' + model.lastModified();
        Probe p = lastProbe;
        if (key.equals(lastKey) && p != null) {
            return p;
        }
        try (InputStream in = new BufferedInputStream(new FileInputStream(model), 1 << 16)) {
            p = read(in);
        } catch (IOException | RuntimeException e) {
            p = Probe.unknown();
        }
        lastProbe = p;
        lastKey = key;
        return p;
    }

    /** The sizes from a GGUF stream (version 2 or 3). */
    static Probe read(InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(raw);
        if (i32(in) != MAGIC) {
            return Probe.unknown();
        }
        int version = i32(in);
        if (version < 2) {
            return Probe.unknown();
        }
        i64(in); // tensor count
        long kvs = i64(in);
        String arch = "";
        long ctx = 0, layers = 0, embd = 0, heads = 0, headsKv = 0, keyLen = 0, valueLen = 0;
        for (long i = 0; i < kvs; i++) {
            String k = string(in);
            int type = i32(in);
            if (type == 8 && k.equals("general.architecture")) {
                arch = string(in);
                continue;
            }
            Long n = number(in, type);
            if (n == null) {
                continue;
            }
            String field = k.indexOf('.') > 0 ? k.substring(k.indexOf('.') + 1) : k;
            if (!arch.isEmpty() && !k.startsWith(arch + ".")) {
                continue;
            }
            switch (field) {
                case "context_length": ctx = n; break;
                case "block_count": layers = n; break;
                case "embedding_length": embd = n; break;
                case "attention.head_count": heads = n; break;
                case "attention.head_count_kv": headsKv = n; break;
                case "attention.key_length": keyLen = n; break;
                case "attention.value_length": valueLen = n; break;
                default: break;
            }
        }
        if (headsKv <= 0) {
            headsKv = heads;
        }
        long headDim = heads > 0 ? embd / heads : 0;
        long kWidth = (keyLen > 0 ? keyLen : headDim) * headsKv;
        long vWidth = (valueLen > 0 ? valueLen : headDim) * headsKv;
        int gqa = headsKv > 0 && heads > 0 ? (int) (heads / headsKv) : 0;
        return new Probe(clamp(ctx), clamp(layers), clamp(embd), clamp(heads), clamp(headsKv), clamp(kWidth),
                clamp(vWidth), gqa);
    }

    /** Free memory the system could hand out now (MemAvailable), in bytes; 0 when it can't be read. */
    public static long freeBytes() {
        File f = new File("/proc/meminfo");
        if (!f.canRead()) {
            return 0;
        }
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("MemAvailable:")) {
                    return Long.parseLong(line.replaceAll("[^0-9]", "")) * 1024L;
                }
            }
        } catch (IOException | NumberFormatException e) {
            return 0;
        }
        return 0;
    }

    private static int clamp(long v) {
        return v <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, v);
    }

    /** A whole-number value, or null after skipping a value that isn't one. */
    private static Long number(DataInputStream in, int type) throws IOException {
        switch (type) {
            case 0: return (long) in.readUnsignedByte();
            case 1: return (long) in.readByte();
            case 2: return (long) (Short.reverseBytes(in.readShort()) & 0xffff);
            case 3: return (long) Short.reverseBytes(in.readShort());
            case 4: return i32(in) & 0xffffffffL;
            case 5: return (long) i32(in);
            case 10: case 11: return i64(in);
            default:
                skip(in, type);
                return null;
        }
    }

    /** Skips one value of the given type, arrays included, without keeping it. */
    private static void skip(DataInputStream in, int type) throws IOException {
        switch (type) {
            case 0: case 1: case 7: skipBytes(in, 1); break;
            case 2: case 3: skipBytes(in, 2); break;
            case 4: case 5: case 6: skipBytes(in, 4); break;
            case 10: case 11: case 12: skipBytes(in, 8); break;
            case 8: skipBytes(in, length(in)); break;
            case 9: {
                int inner = i32(in);
                long n = i64(in);
                if (n < 0) {
                    throw new IOException("a damaged array");
                }
                int width = inner == 0 || inner == 1 || inner == 7 ? 1 : inner == 2 || inner == 3 ? 2
                        : inner == 4 || inner == 5 || inner == 6 ? 4 : inner >= 10 && inner <= 12 ? 8 : -1;
                if (width > 0) {
                    skipBytes(in, n * width);
                } else {
                    for (long i = 0; i < n; i++) {
                        skip(in, inner);
                    }
                }
                break;
            }
            default: throw new IOException("an unknown value type " + type);
        }
    }

    private static long length(DataInputStream in) throws IOException {
        long n = i64(in);
        if (n < 0 || n > MOST_STRING) {
            throw new IOException("a damaged string");
        }
        return n;
    }

    private static String string(DataInputStream in) throws IOException {
        byte[] b = new byte[(int) length(in)];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static void skipBytes(DataInputStream in, long n) throws IOException {
        while (n > 0) {
            long s = in.skip(n);
            if (s <= 0) {
                if (in.read() < 0) {
                    throw new EOFException();
                }
                s = 1;
            }
            n -= s;
        }
    }

    private static int i32(DataInputStream in) throws IOException {
        return Integer.reverseBytes(in.readInt());
    }

    private static long i64(DataInputStream in) throws IOException {
        return Long.reverseBytes(in.readLong());
    }
}
