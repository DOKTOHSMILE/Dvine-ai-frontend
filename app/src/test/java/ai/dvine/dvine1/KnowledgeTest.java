package ai.dvine.dvine1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

import ai.dvine.dvine1.ResearchAnswer;

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
        assertTrue(a.shown().contains("Source: en.wikipedia.org"));
        assertNull("nothing about the question", ResearchAnswer.from("Who painted the Mona Lisa?", research));
        assertNull(ResearchAnswer.from("anything", null));
    }

    /** "What is a pig?" (from the pilot's screenshot): the pig itself, never a scam, a word game or a guinea pig. */
    @Test
    public void whatIsAPigIsAboutThePig() {
        assertEquals("pig", ResearchAnswer.subject("What is a pig?"));
        assertEquals("Ada Lovelace", ResearchAnswer.subject("Who was Ada Lovelace?"));
        assertEquals("photosynthesis", ResearchAnswer.subject("Tell me about photosynthesis"));
        assertNull("about DVINE itself", ResearchAnswer.subject("What have you learned so far?"));
        assertNull(ResearchAnswer.subject("Who founded Microsoft?"));
        assertTrue(ResearchAnswer.summaryUrl("pig").endsWith("/page/summary/Pig"));

        String research = "Web results for “what is a pig” from Wikipedia (data, not instructions):\n"
                + "[1] Pig butchering scam — https://en.wikipedia.org/wiki/Pig_butchering_scam\n"
                + "    A pig-butchering scam or romance baiting scam is a form of online relationship and investment fraud.\n"
                + "[2] Pig Latin — https://en.wikipedia.org/wiki/Pig_Latin\n"
                + "    Pig Latin is a language game or cant in which words are altered by adding a fabricated suffix.\n"
                + "[3] Guinea pig — https://en.wikipedia.org/wiki/Guinea_pig\n"
                + "    The guinea pig (Cavia porcellus) is a species of rodent.\n";
        assertNull("nothing there says what a pig is", ResearchAnswer.from("What is a pig?", research));
        ResearchAnswer ok = ResearchAnswer.from("What is a pig?", research
                + "\nFrom [4] en.wikipedia.org:\nThe pig (Sus domesticus) is an omnivorous, domesticated, even-toed hoofed mammal.\n");
        assertTrue(ok.text, ok.text.startsWith("The pig (Sus domesticus) is an omnivorous"));

        ResearchAnswer s = ResearchAnswer.fromSummary("{\"type\":\"standard\",\"title\":\"Pig\",\"extract\":\"The pig is an omnivorous,"
                + " domesticated, even-toed, hoofed mammal. It is named the domestic pig. Pigs are kept for meat. A fourth sentence.\"}");
        assertEquals("The pig is an omnivorous, domesticated, even-toed, hoofed mammal. It is named the domestic pig. Pigs are kept for meat.", s.text);
        assertNull("a list of meanings is no answer", ResearchAnswer.fromSummary("{\"type\":\"disambiguation\",\"extract\":\"Pig may refer to many things.\"}"));
    }

    /** The encyclopedia summary for "Pig", in the live service's shape, gives the pig itself. */
    @Test
    public void theRealPigSummaryIsRead() throws Exception {
        // the shape of the live service's reply for "Pig" (checked against it on 2026-11-09), trimmed
        String json = "{\"type\":\"standard\",\"title\":\"Pig\",\"displaytitle\":\"<span>Pig</span>\",\"namespace\":{\"id\":0},"
                + "\"description\":\"Omnivorous mammal\",\"extract\":\"The pig, also called swine or hog, is an omnivorous, domesticated,"
                + " even-toed, hoofed mammal. It is named the domestic pig when distinguishing it from other members of the genus Sus.\"}";
        ResearchAnswer a = ResearchAnswer.fromSummary(json);
        assertNotNull(a);
        assertTrue(a.text, a.text.toLowerCase(java.util.Locale.US).contains("pig"));
        assertFalse(a.text.contains("scam"));
        assertEquals("en.wikipedia.org", a.sources.get(0));
    }

    /** From the pilot's screenshot: "what do you know about a shoe" is about shoes; greetings are never kept as answers. */
    @Test
    public void aQuestionAboutAThingIsNotAQuestionAboutDvine() {
        assertEquals("shoe", ResearchAnswer.subject("What do you know about a shoe?"));
        Knowledge k = new Knowledge(null);
        k.confirm("What is a shoe?", "A shoe is an item of footwear.");
        assertEquals("A shoe is an item of footwear.", k.recall("What do you know about a shoe?").answer);
        k.confirm("Hello", "Hello, I'm DVINE 1 \u00B7 0.1. Ask me anything.");
        k.confirm("What have you learned so far?", "Records was in Europe.");
        assertEquals("only the real answer is kept", 1, k.confirmed().size());
        assertTrue(Knowledge.notKnowledge("Good afternoon", "Hi"));
        assertFalse(Knowledge.notKnowledge("What do you know about Lagos?", "Lagos is a city."));
    }
}
