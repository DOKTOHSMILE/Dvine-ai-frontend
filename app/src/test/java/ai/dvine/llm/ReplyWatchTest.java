package ai.dvine.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReplyWatchTest {
    @Test
    public void tokensKeepAReplyAliveUntilTheCap() {
        ReplyWatch watch = new ReplyWatch(12_000, 90_000, 0);
        assertFalse(watch.expired(12_000));
        assertFalse(watch.expired(40_000));
        watch.touch(40_000);
        assertFalse(watch.expired(50_000));
        assertTrue(watch.expired(52_000));
        assertEquals("idle", watch.reason(52_000));
        assertTrue(watch.expired(130_000)); // the cap counts from the first word (40 s) on
        assertEquals("cap", watch.reason(130_000));
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

    @org.junit.Test
    public void readingALongQuestionIsNotCountedAsWriting() {
        ReplyWatch w = new ReplyWatch(12_000, 180_000, 600_000, 0);
        org.junit.Assert.assertFalse(w.expired(300_000)); // five minutes reading a long prompt: still fine
        w.touch(300_000); // the first word
        org.junit.Assert.assertFalse(w.expired(305_000));
        w.touch(470_000);
        org.junit.Assert.assertTrue(w.expired(480_001)); // three minutes of writing since the first word
        org.junit.Assert.assertEquals("cap", w.reason(480_001));
        ReplyWatch stuck = new ReplyWatch(12_000, 180_000, 600_000, 0);
        org.junit.Assert.assertFalse(stuck.expired(600_000)); // ten minutes of reading is not a failure
        org.junit.Assert.assertEquals("", stuck.reason(600_000));
        org.junit.Assert.assertFalse(stuck.expired(3_600_000));
    }
}
