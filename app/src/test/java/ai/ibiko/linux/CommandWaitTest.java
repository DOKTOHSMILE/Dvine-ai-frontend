package ai.ibiko.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CommandWaitTest {
    @Test
    public void aCloneIsNotCutOffAtThreeMinutes() {
        String clone = "git clone https://github.com/DOKTOHSMILE/Dvine-ai-frontend.git";
        assertTrue(CommandWait.longJob(clone));
        assertEquals(CommandWait.LONG_MS, CommandWait.ms(clone));
        assertTrue(CommandWait.longJob("git -c http.version=HTTP/1.1 clone --depth 1 https://github.com/a/b.git"));
        assertTrue(CommandWait.longJob("apk add git"));
        assertFalse(CommandWait.longJob("ls /workspace"));
        assertEquals(CommandWait.SHORT_MS, CommandWait.ms("ls"));
        assertEquals("3 minutes", CommandWait.lasted(CommandWait.SHORT_MS));
        assertEquals("2 hours", CommandWait.lasted(CommandWait.LONG_MS));
    }
}
