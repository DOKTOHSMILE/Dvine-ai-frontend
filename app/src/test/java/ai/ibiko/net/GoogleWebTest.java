package ai.ibiko.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class GoogleWebTest {
    @Test
    public void readsGooglesResultsAndKnowsItsRobotCheck() {
        String page = "{\"at\":\"https://www.google.com/search?q=petrol+lagos\",\"r\":["
                + "{\"t\":\"Petrol price in Lagos today\",\"u\":\"https://www.google.com/url?q=https%3A%2F%2Fnews.ng%2Fpetrol&sa=U\",\"s\":\"N1,050 a litre at NNPC...\"},"
                + "{\"t\":\"Fuel prices\",\"u\":\"https://globalpetrolprices.com/Nigeria/\",\"s\":\"  weekly   prices \"},"
                + "{\"t\":\"\",\"u\":\"https://x.com\",\"s\":\"\"}]}";
        List<WebSearch.Hit> hits = GoogleWeb.parse(page, 10);
        assertEquals(2, hits.size());
        assertEquals("a Google redirect is the page it leads to", "https://news.ng/petrol", hits.get(0).url);
        assertEquals("weekly prices", hits.get(1).snippet);
        assertTrue(GoogleWeb.parse("{\"at\":\"https://www.google.com/sorry/index?continue=…\",\"r\":[]}", 10).isEmpty());
        assertTrue(GoogleWeb.parse("", 10).isEmpty());
        assertTrue(GoogleWeb.url("gas price lagos").startsWith("https://www.google.com/search?hl=en&num=10&q=gas+price+lagos"));
    }

    @Test
    public void searchesGoogleFirstThenWikipedia() {
        WebSearch.Browser before = WebSearch.browser;
        try {
            WebSearch.browser = (q, most) -> java.util.Collections.singletonList(new WebSearch.Hit("From Google", "https://g.example/" + q, ""));
            List<WebSearch.Hit> hits = WebSearch.search("lagos", (m, u, h, b) -> {
                throw new java.io.IOException("no network in the test");
            }, new WebSearch.Settings("", "", ""), 5);
            assertEquals("From Google", hits.get(0).title);
            assertEquals("Google, then Wikipedia", new WebSearch.Settings("", "", "").label());
        } finally {
            WebSearch.browser = before;
        }
    }
}
