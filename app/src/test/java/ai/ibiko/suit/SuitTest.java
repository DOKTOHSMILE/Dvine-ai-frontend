package ai.ibiko.suit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/** The suit: machine codes only from the brain, exact arithmetic, a reading of each message, deep thinking. */
public class SuitTest {
    private static String calc(String s) {
        return Arithmetic.show(Arithmetic.eval(s));
    }

    @Test
    public void arithmeticIsExact() {
        assertEquals("84", calc("12*7"));
        assertEquals("0.3", calc("0.1+0.2"));
        assertEquals("49", calc("(3+4)^2"));
        assertEquals("512", calc("2^3^2"));
        assertEquals("360", calc("15% of 2,400"));
        assertEquals("84", calc("twelve times seven"));
        assertEquals("9", calc("square root of 81"));
        assertEquals("3.5", calc("7 divided by 2"));
        assertEquals("1", calc("7 mod 3"));
        assertEquals("105", calc("one hundred and five"));
        assertEquals("-5", calc("-(2+3)"));
        assertNull(Arithmetic.eval("5/0"));
        assertNull(Arithmetic.eval("hello"));
        assertEquals("12 x 7", Arithmetic.asked("What's 12 x 7?"));
        assertNull(Arithmetic.asked("What is the capital of France?"));
    }

    @Test
    public void theCortexReadsTheMessage() {
        Comprehension q = Comprehension.of("Can you fix the bug in MainActivity.java on doktohsmile/dvine-ai-frontend?");
        assertEquals(Comprehension.Act.QUESTION, q.act);
        assertTrue(q.files.contains("MainActivity.java"));
        assertTrue(q.repos.toString(), q.repos.contains("doktohsmile/dvine-ai-frontend"));
        assertTrue(q.brief().contains("files: MainActivity.java"));
        assertEquals(Comprehension.Act.REQUEST, Comprehension.of("Build me a chair 40 cm wide").act);
        assertEquals(Comprehension.Act.GREETING, Comprehension.of("Hello there!").act);
        assertEquals(Comprehension.Act.CORRECTION, Comprehension.of("No, that's not what I meant").act);
        assertTrue(Comprehension.of("I don't want the red one").negated);
        Comprehension m = Comprehension.of("If I have 12 * 24 bottles how many is that");
        assertEquals("288", m.mathValue);
        assertTrue(m.brief().contains("exact arithmetic: 12 * 24 = 288"));
        assertTrue(Comprehension.of("Ask Ada Lovelace about it").names.contains("Ada Lovelace"));
    }

    @Test
    public void wordsNeverTriggerAFunction() {
        assertNull(FunctionCode.parse("stop"));
        assertNull(FunctionCode.parse("new chat"));
        assertNull(FunctionCode.parse("§SRC is not a code"));
        assertEquals(FunctionCode.CA, FunctionCode.parse("⟦CA⟧ 12*7").code);
        assertTrue(FunctionCode.startsLikeCode("  §"));
        String only = FunctionCode.instructions("", java.util.EnumSet.of(FunctionCode.SR, FunctionCode.TL));
        assertTrue(only.contains("§SR") && !only.contains("§SH") && !only.contains("§TL")); // no tools: no tool code
    }

    @Test
    public void deepThinkingPlansDraftsCritiquesAndRevises() {
        final List<String> roles = new ArrayList<String>();
        DeepThink.Brain brain = (system, prompt) -> {
            if (system.endsWith(DeepThink.PLAN)) {
                roles.add("plan");
                return "Asked: 2+2. Plan: add.";
            }
            if (system.endsWith(DeepThink.DRAFT)) {
                roles.add("draft");
                return "5";
            }
            if (system.endsWith(DeepThink.CRITIC)) {
                roles.add("critic");
                return prompt.contains("Answer:\n4") ? "SOUND" : "2+2 is 4, not 5.";
            }
            roles.add("revise");
            return "4";
        };
        List<String> steps = new ArrayList<String>();
        assertEquals("4", DeepThink.think(brain, "persona", "What is 2+2?", 3, (n, r) -> steps.add(n), () -> false));
        assertEquals("[plan, draft, critic, revise, critic]", roles.toString()); // stopped once the review found nothing
        assertTrue(steps.contains("Checked: nothing to fix"));
        assertNull(DeepThink.think(brain, "", "q", 2, null, () -> true));
    }

    @Test
    public void withNoModelTheCortexStillAnswersWhatItKnows() {
        Cortex c = new Cortex(q -> q.contains("Kenya") ? "Nairobi." : null);
        assertEquals("12 x 7 = 84", c.answerAlone("what's 12 x 7?"));
        assertEquals("From what I learned earlier: Nairobi.", c.answerAlone("capital of Kenya"));
        assertNull(c.answerAlone("write me a poem"));
        assertTrue(c.context("hello").startsWith("Ibiko's reading of the message: greeting"));
    }

    /** Passing work on, planning a piece, thinking together, the yes to an action: codes from the brain, never words. */
    @org.junit.Test
    public void theBrainsCodesForWhatWordsUsedToDo() {
        ai.ibiko.llm.BrainOrder o = ai.ibiko.llm.BrainOrder.leading("\u00A7HO ibiko\nThat is Ibiko's work.");
        org.junit.Assert.assertEquals(ai.ibiko.llm.BrainOrder.Kind.HANDOVER, o.kind);
        org.junit.Assert.assertEquals("ibiko", o.arg);
        org.junit.Assert.assertEquals("ivie", ai.ibiko.llm.BrainOrder.leading("[[HO]] Ivie.").arg);
        org.junit.Assert.assertEquals(ai.ibiko.llm.BrainOrder.Kind.WRITEPLAN, ai.ibiko.llm.BrainOrder.leading("\u00A7WP").kind);
        org.junit.Assert.assertEquals(ai.ibiko.llm.BrainOrder.Kind.THINKTOGETHER, ai.ibiko.llm.BrainOrder.leading("\u00A7TT").kind);
        org.junit.Assert.assertEquals(ai.ibiko.llm.BrainOrder.Kind.CONFIRM, ai.ibiko.llm.BrainOrder.leading("\u00A7YS\nPosting it.").kind);
        org.junit.Assert.assertEquals(ai.ibiko.llm.BrainOrder.Kind.CANCEL, ai.ibiko.llm.BrainOrder.leading("\u00A7CX").kind);
        org.junit.Assert.assertNull("there is no 3D canvas any more", ai.ibiko.llm.BrainOrder.leading("\u00A7DR make the wheels bigger"));
        org.junit.Assert.assertEquals(ai.ibiko.llm.BrainOrder.Kind.IMAGE, ai.ibiko.llm.BrainOrder.leading("\u00A7IM a call icon").kind);
        // the pilot's own words are never a code
        org.junit.Assert.assertNull(ai.ibiko.llm.BrainOrder.leading("pull it now"));
        org.junit.Assert.assertNull(ai.ibiko.llm.BrainOrder.leading("yes, post it"));
        org.junit.Assert.assertNull(ai.ibiko.llm.BrainOrder.leading("hand this to Ibiko"));
        org.junit.Assert.assertTrue(FunctionCode.instructions("", null).contains("\u00A7HO"));
    }

    /** A code is Ibiko's, never the conversation's: found below a sentence, and taken out of what is shown. */
    @org.junit.Test
    public void aCodeNeverReachesTheConversation() {
        String reply = "Sure, on it.\n\u00A7SR weather in Lagos\nLooking that up.";
        org.junit.Assert.assertEquals(FunctionCode.SR, FunctionCode.parse(reply).code);
        org.junit.Assert.assertEquals("Sure, on it.\nLooking that up.", FunctionCode.withoutCode(reply));
        org.junit.Assert.assertEquals(FunctionCode.NC, FunctionCode.parse("**\u00A7NC**").code);
        org.junit.Assert.assertFalse(ai.ibiko.chat.ReplyText.prepare("Here you go.\n[[MF]] pdf\nDone.").contains("[[MF]]"));
        org.junit.Assert.assertFalse(ai.ibiko.chat.ReplyText.prepare("\u00A7ZZ not a real code\nHello").contains("\u00A7"));
        org.junit.Assert.assertEquals("Costs \u00A7 5 in the table.", ai.ibiko.chat.ReplyText.prepare("Costs \u00A7 5 in the table.").trim());
        org.junit.Assert.assertEquals(ai.ibiko.llm.BrainOrder.Kind.WORK, ai.ibiko.llm.BrainOrder.leading("\u00A7WK").kind);
    }
}
