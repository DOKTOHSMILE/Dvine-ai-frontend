package ai.dvine.net;

import android.content.SharedPreferences;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import ai.dvine.brain.MiniJson;

/**
 * Signs this phone in to the DVINE server by itself: registers once to get
 * a device secret (kept in {@link DeviceVault}), then trades it for 12-hour
 * session tokens. Call {@link #token()} off the main thread.
 */
public final class SessionClient {
    private final Supplier<String> base;
    private final SharedPreferences prefs;
    private final DeviceVault vault;
    private String token;
    private long expiresAt;

    public SessionClient(Supplier<String> base, SharedPreferences prefs, DeviceVault vault) {
        this.base = base;
        this.prefs = prefs;
        this.vault = vault;
    }

    /** A valid session token, or null when the server can't be reached. */
    public synchronized String token() {
        long now = System.currentTimeMillis();
        if (token != null && expiresAt - now > 5 * 60_000L) {
            return token;
        }
        String root = root();
        if (root == null) {
            return null;
        }
        try {
            String secret = vault.secret();
            if (secret == null) {
                secret = register(root);
                if (secret == null) {
                    return null;
                }
            }
            Reply r = post(root + "/api/v1/sessions/device", MiniJson.write(Map.of(
                    "deviceId", deviceId(), "deviceSecret", secret)));
            if (r.code == 401) {
                // the server no longer knows this secret: start a fresh registration next time
                vault.clear();
                prefs.edit().remove("device_id").apply();
                return null;
            }
            if (r.code != 200) {
                return null;
            }
            Map<String, Object> body = MiniJson.obj(MiniJson.parse(r.body));
            token = MiniJson.str(body.get("token"), null);
            expiresAt = parseInstant(MiniJson.str(body.get("expiresAt"), ""));
            return token;
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            return null;
        }
    }

    /** The id this phone uses on the server; shared with learning sync. */
    public synchronized String deviceId() {
        String id = prefs.getString("device_id", null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString("device_id", id).apply();
        }
        return id;
    }

    private String register(String root) throws IOException {
        Reply r = post(root + "/api/v1/devices/register", MiniJson.write(Map.of("deviceId", deviceId())));
        if (r.code == 409) {
            // this id was registered but we lost its secret: take a new id and try again
            prefs.edit().putString("device_id", UUID.randomUUID().toString()).apply();
            r = post(root + "/api/v1/devices/register", MiniJson.write(Map.of("deviceId", deviceId())));
        }
        if (r.code != 201) {
            return null;
        }
        String secret = MiniJson.str(MiniJson.obj(MiniJson.parse(r.body)).get("deviceSecret"), null);
        if (secret != null) {
            vault.store(secret);
        }
        return secret;
    }

    private String root() {
        String b = base.get();
        if (b == null) {
            return null;
        }
        b = b.trim().replaceAll("/+$", "");
        return b.startsWith("https://") ? b : null;
    }

    private static long parseInstant(String s) {
        try {
            return Instant.parse(s).toEpochMilli();
        } catch (RuntimeException e) {
            return System.currentTimeMillis() + 60 * 60_000L;
        }
    }

    static final class Reply {
        final int code;
        final String body;

        Reply(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    static Reply post(String url, String json) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(8000);
            c.setReadTimeout(15000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("Accept", "application/json");
            try (OutputStream out = c.getOutputStream()) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
            return new Reply(code, in == null ? "" : KnowledgeStore.read(in));
        } finally {
            c.disconnect();
        }
    }
}
