package ai.ibiko.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/** Each task to its persona; long writing planned before it is written. */
public class HandoffOwnerTest {
    @Test
    public void eachTaskHasItsPersona() {
        assertEquals("ibiko", Handoff.owner("fix the login crash in my android app"));
        assertEquals("ivie", Handoff.owner("Write a short story about a girl and a lion"));
        assertEquals("ivie", Handoff.owner("create a script for a 2 minute skit"));
        assertEquals("obi", Handoff.owner("prepare a report on the Lagos fuel market"));
        assertEquals("obi", Handoff.owner("draft a business plan for a bakery"));
        assertNull("a question stays where it is", Handoff.owner("what is a business plan?"));
        assertNull("a social post is anyone's", Handoff.owner("write a Facebook post about my story"));
        assertNull(Handoff.owner("hello"));
    }

    @Test
    public void longWritingIsPlannedFirst() {
        assertTrue(WritingPlan.applies("Write a story about a brave girl"));
        assertTrue(WritingPlan.applies("prepare a report on fuel prices"));
        assertFalse(WritingPlan.applies("write a one line caption for my story"));
        assertFalse(WritingPlan.applies("what makes a good story?"));
        String outline = "Title: The Brave Girl\nReader: children\nTone: warm\nLength: 900 words\n"
                + "1. Opening: Ada finds the letter\n2. **Journey**: she crosses the river\n3. Ending: she comes home";
        List<String> s = WritingPlan.sections(outline);
        assertEquals(3, s.size());
        assertEquals("Journey: she crosses the river", s.get(1));
        assertEquals("The Brave Girl", WritingPlan.title(outline));
        assertEquals("# The Brave Girl\n\nOne.\n\nTwo.", WritingPlan.join("The Brave Girl", java.util.Arrays.asList("One.", " ", "Two.")));
        assertTrue(WritingPlan.checkPrompt("a story", outline).contains("loopholes"));
        assertTrue(WritingPlan.sectionPrompt("a story", outline, "Ada woke.", 2, s.get(1)).contains("do not repeat it"));
    }

    @Test
    public void xPostsFitTheLimit() {
        assertEquals("5/280", ai.ibiko.social.PostDrafts.length("x", "hello"));
        assertEquals("", ai.ibiko.social.PostDrafts.length("facebook", "hello"));
        assertNull(ai.ibiko.social.PostDrafts.tooLong("x", "short"));
        assertTrue(ai.ibiko.social.PostDrafts.tooLong("x", new String(new char[300]).replace('\0', 'a')).contains("300 of 280"));
    }

    @Test
    public void inTheGroupANameAddressesThatPersona() {
        assertEquals("ivie", Handoff.mentioned("@Ivie draw me a cat"));
        assertEquals("obi", Handoff.mentioned("Obi, what's the time in Lagos?"));
        assertEquals("ibiko", Handoff.mentioned("please @ibiko run the tests"));
        assertNull(Handoff.mentioned("ask obi later"));
        assertNull(Handoff.mentioned("email me@ivie.com"));
    }

    @Test
    public void inTheGroupTheWordsChooseWhoAnswers() {
        assertEquals("ivie", Handoff.groupRoute("@Ivie what do you think?", "obi"));
        assertEquals("ibiko", Handoff.groupRoute("fix the crash in my android app", "obi"));
        assertEquals("ivie", Handoff.groupRoute("write a story about rain", "ibiko"));
        assertEquals("the one working carries on", "ibiko", Handoff.groupRoute("continue", "ibiko"));
        assertEquals("ivie", Handoff.groupRoute("yes", "ivie"));
        assertEquals("everything else is Obi's", "obi", Handoff.groupRoute("what's the weather in Lagos?", "ibiko"));
        assertEquals("obi", Handoff.groupRoute("tell me a joke", "ivie"));
    }

    @Test
    public void commandsToCarryOutAreIbikos() {
        assertEquals("ibiko", Handoff.groupRoute("Pull it now", "obi"));
        assertEquals("ibiko", Handoff.groupRoute("ibiko Pull it now", "obi"));
        assertEquals("ibiko", Handoff.groupRoute("Run it directly", "obi"));
        assertEquals("ibiko", Handoff.groupRoute("Install the necessary files needed by Linux", "obi"));
        assertEquals("ibiko", Handoff.groupRoute("Use the internal Linux environment", "obi"));
        assertEquals("ibiko", Handoff.groupRoute("Update local Ibiko ai files from GitHub it's been updated", "obi"));
        assertEquals("a name at the start is enough, no comma needed", "ivie", Handoff.groupRoute("Ivie draw me a cat", "obi"));
        assertEquals("talk about someone is not a call to them", "obi", Handoff.groupRoute("Ibiko is fast today", "obi"));
        assertEquals("obi", Handoff.groupRoute("What can you do?", "obi"));
    }
}
