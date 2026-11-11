package ai.ibiko.ibiko1.formulas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Thinking in probabilities: base rates moved by evidence, expected value, chains, calibrated words. */
public class ProbabilityTest {
    @Test
    public void evidenceMovesTheOddsFromTheBaseRate() {
        // a 1% base rate; a test 90% sensitive, 9% false positives: a positive is ratio 10, so only about 9%
        assertEquals(0.0917, Probability.update(0.01, 0.9 / 0.09), 0.001);
        assertEquals(0.5, Probability.update(0.5, 1.0), 1e-9); // a clue equally likely either way changes nothing
        assertEquals(Probability.update(Probability.update(0.2, 3), 2), Probability.combine(0.2, 3, 2), 1e-9);
        assertEquals(3.0, Probability.odds(0.75), 1e-9);
    }

    @Test
    public void choicesByExpectedValueAndCostOfBeingWrong() {
        assertEquals(30.0, Probability.expected(new double[]{0.6, 0.4}, new double[]{100, -75}), 1e-9);
        assertTrue(Probability.actNow(0.9, 10, 10));
        assertFalse("costly to undo: wait and check", Probability.actNow(0.9, 10, 200));
        assertEquals(0.729, Probability.all(0.9, 0.9, 0.9), 1e-9); // P9: a chain is weaker than its steps
        assertEquals(0.75, Probability.any(0.5, 0.5), 1e-9);
    }

    @Test
    public void wordsMatchTheChanceAndTheFormulaReachesDecisions() {
        assertEquals("almost certainly", Probability.words(0.97));
        assertEquals("likely", Probability.words(0.7));
        assertEquals("a toss-up", Probability.words(0.5));
        assertEquals("unlikely", Probability.words(0.2));
        assertTrue(Probability.applies("Should I use Postgres or SQLite?"));
        assertTrue(Probability.applies("what are the chances it rains tomorrow"));
        assertFalse(Probability.applies("write a poem about the sea"));
    }
}
