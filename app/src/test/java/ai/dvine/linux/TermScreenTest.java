package ai.dvine.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TermScreenTest {
    @Test
    public void aReturnRewritesTheLineAndColorIsKept() {
        TermScreen screen = new TermScreen();
        assertEquals("hello", screen.write("hello").get(0).text);
        assertNull(screen.write("\rbye"));
        assertEquals("byelo", screen.plain());
        screen.write("\033[31mred\033[0m");
        assertTrue(screen.plain().contains("red"));
        assertEquals(0xFFCC5555, lastColor(screen));
    }

    @Test
    public void clearAndEraseAndASplitEscape() {
        TermScreen screen = new TermScreen();
        screen.write("gone");
        assertNull(screen.write("\033[2J"));
        assertEquals("", screen.plain());
        screen.write("abcdef");
        assertNull(screen.write("\r\033[K"));
        assertEquals("", screen.plain());
        screen.write("\033[32");
        assertEquals("ok", screen.write("mok").get(0).text);
        assertEquals(0xFF55AA55, screen.pieces().get(0).color);
    }

    private static int lastColor(TermScreen screen) {
        java.util.List<TermScreen.Piece> pieces = screen.pieces();
        return pieces.get(pieces.size() - 1).color;
    }
}
