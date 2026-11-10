package ai.ibiko.connect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Lines added and removed, the changed places with their context, and a file added from nothing. */
public class LineDiffTest {
    @Test
    public void countsAndShowsTheChangedPlaces() {
        StringBuilder before = new StringBuilder(), after = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            before.append("line ").append(i).append('\n');
            after.append(i == 10 ? "line ten\n" : "line " + i + "\n");
            if (i == 15) {
                after.append("new line\n");
            }
        }
        LineDiff.Result r = LineDiff.of(before.toString(), after.toString());
        assertEquals(2, r.added);
        assertEquals(1, r.removed);
        assertTrue(r.lines.toString(), r.lines.contains("-line 10") && r.lines.contains("+line ten") && r.lines.contains("+new line"));
        assertTrue(r.lines.toString(), r.lines.contains(" line 7") && !r.lines.contains(" line 1"));
        assertTrue(r.lines.get(0), r.lines.get(0).startsWith("@ line"));

        LineDiff.Result fresh = LineDiff.of("", "a\nb\n");
        assertEquals(2, fresh.added);
        assertEquals(0, fresh.removed);
        assertEquals(0, LineDiff.of("same\n", "same\n").added);
    }
}
