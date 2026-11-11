package ai.ibiko.llm;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The models a provider offers, read from its own list ({@code <address>/models}): OpenAI and OpenAI-compatible
 * gateways answer {@code {"data":[{"id":...}]}}, Anthropic the same shape, Google Gemini {@code {"models":[{"name":
 * "models/..."}]}}. Models that can't chat (embeddings, speech, moderation) are left out, so the list is only what
 * can answer.
 */
public final class ModelList {
    /** One request out with GET, the body back: the tests give their own. */
    public interface Getter {
        RemoteBrain.Response get(String url, Map<String, String> headers, int timeoutMs) throws IOException;
    }

    private ModelList() {
    }

    /** Where the list is: the API's base address with /models. */
    public static String url(String base) {
        return RemoteBrain.normalise(base) + "/models";
    }

    /** The headers the provider reads its key from: Anthropic's own, else a Bearer token. */
    public static Map<String, String> headers(RemoteBrain.Protocol protocol, String base, String key) {
        Map<String, String> h = new LinkedHashMap<String, String>();
        h.put("Accept", "application/json");
        if (protocol == RemoteBrain.Protocol.ANTHROPIC) {
            h.put("x-api-key", key);
            h.put("anthropic-version", RemoteBrain.ANTHROPIC_VERSION);
            if (!RemoteBrain.normalise(base).contains("api.anthropic.com")) {
                h.put("Authorization", "Bearer " + key);
            }
        } else {
            h.put("Authorization", "Bearer " + key);
        }
        return h;
    }

    /** The model names in a list reply, sorted, without the ones that can't chat. Empty when none are found. */
    public static List<String> parse(String json) {
        TreeSet<String> out = new TreeSet<String>();
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<String>(out);
        }
        try {
            org.json.JSONArray items;
            String t = json.trim();
            if (t.startsWith("[")) {
                items = new org.json.JSONArray(t);
            } else {
                org.json.JSONObject o = new org.json.JSONObject(t);
                items = o.optJSONArray("data");
                if (items == null) {
                    items = o.optJSONArray("models");
                }
            }
            for (int i = 0; items != null && i < items.length(); i++) {
                Object it = items.get(i);
                String id;
                if (it instanceof org.json.JSONObject) {
                    org.json.JSONObject m = (org.json.JSONObject) it;
                    id = m.optString("id", m.optString("name", ""));
                } else {
                    id = String.valueOf(it);
                }
                if (id.startsWith("models/")) {
                    id = id.substring("models/".length()); // Gemini names its models "models/gemini-..."
                }
                if (!id.isEmpty() && chats(id)) {
                    out.add(id);
                }
            }
        } catch (org.json.JSONException e) {
            return new ArrayList<String>();
        }
        return new ArrayList<String>(out);
    }

    /** False for models made for something other than answering: embeddings, speech, moderation, old completions. */
    static boolean chats(String id) {
        String m = id.toLowerCase(java.util.Locale.US);
        return !m.matches(".*(embed|whisper|tts|moderation|davinci|babbage|transcribe|-audio-|aqa).*");
    }

    /** Asks the provider for its list. Throws with the reason (the status and the start of the reply) when it can't. */
    public static List<String> fetch(RemoteBrain.Protocol protocol, String base, String key, Getter getter) throws IOException {
        RemoteBrain.Response r = getter.get(url(base), headers(protocol, base, key), 20_000);
        String body = read(r.body);
        if (r.status >= 400) {
            String said = body.replaceAll("\\s+", " ").trim();
            throw new IOException("the list wasn't given (" + r.status + (said.isEmpty() ? "" : ": "
                    + (said.length() > 160 ? said.substring(0, 160) + "…" : said)) + ")");
        }
        List<String> out = parse(body);
        if (out.isEmpty()) {
            throw new IOException("the reply named no models");
        }
        return out;
    }

    /** GET over https, with the provider's certificate when it has one. */
    public static Getter https(javax.net.ssl.SSLSocketFactory sockets) {
        return (url, headers, timeoutMs) -> {
            java.net.URL u = new java.net.URL(url);
            if (!"https".equalsIgnoreCase(u.getProtocol())) {
                throw new IOException("only https is allowed");
            }
            javax.net.ssl.HttpsURLConnection c = (javax.net.ssl.HttpsURLConnection) u.openConnection();
            if (sockets != null) {
                c.setSSLSocketFactory(sockets);
            }
            c.setRequestMethod("GET");
            c.setConnectTimeout(8_000);
            c.setReadTimeout(timeoutMs);
            for (Map.Entry<String, String> h : headers.entrySet()) {
                c.setRequestProperty(h.getKey(), h.getValue());
            }
            int status = c.getResponseCode();
            InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
            return new RemoteBrain.Response(status, in == null ? new java.io.ByteArrayInputStream(new byte[0]) : in);
        };
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        try (InputStream s = in) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = s.read(buf)) > 0 && b.size() < 4_000_000) {
                b.write(buf, 0, n);
            }
            return b.toString("UTF-8");
        }
    }
}
