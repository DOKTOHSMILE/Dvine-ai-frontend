package ai.ibiko.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** The fact check: claims out of the answer, each judged against what the web says, a correction only when wrong. */
public class FactCheckTest {
    @Test
    public void aWrongClaimGivesACorrectionWithItsSource() {
        List<String> replies = new ArrayList<String>(Arrays.asList(
                "CLAIM: Lagos has about 16 million people.\nCLAIM: Abuja became Nigeria's capital in 1976.",
                "VERDICT: SUPPORTED\nSOURCE: Wikipedia\nFIX: none",
                "VERDICT: CONTRADICTED\nSOURCE: Britannica\nFIX: Abuja became Nigeria's capital in 1991."));
        List<String> searched = new ArrayList<String>();
        FactCheck.Result r = FactCheck.run((sys, p) -> replies.remove(0), q -> {
            searched.add(q);
            return "evidence about " + q;
        }, "What is the population of Lagos and when did Abuja become the capital?",
                "Lagos has about 16 million people, and Abuja became Nigeria's capital in 1976.", () -> false);
        assertEquals(Arrays.asList("Lagos has about 16 million people.", "Abuja became Nigeria's capital in 1976."), searched);
        assertEquals(1, r.count(FactCheck.Verdict.SUPPORTED));
        assertEquals(1, r.count(FactCheck.Verdict.CONTRADICTED));
        assertEquals("Correction after checking the web:\n• Abuja became Nigeria's capital in 1991. (Britannica)", r.correction());
        // all supported, or nothing found: no correction
        List<String> fine = new ArrayList<String>(Arrays.asList("CLAIM: Paris is the capital of France.", "VERDICT: SUPPORTED\nSOURCE: Britannica"));
        assertNull(FactCheck.run((s, p) -> fine.remove(0), q -> "Paris is France's capital", "What is the capital of France?",
                "The capital of France is Paris, on the Seine.", () -> false).correction());
        assertNull(FactCheck.run((s, p) -> "CLAIM: A thing about 1990.", q -> null, "When was it?", "It was in 1990, said John Smith.",
                () -> false).correction()); // nothing found: unclear, not wrong
    }

    @Test
    public void onlyFactualAnswersAreChecked() {
        assertTrue(FactCheck.worth("What is the population of Lagos?", "Lagos has about 16 million people in its metro area."));
        assertTrue(FactCheck.worth("Who founded Microsoft?", "Microsoft was founded by Bill Gates and Paul Allen in 1975."));
        assertFalse(FactCheck.worth("Write me a poem about Lagos", "Lagos, city of 16 million dreams, where the lagoon shines."));
        assertFalse(FactCheck.worth("Should I learn Java?", "Yes, Java is used by millions of developers since 1995."));
        assertFalse(FactCheck.worth("hello", "Hello! How can I help you today, my friend?"));
    }
}
