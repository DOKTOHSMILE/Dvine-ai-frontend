package ai.dvine.ops.mind;

import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Public search text. DuckDuckGo is a web index, not Google.
 * Reddit is a public post search, not a private social feed.
 */
public final class OpenSearch {
    private OpenSearch() {
    }

    public static String duckUrl(String question) {
        try {
            return "https://api.duckduckgo.com/?format=json&no_html=1&skip_disambig=1&q="
                    + URLEncoder.encode(question == null ? "" : question.trim(), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String redditUrl(String question) {
        try {
            return "https://www.reddit.com/search.json?limit=3&sort=relevance&q="
                    + URLEncoder.encode(question == null ? "" : question.trim(), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final Pattern ABSTRACT = Pattern.compile("\"AbstractText\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern TITLE = Pattern.compile("\"title\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    public static String duckText(String json) {
        if (json == null || !json.contains("AbstractText")) {
            return null;
        }
        Matcher m = ABSTRACT.matcher(json);
        if (!m.find()) {
            return null;
        }
        String text = unescape(m.group(1));
        return text.length() < 40 ? null : text;
    }

    /** Titles from a Reddit listing. A page that is not a listing returns nothing. */
    public static String redditText(String json) {
        if (json == null || !json.contains("\"kind\":\"Listing\"") && !json.contains("\"kind\": \"Listing\"")) {
            return null;
        }
        Matcher m = TITLE.matcher(json);
        StringBuilder b = new StringBuilder();
        int n = 0;
        while (m.find() && n < 3) {
            String title = unescape(m.group(1)).trim();
            if (title.isEmpty() || title.equalsIgnoreCase("listing")) {
                continue;
            }
            if (b.length() > 0) {
                b.append(' ');
            }
            b.append(title);
            n++;
        }
        String text = b.toString().trim();
        return text.length() < 20 ? null : text;
    }

    private static String unescape(String t) {
        return t.replace("\\\"", "\"").replace("\\/", "/").replace("\\n", " ").replaceAll("\\s+", " ").trim();
    }
}
