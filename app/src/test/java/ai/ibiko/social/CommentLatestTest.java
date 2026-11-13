package ai.ibiko.social;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** "Latest post about X and write a comment" is a search plus a draft, not a link the user already has. */
public class CommentLatestTest {
    @Test
    public void theScreenshotAsksForAFacebookComment() {
        String[] c = PostDrafts.commentOnLatest(
                "Ok try Facebook, latest post concerning or mentioning Elon musk and write an interesting comment related to the topic.");
        assertEquals("facebook", c[0]);
        assertEquals("Elon musk", c[1]);
        assertEquals("an interesting comment related to the topic", c[2]);
    }

    @Test
    public void aTweetOnXAndALinkedCommentStayApart() {
        String[] x = PostDrafts.commentOnLatest("reply to the newest tweet on X about the launch");
        assertEquals("x", x[0]);
        assertEquals("the launch", x[1]);
        assertNull(PostDrafts.commentOnLatest("comment under this post https://www.facebook.com/someone/posts/123 hi"));
        assertNull(PostDrafts.commentOnLatest("what is a comment?"));
        assertNull(PostDrafts.commentOnLatest("search Facebook for Elon Musk"));
        assertNull(PostDrafts.commentOnLatest("write a post about cars"));
    }
}
