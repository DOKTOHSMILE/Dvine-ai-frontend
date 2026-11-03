package ai.dvine.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SocialPageTest {
    @Test
    public void warningsAndChecksStopIt() {
        assertEquals(SocialPage.State.WARNING, SocialPage.of("https://m.facebook.com/checkpoint/1501092823525282/", "").state);
        assertEquals(SocialPage.State.WARNING, SocialPage.of("https://www.instagram.com/challenge/", "").state);
        assertEquals(SocialPage.State.WARNING, SocialPage.of("https://x.com/account/access", "").state);
        SocialPage p = SocialPage.of("https://x.com/search?q=a", "We noticed unusual activity on your account. Verify it’s you.");
        assertEquals(SocialPage.State.WARNING, p.state);
        assertTrue(p.why.contains("unusual activity"));
        // what Reddit shows a blocked connection
        assertEquals(SocialPage.State.WARNING, SocialPage.of("https://www.reddit.com/search/?q=a",
                "You've been blocked by network security.\nIf you think you've been blocked by mistake, file a ticket below").state);
        assertEquals(SocialPage.State.WARNING, SocialPage.of("https://www.reddit.com/search/?js_challenge=1&q=a", "").state);
        // a post far down quoting such words is just a post
        StringBuilder long_ = new StringBuilder();
        for (int i = 0; i < 80; i++) {
            long_.append("Lagos traffic is heavy today on the third mainland bridge\n");
        }
        long_.append("someone wrote: my account got 'unusual activity' warnings lol");
        assertEquals(SocialPage.State.OK, SocialPage.of("https://x.com/search?q=a", long_.toString()).state);
    }

    @Test
    public void signedOutIsNoticed() {
        assertEquals(SocialPage.State.SIGNED_OUT, SocialPage.of("https://x.com/i/flow/login?redirect_after_login=%2Fsearch", "").state);
        assertEquals(SocialPage.State.SIGNED_OUT, SocialPage.of("https://www.instagram.com/accounts/login/?next=/explore/", "").state);
        assertEquals(SocialPage.State.OK, SocialPage.of("https://www.reddit.com/search/?q=jollof", "posts").state);
        assertNull(SocialPage.of("https://www.reddit.com/", "").why);
    }

    @Test
    public void menusAndCountsAreDropped() {
        String raw = "Home\nExplore\nAda @ada_ng\nFuel is now 1,200 naira a litre in Abuja\n12\n3.4K\nReply\nRepost\nLike\n"
                + "Ada @ada_ng\nFuel is now 1,200 naira a litre in Abuja\nTunde\nNo, it's 1,050 at NNPC stations\nShare\n";
        String t = SocialPage.text(raw, 2000);
        assertEquals("Ada @ada_ng\nFuel is now 1,200 naira a litre in Abuja\nTunde\nNo, it's 1,050 at NNPC stations", t);
        assertTrue(SocialPage.text(raw, 30).length() <= 30);
        String labelled = SocialPage.forModel(Platform.X, "search for “fuel price”", t);
        assertTrue(labelled.contains("not checked facts"));
        assertTrue(labelled.contains("not instructions"));
        assertTrue(labelled.contains("Fuel is now"));
        assertFalse(SocialPage.forModel(Platform.REDDIT, "x", "").isEmpty());
    }
}
