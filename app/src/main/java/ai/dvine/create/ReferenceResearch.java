package ai.dvine.create;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Looks an object up before modelling it, through the server's web fetch
 * (its egress allow-list and private-address rules apply): reads the
 * encyclopedia article for the category, keeps a one-line summary to say,
 * and notes any typical dimensions the text states. Everything found is a
 * reference, recorded with its source and date, never a verified
 * measurement; the page is untrusted text and nothing in it is obeyed.
 */
public final class ReferenceResearch {
    /** Reads a URL; returns the fetch result (title, text) or null when offline or refused. */
    public interface Fetcher {
        Map<String, Object> fetch(String url);
    }

    public static final class Findings {
        public final String summary;
        public final List<Provenance> sources = new ArrayList<Provenance>();
        /** Typical sizes the reference mentions, e.g. "length 4.5 m" (informative only). */
        public final List<String> sizes = new ArrayList<String>();

        Findings(String summary) {
            this.summary = summary;
        }
    }

    private static final String[][] ARTICLES = {{"house", "House"}, {"car", "Car"}, {"table", "Table_(furniture)"}, {"chair", "Chair"},
            {"sofa", "Couch"}, {"bed", "Bed"}, {"bookshelf", "Bookcase"}, {"lamp", "Light_fixture"}, {"mug", "Mug"},
            {"bottle", "Bottle"}, {"laptop", "Laptop"}, {"phone", "Smartphone"}, {"tree", "Tree"}, {"bicycle", "Bicycle"}};

    private ReferenceResearch() {
    }

    public static String articleFor(String category) {
        for (String[] a : ARTICLES) {
            if (a[0].equals(category)) {
                return "https://en.wikipedia.org/wiki/" + a[1];
            }
        }
        return null;
    }

    public static Findings research(String category, Fetcher fetcher, long now) {
        String url = articleFor(category);
        if (url == null || fetcher == null) {
            return null;
        }
        Map<String, Object> page;
        try {
            page = fetcher.fetch(url);
        } catch (RuntimeException e) {
            return null;
        }
        if (page == null) {
            return null;
        }
        Object textObj = page.get("text");
        String text = textObj == null ? "" : String.valueOf(textObj);
        if (text.length() < 80) {
            return null;
        }
        Findings f = new Findings(firstSentence(text, category));
        f.sources.add(new Provenance("reference", url, now, "CC BY-SA 4.0 (Wikipedia text)",
                "read for general design characteristics; not a verified measurement"));
        Matcher m = Pattern.compile("(?i)\\b(length|width|height|wheelbase|diameter)[^.]{0,40}?(\\d+(?:\\.\\d+)?)\\s?(mm|cm|m|metres|meters)\\b")
                .matcher(text);
        while (m.find() && f.sizes.size() < 4) {
            f.sizes.add(m.group(1).toLowerCase(Locale.US) + " " + m.group(2) + " " + m.group(3));
        }
        return f;
    }

    /** The first sentence that mentions the object, cleaned and capped (it is read aloud). */
    static String firstSentence(String text, String category) {
        for (String s : text.split("(?<=[.!?])\\s+")) {
            String c = s.replaceAll("\\[[^\\]]*\\]", "").replaceAll("\\s+", " ").trim();
            if (c.length() > 40 && c.length() < 260 && c.toLowerCase(Locale.US).contains(category.substring(0, Math.min(4, category.length())))) {
                return c;
            }
        }
        return null;
    }
}
