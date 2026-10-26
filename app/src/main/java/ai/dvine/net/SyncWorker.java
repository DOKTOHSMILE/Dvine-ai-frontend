package ai.dvine.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ai.dvine.brain.KnowledgePack;
import ai.dvine.brain.LearningJournal;
import ai.dvine.brain.MiniJson;

/**
 * Runs when the phone comes online. Uploads consented learning and pulls a
 * newer knowledge pack. Does nothing while no desk URL is configured.
 * Contracts: docs/DVINE_MASTER_BUILD_HANDBOOK.md §5.
 */
public final class SyncWorker {
    public interface TokenSource {
        /** Current session token, or null when signed out. Never stored in source. */
        String token();
    }

    public interface PackListener {
        void onNewPack(KnowledgePack pack);
    }

    /** Told how many learned entries the server accepted in one run. */
    public interface UploadListener {
        void onUploaded(int count);
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final java.util.function.Supplier<String> baseSource;
    private final java.util.function.Supplier<String> deviceId;
    private UploadListener uploads;
    private volatile boolean again;
    private final LearningJournal journal;
    private final KnowledgeStore store;
    private final TokenSource tokens;
    private final PackListener packs;
    private volatile boolean running;

    public SyncWorker(java.util.function.Supplier<String> base, java.util.function.Supplier<String> deviceId,
                      LearningJournal journal, KnowledgeStore store, TokenSource tokens, PackListener packs) {
        this.baseSource = base;
        this.deviceId = deviceId;
        this.journal = journal;
        this.store = store;
        this.tokens = tokens;
        this.packs = packs;
    }

    public void setUploadListener(UploadListener l) {
        uploads = l;
    }

    private String base() {
        String b = baseSource.get();
        return b == null ? "" : b.trim().replaceAll("/+$", "");
    }

    public boolean configured() {
        return base().startsWith("https://");
    }

    /** Runs a sync now (pack check + upload). A kick during a run queues one more run. */
    public void kick(final int packVersion) {
        if (!configured()) {
            return;
        }
        if (running) {
            again = true;
            return;
        }
        running = true;
        io.execute(() -> {
            try {
                do {
                    again = false;
                    refreshPack(packVersion);
                    int sent = uploadLearning();
                    if (sent > 0 && uploads != null) {
                        uploads.onUploaded(sent);
                    }
                } while (again);
            } finally {
                running = false;
            }
        });
    }

    public void shutdown() {
        io.shutdownNow();
    }

    private void refreshPack(int have) {
        String base = base();
        try {
            Map<String, Object> manifest = MiniJson.obj(MiniJson.parse(get(base + "/api/v1/knowledge/manifest", null)));
            int version = (int) MiniJson.num(manifest.get("version"), 0);
            String sha = MiniJson.str(manifest.get("sha256"), "");
            String url = MiniJson.str(manifest.get("url"), "");
            if (version <= have || sha.length() != 64 || !url.startsWith("/")) {
                return;
            }
            String json = get(base + url, null);
            if (!sha.equalsIgnoreCase(sha256(json))) {
                return;
            }
            KnowledgePack pack = store.install(json, have);
            if (pack != null) {
                packs.onNewPack(pack);
            }
        } catch (IOException | IllegalArgumentException ignored) {
            // try again next time we come online
        }
    }

    /** Uploads pending learning; returns how many entries the server accepted. */
    private int uploadLearning() {
        if (journal.pending(1).isEmpty()) {
            return 0;
        }
        String token = tokens.token();
        if (token == null || token.isEmpty()) {
            return 0;
        }
        String base = base();
        int sent = 0;
        for (int round = 0; round < 10; round++) {
            List<LearningJournal.Entry> batch = journal.pending(100);
            if (batch.isEmpty()) {
                return sent;
            }
            try {
                String reply = post(base + "/api/v1/learning/sync", LearningJournal.syncBody(deviceId.get(), batch), token);
                List<String> accepted = new ArrayList<String>(MiniJson.strings(MiniJson.obj(MiniJson.parse(reply)).get("accepted")));
                for (Object r : MiniJson.list(MiniJson.obj(MiniJson.parse(reply)).get("rejected"))) {
                    accepted.add(MiniJson.str(MiniJson.obj(r).get("id"), ""));
                }
                if (accepted.isEmpty()) {
                    return sent;
                }
                journal.markUploaded(accepted);
                sent += accepted.size();
            } catch (IOException | IllegalArgumentException e) {
                return sent;
            }
        }
        return sent;
    }

    private static String get(String url, String token) throws IOException {
        HttpURLConnection c = open(url, token);
        c.setRequestMethod("GET");
        return finish(c);
    }

    private static String post(String url, String body, String token) throws IOException {
        HttpURLConnection c = open(url, token);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        try (OutputStream out = c.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return finish(c);
    }

    private static HttpURLConnection open(String url, String token) throws IOException {
        if (!url.startsWith("https://")) {
            throw new IOException("https only");
        }
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/json");
        if (token != null) {
            c.setRequestProperty("Authorization", "Bearer " + token);
        }
        return c;
    }

    private static String finish(HttpURLConnection c) throws IOException {
        try {
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("http " + code);
            }
            try (InputStream in = c.getInputStream()) {
                return KnowledgeStore.read(in);
            }
        } finally {
            c.disconnect();
        }
    }

    static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
