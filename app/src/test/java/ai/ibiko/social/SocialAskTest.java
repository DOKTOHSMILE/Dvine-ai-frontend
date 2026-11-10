package ai.ibiko.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
    public void connectHelpAndWritingAPost() {
        assertEquals(Platform.FACEBOOK, SocialAsk.connect("Connect my Facebook"));
        assertEquals(Platform.X, SocialAsk.connect("sign in to twitter"));
        assertEquals(Platform.INSTAGRAM, SocialAsk.connect("add my Instagram account"));
        assertNull(SocialAsk.connect("connect the dots"));
        org.junit.Assert.assertTrue(SocialAsk.help("How can I access social media"));
        org.junit.Assert.assertTrue(SocialAsk.help("How do I access my social media functions?"));
        org.junit.Assert.assertFalse(SocialAsk.help("How do I make friends"));
        String[] c = SocialAsk.compose("Generate a new post for Facebook");
        assertEquals("Facebook", c[0]);
        assertEquals("", c[1]);
        c = SocialAsk.compose("write a tweet for X about our weekend sale");
        assertEquals("X", c[0]);
        assertEquals("our weekend sale", c[1]);
        assertNull(SocialAsk.compose("write a post about cats"));
        assertEquals("social.search", SocialAsk.parse("search X for the fuel price").connector());
        assertEquals("social.trending", SocialAsk.parse("what's trending on twitter").connector());
        assertEquals("social.search", SocialAsk.parse("search telegram for @lagosnews").connector());
        assertNull("telegram word search has no connector", SocialAsk.parse("search telegram for fuel price").connector());
        assertNull("telegram has no trending page", SocialAsk.parse("what's trending on telegram").connector());
        assertTrue(SocialAsk.telegramChat("@lagosnews"));
        assertTrue(SocialAsk.telegramChat("t.me/lagosnews"));
        assertFalse(SocialAsk.telegramChat("fuel price"));
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
