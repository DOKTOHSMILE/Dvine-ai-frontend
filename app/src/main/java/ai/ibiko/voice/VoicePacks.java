package ai.ibiko.voice;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Human-sounding voices that run on the device with no internet (neural text-to-speech, played by
 * {@link NeuralVoice}): Kokoro (11 voices, the most natural) and two fast Piper voices. Each pack is downloaded once
 * from the sherpa-onnx model releases, checked against its checksum and unpacked into files/voices/. Pure Java.
 */
public final class VoicePacks {
    private VoicePacks() {
    }

    /** One voice inside a pack: its speaker number, name and whether it is a man's or a woman's voice. */
    public static final class Speaker {
        public final int id;
        public final String name, title;
        public final boolean male;

        public Speaker(int id, String name, String title, boolean male) {
            this.id = id;
            this.name = name;
            this.title = title;
            this.male = male;
        }
    }

    /** A downloadable voice pack. */
    public static final class Pack {
        public final String key, title, about, url, sha256, dir, model, kind;
        public final long bytes;
        public final List<Speaker> speakers;

        public Pack(String key, String title, String about, String url, String sha256, long bytes, String dir, String model,
                String kind, Speaker... speakers) {
            this.key = key;
            this.title = title;
            this.about = about;
            this.url = url;
            this.sha256 = sha256;
            this.bytes = bytes;
            this.dir = dir;
            this.model = model;
            this.kind = kind;
            this.speakers = Collections.unmodifiableList(Arrays.asList(speakers));
        }

        public String size() {
            return Math.round(bytes / 1048576.0) + " MB";
        }

        /** The voice for a man (Ibiko, Obi) or a woman (Ivie): the one named, else the pack's first of that kind. */
        public Speaker speaker(boolean male, String named) {
            for (Speaker s : speakers) {
                if (s.name.equals(named)) {
                    return s;
                }
            }
            for (Speaker s : speakers) {
                if (s.male == male) {
                    return s;
                }
            }
            return speakers.get(0);
        }

        public Speaker byName(String name) {
            for (Speaker s : speakers) {
                if (s.name.equals(name)) {
                    return s;
                }
            }
            return null;
        }
    }

    static final String RELEASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/";

    /**
     * Kokoro 82M (Apache 2.0) in its 8-bit form: speaker numbers as the model orders them, checked by their pitch
     * (the men's voices 98 to 145 Hz, the women's 161 to 211 Hz).
     */
    public static final Pack KOKORO = new Pack("kokoro", "Kokoro: the most natural",
            "11 voices, men and women, American and British. The most human; slower on older devices.",
            RELEASE + "kokoro-int8-en-v0_19.tar.bz2", "c9f0dd393615805b0bab050c340834d5e684e732aec91c0e860cd30e982c08bd",
            103248205L, "kokoro-int8-en-v0_19", "model.int8.onnx", "kokoro",
            new Speaker(6, "am_michael", "Michael (American man)", true),
            new Speaker(5, "am_adam", "Adam (American man)", true),
            new Speaker(9, "bm_george", "George (British man)", true),
            new Speaker(10, "bm_lewis", "Lewis (British man, deep)", true),
            new Speaker(1, "af_bella", "Bella (American woman)", false),
            new Speaker(3, "af_sarah", "Sarah (American woman)", false),
            new Speaker(2, "af_nicole", "Nicole (American woman, soft)", false),
            new Speaker(4, "af_sky", "Sky (American woman)", false),
            new Speaker(0, "af", "Kokoro (American woman)", false),
            new Speaker(7, "bf_emma", "Emma (British woman)", false),
            new Speaker(8, "bf_isabella", "Isabella (British woman)", false));

    public static final Pack PIPER_RYAN = new Pack("piper-ryan", "Piper Ryan: fast, a man's voice",
            "One clear man's voice. Very fast on any device.",
            RELEASE + "vits-piper-en_US-ryan-medium.tar.bz2", "c546af78b6395b4e7c4ce1ed899438b64426a362f5d4ec5fecd090ded9ad7505",
            67213100L, "vits-piper-en_US-ryan-medium", "en_US-ryan-medium.onnx", "vits",
            new Speaker(0, "ryan", "Ryan (American man)", true));

    public static final Pack PIPER_AMY = new Pack("piper-amy", "Piper Amy: fast, a woman's voice",
            "One clear woman's voice. Very fast on any device.",
            RELEASE + "vits-piper-en_US-amy-medium.tar.bz2", "9a5d1fc497f85e8022b785bff5f8105203b1e33099ee6265203efc70b0cb0264",
            67223746L, "vits-piper-en_US-amy-medium", "en_US-amy-medium.onnx", "vits",
            new Speaker(0, "amy", "Amy (American woman)", false));

    public static final List<Pack> ALL = Collections.unmodifiableList(Arrays.asList(KOKORO, PIPER_RYAN, PIPER_AMY));

    public static Pack byKey(String key) {
        for (Pack p : ALL) {
            if (p.key.equals(key)) {
                return p;
            }
        }
        return null;
    }

    public static File root(File filesDir) {
        return new File(filesDir, "voices");
    }

    public static File folder(File filesDir, Pack p) {
        return new File(root(filesDir), p.dir);
    }

    private static File mark(File filesDir, Pack p) {
        return new File(root(filesDir), p.key + ".ready");
    }

    /** Downloaded, checked and unpacked. */
    public static boolean installed(File filesDir, Pack p) {
        File m = mark(filesDir, p);
        try {
            return m.isFile() && p.sha256.equals(new String(Files.readAllBytes(m.toPath()), StandardCharsets.UTF_8).trim())
                    && new File(folder(filesDir, p), p.model).isFile();
        } catch (IOException e) {
            return false;
        }
    }

    public static List<Pack> installedPacks(File filesDir) {
        List<Pack> out = new ArrayList<Pack>();
        for (Pack p : ALL) {
            if (installed(filesDir, p)) {
                out.add(p);
            }
        }
        return out;
    }

    /** Where a download comes from (the web on the device, a file in tests). */
    public interface Source {
        InputStream open(String url, long[] length) throws IOException;
    }

    /** Progress: {@code stage} is "Downloading" or "Unpacking"; percent 0 to 100, or -1 when not known. */
    public interface Progress {
        void at(String stage, int percent);
    }

    public static final Source WEB = (url, length) -> {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true); // GitHub sends the file from another host
        c.setRequestProperty("User-Agent", "Ibiko");
        int code = c.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            c.disconnect();
            throw new IOException("The voice server answered " + code + ".");
        }
        length[0] = c.getContentLengthLong();
        return c.getInputStream();
    };

    /** Downloads, checks and unpacks a pack. Nothing is left half-made: a failure removes what it wrote. */
    public static synchronized void install(File filesDir, Pack p, Source source, Progress progress) throws IOException {
        if (installed(filesDir, p)) {
            return;
        }
        File root = root(filesDir);
        root.mkdirs();
        File archive = new File(root, p.key + ".tar.bz2.part");
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        long[] length = {-1};
        try (InputStream in = source.open(p.url, length); OutputStream o = new FileOutputStream(archive)) {
            long total = length[0] > 0 ? length[0] : p.bytes, got = 0;
            int shown = -2;
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                o.write(buf, 0, n);
                sha.update(buf, 0, n);
                got += n;
                int pct = (int) Math.min(100, got * 100 / Math.max(1, total));
                if (pct != shown && progress != null) {
                    shown = pct;
                    progress.at("Downloading", pct);
                }
            }
        } catch (IOException e) {
            archive.delete();
            throw e;
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : sha.digest()) {
            hex.append(String.format("%02x", b));
        }
        if (!p.sha256.equals(hex.toString())) {
            archive.delete();
            throw new IOException("The voice download was not the expected file. Try again.");
        }
        File into = new File(root, p.key + ".unpacking");
        deleteAll(into);
        try (InputStream in = new Bzip2InputStream(new FileInputStream(archive))) {
            final long expect = Math.max(1, unpackedSize(p));
            final int[] shown = {-2};
            TarUnpack.unpack(in, into, done -> {
                int pct = (int) Math.min(100, done * 100 / expect);
                if (pct != shown[0] && progress != null) {
                    shown[0] = pct;
                    progress.at("Unpacking", pct);
                }
            });
        } catch (IOException e) {
            deleteAll(into);
            throw e;
        } finally {
            archive.delete();
        }
        File made = new File(into, p.dir), dest = folder(filesDir, p);
        if (!new File(made, p.model).isFile()) {
            deleteAll(into);
            throw new IOException("The voice pack is missing its model.");
        }
        deleteAll(dest);
        if (!made.renameTo(dest)) {
            deleteAll(into);
            throw new IOException("Couldn't save the voice.");
        }
        deleteAll(into);
        Files.write(mark(filesDir, p).toPath(), p.sha256.getBytes(StandardCharsets.UTF_8));
        if (progress != null) {
            progress.at("Unpacking", 100); // the estimate of the unpacked size is only for the bar on the way
        }
    }

    /** About how many bytes a pack unpacks to, for the progress bar. */
    static long unpackedSize(Pack p) {
        return "kokoro".equals(p.kind) ? 158_000_000L : 81_500_000L;
    }

    /** Removes a pack (frees its space); the device's own voice is used again. */
    public static void remove(File filesDir, Pack p) {
        mark(filesDir, p).delete();
        deleteAll(folder(filesDir, p));
    }

    static void deleteAll(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteAll(k);
            }
        }
        f.delete();
    }
}
