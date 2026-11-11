package ai.ibiko.linux;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The terminal's greeting is written before the screen knows its width: no line may be wider than a narrow device. */
public class TermWelcomeTest {
    @Test
    public void everyGreetingLineFitsFortyColumns() {
        for (String line : TermSession.WELCOME.split("\r\n")) {
            assertTrue("too wide for a narrow device: " + line, line.length() <= 40);
        }
        assertTrue(ai.ibiko.chat.ui.TerminalActivity.DEFAULT_SP <= 11f);
    }
}
