package ai.ibiko.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.Test;

public class SocialAccountsTest {
    @Test
    public void severalAccountsOneInUse() throws Exception {
        File dir = Files.createTempDirectory("social").toFile();
        File f = new File(dir, "accounts.txt");
        SocialAccounts a = new SocialAccounts(f);
        assertTrue(a.ready().isEmpty());
        SocialAccounts.Account work = a.add(Platform.X, "Work", 1000);
        SocialAccounts.Account home = a.add(Platform.X, " ", 2000);
        assertEquals("X 2", home.label);
        assertEquals(home.id, a.inUse(Platform.X).id);
        a.use(work.id);
        assertEquals(work.id, a.inUse(Platform.X).id);
        a.add(Platform.REDDIT, "Me", 3000);
        a.add(Platform.FACEBOOK, "Shop\tpage", 4000);
        assertEquals(Arrays.asList(Platform.X, Platform.REDDIT, Platform.FACEBOOK), a.ready());
        a.signedIn(work.id, false);
        assertEquals("a signed-out account isn't searched", Arrays.asList(Platform.REDDIT, Platform.FACEBOOK), a.ready());

        SocialAccounts back = new SocialAccounts(f); // read from the file
        assertEquals(4, back.all().size());
        assertFalse(back.get(work.id).signedIn);
        assertEquals("Shop page", back.on(Platform.FACEBOOK).get(0).label);
        back.remove(work.id);
        assertEquals("the other X account comes into use", home.id, back.inUse(Platform.X).id);
        back.remove(home.id);
        assertNull(back.inUse(Platform.X));
    }

    @Test
    public void platforms() {
        assertTrue(Platform.FACEBOOK.signedIn("datr=abc; c_user=1000123; xs=9"));
        assertFalse(Platform.FACEBOOK.signedIn("datr=abc; c_user="));
        assertFalse(Platform.X.signedIn(null));
        assertTrue(Platform.X.signedIn("guest_id=v1; auth_token=f00"));
        assertEquals("https://x.com/search?q=lagos%20flood&src=typed_query&f=live", Platform.X.searchUrl("lagos flood"));
        assertTrue(Platform.FACEBOOK.searchUrl("Elon Musk").startsWith("https://mbasic.facebook.com/search/posts/?q="));
        assertTrue(Platform.FACEBOOK.otherSearchUrl("Elon Musk").startsWith("https://m.facebook.com/search/posts/?q="));
        assertTrue(Platform.REDDIT.searchUrl("a&b").contains("old.reddit.com"));
        assertTrue(Platform.REDDIT.searchUrl("a&b").contains("a%26b"));
        assertTrue(Platform.X.otherSearchUrl("lagos flood").contains("mobile.twitter.com"));
        assertEquals("https://www.instagram.com/explore/tags/jollofrice/", Platform.INSTAGRAM.searchUrl("Jollof rice!"));
        assertTrue(Platform.REDDIT.searchUrl("a&b").contains("a%26b"));
        assertTrue(Platform.X.owns("https://x.com/ada/status/1"));
        assertFalse(Platform.X.owns("https://x.com.evil.example/"));
        assertFalse(Platform.REDDIT.owns("https://example.com/?u=https://reddit.com"));
        assertEquals(Platform.X, Platform.named("Twitter"));
        assertEquals(Platform.INSTAGRAM, Platform.named("IG"));
        assertNull(Platform.named("tiktok"));
    }
}
