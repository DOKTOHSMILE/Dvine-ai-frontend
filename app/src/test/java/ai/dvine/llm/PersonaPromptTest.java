package ai.dvine.llm;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The persona prompt asks rather than guesses, follows the conversation, and is told what surrounds a question. */
public class PersonaPromptTest {
    @Test
    public void curiousAndInContext() {
        String system = PersonaPrompt.system(true, "Val", "Tuesday 27 October 2026");
        assertTrue(system.contains("Interpret the words and answer or act"));
        assertTrue(system.contains("Do not ask the user to repeat themselves"));
        assertTrue(system.contains("in the light of what was said before"));
        String around = PersonaPrompt.situation(false, "the moon", "a lighthouse", "evening", true);
        assertTrue(around, around.startsWith("You are DVINE AI, the one assistant here."));
        assertTrue(around.contains("just talking about the moon"));
        assertTrue(around.contains("On show now: a lighthouse"));
        assertTrue(PersonaPrompt.situation("ibiko", null, null, "night", false).startsWith("You are DVINE AI"));
        String context = PersonaPrompt.context(java.util.Collections.singletonList("Tobi is my brother."), null, around);
        assertTrue(context, context.startsWith("Situation: You are DVINE AI"));
        assertTrue(context.contains("\nFacts the user taught you"));
    }

    @Test
    public void theMasterSpecificationsCharacterAndVoices() {
        String obi = PersonaPrompt.system("obi", null, null), ivie = PersonaPrompt.system("ivie", null, null),
                ibiko = PersonaPrompt.system("ibiko", null, null);
        assertTrue("one assistant, whatever key is passed", obi.equals(ivie) && ivie.equals(ibiko));
        assertTrue(obi.startsWith("You are DVINE AI") && obi.contains("first principles") && obi.contains("lands on people"));
        assertTrue(obi.contains("never call yourself or anyone here Obi, Ivie or Ibiko"));
        for (String s : new String[]{obi, ivie, ibiko}) {
            assertTrue(s.contains("grace and truth together (John 1:14)"));
            assertTrue(s.contains("Viktor Frankl") && s.contains("Daniel Kahneman") && s.contains("Myles Munroe") && s.contains("Stoic"));
            assertTrue(s.contains("never preach")); // a manner, not a sermon
            assertTrue(s.contains("Nigerian Pidgin, French or Yoruba when they write in it"));
            assertTrue(s.contains("be quietly present"));
            assertTrue(s.endsWith("/no_think"));
        }
        ai.dvine.voice.VoiceTuning o = ai.dvine.voice.VoiceTuning.defaults(false), i = ai.dvine.voice.VoiceTuning.defaults(true);
        // Warm side of neutral: pushed-up system voices turn thin and sharp, so Ivie sits just under 1.0 and Obi below it.
        org.junit.Assert.assertEquals(0.80f, o.pitch, 1e-6);
        org.junit.Assert.assertEquals(0.98f, o.rate, 1e-6);
        org.junit.Assert.assertEquals(0.97f, i.pitch, 1e-6);
        org.junit.Assert.assertEquals(1.00f, i.rate, 1e-6);
    }
}
