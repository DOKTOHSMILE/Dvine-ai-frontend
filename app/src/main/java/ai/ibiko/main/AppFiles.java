package ai.ibiko.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.zip.GZIPInputStream;

/**
 * The files Ibiko needs before it opens that are not inside the app: downloaded once, on the first start, and checked
 * against their checksum. The pilot only sees "Downloading app files"; until they are in place the app does not open
 * (AppFilesActivity). Today that is the common-sense table (brain/knowledge/concepts.tsv) the comprehension layer reads.
 * Pure Java: the network is behind {@link Source} so tests give a file instead.
 */
public final class AppFiles {
    private AppFiles() {
    }

    /** Which build of the table the app expects; tools/build_concepts.py prints the checksum for a new one. */
    static final String CONCEPTS_VERSION = "2026-11-11";
    static final String CONCEPTS_SHA256 = "4e019a34747edd6fa3989f3d570978b2a7c266cfe202b570de5200abd16d9f04";
    static final String CONCEPTS_URL = "https://raw.githubusercontent.com/DOKTOHSMILE/Dvine-ai-frontend/main/downloads/knowledge/concepts-"
            + CONCEPTS_VERSION + ".tsv.gz";
    /** What the pilot is shown, the same for every file. */
    public static final String TITLE = "Downloading app files";

    /** Where a download comes from. */
    public interface Source {
        /** The stream to read; {@code length[0]} is set to its size in bytes, or -1 when not known. */
        InputStream open(String url, long[] length) throws IOException;
    }

    /** How far the download is: bytes so far and in all (-1 when not known). */
    public interface Progress {
        void at(long got, long total);
    }

    public static final Source WEB = (url, length) -> {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Ibiko");
        int code = c.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            c.disconnect();
            throw new IOException("The server answered " + code + ".");
        }
        length[0] = c.getContentLengthLong();
        return c.getInputStream();
    };

    static File concepts(File filesDir) {
        return new File(new File(new File(filesDir, "brain"), "knowledge"), "concepts.tsv");
    }

    private static File mark(File filesDir) {
        return new File(concepts(filesDir).getPath() + ".download");
    }

    /** Everything is in place: the app can open. */
    public static boolean ready(File filesDir) {
        File f = concepts(filesDir), m = mark(filesDir);
        try {
            return f.isFile() && f.length() > 0 && m.isFile()
                    && CONCEPTS_VERSION.equals(new String(Files.readAllBytes(m.toPath()), StandardCharsets.UTF_8).trim());
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Downloads what is missing, checks it and unpacks it. Throws when the download breaks off or is not the expected
     * file; nothing half-made is left as if it were ready.
     */
    public static synchronized void fetch(File filesDir, Source source, Progress progress) throws IOException {
        if (ready(filesDir)) {
            return;
        }
        File out = concepts(filesDir);
        File dir = out.getParentFile();
        dir.mkdirs();
        File gz = new File(dir, "concepts.tsv.gz.part"), tsv = new File(dir, "concepts.tsv.part");
        long[] length = {-1};
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        try (InputStream in = source.open(CONCEPTS_URL, length); OutputStream o = new FileOutputStream(gz)) {
            byte[] buf = new byte[32768];
            long got = 0;
            int n;
            if (progress != null) {
                progress.at(0, length[0]);
            }
            while ((n = in.read(buf)) > 0) {
                o.write(buf, 0, n);
                sha.update(buf, 0, n);
                got += n;
                if (progress != null) {
                    progress.at(got, length[0]);
                }
            }
        } catch (IOException e) {
            gz.delete();
            throw e;
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : sha.digest()) {
            hex.append(String.format("%02x", b));
        }
        if (!CONCEPTS_SHA256.equals(hex.toString())) {
            gz.delete();
            throw new IOException("The download was not the expected file.");
        }
        try (InputStream in = new GZIPInputStream(new FileInputStream(gz), 65536); OutputStream o = new FileOutputStream(tsv)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                o.write(buf, 0, n);
            }
        } catch (IOException e) {
            tsv.delete();
            throw e;
        } finally {
            gz.delete();
        }
        Files.move(tsv.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.write(mark(filesDir).toPath(), CONCEPTS_VERSION.getBytes(StandardCharsets.UTF_8));
    }
}
