package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Ibiko 1 greets like a person: the time of day, the name it knows, and not the same words every time. */
public class GreetingTest {
    @Test
    public void warmByNameAndTimeOfDay() {
        String first = Ibiko1.greeting("Doktohsmile", 19, 0);
        assertTrue(first, first.startsWith("Good evening Doktohsmile, it's Ibiko here with you."));
        assertTrue(first.contains("this evening"));
        assertTrue(Ibiko1.greeting(null, 9, 0).startsWith("Good morning, it's Ibiko"));
        assertFalse("the robotic version line is gone", first.contains("0.1"));
    }

    @Test
    public void notTheSameWordsTwice() {
        assertNotEquals(Ibiko1.greeting("Ada", 14, 1), Ibiko1.greeting("Ada", 14, 2));
        assertEquals("Hello again Ada. What can I do for you?", Ibiko1.greeting("Ada", 14, 1));
    }

    @Test
    public void anUnreachableApiIsTold() {
        assertTrue(ai.ibiko.MainActivity.unreachable("I couldn't reach brewkeg.dev (Unable to resolve host \"brewkeg.dev\")"));
        assertFalse(ai.ibiko.MainActivity.unreachable("the model refused"));
    }
}
