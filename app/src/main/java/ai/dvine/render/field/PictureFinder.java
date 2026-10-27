package ai.dvine.render.field;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A reference picture of something to draw, looked up online: the lead image of the best matching Wikipedia article
 * (Wikimedia's own image server, no key, nothing but the subject is sent). The particles then form that picture.
 * Blocking; call off the main thread. The URL and JSON handling are pure Java.
 */
public final class PictureFinder {
    private PictureFinder() {
    }

    private static final int TIMEOUT_MS = 7000, MAX_BYTES = 3 * 1024 * 1024;

    /** The search for the subject's article and its lead image, a thumbnail {@code size} pixels across. */
    public static String searchUrl(String subject, int size) {
        try {
            return "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&generator=search&gsrlimit=1"
                    + "&gsrsearch=" + URLEncoder.encode(subject.trim(), "UTF-8") + "&prop=pageimages&piprop=thumbnail&pithumbsize=" + size;
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final Pattern SOURCE = Pattern.compile("\"thumbnail\"\\s*:\\s*\\{\\s*\"source\"\\s*:\\s*\"(https://upload\\.wikimedia\\.org/[^\"]+)\"");

    /** The thumbnail's address in the search's reply (only Wikimedia's image server), or null. */
    public static String thumbnail(String json) {
        if (json == null) {
            return null;
        }
        Matcher m = SOURCE.matcher(json);
        return m.find() ? m.group(1).replace("\\/", "/") : null;
    }

    /** The picture's bytes, or null when offline, not found, or too large. */
    public static byte[] find(String subject) {
        try {
            byte[] reply = get(searchUrl(subject, 480));
            String src = reply == null ? null : thumbnail(new String(reply, "UTF-8"));
            return src == null ? null : get(src);
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }

    private static byte[] get(String url) throws java.io.IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("User-Agent", "DVINE/0.22 (https://github.com/DOKTOHSMILE/Dvine-ai-frontend; drawing references)");
        c.setInstanceFollowRedirects(true);
        try {
            if (c.getResponseCode() != 200) {
                return null;
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] b = new byte[16384];
                int n;
                while ((n = in.read(b)) > 0) {
                    out.write(b, 0, n);
                    if (out.size() > MAX_BYTES) {
                        return null;
                    }
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }
}
