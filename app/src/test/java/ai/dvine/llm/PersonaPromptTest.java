package ai.dvine.llm;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The persona prompt asks rather than guesses, follows the conversation, and is told what surrounds a question. */
public class PersonaPromptTest {
    @Test
    public void curiousAndInContext() {
        String system = PersonaPrompt.system(true, "Val", "Tuesday 27 October 2026");
        assertTrue(system.contains("ask one short, specific question"));
        assertTrue(system.contains("Do you mean"));
        assertTrue(system.contains("in the light of what was said before"));
        String around = PersonaPrompt.situation(false, "the moon", "a lighthouse", "evening", true);
        assertTrue(around, around.startsWith("You (Obi) are on screen; Ivie, your other half, is with you."));
        assertTrue(around.contains("just talking about the moon"));
        assertTrue(around.contains("On show now: a lighthouse"));
        String context = PersonaPrompt.context(java.util.Collections.singletonList("Tobi is my brother."), null, around);
        assertTrue(context, context.startsWith("Situation: You (Obi)"));
        assertTrue(context.contains("\nFacts the user taught you"));
    }
}
