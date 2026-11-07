package ai.dvine.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class PromptFitTest {
    @Test
    public void aShortQuestionDoesNotReserveTheFullCache() {
        assertEquals(2048, PromptFit.localContext(0, 8192));
        assertEquals(2048, PromptFit.localContext(400, 8192));
        assertEquals(4096, PromptFit.localContext(9_000, 8192));
        assertEquals(8192, PromptFit.localContext(20_000, 8192));
        assertEquals(4096, PromptFit.localContext(20_000, 4096)); // the user's ceiling still wins
        assertEquals(2048, PromptFit.localContext(0, 1024)); // never under the floor the loader accepts
    }

    @Test
    public void oldTurnsAreNotSentUpToTheWholeWindow() {
        List<String[]> turns = new ArrayList<String[]>();
        turns.add(new String[]{"user", "x".repeat(20_000)});
        turns.add(new String[]{"assistant", "y".repeat(20_000)});
        turns.add(new String[]{"user", "what changed?"});
        List<String[]> sent = PromptFit.remoteTurns(turns, 128_000, 1024);
        assertEquals("what changed?", sent.get(sent.size() - 1)[1]);
        int older = 0;
        for (int i = 0; i < sent.size() - 1; i++) {
            older += sent.get(i)[1].length();
        }
        assertTrue(sent.size() < turns.size() || older <= PromptFit.KEPT_HISTORY_CHARS);
        assertTrue(older <= PromptFit.KEPT_HISTORY_CHARS);
    }

    @Test
    public void aSimpleQuestionDoesNotResendTheWholeChat() {
        List<String[]> turns = new ArrayList<String[]>();
        turns.add(new String[]{"user", "x".repeat(6_000)});
        turns.add(new String[]{"assistant", "y".repeat(6_000)});
        turns.add(new String[]{"user", "what time is it?"});
        List<String[]> sent = PromptFit.remoteTurns(turns, 128_000, 160, true);
        assertEquals("what time is it?", sent.get(sent.size() - 1)[1]);
        int older = 0;
        for (int i = 0; i < sent.size() - 1; i++) {
            older += sent.get(i)[1].length();
        }
        assertTrue(older <= PromptFit.BRIEF_HISTORY_CHARS);
        assertTrue(sent.size() < turns.size());
    }

    @Test
    public void aShortPlainCallIsNotPaddedToAThousandTokens() {
        assertEquals(48, PromptFit.outputTokens(48, false, 0));
        assertEquals(160, PromptFit.outputTokens(160, false, 0));
        assertEquals(1500, PromptFit.outputTokens(1500, false, 0));
        assertEquals(4000, PromptFit.outputTokens(48, true, 4000)); // the user's cap is not raised
        assertEquals(2048, PromptFit.completionTokens(48, true, 0));
        assertEquals(8000, PromptFit.completionTokens(2000, true, 0));
        assertEquals(4000, PromptFit.completionTokens(48, true, 4000));
    }

    @Test
    public void aShortChatIsLeftWhole() {
        List<String[]> turns = new ArrayList<String[]>();
        turns.add(new String[]{"user", "hi"});
        turns.add(new String[]{"assistant", "hello"});
        turns.add(new String[]{"user", "again"});
        assertSame(turns, PromptFit.remoteTurns(turns, 8192, 256));
    }

    @Test
    public void rememberedApiAnswersKeepTheEnding() {
        String answer = "start " + "m".repeat(5_000) + " ENDING";
        String kept = LocalModel.fit(answer, 4_000);
        assertTrue(kept.length() <= 4_000);
        assertTrue(kept.startsWith("start"));
        assertTrue(kept.endsWith("ENDING"));
    }

    @Test
    public void aMissingHostIsNotTriedAgain() {
        assertFalse(PromptFit.retryReach("Unable to resolve host \"brewkeg.dev\": No address associated with hostname"));
        assertTrue(PromptFit.retryReach("Software caused connection abort"));
    }
}
