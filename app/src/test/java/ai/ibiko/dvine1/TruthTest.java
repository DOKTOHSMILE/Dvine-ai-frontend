package ai.ibiko.dvine1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import ai.ibiko.dvine1.formulas.Truth;
import ai.ibiko.dvine1.formulas.Truth.Claim;
import ai.ibiko.dvine1.formulas.Truth.Source;
import ai.ibiko.dvine1.formulas.Truth.Sure;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** The foundation: what stands is what the most trusted source says; agreement makes it firm; the unsure is said so. */
public class TruthTest {
    @Test
    public void theMoreTrustedSourceStands() {
        List<Truth.Settled> s = Truth.settle(Arrays.asList(
                new Claim("capital:nigeria", "Lagos", Source.MODEL, 1),
                new Claim("capital:nigeria", "Abuja", Source.BUILT_IN, 1)));
        assertEquals("Abuja", s.get(0).stands.says);
        assertEquals("Lagos", s.get(0).overruled.get(0).says);
        assertEquals(Sure.LIKELY, s.get(0).sure);
    }

    @Test
    public void twoDifferentSourcesAgreeingMakeItFirm() {
        List<Truth.Settled> s = Truth.settle(Arrays.asList(
                new Claim("x", "Abuja", Source.BUILT_IN, 1), new Claim("x", "abuja.", Source.LEARNED, 2)));
        assertEquals(Sure.FIRM, s.get(0).sure);
    }

    @Test
    public void thePilotsWordAndWhatTheyConfirmedAreFirmAndTheUncheckedIsNot() {
        assertEquals(Sure.FIRM, Truth.settle(Arrays.asList(new Claim("me", "I live in Benin City", Source.PILOT, 1))).get(0).sure);
        assertEquals(Sure.FIRM, Truth.settle(Arrays.asList(new Claim("q", "42", Source.CONFIRMED, 1))).get(0).sure);
        assertEquals(Sure.UNSURE, Truth.settle(Arrays.asList(new Claim("q", "42", Source.LEARNED, 1))).get(0).sure);
    }

    @Test
    public void atEqualRankTheNewerStandsAndTheDisagreementIsSaid() {
        Truth.Settled s = Truth.settle(Arrays.asList(
                new Claim("price", "1,500 naira", Source.SOURCE, 10), new Claim("price", "1,800 naira", Source.SOURCE, 20))).get(0);
        assertEquals("1,800 naira", s.stands.says);
        assertTrue(s.differ);
    }

    @Test
    public void theBriefGivesTheRulesAndWhatIsKnownInOrder() {
        String b = Truth.brief(Arrays.asList(new Claim("c", "Lagos", Source.MODEL, 1), new Claim("c", "Abuja", Source.BUILT_IN, 1)));
        assertTrue(b, b.contains("Base every answer on truth") && b.contains("[built-in reference, likely] Abuja")
                && b.contains("overruled: from memory, unverified said “Lagos”"));
        String none = Truth.brief(Arrays.asList());
        assertTrue(none.contains("never invent"));
        assertFalse(none.contains("What is already known"));
    }
}
