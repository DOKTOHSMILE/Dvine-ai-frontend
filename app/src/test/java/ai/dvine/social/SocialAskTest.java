package ai.dvine.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SocialAskTest {
    @Test
    public void requestsAreRead() {
        check("search X for the fuel price in Abuja", Platform.X, "the fuel price in Abuja");
        check("Check Reddit for reviews of the Tecno Camon 30", Platform.REDDIT, "reviews of the Tecno Camon 30");
        check("what are people saying about the new minimum wage on twitter?", Platform.X, "the new minimum wage");
        check("What are people saying on Facebook about the Lekki toll gate", Platform.FACEBOOK, "the Lekki toll gate");
        check("what is reddit saying about Flutter vs React Native", Platform.REDDIT, "Flutter vs React Native");
        check("look up Burna Boy's new album on instagram", Platform.INSTAGRAM, "Burna Boy's new album");
        check("search social media for \"Abuja flood\"", null, "Abuja flood");
    }

    @Test
    public void trendingOpensTheTrendingPage() {
        for (String said : new String[]{"Check trending post on x", "what's trending on twitter?", "show me trending posts on X",
                "Popular on Reddit today", "X trends", "check the trending topics on x right now"}) {
            SocialAsk a = SocialAsk.parse(said);
            assertNotNull(said, a);
            org.junit.Assert.assertTrue(said, a.trending);
        }
        assertEquals(Platform.X, SocialAsk.parse("Check trending post on x").platform);
        assertEquals(Platform.REDDIT, SocialAsk.parse("Popular on Reddit today").platform);
        org.junit.Assert.assertFalse(SocialAsk.parse("search X for trending sneakers").trending);
        assertEquals("https://x.com/explore/tabs/trending", Platform.X.trendingUrl());
    }

    @Test
    public void ordinaryQuestionsAreNot() {
        assertNull(SocialAsk.parse("what are people saying about inflation"));
        assertNull(SocialAsk.parse("search for the best laptop"));
        assertNull(SocialAsk.parse("How do I post on Instagram?"));
        assertNull(SocialAsk.parse("search X for"));
        assertNull(SocialAsk.parse(null));
    }

    private static void check(String said, Platform p, String q) {
        SocialAsk a = SocialAsk.parse(said);
        assertNotNull(said, a);
        assertEquals(said, p, a.platform);
        assertEquals(said, q, a.query);
    }
}
