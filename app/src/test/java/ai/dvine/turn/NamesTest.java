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

    @Test
    public void asksAboutWordsThatOnlySoundLikeAName() {
        Names.restore(null);
        Names.Hearing h = Names.hear("Eva, what time is it");
        assertEquals("Eva", h.word);
        assertEquals("Ivie", h.guess);
        assertEquals("Ivie, what time is it", h.suggested);
        assertEquals("Did you mean Ivie? I heard \"Eva\".", h.question());
        assertEquals("Obi", Names.hear("talk to Abi").guess);
        assertEquals("Ivie", Names.hear("hey Ava").guess);
        // clear spellings are just fixed; ordinary words, and sound-alikes away from a name's place, are left alone
        assertNull(Names.hear("switch to Evie").word);
        assertEquals("switch to Ivie", Names.hear("switch to Evie").text);
        assertNull(Names.hear("happy birthday to you").word);
        assertNull(Names.hear("that was evil").word);
        assertNull(Names.hear("my sister Eva is coming").word);
        assertNull(Names.hear("what is the weather").word);
    }

    @Test
    public void remembersTheAnswer() {
        Names.restore(null);
        Names.learn("Eva", "Ivie");
        assertEquals("Ivie, what time is it", Names.hear("Eva, what time is it").text);
        assertNull(Names.hear("Eva, what time is it").word);
        Names.notAName("Ava");
        assertNull(Names.hear("Ava, hello").word);
        String saved = Names.saved();
        Names.restore(null);
        assertEquals("Ivie", Names.hear("Ava there").guess);
        Names.restore(saved);
        assertNull(Names.hear("Ava there").word);
        assertEquals("Ivie there", Names.hear("Eva there").text);
        Names.restore(null);
        assertEquals(Boolean.TRUE, Names.yesNo("Yes, Ivie"));
        assertEquals(Boolean.TRUE, Names.yesNo("that's right"));
        assertEquals(Boolean.FALSE, Names.yesNo("no, I meant Eva my sister"));
        assertNull(Names.yesNo("okay tell me a joke"));
        assertNull(Names.yesNo("what is the time"));
    }
}
