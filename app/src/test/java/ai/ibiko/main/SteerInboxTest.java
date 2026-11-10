package ai.ibiko.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SteerInboxTest {
    @Test
    public void notesAreTakenOnceInOrder() {
        SteerInbox box = new SteerInbox();
        assertFalse(box.pending());
        assertNull(box.drain());
        box.add("  ");
        box.add("Also check the weather in Lagos");
        box.add("And the traffic");
        assertEquals(2, box.size());
        assertEquals("Also check the weather in Lagos\n\nAnd the traffic", box.drain());
        assertFalse(box.pending());
        assertNull(box.drain());
        assertTrue(box.size() == 0);
    }
}
