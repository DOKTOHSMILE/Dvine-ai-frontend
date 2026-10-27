package ai.dvine.llm;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.dvine.brain.MiniJson;

/**
 * Newer knowledge than the model's training, for a factual question asked online: the opening of the best matching
 * Wikipedia article, handed to the model as a reference. Only the question goes out, and only questions about the
 * world (never ones about the user: "my", "me"). The URL, the choice and the reply handling are pure Java.
 */
public final class FreshFacts {
    private FreshFacts() {
    }

    /** Whether a question is about the world, so a reference can help. */
    public static boolean worthLooking(String question) {
        String q = question.toLowerCase(Locale.US).trim();
        if (q.matches(".*\\b(my|me|mine|i|i'm|im|you|your|yours|we|our|us)\\b.*") && !q.matches(".*\\b(who|what) (is|are|was) the\\b.*")) {
            return false;
        }
        return q.matches("^(who|what|when|where|which|how many|how much|how old|how tall|how long|how far|is|are|was|were|did|does|tell me about|explain)\\b.+")
                || q.matches(".*\\b(latest|current|currently|now|today|this year|recent|recently|new|newest|still)\\b.*");
    }

    /** What to search for: the question without its question words. */
    static String terms(String question) {
        return question.toLowerCase(Locale.US).replaceAll("[?.!]+$", "")
                .replaceAll("^(tell me about|explain|who|what|when|where|which|how many|how much|how old|how tall|how long|how far)\\s+", "")
                .replaceAll("^(is|are|was|were|did|does|do)\\s+", "").replaceAll("^(the|a|an)\\s+", "").trim();
    }

    public static String searchUrl(String question) {
        try {
            return "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&generator=search&gsrlimit=1"
                    + "&prop=extracts&exintro=1&explaintext=1&exsentences=6&redirects=1&gsrsearch="
                    + java.net.URLEncoder.encode(terms(question), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The article's title and opening from the API's reply, as "Title: text", or null. Capped at 900 characters. */
    public static String extract(String json) {
        try {
            Map<String, Object> root = MiniJson.obj(MiniJson.parse(json));
            Map<String, Object> query = root == null ? null : MiniJson.obj(root.get("query"));
            List<Object> pages = query == null ? null : MiniJson.list(query.get("pages"));
            if (pages == null || pages.isEmpty()) {
                return null;
            }
            Map<String, Object> page = MiniJson.obj(pages.get(0));
            String title = MiniJson.str(page.get("title"), ""), text = MiniJson.str(page.get("extract"), "").trim();
            if (text.length() < 40) {
                return null;
            }
            text = text.replaceAll("\\s+", " ");
            if (text.length() > 900) {
                int cut = text.lastIndexOf(". ", 900);
                text = text.substring(0, cut > 300 ? cut + 1 : 900);
            }
            return title + ": " + text;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The reference for a question, or null (offline, nothing found, too slow). Blocking. */
    public static String lookUp(String question) {
        byte[] b = ai.dvine.net.WebGet.get(searchUrl(question), 6000, 400_000);
        try {
            return b == null ? null : extract(new String(b, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            return null;
        }
    }
}
