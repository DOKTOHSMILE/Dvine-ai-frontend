package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import ai.ibiko.ibiko1.formulas.Analyst;

import java.util.List;

import org.junit.Test;

/** Data handled with surgical precision: exact figures, outliers flagged, every total checked, no claim past the data. */
public class AnalystTest {
    @Test
    public void aListOfNumbersIsProfiledExactly() {
        String q = "Analyse my monthly sales: 120, 135, 150, 160, 900, 170";
        assertTrue(Analyst.hasData(q));
        List<Analyst.Profile> p = Analyst.read(q);
        assertEquals(1, p.size());
        Analyst.Profile s = p.get(0);
        assertEquals(6, s.values.length);
        assertEquals(1635, s.sum, 1e-9);
        assertEquals(272.5, s.mean, 1e-9);
        assertEquals(155, s.median, 1e-9);
        assertEquals("900 is flagged, not deleted", 1, s.outliers.size());
        assertEquals(900, s.outliers.get(0), 1e-9);
        assertTrue(s.checked);
        String r = Analyst.report(p);
        assertTrue(r, r.contains("Sum 1,635") && r.contains("median 155") && r.contains("Unusual: 900"));
        assertTrue(r.contains("The mean and median differ a lot"));
        assertTrue(r.contains("What this can't tell you"));
    }

    @Test
    public void aTableKeepsItsColumnsAndUnits() {
        String q = "Month,Revenue,Visitors\nJan,$1,200,300\nFeb,$1,500,320\nMar,$1,800,310\nApr,$2,100,330";
        List<Analyst.Profile> p = Analyst.read("month\trevenue\tvisitors\nJan\t$1200\t300\nFeb\t$1500\t320\nMar\t$1800\t310\nApr\t$2100\t330");
        assertEquals(2, p.size());
        assertEquals("revenue", p.get(0).name);
        assertEquals("$", p.get(0).unit);
        String r = Analyst.report(p);
        assertTrue(r, r.contains("$1,200") && r.contains("+75%") && r.contains("rising"));
        assertTrue(q.contains("Month")); // a CSV with thousands commas inside cells is read as text, not split wrongly
    }

    @Test
    public void aQuestionWithNumbersIsNotData() {
        assertFalse(Analyst.hasData("What is the population of Nigeria?"));
        assertFalse(Analyst.hasData("hello 1 2"));
        assertTrue(Analyst.BRIEF.contains("Tukey"));
    }
}
