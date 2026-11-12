package ai.ibiko.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;


/** The brain knows the app it lives in; its switch tags are acted on and never spoken; a question opens the mic. */
public class SelfAndTagsTest {
    @Test
    public void knowsIbiko() {
        IbikoSelf.Facts f = new IbikoSelf.Facts();
        f.version = "0.26.0";
        f.device = "Tecno Spark";
        f.userName = "Val";
        f.taughtFacts = 3;
        String s = IbikoSelf.sheet(f);
        for (String must : new String[]{"Ibiko (version 0.26.0)", "Tecno Spark", "Ibiko is one assistant", "offline", "The user is Val", "taught you 3 facts"}) {
            assertTrue(must, s.contains(must));
        }
        String ibiko = PersonaPrompt.system("ibiko", "Val", "Tuesday 27 October 2026", s);
        assertTrue(ibiko.startsWith("You are Ibiko"));
        assertTrue("no other names in what it knows about itself", !s.contains("Ivie") && !s.contains("Obi") && !s.contains("switch:"));
        assertTrue(ibiko.contains("Ibiko (version 0.26.0)"));
        assertTrue(PersonaPrompt.system("ivie", null, null, s).startsWith("You are Ibiko"));
    }

    @Test
    public void tagsAreActedOnNotSpoken() {
        ActionTags t = new ActionTags();
        StringBuilder said = new StringBuilder();
        for (String piece : new String[]{"Of course, here", " is Obi. [", "[swi", "tch:o", "bi]]", ""}) {
            said.append(t.add(piece));
        }
        said.append(t.flush());
        assertEquals("Of course, here is Obi. ", said.toString());
        assertEquals("obi", t.switchTo());
        ActionTags u = new ActionTags();
        assertEquals("a [b] c", u.add("a [b] c"));
        assertNull(u.switchTo());
        assertEquals("Hello.", ActionTags.strip("Hello. [[switch:ivie]]"));
        assertEquals("Hello.", ActionTags.strip("Hello. [[switch:somebody]]"));
    }
}
