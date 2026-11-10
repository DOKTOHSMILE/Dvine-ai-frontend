package ai.ibiko.dvine1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import org.junit.Test;

/** Night learning: the window, tonight's topics, the day's feed, and answers learned but not yet checked. */
public class LearningTest {
    static final TimeZone LAGOS = TimeZone.getTimeZone("Africa/Lagos");

    static long at(int h, int m) {
        Calendar c = Calendar.getInstance(LAGOS);
        c.clear();
        c.set(2026, Calendar.NOVEMBER, 9, h, m, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void theWindowCrossesMidnight() {
        LearningSchedule.Plan p = new LearningSchedule.Plan(true, 23 * 60, 6 * 60, true, true, 40);
        assertTrue(p.inWindow(at(23, 30), LAGOS));
        assertTrue(p.inWindow(at(2, 0), LAGOS));
        assertFalse(p.inWindow(at(6, 0), LAGOS));
        assertFalse(p.inWindow(at(14, 0), LAGOS));
        assertEquals(9 * 3_600_000L, p.untilStart(at(14, 0), LAGOS));
        assertEquals(0, p.untilStart(at(1, 0), LAGOS));
        assertEquals(5 * 3_600_000L, p.untilEnd(at(1, 0), LAGOS));
        assertEquals("23:00 to 06:00, on Wi-Fi while charging", p.words());
        LearningSchedule.Plan back = LearningSchedule.Plan.read(p.write());
        assertEquals(p.write(), back.write());
        assertFalse("off unless the pilot turns it on", LearningSchedule.Plan.defaults().on);
    }

    @Test
    public void tonightLearnsOnlyFromUsage() {
        List<String> t = LearningSchedule.topics(Arrays.asList("who invented the radio", "What is a quasar?", "What is Lagos?"),
                Arrays.asList("Afrobeats", "photosynthesis", "Afrobeats"), topic -> topic.equals("What is Lagos?"), 4);
        assertEquals(Arrays.asList("who invented the radio", "What is a quasar?", "Afrobeats", "photosynthesis"), t);
        assertEquals("What is Afrobeats?", LearningSchedule.question("Afrobeats"));
        assertEquals("who invented the radio", LearningSchedule.question("who invented the radio"));
    }

    @Test
    public void whatItLearnedAtNightIsCheckedBeforeItIsSure() throws Exception {
        File f = new File(Files.createTempDirectory("nl").toFile(), "knowledge.tsv");
        Knowledge k = new Knowledge(f);
        assertTrue(k.learnUnchecked("What is a quasar?", "A quasar is an extremely luminous active galactic nucleus."));
        Knowledge.Fact got = new Knowledge(f).recall("what's a quasar");
        assertNotNull(got);
        assertFalse("learned at night: not sure until checked", got.sure);
        assertEquals("not in the confirmed list", 0, k.confirmed().size());
        k.confirm("What is a quasar?", got.answer);
        assertTrue(new Knowledge(f).recall("quasar?").sure);
        assertFalse("a confirmed answer isn't replaced by the night", k.learnUnchecked("What is a quasar?", "Something else."));
        k.reject("What is a pulsar?", "A pulsar is a kind of quasar.");
        assertFalse("nor brought back once said to be wrong", k.learnUnchecked("What is a pulsar?", "A pulsar is a kind of quasar."));
    }

    @Test
    public void wordsItDoesntKnowAreLearnedFromTheQuestions() {
        Lexicon l = new Lexicon(null);
        l.learn("photosynthesis", "{\"en\":[{\"partOfSpeech\":\"Noun\",\"definitions\":[{\"definition\":\"How plants make food.\"}]}]}");
        assertEquals(Arrays.asList("ephemeral", "serendipity"), LearningJob.unknownWords(
                Arrays.asList("What does ephemeral mean?", "Is serendipity about photosynthesis?"), l, 10));
    }
}
