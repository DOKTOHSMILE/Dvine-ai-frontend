package ai.ibiko.voice;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;

/**
 * The sound of sentences already made, kept on the device for a while: a reply's audio is made in the background while
 * it is read on the screen, so Listen plays at once. Each sentence's sound is a file in {@code files/voices/said/};
 * after {@link #KEEP_MS} it is cleared, and the folder never grows past {@link #MOST_BYTES} (the oldest go first). When
 * the audio is wanted again after that, it is simply made again.
 */
public final class SpeechCache {
    /** A day: long enough to listen later, short enough not to pile up. */
    public static final long KEEP_MS = 7L * 24 * 60 * 60 * 1000; // a week, then cleared and made again when wanted
    /** About 40 minutes of speech at 24 kHz. */
    public static final long MOST_BYTES = 300L * 1024 * 1024;

    private final File dir;

    public SpeechCache(File filesDir) {
        dir = new File(filesDir, "voices/said");
    }

    static String name(String key) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(key.getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (byte x : h) {
                b.append(String.format(java.util.Locale.US, "%02x", x));
            }
            return b + ".f32";
        } catch (Exception e) {
            return Integer.toHexString(key.hashCode()) + ".f32";
        }
    }

    /** The kept sound for this sentence, or null when there is none or it is older than a week. */
    public float[] get(String key) {
        File f = new File(dir, name(key));
        if (!f.isFile()) {
            return null;
        }
        if (System.currentTimeMillis() - f.lastModified() > KEEP_MS) {
            f.delete();
            return null;
        }
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
        File f = new File(dir, name(key));
        return f.isFile() && System.currentTimeMillis() - f.lastModified() <= KEEP_MS;
    }

    /** Keeps a sentence's sound; then clears what is too old and, past the size limit, the oldest. */
    public void put(String key, float[] samples) {
        if (samples == null || samples.length == 0) {
            return;
        }
        dir.mkdirs();
        File f = new File(dir, name(key));
        File tmp = new File(dir, f.getName() + ".part");
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

    /** Clears sounds older than a week, then the oldest until the folder fits {@link #MOST_BYTES}. */
    public void prune() {
        File[] all = dir.listFiles((d, n) -> n.endsWith(".f32"));
        if (all == null) {
            return;
        }
        long now = System.currentTimeMillis(), total = 0;
        java.util.List<File> kept = new java.util.ArrayList<File>();
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
    }
}
