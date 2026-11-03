package ai.dvine.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class PromptFitTest {
    @Test
    public void phonePromptLeavesRoomAndFitsTheFirstTime() {
        int reply = PromptFit.localReplyTokens(8192, 4000, false);
        assertTrue(reply <= 512);
        int chars = PromptFit.localPromptChars(8192, reply, 2000);
        assertTrue(chars > 400);
        assertTrue((chars + 2000) / 2 + reply + 160 <= 8192);
    }

    @Test
    public void aLongChatKeepsOnlyWhatFitsTheApi() {
        List<String[]> turns = new ArrayList<String[]>();
        for (int i = 0; i < 20; i++) {
            turns.add(new String[]{"user", "question " + i + " " + new String(new char[800]).replace('\0', 'x')});
            turns.add(new String[]{"assistant", "answer " + i});
        }
        turns.add(new String[]{"user", "what now"});
        List<String[]> fitted = PromptFit.remoteTurns(turns, 4096, 512);
        assertTrue(fitted.size() < turns.size());
        assertEquals("what now", fitted.get(fitted.size() - 1)[1]);
    }

    @Test
    public void aMissingHostIsNotTriedAgain() {
        assertFalse(PromptFit.retryReach("Unable to resolve host \"brewkeg.dev\": No address associated with hostname"));
        assertTrue(PromptFit.retryReach("Software caused connection abort"));
    }
}
