package ai.ibiko.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import ai.ibiko.connect.Connectors;

/** Searching through the APIs (Brave with a key, Wikipedia without), and reading the best pages. */
public class WebSearchTest {
    private static final String PAGE = "<html><nav>Home | About</nav><p>Google LLC is an American technology company founded in "
            + "1998 by Larry Page and Sergey Brin while they were PhD students at Stanford University.</p><script>x()</script></html>";

    @Test
    public void braveWithAKeyThenReadsThePages() {
        final List<String> sent = new ArrayList<String>();
        Connectors.Http http = (method, url, headers, body) -> {
            sent.add(url + " " + headers.get("X-Subscription-Token"));
            return new Connectors.Reply(200, "{\"web\":{\"results\":[{\"title\":\"Google - Wikipedia\",\"url\":\"https://en.wikipedia.org/wiki/Google\","
                    + "\"description\":\"Google LLC is an <strong>American</strong> company\"},{\"title\":\"About Google\",\"url\":\"https://about.google/\","
                    + "\"description\":\"Our story\"}]}}", null);
        };
        String r = WebSearch.research("google history", http, new WebSearch.Settings("brave", "k123", ""), url -> PAGE, 2, 5000);
        assertTrue(sent.get(0), sent.get(0).startsWith("https://api.search.brave.com/res/v1/web/search?count=8&q=google+history k123"));
        assertTrue(r, r.contains("from Brave Search") && r.contains("[1] Google - Wikipedia") && r.contains("[2] About Google"));
        assertTrue(r, r.contains("Google LLC is an American company"));
        assertTrue(r, r.contains("From [1] en.wikipedia.org:") && r.contains("founded in 1998"));
        assertTrue(r, !r.contains("Home | About") && !r.contains("x()"));
        assertEquals("en.wikipedia.org, about.google", WebSearch.hosts(r));
    }

    @Test
    public void withoutAKeyWikipediaGivesSeveralArticles() {
        Connectors.Http http = (method, url, headers, body) -> {
            assertTrue(url, url.startsWith("https://en.wikipedia.org/w/api.php?action=query&list=search"));
            return new Connectors.Reply(200, "{\"query\":{\"search\":[{\"title\":\"Google\",\"snippet\":\"an American <span>company</span>\"},"
                    + "{\"title\":\"History of Google\",\"snippet\":\"founded in 1998\"}]}}", null);
        };
        List<WebSearch.Hit> hits = WebSearch.search("google", http, new WebSearch.Settings("", "", ""), 5);
        assertEquals(2, hits.size());
        assertEquals("https://en.wikipedia.org/wiki/History_of_Google", hits.get(1).url);
        assertEquals("an American company", hits.get(0).snippet);
        // a key for a provider that refuses it falls back to Wikipedia rather than nothing
        Connectors.Http refusing = (method, url, headers, body) -> url.contains("brave")
                ? new Connectors.Reply(401, "{}", null) : http.send(method, url, headers, body);
        assertEquals(2, WebSearch.search("google", refusing, new WebSearch.Settings("brave", "bad", ""), 5).size());
    }
}
