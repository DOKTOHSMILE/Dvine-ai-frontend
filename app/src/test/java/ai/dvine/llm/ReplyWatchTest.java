package ai.dvine.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReplyWatchTest {
    @Test
    public void tokensKeepAReplyAliveUntilTheCap() {
        ReplyWatch watch = new ReplyWatch(12_000, 90_000, 0);
        assertFalse(watch.expired(11_000));
        assertTrue(watch.expired(12_000));
        assertEquals("idle", watch.reason(12_000));
        watch.touch(12_000);
        assertFalse(watch.expired(20_000));
        assertTrue(watch.expired(90_000));
        assertEquals("cap", watch.reason(90_000));
        watch.finish();
        assertFalse(watch.expired(120_000));
    }

    @Test
    public void aPackedAnswerIsNotRefused() {
        assertFalse(AnswerGate.refuseUngrounded(false, true, true));
        assertTrue(AnswerGate.refuseUngrounded(false, false, true));
        assertFalse(AnswerGate.refuseUngrounded(true, false, true));
        assertFalse(AnswerGate.refuseUngrounded(false, false, false));
    }
}
