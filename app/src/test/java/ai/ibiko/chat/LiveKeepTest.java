package ai.ibiko.chat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LiveKeepTest {
    @Test
    public void aStatusLineIsNotKeptAsTheReply() {
        String status = "I've read the record. The plan says projects/Dvine-ai-frontend, which does not exist. I'll fix those paths.";
        assertTrue(status.length() >= 400 || status.length() > 80);
        assertFalse(LiveKeep.keep(status, 80));
        assertFalse(LiveKeep.keep("Alright, I am continuing now.", 10));
        assertTrue(LiveKeep.keep("The download bar is on the chat screen, under the header, and the tests pass.", 40));
    }
}
