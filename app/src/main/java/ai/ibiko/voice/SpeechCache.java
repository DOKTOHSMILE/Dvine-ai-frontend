package ai.ibiko.voice;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The voice of replies, made once and kept on the device for {@link #KEEP_MS} (7 days), so Listen plays the kept sound
 * at once instead of making it while you wait.
 * <p>
 * Each reply's voice belongs to that message alone: it is kept in {@code files/voices/said/<chat>/<message>/}, one file
 * a sentence, numbered in the order they are said ({@code 000-<sha1>.f32}, {@code 001-…}). The owner is
 * {@code "<chat>/<message>"}. Deleting a chat deletes {@code said/<chat>} with every message's voice in it
 * ({@link #forgetChat}). Speech that belongs to no message (a sentence read as it is written) is kept loose in
 * {@code said/} for the same week. Past 7 days, or past {@link #MOST_BYTES} in all (the oldest first), files are
 * cleared; the voice is then simply made again when wanted.
 */
public final class SpeechCache {
    /** A week: long enough to listen later, short enough not to pile up. */
    public static final long KEEP_MS = 7L * 24 * 60 * 60 * 1000;
    /** About two hours of speech at 24 kHz. */
    public static final long MOST_BYTES = 300L * 1024 * 1024;

    private final File dir;

    public SpeechCache(File filesDir) {
        dir = root(filesDir);
    }

    static File root(File filesDir) {
        return new File(filesDir, "voices/said");
    }

    /** Deletes the voice kept for every message of a chat. */
    public static void forgetChat(File filesDir, String chat) {
        if (filesDir == null || chat == null || chat.isEmpty()) {
            return;
        }
        wipe(new File(root(filesDir), safe(chat)));
    }

    static String sha(String key) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(key.getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (byte x : h) {
                b.append(String.format(java.util.Locale.US, "%02x", x));
            }
            return b.toString();
        } catch (Exception e) {
            return Integer.toHexString(key.hashCode());
        }
    }

    /** A loose sentence's file name (no owner). */
    static String name(String key) {
        return sha(key) + ".f32";
    }

    private static String safe(String part) {
        String s = part == null ? "" : part.replaceAll("[^A-Za-z0-9._-]", "_");
        return s.isEmpty() || s.startsWith(".") ? "_" : s;
    }

    /** The folder of one message's voice: {@code said/<chat>/<message>}; {@code said} itself with no owner. */
    File folder(String owner) {
        if (owner == null || owner.isEmpty()) {
            return dir;
        }
        File f = dir;
        for (String part : owner.split("/")) {
            if (!part.isEmpty()) {
                f = new File(f, safe(part));
            }
        }
        return f;
    }

    /** This sentence's kept file for this owner, or null. */
    private File find(String owner, String key) {
        if (owner == null || owner.isEmpty()) {
            File f = new File(dir, name(key));
            return f.isFile() ? f : null;
        }
        final String end = "-" + sha(key) + ".f32";
        File[] hit = folder(owner).listFiles((d, n) -> n.endsWith(end));
        return hit == null || hit.length == 0 ? null : hit[0];
    }

    public float[] get(String key) {
        return get(null, key);
    }

    /** The kept sound of this sentence of this message, or null (none, or older than a week). */
    public float[] get(String owner, String key) {
        File f = find(owner, key);
        if (f == null) {
            return null;
        }
        if (System.currentTimeMillis() - f.lastModified() > KEEP_MS) {
            f.delete();
            return null;
        }
        return read(f);
    }

    static float[] read(File f) {
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new java.io.FileInputStream(f)))) {
            int n = in.readInt();
            if (n <= 0 || n > 20_000_000) {
                return null;
            }
            float[] s = new float[n];
            for (int i = 0; i < n; i++) {
                s[i] = in.readFloat();
            }
            return s;
        } catch (IOException e) {
            f.delete();
            return null;
        }
    }

    public boolean has(String key) {
        return has(null, key);
    }

    public boolean has(String owner, String key) {
        File f = find(owner, key);
        return f != null && System.currentTimeMillis() - f.lastModified() <= KEEP_MS;
    }

    public void put(String key, float[] samples) {
        put(null, key, samples);
    }

    /**
     * Keeps a sentence's sound under its message, numbered after the ones already there (the order they are said);
     * then clears what is too old and, past the size limit, the oldest.
     */
    public void put(String owner, String key, float[] samples) {
        if (samples == null || samples.length == 0) {
            return;
        }
        File folder = folder(owner);
        folder.mkdirs();
        File f = find(owner, key);
        if (f == null) {
            if (owner == null || owner.isEmpty()) {
                f = new File(folder, name(key));
            } else {
                File[] there = folder.listFiles((d, n) -> n.endsWith(".f32"));
                int next = there == null ? 0 : there.length;
                f = new File(folder, String.format(java.util.Locale.US, "%03d-%s.f32", next, sha(key)));
            }
        }
        File tmp = new File(folder, f.getName() + ".part");
        try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(new java.io.FileOutputStream(tmp)))) {
            out.writeInt(samples.length);
            for (float v : samples) {
                out.writeFloat(v);
            }
        } catch (IOException e) {
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(f)) {
            tmp.delete();
        }
        prune();
    }

    /** One message's kept sentences, in the order they are said. */
    public List<File> ordered(String owner) {
        File[] all = owner == null || owner.isEmpty() ? null : folder(owner).listFiles((d, n) -> n.endsWith(".f32"));
        List<File> out = new ArrayList<File>(all == null ? new ArrayList<File>() : Arrays.asList(all));
        out.sort((a, b) -> a.getName().compareTo(b.getName()));
        return out;
    }

    /** Clears sounds older than a week (in every message's folder), then the oldest until all fits {@link #MOST_BYTES}. */
    public void prune() {
        List<File> all = new ArrayList<File>();
        collect(dir, all);
        long now = System.currentTimeMillis(), total = 0;
        List<File> kept = new ArrayList<File>();
        for (File f : all) {
            if (now - f.lastModified() > KEEP_MS) {
                f.delete();
            } else {
                kept.add(f);
                total += f.length();
            }
        }
        kept.sort((a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i < kept.size() && total > MOST_BYTES; i++) {
            total -= kept.get(i).length();
            kept.get(i).delete();
        }
        dropEmpty(dir);
    }

    private static void collect(File d, List<File> out) {
        File[] kids = d.listFiles();
        if (kids == null) {
            return;
        }
        for (File k : kids) {
            if (k.isDirectory()) {
                collect(k, out);
            } else if (k.getName().endsWith(".f32")) {
                out.add(k);
            }
        }
    }

    private void dropEmpty(File d) {
        File[] kids = d.listFiles();
        if (kids == null) {
            return;
        }
        for (File k : kids) {
            if (k.isDirectory()) {
                dropEmpty(k);
                String[] left = k.list();
                if (left != null && left.length == 0) {
                    k.delete();
                }
            }
        }
    }

    private static void wipe(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                wipe(k);
            }
        }
        f.delete();
    }
}
