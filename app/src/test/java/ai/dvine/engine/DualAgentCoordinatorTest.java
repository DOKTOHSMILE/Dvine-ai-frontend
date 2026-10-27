package ai.dvine.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import ai.dvine.turn.DualAgentCoordinator;
import ai.dvine.turn.DualAgentCoordinator.Mode;
import ai.dvine.turn.DualAgentCoordinator.Reply;
import ai.dvine.turn.DualAgentCoordinator.State;
import ai.dvine.turn.TurnLine;

/** Two agents, one question: each answers on its own, the second hears the first, turns stay bounded. */
public class DualAgentCoordinatorTest {
    /** An agent that says its scripted answers in turn (the last one repeats) and records what it heard. */
    static final class Scripted implements DualAgentCoordinator.Agent {
        final List<String[]> answers;
        final List<List<TurnLine>> heard = new ArrayList<List<TurnLine>>();
        int calls;

        Scripted(String[]... answers) {
            this.answers = Arrays.asList(answers);
        }

        public Reply answer(String q, List<TurnLine> h) {
            heard.add(h);
            String[] a = answers.get(Math.min(calls++, answers.size() - 1));
            return a == null ? new Reply(new ArrayList<String>(), false) : new Reply(Arrays.asList(a), true);
        }
    }

    private static List<String> said(List<TurnLine> lines, String who) {
        List<String> out = new ArrayList<String>();
        for (TurnLine l : lines) {
            if ("SAY".equals(l.cue) && l.agent.equals(who)) {
                out.add(l.text);
            }
        }
        return out;
    }

    @Test
    public void eachAnswersOnItsOwnAndTheSecondHearsTheFirst() {
        Scripted obi = new Scripted(new String[]{"Solar panels turn sunlight into electricity with silicon cells."});
        Scripted ivie = new Scripted(new String[]{"Wind turbines are cheaper per unit of energy in windy places."});
        final List<State> states = new ArrayList<State>();
        List<TurnLine> out = new DualAgentCoordinator(obi, ivie).discuss("what is the best clean energy", "obi",
                Mode.SECOND_OPINION, (s, p) -> states.add(s));
        assertEquals(1, obi.calls);
        assertEquals(1, ivie.calls);
        assertTrue("Ivie heard Obi's answer before giving hers", said(ivie.heard.get(0), "obi").size() > 0);
        assertTrue(said(out, "ivie").get(0).contains("differently"));
        assertTrue(said(out, "ivie").contains("Wind turbines are cheaper per unit of energy in windy places."));
        List<String> obiLines = said(out, "obi");
        assertTrue("Obi closes with a summary that keeps both views", obiLines.get(obiLines.size() - 2).contains("Ivie's view"));
        assertEquals(Arrays.asList(State.QUESTION_RECEIVED, State.AGENT_A_THINKING, State.AGENT_A_SPEAKING,
                State.AGENT_B_ANALYZING, State.AGENT_B_SPEAKING, State.FINAL_SUMMARY, State.IDLE), states);
    }

    @Test
    public void agreementIsNotDressedUpAsDebate() {
        String same = "Water boils at one hundred degrees Celsius at sea level.";
        Scripted obi = new Scripted(new String[]{same});
        Scripted ivie = new Scripted(new String[]{same, "At higher altitude water boils at a lower temperature."});
        final List<State> states = new ArrayList<State>();
        List<TurnLine> out = new DualAgentCoordinator(obi, ivie).discuss("when does water boil", "obi", Mode.DEBATE,
                (s, p) -> states.add(s));
        assertTrue(said(out, "ivie").get(0).startsWith("I agree with Obi"));
        assertTrue(states.contains(State.CONSENSUS));
        assertTrue("no debate rounds when they agree", !states.contains(State.DEBATE_CONTINUING));
        assertTrue(said(out, "obi").get(said(out, "obi").size() - 1).startsWith("So we agree"));
    }

    @Test
    public void aDebateStopsOnRepeatsAndStaysBounded() {
        Scripted obi = new Scripted(new String[]{"Remote work raises productivity for focused tasks."});
        Scripted ivie = new Scripted(new String[]{"Office work builds teams and helps new people learn faster."});
        DualAgentCoordinator c = new DualAgentCoordinator(obi, ivie);
        List<TurnLine> out = c.discuss("is remote work better", "obi", Mode.DEBATE, null);
        assertTrue("Obi repeats himself, so he stands by his answer instead of looping",
                said(out, "obi").contains("I'll stand by what I said."));
        int turns = 0;
        String last = null;
        for (TurnLine l : out) {
            if ("SAY".equals(l.cue) && !l.agent.equals(last)) {
                turns++;
                last = l.agent;
            }
        }
        assertTrue("bounded: " + turns, turns <= DualAgentCoordinator.MAX_TURNS);
        assertEquals(State.IDLE, c.state());
    }

    @Test
    public void unknownAnswersAndFailuresAreHonest() {
        Scripted obi = new Scripted((String[]) null);
        Scripted ivie = new Scripted((String[]) null);
        List<TurnLine> out = new DualAgentCoordinator(obi, ivie).discuss("what will the weather be in 2090", "ivie",
                Mode.SECOND_OPINION, null);
        assertEquals("I don't know this one either.", said(out, "obi").get(0));
        assertTrue("no summary that only repeats that neither knows", said(out, "ivie").isEmpty());

        DualAgentCoordinator.Agent broken = (q, h) -> {
            throw new java.io.IOException("offline");
        };
        final List<State> states = new ArrayList<State>();
        List<TurnLine> err = new DualAgentCoordinator(broken, ivie).discuss("anything", "obi", Mode.SECOND_OPINION,
                (s, p) -> states.add(s));
        assertEquals(State.ERROR, states.get(states.size() - 1));
        assertEquals(1, err.size());
    }

    @Test
    public void cancelStopsAtTheNextTurn() {
        final DualAgentCoordinator[] c = new DualAgentCoordinator[1];
        DualAgentCoordinator.Agent obi = (q, h) -> {
            c[0].cancel(); // the user interrupts while Obi is answering
            return new Reply(Arrays.asList("A long answer."), true);
        };
        c[0] = new DualAgentCoordinator(obi, new Scripted(new String[]{"Never heard."}));
        List<TurnLine> out = c[0].discuss("question", "obi", Mode.DEBATE, null);
        assertEquals(State.CANCELLED, c[0].state());
        assertTrue(said(out, "ivie").isEmpty());
    }

    @Test
    public void requestsAreRecognised() {
        DualAgentCoordinator.Request r = DualAgentCoordinator.parse("Both of you discuss the future of electric cars");
        assertNotNull(r);
        assertEquals(Mode.SECOND_OPINION, r.mode);
        assertEquals("tell me about the future of electric cars", r.question);
        assertEquals(Mode.DEBATE, DualAgentCoordinator.parse("debate whether AI will replace programmers?").mode);
        assertEquals(Mode.DEBATE, DualAgentCoordinator.parse("Obi and Ivie, debate about remote work").mode);
        assertEquals("what is gravity", DualAgentCoordinator.parse("what do you both think about gravity").question);
        assertEquals(Mode.JOINT, DualAgentCoordinator.parse("explain photosynthesis together").mode);
        assertNull(DualAgentCoordinator.parse("what is gravity"));
        assertNull(DualAgentCoordinator.parse("walk together"));
    }
}
