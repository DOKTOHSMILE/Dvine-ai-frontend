package ai.dvine.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Posting from the pilot's account: which networks, and the page steps (type, press Post once, confirm). */
public class SocialPosterTest {
    @Test
    public void facebookAndXArePostedDirectly() {
        assertEquals(Platform.FACEBOOK, SocialPoster.direct("facebook"));
        assertEquals(Platform.X, SocialPoster.direct("x"));
        assertNull("Instagram needs a photo: the app", SocialPoster.direct("instagram"));
    }

    @Test
    public void theScriptTypesPressesOnceAndConfirms() {
        String js = SocialPoster.script("facebook", "Success is a habit");
        assertTrue(js, js.contains("what.?s on your mind"));
        assertTrue(js, js.contains("execCommand('insertText'"));
        assertTrue("Post is pressed once (stage 2), then stage 3 only watches", js.contains("btn.click();d.stage=3"));
        assertTrue(js, js.contains("'done'") && js.contains("signedout"));
        assertTrue(js, js.endsWith("(\"facebook\",\"Success is a habit\",[],false)"));
    }

    @Test
    public void theWordsCannotBreakOutOfTheScript() {
        assertEquals("\"say \\\"hi\\\"\\n\\u003c/script>\"", SocialPoster.quote("say \"hi\"\n</script>"));
    }
}
