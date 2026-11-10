package ai.ibiko.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Post versions, named tags (never random ones), and comments under a post by its link. */
public class PostDraftsTest {
    @Test
    public void versionsAreSplitAndCleaned() {
        List<String> v = PostDrafts.parse("Version 1: Success is built daily. #Success\n=====\n**Personal**\nI learned success the hard way.\n=====\n\"Show up. Win.\"");
        assertEquals(3, v.size());
        assertEquals("Success is built daily. #Success", v.get(0));
        assertEquals("I learned success the hard way.", v.get(1));
        assertEquals("Show up. Win.", v.get(2));
        assertEquals(2, PostDrafts.parse("Version 1\nFirst post text here.\nVersion 2\nSecond post text here.").size());
        assertTrue(PostDrafts.prompt("Facebook", "success").contains("====="));
    }

    @Test
    public void onlyNamedPeopleAreTagged() {
        assertEquals(Arrays.asList("John Okafor", "mary_a"), PostDrafts.tags("about success and tag John Okafor and @mary_a"));
        assertEquals("about success", PostDrafts.withoutTags("about success and tag John Okafor and @mary_a"));
        assertTrue(PostDrafts.randomTags("about success and tag 5 random persons"));
        assertTrue(PostDrafts.tags("about success and tag 5 random persons").isEmpty());
        assertFalse(PostDrafts.randomTags("about success and tag John"));
        assertTrue(PostDrafts.tags("about success").isEmpty());
    }

    @Test
    public void aCommentUnderAPostByItsLink() {
        String[] c = PostDrafts.comment("comment under this post https://www.facebook.com/someone/posts/123 congratulate them");
        assertEquals("facebook", c[0]);
        assertEquals("https://www.facebook.com/someone/posts/123", c[1]);
        assertEquals("congratulate them", c[2]);
        assertEquals("x", PostDrafts.comment("reply to https://x.com/a/status/9 with thanks")[0]);
        assertNull(PostDrafts.comment("write a post about cars"));
    }

    @Test
    public void tagsAndCommentsAreInThePageSteps() {
        String js = SocialPoster.script("facebook", "Hello", Arrays.asList("John"), true);
        assertTrue(js, js.contains("' @'+tags[d.ti]") && js.contains("[\"John\"]") && js.endsWith(",true)"));
        assertTrue(js, js.contains("comment|post|reply|send"));
    }
}
