package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** Asking back: the first search, the person's explanation, the two compared, "I may be wrong", and a correction. */
public class InquiryTest {
    private static Inquiry.Pending pending(String q, String subject, ResearchAnswer first) {
        return new Inquiry.Pending(q, subject, first, 0);
    }

    @Test
    public void itAsksWhatThePersonMeansAndCanBeSkipped() {
        String asked = Inquiry.askBack("What is a zorblat?", "zorblat", true);
        assertTrue(asked, asked.contains("what do you mean by “zorblat”"));
        assertTrue(Inquiry.askBack("What is a zorblat?", "zorblat", false).contains("didn't find a clear answer"));
        assertTrue(Inquiry.dropsIt("never mind"));
        assertTrue(Inquiry.dropsIt("Just answer"));
        assertFalse(Inquiry.dropsIt("a dance from Lagos"));
        assertTrue(pending("q", null, null).expired(Inquiry.WAIT_MS + 1));
        assertFalse(pending("q", null, null).expired(Inquiry.WAIT_MS));
    }

    @Test
    public void theExplanationIsSearchedWithTheSubject() {
        Inquiry.Pending p = pending("What is a zorblat?", "zorblat", null);
        assertEquals("zorblat a dance from Lagos", Inquiry.explanationQuery(p, "I mean a dance from Lagos"));
        assertEquals("the zorblat dance", Inquiry.explanationQuery(p, "the zorblat dance"));
    }

    @Test
    public void twoSearchesThatAgreeGiveOneAnswerAndInviteACorrection() {
        ResearchAnswer a = ResearchAnswer.of("The zorblat is a dance from Lagos, danced at weddings.", "en.wikipedia.org");
        ResearchAnswer b = ResearchAnswer.of("A zorblat is a wedding dance from Lagos.", "example.org");
        Inquiry.Outcome o = Inquiry.compare("What is a zorblat?", "a dance from Lagos", a, b);
        assertTrue(o.agree);
        assertTrue(o.text, o.text.startsWith("The zorblat is a dance from Lagos"));
        assertEquals(Arrays.asList("en.wikipedia.org", "example.org"), o.sources);
        assertTrue(o.shown().endsWith(Inquiry.MAY_BE_WRONG));
    }

    @Test
    public void whenTheyDifferThePersonsOwnWordsDecide() {
        ResearchAnswer a = ResearchAnswer.of("Zorblat is a programming library for parsing spreadsheets.", "code.example");
        ResearchAnswer b = ResearchAnswer.of("The zorblat is a dance from Lagos performed at weddings.", "dance.example");
        Inquiry.Outcome o = Inquiry.compare("What is a zorblat?", "a dance people do at weddings in Lagos", a, b);
        assertFalse(o.agree);
        assertTrue(o.text, o.text.startsWith("The zorblat is a dance from Lagos"));
        assertTrue(o.text, o.text.contains("If you meant the other: Zorblat is a programming library"));
    }

    @Test
    public void oneSearchIsSaidToRestOnOneAndNoneIsNoAnswer() {
        ResearchAnswer b = ResearchAnswer.of("A zorblat is a wedding dance.", "example.org");
        Inquiry.Outcome o = Inquiry.compare("What is a zorblat?", "a dance", null, b);
        assertTrue(o.text, o.text.contains("Only the search on what you explained found something"));
        assertNull(Inquiry.compare("What is a zorblat?", "a dance", null, null));
    }

    @Test
    public void aCorrectionIsReadAndItsAnswerKept() {
        assertTrue(Inquiry.corrects("No, it's a dance from Lagos."));
        assertTrue(Inquiry.corrects("Actually the zorblat is a song"));
        assertFalse(Inquiry.corrects("no"));
        assertFalse(Inquiry.corrects("what about Abuja?"));
        assertEquals("A dance from Lagos.", Inquiry.correction("No, it's a dance from Lagos."));
        assertEquals("The zorblat is a song", Inquiry.correction("Actually, the zorblat is a song"));
    }

    @Test
    public void newKnowledgeIsMatchedByWordIdsWithWhatItKnows() {
        List<Knowledge.Fact> known = new ArrayList<Knowledge.Fact>();
        known.add(fact("What is Lagos?", "Lagos is the largest city in Nigeria."));
        known.add(fact("What is a wedding?", "A wedding is a ceremony where two people marry."));
        known.add(fact("What is Python?", "Python is a programming language."));
        List<Knowledge.Fact> rel = Inquiry.related("What is a zorblat?", "A zorblat is a wedding dance from Lagos.", known, 2);
        List<String> qs = new ArrayList<String>();
        for (Knowledge.Fact f : rel) {
            qs.add(f.question);
        }
        assertTrue(qs.toString(), qs.contains("What is Lagos?"));
        assertTrue(qs.toString(), qs.contains("What is a wedding?"));
        assertFalse(qs.contains("What is Python?"));
        assertTrue(Inquiry.tiesIn(rel).startsWith("\nThis ties in with what I already know:"));
        assertEquals("", Inquiry.tiesIn(new ArrayList<Knowledge.Fact>()));
        assertEquals("the same words, the same ids", Inquiry.ids("Lagos weddings"), Inquiry.ids("lagos wedding"));
    }

    private static Knowledge.Fact fact(String q, String a) {
        return new Knowledge.Fact(q, a, Knowledge.kind(q), Knowledge.meaning(q), true);
    }

    @Test
    public void aReplyThatContradictsWhatThePilotConfirmedIsCorrected() throws Exception {
        java.io.File f = java.nio.file.Files.createTempFile("knowledge", ".tsv").toFile();
        Knowledge k = new Knowledge(f);
        k.confirm("What is a zorblat?", "A wedding dance from Ibadan.");
        ai.ibiko.ibiko1.formulas.Truth.Checked wrong = k.check("A zorblat is a dance from Lagos.");
        assertEquals(ai.ibiko.ibiko1.formulas.Truth.Verdict.FALSE, wrong.verdict);
        assertEquals("You told me zorblat is: A wedding dance from Ibadan.", wrong.correction);
        assertEquals(ai.ibiko.ibiko1.formulas.Truth.Verdict.TRUE, k.check("The zorblat is a wedding dance from Ibadan.").verdict);
        assertEquals("not about it: not judged", ai.ibiko.ibiko1.formulas.Truth.Verdict.UNKNOWN, k.check("Lagos is a big city.").verdict);
        // through the whole check every reply gets: the pilot's word comes first and the correction is said
        java.util.List<ai.ibiko.ibiko1.formulas.Truth.Witness> w = new java.util.ArrayList<ai.ibiko.ibiko1.formulas.Truth.Witness>();
        w.add(k::check);
        String said = ai.ibiko.ibiko1.formulas.Truth.correction(ai.ibiko.ibiko1.formulas.Truth.question(
                "Sure. A zorblat is a dance from Lagos. People love it.", w));
        assertTrue(said, said != null && said.contains("You told me zorblat is: A wedding dance from Ibadan."));
    }
}
