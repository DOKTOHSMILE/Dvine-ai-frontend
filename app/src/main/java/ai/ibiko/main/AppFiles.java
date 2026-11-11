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
 * (AppFilesActivity). They are the common-sense table (brain/knowledge/concepts.tsv) the comprehension layer reads
 * and the human voice (Kokoro: a man's and a woman's voices, {@link ai.ibiko.voice.VoicePacks}). Models (llama and
 * the others) are not among them: they are chosen and downloaded in Settings. Pure Java: the network is behind
 * {@link Source} so tests give a file instead.
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

    /** Where a download comes from; it can start part-way, so a broken download carries on (resume). */
    public interface Source extends ai.ibiko.net.Resume.Source {
    }

    /** How far it is, over all the files: a percent (0 to 100) and a line such as "12.3 of 105.0 MB". */
    public interface Progress {
        void at(int percent, String line);
    }

    /** The voice every start needs: Kokoro holds both the man's and the woman's voices. */
    public static final ai.ibiko.voice.VoicePacks.Pack VOICE = ai.ibiko.voice.VoicePacks.KOKORO;
    /** The common-sense table's download size, for the one progress bar over everything. */
    static final long CONCEPTS_BYTES = 2_622_379L;

    public static final Source WEB = ai.ibiko.net.Resume.WEB::open;

    /** How long a broken download waits before it carries on (tests don't wait). */
    static ai.ibiko.net.Resume.Pause pause = ai.ibiko.net.Resume.WAIT;

    static File concepts(File filesDir) {
        return new File(new File(new File(filesDir, "brain"), "knowledge"), "concepts.tsv");
    }

    private static File mark(File filesDir) {
        return new File(concepts(filesDir).getPath() + ".download");
    }

    /** Everything is in place: the app can open. */
    public static boolean ready(File filesDir) {
        return ready(filesDir, VOICE);
    }

    static boolean ready(File filesDir, ai.ibiko.voice.VoicePacks.Pack voice) {
        return conceptsReady(filesDir) && (voice == null || ai.ibiko.voice.VoicePacks.installed(filesDir, voice));
    }

    static boolean conceptsReady(File filesDir) {
        File f = concepts(filesDir), m = mark(filesDir);
        try {
            return f.isFile() && f.length() > 0 && m.isFile()
                    && CONCEPTS_VERSION.equals(new String(Files.readAllBytes(m.toPath()), StandardCharsets.UTF_8).trim());
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Downloads what is missing, checks it and unpacks it, with one progress over all of it. Throws when a download
     * breaks off or is not the expected file; nothing half-made is left as if it were ready.
     */
    public static void fetch(File filesDir, Source source, Progress progress) throws IOException {
        fetch(filesDir, source, progress, VOICE);
    }

    static synchronized void fetch(File filesDir, Source source, Progress progress, ai.ibiko.voice.VoicePacks.Pack voice)
            throws IOException {
        boolean needConcepts = !conceptsReady(filesDir);
        boolean needVoice = voice != null && !ai.ibiko.voice.VoicePacks.installed(filesDir, voice);
        final long a = needConcepts ? CONCEPTS_BYTES : 0, b = needVoice ? voice.bytes : 0, all = Math.max(1, a + b);
        if (needConcepts) {
            fetchConcepts(filesDir, source, (got, total) -> say(progress, got, all, null));
        }
        if (needVoice) {
            // the voice: downloaded (most of the bar), then unpacked (the last tenth)
            ai.ibiko.voice.VoicePacks.install(filesDir, voice, source::open, (stage, pct) -> {
                if ("Downloading".equals(stage)) {
                    say(progress, a + (long) (b * 0.9 * pct / 100.0), all, null);
                } else {
                    say(progress, a + (long) (b * (0.9 + 0.1 * pct / 100.0)), all, "Setting up the app files");
                }
            });
        }
        if (progress != null) {
            progress.at(100, "Done");
        }
    }

    private static void say(Progress p, long got, long all, String line) {
        if (p != null) {
            p.at((int) Math.min(100, got * 100 / all), line != null ? line
                    : String.format(java.util.Locale.US, "%.1f of %.1f MB", got / 1048576.0, all / 1048576.0));
        }
    }

    private interface Bytes {
        void at(long got, long total);
    }

    private static void fetchConcepts(File filesDir, Source source, Bytes progress) throws IOException {
        File out = concepts(filesDir);
        File dir = out.getParentFile();
        dir.mkdirs();
        File gz = new File(dir, "concepts.tsv.gz.part"), tsv = new File(dir, "concepts.tsv.part");
        // what arrived before is kept: a broken download carries on from there
        ai.ibiko.net.Resume.fetch(CONCEPTS_URL, gz, source, progress::at, ai.ibiko.net.Resume.TRIES, pause);
        if (!CONCEPTS_SHA256.equals(ai.ibiko.net.Resume.sha256(gz))) {
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
