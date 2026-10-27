package ai.dvine.llm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Fetches a model file: into a ".part" file that resumes where it stopped, hashed as it arrives, and renamed
 * into place only when its size and SHA-256 match the spec (a damaged or tampered file is deleted, never loaded).
 * The network is behind {@link Source} so the logic is tested without one. Blocking: run it off the UI thread.
 */
public final class ModelDownloader {
    /** Opens the file from a byte offset (a Range request); returns the stream positioned there. */
    public interface Source {
        InputStream open(long from) throws IOException;
    }

    public interface Progress {
        /** Bytes so far of the total; return false to stop (the part file is kept for a resume). */
        boolean onProgress(long done, long total);
    }

    public enum Result { DONE, STOPPED, FAILED, BAD_FILE }

    private ModelDownloader() {
    }

    public static Result fetch(ModelSpec spec, File target, Source source, Progress progress) {
        File part = new File(target.getPath() + ".part");
        File dir = target.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            return Result.FAILED;
        }
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            return Result.FAILED;
        }
        byte[] buf = new byte[1 << 16];
        long have = part.isFile() ? part.length() : 0L;
        if (have > spec.bytes) {
            part.delete();
            have = 0L;
        }
        // the part already on disk counts toward the hash
        if (have > 0) {
            try (FileInputStream in = new FileInputStream(part)) {
                int n;
                while ((n = in.read(buf)) > 0) {
                    sha.update(buf, 0, n);
                }
            } catch (IOException e) {
                part.delete();
                have = 0L;
                sha.reset();
            }
        }
        long done = have;
        if (done < spec.bytes) {
            try (InputStream in = source.open(done); FileOutputStream out = new FileOutputStream(part, done > 0)) {
                int n;
                long lastTold = -1;
                while (done < spec.bytes && (n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    sha.update(buf, 0, n);
                    done += n;
                    if (progress != null && done - lastTold >= 1 << 20) {
                        lastTold = done;
                        if (!progress.onProgress(done, spec.bytes)) {
                            return Result.STOPPED;
                        }
                    }
                }
            } catch (IOException e) {
                return Result.FAILED; // keep the part: the next try resumes
            }
        }
        if (done != spec.bytes || !hex(sha.digest()).equalsIgnoreCase(spec.sha256)) {
            part.delete();
            return Result.BAD_FILE;
        }
        if (target.exists() && !target.delete()) {
            return Result.FAILED;
        }
        if (!part.renameTo(target)) {
            return Result.FAILED;
        }
        if (progress != null) {
            progress.onProgress(done, spec.bytes);
        }
        return Result.DONE;
    }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder(b.length * 2);
        for (byte x : b) {
            s.append(Character.forDigit((x >> 4) & 15, 16)).append(Character.forDigit(x & 15, 16));
        }
        return s.toString();
    }

    /** The real source: HTTPS with a Range header to resume. */
    public static Source https(final String url) {
        return from -> {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(true);
            if (from > 0) {
                c.setRequestProperty("Range", "bytes=" + from + "-");
            }
            int code = c.getResponseCode();
            if (from > 0 && code != 206) {
                c.disconnect();
                throw new IOException("the server did not resume the download (HTTP " + code + ")");
            }
            if (code != 200 && code != 206) {
                c.disconnect();
                throw new IOException("HTTP " + code);
            }
            return c.getInputStream();
        };
    }
}
