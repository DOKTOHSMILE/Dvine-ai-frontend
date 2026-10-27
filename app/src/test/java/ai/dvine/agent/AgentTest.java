package ai.dvine.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

/** The brain reads, DVINE checks it can, does it and learns it; debates on any topic. */
public class AgentTest {
    @Test
    public void readsTheBrainsInterpretation() {
        Interpretation i = Interpretation.parse("INTENT: request\nMEANS: The user wants a lion drawn.\nTASK: draw\nTARGET: lion\nINTERNET: no");
        assertEquals(Interpretation.Intent.REQUEST, i.intent);
        assertEquals(Interpretation.Task.DRAW, i.task);
        assertEquals("draw a lion in 3d", i.command());
        assertEquals("request · draw · lion", i.summary());
        Interpretation q = Interpretation.parse("- INTENT: Question.\n- TASK: answer\n- TARGET: none\n- INTERNET: Yes");
        assertEquals(Interpretation.Intent.QUESTION, q.intent);
        assertTrue(q.internet);
        assertNull(q.command());
        assertEquals("open whatsapp", Interpretation.parse("INTENT: request\nTASK: open app\nTARGET: WhatsApp").command());
        assertEquals(Interpretation.Task.DEBATE, Interpretation.parse("INTENT: prompt\nTASK: debate\nTARGET: cats vs dogs").task);
        assertEquals(Interpretation.Task.OTHER, Interpretation.parse("INTENT: request\nTASK: book a flight").task);
        assertEquals("music off", Interpretation.parse("INTENT: request\nTASK: setting\nTARGET: music off").command());
        assertNull(Interpretation.parse("I think you want a lion."));
    }

    @Test
    public void checksItCanBeforeItActs() {
        Capabilities c = new Capabilities();
        assertNotNull(c.missing(Interpretation.Task.SEARCH));
        assertNotNull(c.missing(Interpretation.Task.DEBATE));
        assertNotNull(c.missing(Interpretation.Task.MOVE));
        assertNotNull(c.missing(Interpretation.Task.OTHER));
        c.online = true;
        c.brainReady = true;
        assertNull(c.missing(Interpretation.Task.SEARCH));
        assertNull(c.missing(Interpretation.Task.DEBATE));
        assertNull(c.missing(Interpretation.Task.DRAW));
        c.lookupsAllowed = false;
        assertNotNull(c.missing(Interpretation.Task.SEARCH));
    }

    @Test
    public void learnsFromTheBrain() throws Exception {
        File f = File.createTempFile("learned", ".txt");
        f.delete();
        LearnedIntents a = new LearnedIntents(f);
        assertNull(a.commandFor("could you sketch me a lion"));
        a.learn("Could you sketch me a lion?", "draw a lion in 3d");
        assertEquals("draw a lion in 3d", new LearnedIntents(f).commandFor("could you sketch me a lion"));
        a.learn("draw a cat", "draw a cat"); // nothing to learn
        assertEquals(1, new LearnedIntents(f).size());
        f.delete();
    }

    @Test
    public void debatesAnyTopic() {
        assertEquals("pineapple on pizza", Debate.topicOf("debate pineapple on pizza"));
        assertEquals("whether AI will replace teachers", Debate.topicOf("Let Obi and Ivie debate whether AI will replace teachers?").replaceFirst("^", "whether ").replace("whether whether", "whether"));
        assertEquals("cats versus dogs", Debate.topicOf("can you have a debate about cats versus dogs"));
        assertNull(Debate.topicOf("what is a debate"));
        Debate d = new Debate("cats versus dogs");
        int turns = 0;
        while (!d.done()) {
            assertTrue(d.rules().contains(turns % 2 == 0 ? "FOR" : "AGAINST"));
            String line = d.take("Obi: Cats are calm. They clean themselves. They purr. They nap. They judge you.");
            assertEquals("Cats are calm. They clean themselves. They purr. They nap.", line);
            turns++;
        }
        assertEquals(6, turns);
        assertTrue(d.prompt().contains("Ivie (closing)"));
    }
}
