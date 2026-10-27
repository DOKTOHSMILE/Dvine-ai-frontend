package ai.dvine.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ai.dvine.turn.Followup;

/** The brain knows the app it lives in; its switch tags are acted on and never spoken; a question opens the mic. */
public class SelfAndTagsTest {
    @Test
    public void knowsDvine() {
        DvineSelf.Facts f = new DvineSelf.Facts();
        f.version = "0.26.0";
        f.device = "Tecno Spark";
        f.userName = "Val";
        f.taughtFacts = 3;
        String s = DvineSelf.sheet(f);
        for (String must : new String[]{"DVINE (version 0.26.0)", "Tecno Spark", "Ivie", "Obi", "Ibiko", "coding robot",
                "switch to", "Evie or Eva", "offline", "The user is Val", "taught you 3 facts", "[[switch:ibiko]]"}) {
            assertTrue(must, s.contains(must));
        }
        String ibiko = PersonaPrompt.system("ibiko", "Val", "Tuesday 27 October 2026", s);
        assertTrue(ibiko.startsWith("You are Ibiko"));
        assertTrue(ibiko.contains("DVINE (version 0.26.0)"));
        assertTrue(PersonaPrompt.system("ivie", null, null, s).startsWith("You are Ivie"));
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

    @Test
    public void aRequestForAnAnswerOpensTheMicrophone() {
        assertTrue(Followup.wantsReply("What would you like to build?"));
        assertTrue(Followup.wantsReply("I can help with that. Tell me more about the app."));
        assertTrue(Followup.wantsReply("Okay. Let me know when you're ready."));
        assertTrue(Followup.wantsReply("Go ahead, I'm listening."));
        assertFalse(Followup.wantsReply("The capital of France is Paris."));
        assertFalse(Followup.wantsReply("Copied. The crash report is on your clipboard."));
        assertFalse(Followup.wantsReply(""));
    }
}
