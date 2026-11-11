package ai.ibiko.net;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * A download that carries on where it stopped. What has arrived is kept in a ".part" file; when the network breaks,
 * the download asks the server for the rest only (an HTTP Range request) and appends it, a few times on its own
 * with a growing pause, and again from the same point when the pilot taps Try again later. Only a server that won't
 * send a part makes it start over. Pure Java: the network is behind {@link Source}, so tests give a file.
 */
public final class Resume {
    private Resume() {
    }

    /**
     * Opens the download from byte {@code from}. {@code info[0]}: how many bytes are coming (-1 when not known);
     * {@code info[1]}: 1 when they start at {@code from} (the server sent the rest), 0 when they start at the beginning.
     */
    public interface Source {
        InputStream open(String url, long from, long[] info) throws IOException;
    }

    /** Bytes in the file so far, and in all (-1 when not known). */
    public interface Progress {
        void at(long got, long total);
    }

    /** Waits between tries; tests don't wait. */
    public interface Pause {
        void sleep(long ms) throws InterruptedException;
    }

    /** How many times a broken download carries on by itself before it gives up (Try again carries on after that). */
    public static final int TRIES = 5;

    public static final Source WEB = (url, from, info) -> {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Ibiko");
        if (from > 0) {
            c.setRequestProperty("Range", "bytes=" + from + "-");
            c.setRequestProperty("Accept-Encoding", "identity"); // byte offsets only mean something unencoded
        }
        int code = c.getResponseCode();
        if (code == 416 && from > 0) {
            // nothing left to send: the part is the whole file
            c.disconnect();
            info[0] = 0;
            info[1] = 1;
            return new java.io.ByteArrayInputStream(new byte[0]);
        }
        if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
            c.disconnect();
            throw new IOException("The server answered " + code + ".");
        }
        info[0] = c.getContentLengthLong();
        info[1] = code == HttpURLConnection.HTTP_PARTIAL ? 1 : 0;
        return c.getInputStream();
    };

    /**
     * Downloads {@code url} into {@code part}, carrying on from what is already there. Returns when the whole file is
     * in {@code part}; throws when it still breaks after {@code tries} goes (the part is kept for the next try).
     */
    public static void fetch(String url, File part, Source source, Progress progress, int tries, Pause pause) throws IOException {
        part.getParentFile().mkdirs();
        IOException last = null;
        long total = -1;
        for (int attempt = 0; attempt < Math.max(1, tries); attempt++) {
            if (attempt > 0) {
                try {
                    pause.sleep(Math.min(16000, 2000L << (attempt - 1))); // 2, 4, 8, 16 s
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Stopped.");
                }
            }
            long from = part.isFile() ? part.length() : 0;
            long[] info = {-1, 0};
            InputStream in;
            try {
                in = source.open(url, from, info);
            } catch (IOException e) {
                last = e;
                continue;
            }
            boolean append = from > 0 && info[1] == 1;
            if (!append) {
                from = 0; // the server sends it all again: start the part over
            }
            if (info[0] >= 0) {
                total = from + info[0];
            }
            long got = from;
            try (InputStream src = in; OutputStream o = new FileOutputStream(part, append)) {
                byte[] buf = new byte[65536];
                int n;
                if (progress != null) {
                    progress.at(got, total);
                }
                while ((n = src.read(buf)) > 0) {
                    o.write(buf, 0, n);
                    got += n;
                    if (progress != null) {
                        progress.at(got, total);
                    }
                }
            } catch (IOException e) {
                last = e; // what arrived is kept; the next go carries on from there
                continue;
            }
            if (total >= 0 && got < total) {
                last = new IOException("The download broke off.");
                continue;
            }
            return;
        }
        throw last != null ? last : new IOException("The download broke off.");
    }

    /** The default pause: really waits. */
    public static final Pause WAIT = Thread::sleep;

    /** The SHA-256 of a file, in hex. */
    public static String sha256(File f) throws IOException {
        java.security.MessageDigest md;
        try {
            md = java.security.MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        try (InputStream in = new java.io.FileInputStream(f)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : md.digest()) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
