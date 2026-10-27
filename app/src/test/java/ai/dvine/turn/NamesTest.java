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
        Names.Hearing h = Names.hear("Ava, what time is it");
        assertEquals("Ava", h.word);
        assertEquals("Ivie", h.guess);
        assertEquals("Ivie, what time is it", h.suggested);
        assertEquals("Did you mean Ivie? I heard \"Ava\".", h.question());
        assertEquals("Obi", Names.hear("hey Abi").guess);
        assertEquals("Ivie", Names.hear("hey Ovie").guess);
        // asked to switch, a sound-alike is clearly the name: no question
        assertNull(Names.hear("talk to Abi").word);
        assertEquals("talk to Obi", Names.hear("talk to Abi").text);
        // the user calls her Eva: always Ivie
        assertEquals("switch to the other AI Ivie", Names.hear("switch to the other AI Eva").text);
        assertEquals("ivie", LocalTurn.route("switch to Eva", "obi").ivie ? "ivie" : "obi");
        // clear spellings are just fixed; ordinary words, and sound-alikes away from a name's place, are left alone
        assertNull(Names.hear("switch to Evie").word);
        assertEquals("switch to Ivie", Names.hear("switch to Evie").text);
        assertNull(Names.hear("happy birthday to you").word);
        assertNull(Names.hear("that was evil").word);
        assertNull(Names.hear("my friend Ava is coming").word);
        assertNull(Names.hear("what is the weather").word);
        // Ibiko, however it is heard
        assertEquals("bring Ibiko", Names.normalize("bring ebiko"));
        assertEquals("ibiko", Names.switchTarget("talk to Ibiko"));
        TurnRoute r = LocalTurn.route("switch to Ibiko", "ivie");
        assertTrue(r.switchOnly);
        assertEquals("ibiko", r.lines.get(0).agent);
    }

    @Test
    public void remembersTheAnswer() {
        Names.restore(null);
        Names.learn("Ava", "Ivie");
        assertEquals("Ivie, what time is it", Names.hear("Ava, what time is it").text);
        assertNull(Names.hear("Ava, what time is it").word);
        Names.notAName("Abi");
        assertNull(Names.hear("Abi, hello").word);
        String saved = Names.saved();
        Names.restore(null);
        assertEquals("Obi", Names.hear("Abi there").guess);
        Names.restore(saved);
        assertNull(Names.hear("Abi there").word);
        assertEquals("Ivie there", Names.hear("Ava there").text);
        Names.restore(null);
        assertEquals(Boolean.TRUE, Names.yesNo("Yes, Ivie"));
        assertEquals(Boolean.TRUE, Names.yesNo("that's right"));
        assertEquals(Boolean.FALSE, Names.yesNo("no, I meant Ava my sister"));
        assertNull(Names.yesNo("okay tell me a joke"));
        assertNull(Names.yesNo("what is the time"));
    }
}
