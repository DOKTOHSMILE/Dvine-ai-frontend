package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class MethodsTest {
    private static final List<String> RESEARCH = Arrays.asList("Thinking", "Searching the web for “fuel price”",
            "Fetched https://example.org/a", "Fetched https://example.org/b", "Replying");
    private static final List<String> CODE = Arrays.asList("Read app/Main.java", "Read app/Save.java", "Edited app/Save.java",
            "Ran the tests: gradle test");

    @Test
    public void theStepsShownBecomeAMethod() {
        assertEquals(Arrays.asList(Methods.Step.SEARCH, Methods.Step.READ_PAGE, Methods.Step.ANSWER), Methods.steps(RESEARCH));
        assertEquals(Arrays.asList(Methods.Step.READ_FILES, Methods.Step.EDIT, Methods.Step.TEST, Methods.Step.ANSWER), Methods.steps(CODE));
        assertTrue("a bare answer has no method", Methods.steps(Arrays.asList("Thinking", "Replying")).isEmpty());
    }

    @Test
    public void oneWayServesEveryRequestOfItsKind() {
        assertEquals(Methods.kind("How much is fuel in Lagos today?"), Methods.kind("Obi, how much is rice in Abuja?"));
        assertTrue(!Methods.kind("Fix the login crash").equals(Methods.kind("Who is the president of Ghana?")));
    }

    @Test
    public void learnedWithoutATapStrongerWhenSeenAgainAndUsedAsAHint() throws Exception {
        File f = new File(Files.createTempDirectory("methods").toFile(), "methods.tsv");
        Methods m = new Methods(f);
        assertNull(m.recall("How much is fuel in Lagos?"));
        m.observe("How much is fuel in Lagos?", Methods.steps(RESEARCH), 1);
        assertNull("seen once, never judged: not trusted yet", m.recall("How much is rice in Abuja?"));
        m.observe("How much is bread in Kano?", Methods.steps(RESEARCH), 2);
        Methods.Way w = m.recall("How much is rice in Abuja?");
        assertNotNull("seen twice: learned with no tap", w);
        assertEquals("search the web, read the best pages, then write the answer", w.said());
        assertTrue(m.hint("How much is a bag of cement?").contains("search the web"));
        assertEquals("kept on the device", 1, new Methods(f).trusted());
    }

    @Test
    public void theCheckStrengthensACrossWeakensAndABadWayIsDropped() throws Exception {
        Methods m = new Methods(new File(Files.createTempDirectory("methods").toFile(), "methods.tsv"));
        m.observe("Fix the login crash", Methods.steps(CODE), 1);
        m.judge("Fix the save bug", true);
        assertNotNull("marked right once: trusted", m.recall("Fix the upload error"));
        m.judge("Fix the save bug", false);
        m.judge("Fix the save bug", false);
        assertNull("it kept failing: no longer offered", m.recall("Fix the upload error"));
        m.judge("Fix the save bug", false);
        assertEquals("and then forgotten", 0, m.size());
    }
}
