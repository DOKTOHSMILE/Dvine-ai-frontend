package ai.dvine.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/** A small model writing the rest of the conversation itself (the user's screenshot): stopped and cut there. */
public class FakeTurnTest {
    @Test
    public void theInventedTurnsAreCutAndTheModelStopped() {
        String looping = "ibiko: The issue is that the Settings are not available.\n\nibiko: The updated code will be attached."
                + "\n\nUser: Carry out the plan\n\nibiko: Llama 3.2 1B is running slowly";
        assertEquals("ibiko: The issue is that the Settings are not available.", FakeTurn.cut(looping));
        assertEquals("The answer.", FakeTurn.cut("The answer.\n**User**: more"));
        assertEquals("A plain answer\nwith lines.", FakeTurn.cut("A plain answer\nwith lines."));
        assertEquals("ibiko: leading label only", FakeTurn.cut("ibiko: leading label only")); // not a new turn
        assertEquals(-1, FakeTurn.at("The user said: hello")); // words, not a turn label at a line start
        // streamed: pieces pass until the invented turn starts, then the model is told to stop
        List<String> shown = new ArrayList<String>();
        NativeLlm.Sink sink = FakeTurn.guard(t -> {
            shown.add(t);
            return true;
        });
        assertTrue(sink.onText("Paris is the capital."));
        assertTrue(sink.onText("\n"));
        assertFalse(sink.onText("User: and Spain?"));
        assertEquals("[Paris is the capital., \n]", shown.toString());
    }
}
