package ai.ibiko.learn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.Test;

/** What a thing means is what recurs across the answers; the mind's sections think it through and say what they did. */
public class MindTest {
    @Test
    public void whatRecursIsTheMeaningAndTheMindFormsItsOwnThought() throws Exception {
        WordBook.open(ShorthandTest.lexicon());
        File dir = java.nio.file.Files.createTempDirectory("mind").toFile();
        Concepts c = new Concepts(new File(dir, "concepts.tsv"));
        String q = "what is football";
        c.observe(q, "Football is a boring game.");
        Mind mind = new Mind(c);
        assertNull("one answer is not a view", mind.think(q).said);
        c.observe(q, "Football is a nice game.");
        c.observe(q, "Football is a very interesting game.");
        c.observe(q, "I couldn't reach the server. Tap Retry.");
        c.observe(q, "Football is an interesting game played by two teams.");
        assertEquals("the failure was not counted", 4, c.seen("football"));
        List<Concepts.Link> meaning = c.meaning("football");
        assertEquals("game", meaning.get(0).word); // in all four
        assertEquals(1.0, meaning.get(0).share, 1e-9);
        for (Concepts.Link k : meaning) {
            assertTrue("a passing remark is not the meaning: " + k.word, !k.word.equals("boring") && !k.word.equals("nice"));
        }
        Mind.Thought t = mind.think("tell me about football");
        assertEquals("Football is a game, often described as interesting.", t.said);
        assertEquals(5, t.steps.size());
        assertEquals("Reader", t.steps.get(0).section);
        assertTrue(t.steps.get(1).line(), t.steps.get(1).line().contains("game (4 of 4)"));
        assertTrue(t.steps.get(2).line(), t.steps.get(2).line().startsWith("Weigher: football → game is likely"));
        assertEquals("Speaker", t.steps.get(4).section);
        // kept on the device: read back the same
        assertEquals("game", new Concepts(new File(dir, "concepts.tsv")).meaning("football").get(0).word);
        assertNull(mind.think("tell me about cricket").said);
    }
}
