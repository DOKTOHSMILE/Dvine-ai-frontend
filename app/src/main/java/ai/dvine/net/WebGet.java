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

    /** What a page fetch got: the text (possibly cut at the limit), or why there is none. */
    public static final class Page {
        public final int status;
        public final String body, why;

        Page(int status, String body, String why) {
            this.status = status;
            this.body = body;
            this.why = why;
        }
    }

    /** A browser-like User-Agent: many sites refuse a bare client. */
    public static final String BROWSER = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36 DVINE";

    /**
     * Any https page for reading: a big page is cut at {@code maxBytes} rather than dropped, redirects are followed,
     * and a failure says why ("404", "the site didn't answer"). Blocking; call off the main thread.
     */
    public static Page page(String url, int timeoutMs, int maxBytes) {
        if (url == null || !url.startsWith("https://")) {
            return new Page(0, null, "only https addresses can be fetched");
        }
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setRequestProperty("User-Agent", BROWSER);
            c.setRequestProperty("Accept", "text/html,text/plain,application/json;q=0.9,*/*;q=0.8");
            c.setRequestProperty("Accept-Language", "en");
            int status = c.getResponseCode();
            InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (in != null) {
                try (InputStream i = in) {
                    byte[] b = new byte[16384];
                    int n;
                    while (out.size() < maxBytes && (n = i.read(b)) > 0) {
                        out.write(b, 0, Math.min(n, maxBytes - out.size()));
                    }
                }
            }
            String body = new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            if (status < 200 || status >= 300) {
                return new Page(status, null, "the site said " + status);
            }
            return new Page(status, body, null);
        } catch (java.net.UnknownHostException e) {
            return new Page(0, null, "no such site (" + e.getMessage() + ")");
        } catch (java.net.SocketTimeoutException e) {
            return new Page(0, null, "the site didn't answer in time");
        } catch (java.io.IOException | RuntimeException e) {
            return new Page(0, null, "it couldn't be reached (" + e.getClass().getSimpleName() + ")");
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }
}
