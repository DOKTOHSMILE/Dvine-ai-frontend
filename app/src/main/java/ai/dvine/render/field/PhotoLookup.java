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
        int n = Math.max(1, Math.min(20, limit));
        try {
            String q = (subject == null ? "" : subject.trim()) + " -portrait -crowd -people -boy -man -woman filetype:bitmap";
            return "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrnamespace=6&gsrlimit="
                    + n + "&gsrsearch=" + URLEncoder.encode(q, "UTF-8")
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

    public static final class Shot {
        public final String title;
        public final String url;

        public Shot(String title, String url) {
            this.title = title == null ? "" : title;
            this.url = url == null ? "" : url;
        }
    }

    /**
     * Prefers a file whose name is the thing itself. A person, a crowd or a portrait is used only when
     * nothing closer was found.
     */
    public static List<Shot> choose(String json, String subject, int limit) {
        List<Shot> found = new ArrayList<Shot>();
        if (json == null || limit < 1) {
            return found;
        }
        Matcher titled = Pattern.compile(
                "\"title\"\\s*:\\s*\"(File:[^\"]+)\"[\\s\\S]{0,900}?\"(?:thumburl|url)\"\\s*:\\s*\"(https://upload\\.wikimedia\\.org/[^\"]+)\"")
                .matcher(json);
        while (titled.find()) {
            String url = titled.group(2).replace("\\/", "/");
            boolean seen = false;
            for (Shot s : found) {
                if (s.url.equals(url)) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                found.add(new Shot(titled.group(1).replace("\\/", "/"), url));
            }
        }
        if (found.isEmpty()) {
            for (String url : urls(json, limit)) {
                found.add(new Shot("", url));
            }
            return found;
        }
        final String want = subject == null ? "" : subject.toLowerCase(java.util.Locale.US);
        java.util.Collections.sort(found, (a, b) -> score(b, want) - score(a, want));
        List<Shot> clean = new ArrayList<Shot>();
        for (Shot s : found) {
            if (score(s, want) >= 4) {
                clean.add(s);
            }
        }
        List<Shot> use = clean.isEmpty() ? new ArrayList<Shot>() : clean;
        if (use.size() > limit) {
            return new ArrayList<Shot>(use.subList(0, limit));
        }
        return use;
    }

    /** A second, plainer search when the first name is too broad. */
    public static String wider(String subject) {
        if (subject == null) {
            return "";
        }
        String s = subject.trim().toLowerCase(java.util.Locale.US);
        if ("banana".equals(s)) {
            return "banana fruit";
        }
        if ("trailer".equals(s)) {
            return "semi-trailer truck";
        }
        if ("car".equals(s)) {
            return "sedan automobile";
        }
        if ("bicycle".equals(s) || "bike".equals(s)) {
            return "road bicycle";
        }
        return "";
    }

    static int score(Shot shot, String subject) {
        String title = shot.title.toLowerCase(java.util.Locale.US);
        int n = 0;
        if (!subject.isEmpty() && title.matches(".*\\b" + java.util.regex.Pattern.quote(subject) + "\\b.*")) {
            n += 10;
        } else if (!subject.isEmpty() && title.contains(subject)) {
            n += 4;
        }
        if (title.matches(".*\\b(man|boy|woman|girl|child|person|people|portrait|crowd|soldier|farmer|rider|family|wedding)\\b.*")) {
            n -= 14;
        }
        if (title.matches(".*\\b(fodder|harvest|market|street|parade|war|protest|vintage|historical)\\b.*")) {
            n -= 8;
        }
        return n;
    }
}
