package ai.dvine.turn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The names as speech recognition hears them, and the ways people ask for one persona. */
public class NamesTest {
    @Test
    public void hearsTheNames() {
        assertEquals("switch to Ivie", Names.normalize("switch to Evie"));
        assertEquals("communicate with Ivie your other half", Names.normalize("communicate with Evie your other half"));
        assertEquals("hey Ivie", Names.normalize("hey Ivy"));
        assertEquals("talk to Obi", Names.normalize("talk to Obie"));
        assertEquals("switch to Obi", Names.normalize("switch to obey"));
        assertEquals("we should obey the rules", Names.normalize("we should obey the rules"));
        assertEquals("Obi, what time is it", Names.normalize("obey, what time is it"));
    }

    @Test
    public void bringsOnePersonaForward() {
        assertEquals("ivie", Names.switchTarget("switch to Ivie"));
        assertEquals("ivie", Names.switchTarget("communicate with Ivie your other half"));
        assertEquals("obi", Names.switchTarget("let me talk to Obi"));
        assertEquals("ivie", Names.switchTarget("bring Ivie"));
        assertNull(Names.switchTarget("talk to Ivie about the weather tomorrow"));
        assertNull(Names.switchTarget("what is the capital of France"));
        TurnRoute r = LocalTurn.route("communicate with Evie your other half", "obi");
        assertTrue(r.switchOnly && r.ivie);
        TurnRoute o = LocalTurn.route("switch to obey", "ivie");
        assertTrue(o.switchOnly && !o.ivie);
    }
}
