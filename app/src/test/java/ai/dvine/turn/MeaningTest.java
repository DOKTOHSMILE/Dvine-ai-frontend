package ai.dvine.turn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A message is acted on by what it means, not its words: the reader names the command, DVINE runs its wording. */
public class MeaningTest {
    @Test
    public void theReaderNamesTheCommandWhateverTheWords() {
        // what a model writes back, forgiving its slips
        assertEquals(Meaning.Command.RESULT, Meaning.read("COMMAND: result"));
        assertEquals(Meaning.Command.RESULT, Meaning.read("<think>they ask if it is finished</think>\nCOMMAND: result"));
        assertEquals(Meaning.Command.CONTINUE, Meaning.read("command: **continue**"));
        assertEquals(Meaning.Command.REMOVE_MODEL, Meaning.read("COMMAND: remove_model."));
        assertEquals(Meaning.Command.NONE, Meaning.read("COMMAND: none"));
        assertEquals(Meaning.Command.NONE, Meaning.read("Sure! The capital of France is Paris."));
        assertEquals(Meaning.Command.NONE, Meaning.read(null));
        // each command runs under the wording DVINE's code knows
        assertEquals("give me the result", Meaning.Command.RESULT.canonical);
        assertEquals("continue", Meaning.Command.CONTINUE.canonical);
        assertEquals("delete the model", Meaning.Command.REMOVE_MODEL.canonical);
        // the commands DVINE carries out with its own actions (its buttons'), not as a message
        assertEquals(Meaning.Command.SWITCH_OBI, Meaning.read("COMMAND: switch_obi"));
        assertEquals(Meaning.Command.CLEAR_WORKSPACE, Meaning.read("COMMAND: clear_workspace"));
        for (Meaning.Command c : new Meaning.Command[]{Meaning.Command.NEW_CHAT, Meaning.Command.SWITCH_IVIE, Meaning.Command.SWITCH_OBI,
                Meaning.Command.SWITCH_IBIKO, Meaning.Command.STOP, Meaning.Command.AUTO_ON, Meaning.Command.AUTO_OFF,
                Meaning.Command.CLEAR_WORKSPACE, Meaning.Command.OPEN_SETTINGS}) {
            assertTrue(c.name(), c.direct());
            assertTrue(c.name(), Meaning.prompt(false).contains(c.name().toLowerCase(java.util.Locale.US) + ":"));
        }
        assertFalse(Meaning.Command.RESULT.direct() || Meaning.Command.REMOVE_MODEL.direct() || Meaning.Command.NONE.direct());
    }

    @Test
    public void carryingOnIsOfferedOnlyWhileThereIsWork() {
        String busy = Meaning.prompt(true), idle = Meaning.prompt(false);
        assertTrue(busy.contains("continue:") && busy.contains("result:"));
        assertFalse(idle.contains("continue:") || idle.contains("result:"));
        assertTrue(idle.contains("download_model:") && idle.contains("none:"));
    }

    @Test
    public void shortMessagesAreReadAndEachPhrasingIsRemembered() {
        assertTrue(Meaning.worthReading("Are you done?"));
        assertFalse(Meaning.worthReading("x".repeat(Meaning.MOST_CHARS + 1))); // a long message is a task: it goes on as written
        assertFalse(Meaning.worthReading("fix this\n```java\nint a;\n```"));
        assertEquals("are you done", Meaning.key("  Are you DONE?? "));
        Meaning.Memory m = new Meaning.Memory();
        assertNull(m.get("are you done?"));
        m.put("Are you done?", Meaning.Command.RESULT);
        m.put("what's the capital of France", Meaning.Command.NONE);
        assertEquals(Meaning.Command.RESULT, m.get("are you done")); // the same phrasing, another punctuation
        Meaning.Memory back = Meaning.Memory.read(m.write()); // kept on the phone
        assertEquals(Meaning.Command.RESULT, back.get("ARE YOU DONE?"));
        assertEquals(Meaning.Command.NONE, back.get("What's the capital of France?"));
        for (int i = 0; i < Meaning.Memory.KEEP + 20; i++) {
            back.put("phrase " + i, Meaning.Command.NONE);
        }
        assertNull(back.get("are you done")); // the oldest go first
    }
}
