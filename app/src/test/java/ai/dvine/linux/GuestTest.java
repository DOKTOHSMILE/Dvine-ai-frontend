package ai.dvine.linux;

import static org.junit.Assert.assertEquals;
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
        Guest.Answer answer = Guest.split("hi\n/usr\n" + Guest.MARK + "0\nThis ran in DVINE's Linux.\n", "/root");
        assertEquals("hi\n", answer.text);
        assertEquals("/usr", answer.cwd);
        assertEquals(0, answer.exit);
        assertEquals("~/bin", Guest.prompt("/root/bin"));
        assertEquals("~", Guest.prompt("/root"));
    }

    @Test
    public void aQuoteInTheFolderDoesNotBreakTheScript() {
        assertEquals("'a'\\''b'", Guest.quote("a'b"));
        Guest.Answer same = Guest.split("no marker here\n", "/etc");
        assertEquals("/etc", same.cwd);
        assertEquals("no marker here\n", same.text);
    }
}
