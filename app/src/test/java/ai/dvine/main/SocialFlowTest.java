package ai.dvine.main;

import static org.junit.Assert.assertEquals;

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
}
