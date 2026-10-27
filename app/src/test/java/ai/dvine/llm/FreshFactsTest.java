package ai.dvine.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class FreshFactsTest {
    @Test
    public void looksUpOnlyQuestionsAboutTheWorld() {
        assertTrue(FreshFacts.worthLooking("who is the president of nigeria"));
        assertTrue(FreshFacts.worthLooking("what is the latest iphone"));
        assertFalse(FreshFacts.worthLooking("what is my sister's name"));
        assertFalse(FreshFacts.worthLooking("how are you"));
        assertFalse(FreshFacts.worthLooking("tell me a joke"));
        assertEquals("president of nigeria", FreshFacts.terms("Who is the president of Nigeria?"));
        assertTrue(FreshFacts.searchUrl("who is the president of nigeria").endsWith("gsrsearch=president+of+nigeria"));
    }

    @Test
    public void readsTheReferenceAndBuildsTheContext() {
        String json = "{\"query\":{\"pages\":[{\"pageid\":1,\"title\":\"President of Nigeria\",\"extract\":"
                + "\"The president of Nigeria is the head of state and head of government. The current president is Bola Tinubu.\\nMore.\"}]}}";
        String ref = FreshFacts.extract(json);
        assertTrue(ref, ref.startsWith("President of Nigeria: The president") && ref.contains("Bola Tinubu") && !ref.contains("\n"));
        assertNull(FreshFacts.extract("{\"batchcomplete\":true}"));
        assertNull(FreshFacts.extract("not json"));
        String c = PersonaPrompt.context(Arrays.asList("the president of nigeria is Bola Tinubu"), ref);
        assertTrue(c.startsWith("Facts the user taught you") && c.contains("- the president of nigeria is Bola Tinubu") && c.contains("Reference fetched today"));
        assertNull(PersonaPrompt.context(null, null));
        String sys = PersonaPrompt.system(false, "Val", "Tuesday 27 October 2026");
        assertTrue(sys.contains("Today is Tuesday 27 October 2026.") && sys.contains("never argue") && sys.endsWith("/no_think"));
    }
}
