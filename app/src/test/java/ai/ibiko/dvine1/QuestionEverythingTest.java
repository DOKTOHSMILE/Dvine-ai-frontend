package ai.ibiko.dvine1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import ai.ibiko.dvine1.formulas.Truth;
import ai.ibiko.dvine1.formulas.Truth.Verdict;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** G8: every piece of data, in and out, asked "is this true or false?". */
public class QuestionEverythingTest {
    private static BuiltIn world() throws Exception {
        return new BuiltIn(new FileInputStream("../ibiko1/src/main/assets/knowledge/world_facts.tsv"));
    }

    private static Verdict v(BuiltIn b, String s) {
        return b.check(s).verdict;
    }

    @Test
    public void eachSentenceIsJudgedAgainstTheBuiltInReference() throws Exception {
        BuiltIn b = world();
        assertEquals(Verdict.TRUE, v(b, "The capital of Nigeria is Abuja."));
        assertEquals(Verdict.FALSE, v(b, "Lagos is the capital of Nigeria."));
        assertEquals(Verdict.FALSE, v(b, "Nigeria's capital is Lagos."));
        assertEquals(Verdict.FALSE, v(b, "The capital of Ghana is Lagos."));
        assertEquals(Verdict.TRUE, v(b, "Gold has the chemical symbol Au."));
        assertEquals(Verdict.FALSE, v(b, "The chemical symbol of gold is Ag."));
        assertEquals(Verdict.FALSE, v(b, "The atomic number of gold is 80."));
        assertEquals(Verdict.TRUE, v(b, "Nigeria is in Africa."));
        assertEquals(Verdict.FALSE, v(b, "Nigeria is in Asia."));
        assertEquals("The capital of Nigeria is Abuja.", b.check("Lagos is the capital of Nigeria.").correction);
    }

    @Test
    public void whatIsNotAPlainClaimIsNotJudged() throws Exception {
        BuiltIn b = world();
        assertEquals(Verdict.UNKNOWN, v(b, "Lagos was the capital of Nigeria until 1991."));
        assertEquals(Verdict.UNKNOWN, v(b, "Lagos is not the capital of Nigeria."));
        assertEquals(Verdict.UNKNOWN, v(b, "Is Lagos the capital of Nigeria?"));
        assertEquals(Verdict.UNKNOWN, v(b, "Lagos is the biggest city in Nigeria."));
        assertEquals(Verdict.UNKNOWN, v(b, "Accra is the capital of Ghana, next to Togo.")); // two countries: not judged
        assertEquals(Verdict.UNKNOWN, v(b, "It is the capital of Nigeria."));
    }

    @Test
    public void aReplyIsQuestionedAndCorrectedAndWhatIsLearnedIsOnlyTheTrue() throws Exception {
        BuiltIn b = world();
        List<Truth.Witness> w = Arrays.asList(b::check);
        String reply = "Nigeria is a big country. Lagos is the capital of Nigeria. Its currency is the naira.";
        String fix = Truth.correction(Truth.question(reply, w));
        assertTrue(fix, fix.startsWith("A correction") && fix.contains("The capital of Nigeria is Abuja.") && fix.contains("built-in reference"));
        assertNull(Truth.correction(Truth.question("The capital of Nigeria is Abuja.", w)));
        String kept = Truth.trueOnly(reply, w);
        assertTrue(kept, kept.contains("big country") && !kept.contains("Lagos"));
    }

    @Test
    public void factsHeldFirmlyJudgeToo() {
        Facts f = new Facts(null);
        f.learn("The telephone was invented by Alexander Graham Bell.", null, 3, 1);
        assertEquals(Verdict.FALSE, f.check("The telephone was invented by Edison.").verdict);
        assertEquals(Verdict.TRUE, f.check("The telephone was invented by Alexander Graham Bell.").verdict);
        Facts weak = new Facts(null);
        weak.learn("The telephone was invented by Alexander Graham Bell.", null, 1, 1);
        assertEquals("a weak fact judges nothing", Verdict.UNKNOWN, weak.check("The telephone was invented by Edison.").verdict);
    }
}
