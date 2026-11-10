package ai.ibiko.social.intel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** The social intelligence tools: investigation, similar posts, sentiment, profiles, privacy and the registry. */
public class SocialIntelTest {
    /** A platform with fixed posts and profiles, for tests. */
    static final class Fake implements SocialProvider {
        final String name;
        final List<SocialPost> posts;
        boolean up = true;
        int searches;

        Fake(String name, SocialPost... posts) {
            this.name = name;
            this.posts = Arrays.asList(posts);
        }

        @Override public String platform() { return name; }
        @Override public boolean available() { return up; }
        @Override public String unavailableReason() { return up ? "" : "signed out"; }
        @Override public boolean canSearch() { return true; }
        @Override public boolean canReadPublicProfile() { return true; }
        @Override public boolean canReadOwnProfile() { return true; }

        @Override
        public List<SocialPost> search(String query, int most) {
            searches++;
            return posts;
        }

        @Override
        public SocialProfile readPublic(String handle, int recent) {
            SocialProfile p = new SocialProfile(name, handle, "Ada", "Writes about phones. Mail ada@example.com, call +234 803 555 0101",
                    "https://example.com/" + handle, 1200, 40, false);
            if (handle.equals("hidden")) {
                p.limits.add("The account is private: only what it shows publicly can be read, and nothing more is tried.");
            } else {
                p.recent.addAll(posts);
            }
            return p;
        }

        @Override
        public SocialProfile readOwn(int recent) {
            return new SocialProfile(name, "me", "Me", "My bio", "https://example.com/me", 50, 3, true);
        }
    }

    static SocialPost post(String text, String date, String url) {
        return new SocialPost("X", "@a", text, date, url, 10, 2, -1, 0);
    }

    static final SocialPost P1 = post("The new Pixel phone battery is great, lasts two days easily", "2026-11-01", "https://x.com/1");
    static final SocialPost P2 = post("Pixel battery is terrible, mine dies by noon. Is this a bug?", "2026-11-02", "https://x.com/2");
    static final SocialPost P3 = post("RT The new Pixel phone battery is great, lasts two days easily!", "2026-11-03", "https://x.com/3");
    static final SocialPost P4 = post("Pasta recipe for dinner tonight with garlic", "2026-11-02", "https://x.com/4");

    @Test
    public void claimsAreSplitIntoFactsAndOpinions() {
        List<ClaimExtractor.Claim> c = ClaimExtractor.claims(
                "Google announced the Pixel 11 on 4 November with a 5000 mAh battery. I think it is the best phone ever. Is it out yet?");
        assertEquals(2, c.size());
        assertTrue(c.get(0).factual);
        assertFalse(c.get(1).factual);
        assertFalse(ClaimExtractor.searchPhrases("Google announced the Pixel 11 battery", 3).isEmpty());
    }

    @Test
    public void similarPostsAreRankedByMeaningWithDuplicatesRemoved() {
        List<SocialPost> ranked = SemanticPostMatcher.rank("Pixel phone battery life", Arrays.asList(P1, P2, P3, P4), 0.1, 10);
        assertEquals("the repost and the unrelated post are left out", 2, ranked.size());
        assertFalse(ranked.contains(P4));
        assertTrue(ranked.get(0).relevance >= ranked.get(1).relevance);
    }

    @Test
    public void sentimentStatesItsSampleAndNeverEveryone() {
        SentimentReport r = SentimentReport.of("Pixel battery", Arrays.asList(P1, P2, P4));
        assertEquals(1, r.positive);
        assertEquals(1, r.negative);
        assertEquals(1, r.neutral);
        assertTrue(r.questions.get(0).startsWith("Is this a bug"));
        assertEquals(-1, SentimentReport.score("this is not good at all"));
        String t = r.text();
        assertTrue(t.contains("3 posts from X"));
        assertTrue(t.contains("2026-11-01 to 2026-11-02"));
        assertTrue(t.contains("not everyone's opinion"));
        assertTrue(t.contains("Fewer than 10 posts"));
    }

    @Test
    public void privacyIsGuarded() {
        assertNotNull(SocialPrivacyGuard.refuse("social.profile.read_public", "x https://x.com/messages/123"));
        assertNotNull(SocialPrivacyGuard.refuse("social.profile.read_public", "find her home address"));
        assertNotNull(SocialPrivacyGuard.refuse("social.profile.read_public", "bypass the private account"));
        assertNull(SocialPrivacyGuard.refuse("social.search.posts", "pixel battery"));
        String clean = SocialPrivacyGuard.clean("sessionid=abc123 bio: ada@example.com +234 803 555 0101", false);
        assertFalse(clean.contains("abc123"));
        assertFalse(clean.contains("ada@example.com"));
        assertFalse(clean.contains("555"));
    }

    @Test
    public void redditsPublicApiIsReadAndARefusalIsSaid() throws Exception {
        String listing = "{\"data\":{\"children\":[{\"data\":{\"title\":\"Pixel battery drain\",\"selftext\":\"Mine dies fast\","
                + "\"author\":\"bob\",\"subreddit\":\"GooglePixel\",\"created_utc\":1762000000,\"score\":42,\"num_comments\":7,"
                + "\"permalink\":\"/r/GooglePixel/comments/abc/x/\"}}]}}";
        List<SocialPost> p = RedditPublicProvider.posts(listing);
        assertEquals(1, p.size());
        assertEquals("u/bob", p.get(0).author);
        assertEquals(42, p.get(0).likes);
        assertEquals("https://www.reddit.com/r/GooglePixel/comments/abc/x/", p.get(0).url);
        assertTrue(p.get(0).date.startsWith("2025-"));
        try {
            RedditPublicProvider.json("<html>blocked</html>");
            org.junit.Assert.fail();
        } catch (java.io.IOException e) {
            assertTrue(e.getMessage().contains("refused"));
        }
    }

    @Test
    public void theEngineRunsEachCapabilityWithSourcesAndLimits() {
        Fake x = new Fake("X", P1, P2, P3, P4);
        List<SocialProvider> ps = new ArrayList<SocialProvider>(Arrays.asList(x,
                new UnsupportedProvider("TikTok", "TikTok's API needs an approved developer app")));
        SocialExecutionEngine e = new SocialExecutionEngine(ps, q -> "Reuters: Google confirmed the Pixel 11 battery is 5000 mAh.");
        String inv = e.run("social.post.investigate Google announced the Pixel 11 has a 5000 mAh battery that lasts two days.", 0).text;
        assertTrue(inv, inv.contains("[factual, to verify]"));
        assertTrue(inv.contains("Reuters"));
        assertTrue(inv.contains("https://x.com/1"));
        assertTrue(inv.contains("not that it is true"));

        String sim = e.run("social.search.similar the pixel phone battery", 0).text;
        assertTrue(sim, sim.contains("Not searched: TikTok"));
        String senti = e.run("social.sentiment.analyze pixel battery", 0).text;
        assertTrue(senti.contains("Sentiment sample"));

        String pub = e.run("social.profile.read_public x @ada", 0).text;
        assertTrue(pub.contains("Public X profile @ada"));
        assertFalse("another person's email is hidden", pub.contains("ada@example.com"));
        assertTrue(e.run("social.profile.read_public x hidden", 0).text.contains("private"));
        assertTrue(e.run("social.profile.read_own x", 0).text.contains("own X profile"));
        assertTrue(e.run("social.profile.read_public tiktok @someone", 0).text.contains("approved developer app"));
        assertTrue(e.run("social.accounts.list", 0).text.contains("TikTok: TikTok's API"));
        assertFalse(e.run("social.profile.read_public x dm inbox of @ada", 0).ok);
        assertFalse(e.run("social.made.up thing", 0).ok);

        int before = x.searches;
        assertTrue(e.run("social.search.posts pixel", 1000).text.length() > 0);
        assertTrue(e.run("social.search.posts pixel", 2000).text.contains("Cached"));
        assertEquals("the second one came from the cache", before + 1, x.searches);
    }

    @Test
    public void theRegistryTellsTheBrainWhatRunsNow() {
        Fake x = new Fake("X");
        x.up = false;
        List<SocialProvider> ps = Arrays.<SocialProvider>asList(x);
        String brief = SocialCapabilityRegistry.forBrain(ps);
        assertTrue(brief.contains("§SI"));
        assertTrue(brief.contains("social.search.similar"));
        assertTrue(brief.contains("unavailable (X: signed out)"));
        String json = SocialCapabilityRegistry.json(ps);
        assertTrue(json.startsWith("{\"category\":\"social_media\""));
        assertTrue(json.contains("\"permission\":\"OWN_ACCOUNT\""));
        assertEquals(9, SocialCapabilityRegistry.ALL.size());
    }

    @Test
    public void theBrainCallsItByCode() {
        ai.ibiko.llm.BrainOrder o = ai.ibiko.llm.BrainOrder.leading("§SI social.search.similar pixel battery\nLooking.");
        assertEquals(ai.ibiko.llm.BrainOrder.Kind.SOCIALINTEL, o.kind);
        assertEquals("social.search.similar pixel battery", o.arg);
        assertTrue(ai.ibiko.suit.ChatTools.defaults("obi").allowedCodes().contains(ai.ibiko.suit.FunctionCode.SI));
    }
}
