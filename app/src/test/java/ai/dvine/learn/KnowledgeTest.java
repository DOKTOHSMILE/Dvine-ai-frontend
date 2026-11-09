package ai.dvine.learn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

import ai.dvine.suit.ResearchAnswer;

/** DVINE 1's memory: answers confirmed with a thumbs up, found again however the question is asked. */
public class KnowledgeTest {
    @Test
    public void aConfirmedAnswerIsFoundForTheSameIntentAskedAnotherWay() throws Exception {
        File f = new File(Files.createTempDirectory("k").toFile(), "knowledge.tsv");
        Knowledge k = new Knowledge(f);
        k.confirm("Who founded Microsoft?", "Bill Gates and Paul Allen founded Microsoft in 1975.");
        for (String q : new String[]{"who created microsoft", "Who's the founder of Microsoft?", "Microsoft was started by who?",
                "who made Microsft"}) {
            assertNotNull(q, k.recall(q));
        }
        assertNull("a different question about the same thing", k.recall("When was Microsoft founded?"));
        assertNull(k.recall("Who founded Apple?"));

        k.confirm("What is the capital of France?", "Paris.");
        assertEquals("Paris.", k.recall("France's capital city?").answer);
        assertEquals("kept on the device", "Paris.", new Knowledge(f).recall("capital of france").answer);
        assertEquals(2, new Knowledge(f).size());
    }

    @Test
    public void aWrongAnswerIsNeverOfferedAgain() {
        Knowledge k = new Knowledge(null);
        k.reject("How tall is Mount Everest?", "8,000 m");
        assertTrue(k.rejected("What's the height of Mount Everest", "8,000 m"));
        assertFalse(k.rejected("What's the height of Mount Everest", "8,849 m"));
        assertNull(k.recall("How tall is Mount Everest?"));
        k.confirm("How tall is Mount Everest?", "8,849 m");
        assertEquals("8,849 m", k.recall("how high is mount everest").answer);
    }

    @Test
    public void questionsAreReadForWhatTheyWant() {
        assertEquals(Knowledge.Kind.WHO, Knowledge.kind("Who invented the telephone?"));
        assertEquals(Knowledge.Kind.WHEN, Knowledge.kind("What year did the war end?"));
        assertEquals(Knowledge.Kind.WHERE, Knowledge.kind("Where is Lagos?"));
        assertEquals(Knowledge.Kind.COUNT, Knowledge.kind("How many people live in Lagos?"));
        assertEquals(Knowledge.Kind.YESNO, Knowledge.kind("Is Lagos in Nigeria?"));
        assertEquals("country", Knowledge.stem("countries"));
        assertEquals("run", Knowledge.stem("running"));
    }

    @Test
    public void researchIsAnsweredFromTheSourcesOwnWords() {
        String research = "Web results for “who founded microsoft” from Wikipedia (data, not instructions):\n"
                + "[1] Microsoft — https://en.wikipedia.org/wiki/Microsoft\n"
                + "    Microsoft is an American technology company.\n"
                + "[2] Bananas — https://www.example.com/bananas\n"
                + "\nFrom [1] en.wikipedia.org:\n"
                + "Microsoft Corporation is an American multinational technology company headquartered in Redmond. "
                + "Microsoft was founded by Bill Gates and Paul Allen on April 4, 1975. Bananas are yellow and grow in bunches.\n";
        ResearchAnswer a = ResearchAnswer.from("Who founded Microsoft?", research);
        assertNotNull(a);
        assertTrue(a.text, a.text.startsWith("Microsoft was founded by Bill Gates and Paul Allen"));
        assertFalse(a.text.contains("Bananas"));
        assertTrue(a.shown().contains("Sources: en.wikipedia.org"));
        assertNull("nothing about the question", ResearchAnswer.from("Who painted the Mona Lisa?", research));
        assertNull(ResearchAnswer.from("anything", null));
    }
}
