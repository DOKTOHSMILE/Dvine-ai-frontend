package ai.dvine.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GuestTest {
    @Test
    public void aCommandKeepsTheFolderAndHidesTheMarker() {
        String script = Guest.wrap("/root", "cd /usr && echo hi");
        assertTrue(script.contains("cd '/root'"));
        assertTrue(script.contains("echo hi"));
        assertTrue(script.contains("pwd > /root/.dvine_cwd"));
        assertTrue(!script.contains("echo " + Guest.MARK));
        Guest.Answer answer = Guest.split("hi\n/usr\n" + Guest.MARK + "0\nThis ran in Ibiko's Linux.\n", "/root");
        assertEquals("hi\n", answer.text);
        assertEquals("/usr", answer.cwd);
        assertEquals(0, answer.exit);
        assertEquals("~/bin", Guest.prompt("/root/bin"));
        assertEquals("~", Guest.prompt("/root"));
        String piped = Guest.pipe("cd /usr && echo hi");
        assertTrue(piped.startsWith("eval 'cd /usr && echo hi' > /root/.dvine_out"));
        assertTrue(piped.indexOf(".dvine_cwd") < piped.indexOf(".dvine_status"));
    }

    @Test
    public void aStatusLineEndsTheCommandAndAPartialOneIsHeld() {
        Guest.Stream held = Guest.drain("hello\nDVINE_STAT", "/root");
        assertEquals("hello\n", held.show);
        assertEquals("DVINE_STAT", held.rest);
        assertFalse(held.done);
        Guest.Stream done = Guest.drain("hello\n" + Guest.MARK + "0__/usr\n", "/root");
        assertEquals("hello\n", done.show);
        assertEquals("/usr", done.cwd);
        assertTrue(done.done);
        assertEquals(0, done.exit);
        assertEquals("", done.rest);
    }

    @Test
    public void aQuoteInTheFolderDoesNotBreakTheScript() {
        assertEquals("'a'\\''b'", Guest.quote("a'b"));
        Guest.Answer same = Guest.split("no marker here\n", "/etc");
        assertEquals("/etc", same.cwd);
        assertEquals("no marker here\n", same.text);
    }
}
