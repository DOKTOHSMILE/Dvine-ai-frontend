package ai.dvine.net;

import java.util.Map;
import java.util.function.Supplier;

import ai.dvine.brain.MiniJson;

/**
 * Reads a web page through the DVINE server (POST /api/v1/web/fetch), which
 * applies its egress allow-list and refuses private addresses. The text that
 * comes back is untrusted: it is shown or read, never obeyed. Blocking; call
 * it off the main thread. Returns null on any failure.
 */
public final class WebClient {
    private final Supplier<String> root;
    private final Supplier<String> token;
    private final Runnable invalidate;
    private final DeskClient.Http http;

    public WebClient(Supplier<String> root, Supplier<String> token, Runnable invalidate, DeskClient.Http http) {
        this.root = root;
        this.token = token;
        this.invalidate = invalidate;
        this.http = http == null ? DeskClient::https : http;
    }

    public Map<String, Object> fetch(String url) {
        String base = root.get();
        if (base == null || url == null) {
            return null;
        }
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                String t = token.get();
                if (t == null) {
                    return null;
                }
                SessionClient.Reply r = http.send("POST", base + "/api/v1/web/fetch", t, MiniJson.write(Map.of("url", url)));
                if (r.code == 401) {
                    invalidate.run();
                    continue;
                }
                return r.code == 200 ? MiniJson.obj(MiniJson.parse(r.body)) : null;
            }
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
        return null;
    }
}
