package ai.dvine.net;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * A plain HTTPS GET for public reference sites (Wikipedia's API and image server): short timeouts, a size cap, an
 * honest User-Agent. Blocking; call off the main thread. Null on any failure.
 */
public final class WebGet {
    private WebGet() {
    }

    public static final String AGENT = "DVINE/0.23 (https://github.com/DOKTOHSMILE/Dvine-ai-frontend)";

    public static byte[] get(String url, int timeoutMs, int maxBytes) {
        if (url == null || !url.startsWith("https://")) {
            return null;
        }
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setRequestProperty("User-Agent", AGENT);
            if (c.getResponseCode() != 200) {
                return null;
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] b = new byte[16384];
                int n;
                while ((n = in.read(b)) > 0) {
                    out.write(b, 0, n);
                    if (out.size() > maxBytes) {
                        return null;
                    }
                }
                return out.toByteArray();
            }
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }
}
