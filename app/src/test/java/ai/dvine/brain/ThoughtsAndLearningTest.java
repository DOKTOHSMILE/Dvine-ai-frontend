package ai.dvine.brain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.TimeZone;

import org.junit.Before;
import org.junit.Test;

import ai.dvine.turn.TurnLine;

public class ThoughtsAndLearningTest {
    private File dir;
    private OfflineBrain brain;

    private static KnowledgePack withDictionary() throws Exception {
        String dict = new String(Files.readAllBytes(new File("src/main/assets/knowledge/shards/dictionary_en.json").toPath()),
                StandardCharsets.UTF_8);
        return TestPack.load().merge(KnowledgePack.parse(dict));
    }

    @Before
    public void setUp() throws Exception {
        dir = TestPack.tempDir();
        brain = new OfflineBrain(withDictionary(), new LearningJournal(dir), () -> 1793007000000L, TimeZone.getTimeZone("UTC"));
    }

    private static String thought(BrainAnswer a) {
        for (TurnLine l : a.lines) {
            if ("THINK".equals(l.cue) && l.text != null) {
                return l.text;
            }
        }
        return null;
    }

    @Test
    public void dictionaryIsLarge() throws Exception {
        KnowledgePack p = withDictionary();
        assertTrue(p.wordCount() > 20000);
        assertNotNull(p.word("beautiful"));
    }

    @Test
    public void definesWordsWithEndingsAndSynonyms() {
        BrainAnswer a = brain.respond("what does beautiful mean", "ivie");
        assertTrue(a.spoken().startsWith("Beautiful, adjective, means"));
        assertEquals("Let me check my dictionary.", thought(a));
        assertTrue(brain.respond("define running", "obi").spoken().startsWith("Run"));
        assertTrue(brain.respond("another word for quickly", "obi").spoken().contains("rapidly"));
    }

    @Test
    public void thinksAloudAndCanStop() {
        BrainAnswer a = brain.respond("what is the capital of Nigeria", "obi");
        assertEquals("Let me look through what I know.", thought(a));
        assertNotNull(thought(brain.respond("what is 6 times 7", "obi")));
        assertEquals("THOUGHTS_OFF", brain.respond("stop thinking aloud", "obi").command);
        assertEquals(null, thought(brain.respond("what is 6 times 7", "obi")));
    }

    @Test
    public void learnsGeneralFactsAndAnswersToUnknownQuestions() throws Exception {
        assertTrue(brain.respond("remember that the speed limit in town is 50 km per hour", "obi").spoken().startsWith("Noted"));
        BrainAnswer r = brain.respond("what is the speed limit in town", "obi");
        assertEquals("50 km per hour.", r.spoken());
        assertEquals("I remember you taught me this.", thought(r));

        BrainAnswer unknown = brain.respond("who won the 2026 town football cup", "obi");
        assertTrue(unknown.needsOnline);
        brain.respond("the answer is Sapele United", "obi");
        OfflineBrain again = new OfflineBrain(withDictionary(), new LearningJournal(dir), () -> 1L, TimeZone.getTimeZone("UTC"));
        assertEquals("Sapele United.", again.respond("who won the 2026 town football cup", "obi").spoken());
    }

    @Test
    public void correctionsOverrideThePack() {
        assertTrue(brain.respond("what is the largest ocean", "obi").spoken().contains("Pacific"));
        brain.respond("no, it's the Pacific Ocean, covering about a third of the planet", "obi");
        assertTrue(brain.respond("what is the largest ocean", "obi").spoken().contains("third of the planet"));
    }

    @Test
    public void nearestMeaningPivot() {
        // "biggest" is not a key; the dictionary links it to "largest"
        BrainAnswer a = brain.respond("which is the biggest planet", "obi");
        assertTrue(a.spoken(), a.spoken().contains("Jupiter"));
    }
}
