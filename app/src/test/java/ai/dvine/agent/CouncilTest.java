package ai.dvine.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** The three think tanks: Obi, Ivie (one recheck when she disagrees), Ibiko, then one answer. */
public class CouncilTest {
    @Test
    public void threeViewsOneRecheckOnDisagreementOneAnswer() {
        List<String> systems = new ArrayList<String>(), prompts = new ArrayList<String>(), steps = new ArrayList<String>();
        List<String> replies = new ArrayList<String>(Arrays.asList(
                "Key risk: no savings buffer. Recommend: keep the job 6 months. Confidence: medium.",
                "The family depends on the salary. VERDICT: DISAGREE: a side launch first is safer than 6 months' wait.",
                "Agreed: launch on the side first, then decide at 3 months.",
                "1. Validate with 10 customers. 2. Save 3 months' costs. 3. Launch on weekends.",
                "Start the business on the side first, then decide in three months. Obi checked the risks; Ivie considered your family; Ibiko worked out the steps."));
        Council.Result r = Council.run((sys, p) -> {
            systems.add(sys);
            prompts.add(p);
            return replies.remove(0);
        }, "Should I quit my job to start a business?", "", "obi", (line, who, said) -> steps.add(who + ": " + line), () -> false);
        assertTrue(r.answer.startsWith("Start the business on the side first"));
        assertTrue(r.secondRound);
        assertEquals(5, systems.size());
        assertTrue(systems.get(0).startsWith("You are Obi, the planner") && systems.get(0).contains("numbered"));
        assertTrue(systems.get(1).startsWith("You are Ivie") && systems.get(1).contains("real") && systems.get(1).contains("UI/UX")
                && prompts.get(1).contains("Obi's plan"));
        assertTrue(systems.get(2).startsWith("You are Obi, the planner. Ivie checked") && prompts.get(2).contains("a side launch first"));
        assertTrue(systems.get(3).startsWith("You are Ibiko, the builder") && systems.get(3).contains("carry the plan out"));
        assertTrue(systems.get(4).contains("never invent agreement") && prompts.get(4).contains("Ibiko (how to build it)"));
        assertEquals(Arrays.asList("Obi: Obi made the plan", "Ivie: Ivie checked it against the real world and disagrees on one point",
                "Obi: Obi revised the plan on Ivie's point", "Ibiko: Ibiko worked out how to build it"), steps);
        // agreement: no second round
        List<String> calm = new ArrayList<String>(Arrays.asList("Plan A.", "Fine. VERDICT: AGREE", "Steps.",
                "Answer.\nObi checked \u2026; Ivie considered \u2026; Ibiko worked out \u2026.")); // a copied template line goes
        Council.Result easy = Council.run((sys, p) -> calm.remove(0), "Compare plan A and plan B for my shop", "", "ivie",
                (l, w, s) -> { }, () -> false);
        assertFalse(easy.secondRound);
        assertEquals("Answer.", easy.answer);
        // stopped: nothing
        assertNull(Council.run((sys, p) -> "x", "Should I move to Abuja for work?", "", "obi", (l, w, s) -> { }, () -> true));
    }

    @Test
    public void buildingQuestionsGoOnToIbiko() {
        assertTrue(Council.building("Plan how to build a savings app for market traders"));
        assertTrue(Council.building("Should we launch a website for the shop?"));
        assertFalse(Council.building("Should I quit my job to start a business?"));
        assertFalse(Council.building("Compare two holiday destinations for December"));
    }

    @Test
    public void onlyWeightyQuestionsOrAnOutrightAsk() {
        assertTrue(Council.wanted("Should I quit my job to start a business this year?", true));
        assertTrue(Council.wanted("think together: what's for dinner", false)); // asked outright
        assertTrue(Council.wanted("Let all three of you look at this", false));
        assertFalse(Council.wanted("What is the capital of France?", true));
        assertFalse(Council.wanted("hello", true));
        assertFalse(Council.wanted("Should I quit my job to start a business this year?", false)); // off in Settings
    }
}
