package ai.ibiko.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SocialFlowTest {
    @Test
    public void anOrderSplitsIntoVerbNetworkAndWords() {
        SocialFlow.Parts post = SocialFlow.parts("post facebook: our sale");
        assertEquals("post", post.verb);
        assertEquals("facebook", post.net);
        assertEquals("our sale", post.words);

        SocialFlow.Parts search = SocialFlow.parts("search x: fuel price");
        assertEquals("search", search.verb);
        assertEquals("x", search.net);
        assertEquals("fuel price", search.words);

        SocialFlow.Parts trending = SocialFlow.parts("trending reddit");
        assertEquals("trending", trending.verb);
        assertEquals("reddit", trending.net);
        assertEquals("", trending.words);

        SocialFlow.Parts help = SocialFlow.parts("help");
        assertEquals("help", help.verb);
        assertEquals("", help.net);
        assertEquals("", help.words);
    }

    @Test
    public void aCommentWithoutALinkSearchesThenDrafts() {
        final String[] got = new String[3];
        SocialFlow flow = new SocialFlow(new SocialFlow.Host() {
            public String agent() { return "ibiko"; }
            public void say(String line) { }
            public void openAccounts() { }
            public void openLogin(ai.ibiko.social.Platform platform) { }
            public boolean signedIn(ai.ibiko.social.Platform platform) { return true; }
            public boolean anySignedIn() { return true; }
            public boolean online() { return true; }
            public void writePost(String prompt, int tokens) { }
            public int chatTokens() { return 100; }
            public void search(String question, String call) { }
            public void commentOnLatest(String app, String query, String what) {
                got[0] = app;
                got[1] = query;
                got[2] = what;
            }
        });
        flow.run("Ok try Facebook, latest post concerning or mentioning Elon musk and write an interesting comment related to the topic.",
                "comment facebook:");
        assertEquals("facebook", got[0]);
        assertEquals("Elon musk", got[1]);
        assertTrue(got[2].contains("interesting comment"));
    }
}
