package ai.dvine.connect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LineMergeTest {
    @Test
    public void editsThatDoNotTouchCombine() {
        String base = "line1\nline2\nline3\nline4\n";
        String ours = "line1\nline2-mine\nline3\nline4\n";
        String theirs = "line1\nline2\nline3\nline4-main\n";
        LineMerge.Result m = LineMerge.of(base, ours, theirs, "yours", "main");
        assertTrue(m.clean());
        assertEquals("line1\nline2-mine\nline3\nline4-main\n", m.text);
    }

    @Test
    public void theSameEditIsTakenOnce() {
        String base = "a\nb\nc\n";
        String both = "a\nb2\nc\n";
        LineMerge.Result m = LineMerge.of(base, both, both, "yours", "main");
        assertTrue(m.clean());
        assertEquals(both, m.text);
    }

    @Test
    public void overlappingEditsStayMarked() {
        String base = "a\nb\nc\n";
        LineMerge.Result m = LineMerge.of(base, "a\nmine\nc\n", "a\ntheirs\nc\n", "yours", "main");
        assertFalse(m.clean());
        assertEquals(1, m.conflicts);
        assertTrue(m.text, m.text.contains("<<<<<<< yours") && m.text.contains("mine") && m.text.contains("theirs")
                && m.text.contains(">>>>>>> main"));
        assertTrue(m.text.startsWith("a\n") && m.text.contains("\nc\n"));
    }

    @Test
    public void anInsertAndADistantEditBothStay() {
        String base = "a\nb\nc\n";
        LineMerge.Result m = LineMerge.of(base, "a\nx\nb\nc\n", "a\nb\nz\n", "yours", "main");
        assertTrue(m.text, m.clean());
        assertEquals("a\nx\nb\nz\n", m.text);
    }

    @Test
    public void aDeletionAndADistantEditBothStay() {
        String base = "a\nb\nc\n";
        LineMerge.Result m = LineMerge.of(base, "a\nc\n", "a\nb\nz\n", "yours", "main");
        assertTrue(m.text, m.clean());
        assertEquals("a\nz\n", m.text);
    }
}
