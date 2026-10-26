package ai.dvine.brain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CalculatorTest {
    private static String calc(String spoken) {
        return Calculator.format(Calculator.eval(Calculator.normalize(spoken)));
    }

    @Test
    public void spokenMaths() {
        assertEquals("84", calc("what is 12 times 7"));
        assertEquals("14", calc("2 + 3 * 4"));
        assertEquals("20", calc("(2 + 3) * 4"));
        assertEquals("9", calc("square root of 81"));
        assertEquals("30", calc("15 percent of 200"));
        assertEquals("1024", calc("2 to the power of 10"));
        assertEquals("2.5", calc("5 divided by 2"));
        assertEquals("49", calc("7 squared"));
        assertEquals("-3", calc("2 minus 5"));
        assertEquals("21", calc("3 x 7"));
    }

    @Test
    public void noMathsMeansNull() {
        assertNull(Calculator.normalize("who are you"));
        assertNull(Calculator.normalize("i have 3 kids"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void divideByZero() {
        Calculator.eval("4/0");
    }
}
