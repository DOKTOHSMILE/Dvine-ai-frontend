package ai.dvine.render.field;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Real photographs from Wikimedia Commons. Unrelated pictures are not a 3D scan. */
public final class PhotoLookup {
    private PhotoLookup() {
    }

    public static String commonsUrl(String subject, int limit) {
        int n = Math.max(1, Math.min(6, limit));
        try {
            return "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrnamespace=6&gsrlimit="
                    + n + "&gsrsearch=" + URLEncoder.encode(subject == null ? "" : subject.trim(), "UTF-8")
                    + "&prop=imageinfo&iiprop=url&iiurlwidth=640";
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final Pattern URL = Pattern.compile("\"(?:thumburl|url)\"\\s*:\\s*\"(https://upload\\.wikimedia\\.org/[^\"]+)\"");

    /** Image addresses on Wikimedia's own server, thumbnail first, up to {@code limit}. */
    public static List<String> urls(String json, int limit) {
        List<String> out = new ArrayList<String>();
        if (json == null || limit < 1) {
            return out;
        }
        Matcher m = URL.matcher(json);
        while (m.find() && out.size() < limit) {
            String url = m.group(1).replace("\\/", "/");
            if (!out.contains(url)) {
                out.add(url);
            }
        }
        return out;
    }
}
