package ai.dvine.brain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Whole sentences, read in context: kind, addressee, negation, fillers, references, corrections, several requests. */
public class ContextExaminerTest {
    @Test
    public void readsKindAddresseeAndCore() {
        ContextExaminer x = new ContextExaminer();
        ContextExaminer.Reading r = x.examine("Ivie, could you please tell me what a black hole is?");
        assertEquals("ivie", r.addressee);
        assertEquals(ContextExaminer.Kind.QUESTION, r.kind);
        assertEquals("what is a black hole", r.core);
        assertEquals("black hole", r.topic);
        r = x.examine("Show me the time, Obi");
        assertEquals("obi", r.addressee);
        assertEquals("show me the time", r.core);
        assertEquals(ContextExaminer.Kind.REQUEST, r.kind);
        assertEquals(ContextExaminer.Kind.GREETING, x.examine("Hello there").kind);
        assertEquals(ContextExaminer.Kind.THANKS, x.examine("thanks a lot").kind);
        assertEquals("both", x.examine("both of you discuss money").addressee);
        // a name inside the sentence is not someone being spoken to
        assertNull(x.examine("ask obi").addressee);
    }

    @Test
    public void negationIsOnlyForNegatedRequests() {
        ContextExaminer x = new ContextExaminer();
        assertTrue(x.examine("Don't open Telegram").negated);
        assertTrue(x.examine("please do not send that").negated);
        assertTrue(!x.examine("I don't know").negated);
        assertTrue(!x.examine("don't you think it's late?").negated);
        assertTrue(!x.examine("open telegram").negated);
    }

    @Test
    public void referencesFollowTheConversation() {
        ContextExaminer x = new ContextExaminer();
        x.examine("what is photosynthesis");
        ContextExaminer.Reading r = x.examine("explain it in simple words");
        assertEquals("explain photosynthesis in simple words", r.core);
        assertNull(r.clarify);
        // "that is great": a demonstrative, not a reference
        assertEquals("that is great", x.examine("that is great").core);
        // nothing to refer to yet: ask back
        ContextExaminer fresh = new ContextExaminer();
        assertNotNull(fresh.examine("explain it").clarify);
    }

    @Test
    public void correctionsRewriteTheLastRequest() {
        ContextExaminer x = new ContextExaminer();
        x.examine("what is a bank");
        ContextExaminer.Reading r = x.examine("no, I meant the river bank");
        assertEquals(ContextExaminer.Kind.CORRECTION, r.kind);
        assertEquals("what is the river bank", r.core);
        r = x.examine("no I meant tell me a joke");
        assertEquals("tell me a joke", r.core);
    }

    @Test
    public void severalRequestsAreSeparated() {
        ContextExaminer x = new ContextExaminer();
        ContextExaminer.Reading r = x.examine("tell me the time and then tell me a joke");
        assertEquals(2, r.parts.size());
        assertEquals("tell me the time", r.parts.get(0));
        r = x.examine("tell me a joke and set a timer");
        assertEquals(2, r.parts.size());
        // "and" inside a phrase stays
        assertEquals(1, x.examine("what goes with salt and pepper").parts.size());
    }

    @Test
    public void incompleteSentencesAskBack() {
        ContextExaminer x = new ContextExaminer();
        assertNotNull(x.examine("can you").clarify);
        assertNotNull(x.examine("please").clarify);
        assertNull(x.examine("can you hear me").clarify);
        assertEquals("can you hear me", x.examine("can you hear me").core);
        assertEquals(ContextExaminer.Kind.NEVER_MIND, x.examine("never mind").kind);
    }
}
