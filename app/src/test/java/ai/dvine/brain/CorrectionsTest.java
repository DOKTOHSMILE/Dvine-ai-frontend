package ai.dvine.brain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.TimeZone;

import org.junit.Test;

import ai.dvine.turn.TurnLine;

/** The user sets facts straight; DVINE keeps them, trusts them over the model, and never argues. */
public class CorrectionsTest {
    private static String said(BrainAnswer a) {
        StringBuilder b = new StringBuilder();
        for (TurnLine l : a.lines) {
            if ("SAY".equals(l.cue) && l.text != null) {
                b.append(l.text).append(' ');
            }
        }
        return b.toString().trim();
    }

    @Test
    public void readsTheWaysPeopleCorrect() {
        FactCorrection f = FactCorrection.read("No, the president of Nigeria is Bola Tinubu", null);
        assertEquals("the president of nigeria", f.subject);
        assertEquals("Bola Tinubu", f.value);
        f = FactCorrection.read("that's wrong, it's 2026", "the year");
        assertEquals("the year", f.subject);
        assertEquals("2026", f.value);
        f = FactCorrection.read("The capital of Nigeria is now Abuja", null);
        assertEquals("the capital of nigeria", f.subject);
        assertEquals("Abuja", f.value);
        f = FactCorrection.read("actually, Tinubu became president in 2023", null);
        assertEquals("tinubu became", f.subject);
        assertEquals("president in 2023", f.value);
        assertTrue(FactCorrection.read("you're wrong", "the president of nigeria").needsValue);
        f = FactCorrection.read("remember that the team meeting moved to Friday", null);
        assertEquals("the team meeting moved to Friday", f.value);
        // not corrections
        assertNull(FactCorrection.read("no thanks", "x"));
        assertNull(FactCorrection.read("actually, what is the time?", "x"));
        assertNull(FactCorrection.read("no, I meant the river bank", "bank"));
        assertNull(FactCorrection.read("what is the capital of Nigeria", null));
        assertNull(FactCorrection.read("I like rice", null));
    }

    @Test
    public void keepsTheCorrectionAndAnswersWithIt() throws Exception {
        OfflineBrain brain = new OfflineBrain(TestPack.load(), new LearningJournal(TestPack.tempDir()), () -> 1793007000000L,
                TimeZone.getTimeZone("UTC"));
        brain.respond("who is the president of nigeria", "obi");
        BrainAnswer fix = brain.respond("No, it's Bola Tinubu", "obi");
        assertEquals("LEARN", fix.source);
        assertEquals("MODEL_RESET", fix.command);
        assertTrue(said(fix), said(fix).contains("Bola Tinubu") && said(fix).contains("I've got it") || said(fix).startsWith("Got it"));
        BrainAnswer again = brain.respond("who is the president of nigeria?", "ivie");
        assertEquals("MEMORY", again.source);
        assertTrue(said(again), said(again).contains("Bola Tinubu"));
        // "that's wrong" alone asks what's right, then keeps the next answer
        brain.respond("what year is the next world cup", "obi");
        BrainAnswer ask = brain.respond("that's wrong", "obi");
        assertTrue(ask.listenAfter);
        BrainAnswer kept = brain.respond("2026", "obi");
        assertEquals("2026.", said(brain.respond("what year is the next world cup", "obi")).replaceAll(".*\\b2026", "2026"));
        assertTrue(said(kept), said(kept).contains("2026"));
        // the language model is handed what bears on the question, and the newest corrections
        List<String> facts = brain.taughtFor("Tell me about the president of Nigeria", 6);
        assertEquals("the president of nigeria is Bola Tinubu", facts.get(0));
        assertTrue(facts.toString(), facts.size() >= 2);
        // personal facts keep their own memory
        brain.respond("remember that my sister is Ada", "obi");
        assertTrue(said(brain.respond("no, it's Adaeze", "obi")).contains("Adaeze"));
        assertTrue(said(brain.respond("what is my sister", "obi")).contains("Adaeze"));
    }
}
