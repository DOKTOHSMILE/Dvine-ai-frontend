package ai.dvine.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PhoneSuiteTest {
    @Test
    public void thePhoneDoesTheMechanicalWork() {
        assertTrue(PhoneSuite.local("git"));
        assertTrue(PhoneSuite.local("search"));
        assertTrue(PhoneSuite.local("read_file"));
        assertFalse(PhoneSuite.local("chat"));
        assertTrue(PhoneSuite.NOTE.contains("on the device"));
        assertTrue(PhoneSuite.NOTE.contains("not the whole project"));
    }

    @Test
    public void fetchMainAndMergeIsReadWithoutAModel() {
        PhoneSuite.GitOrder both = PhoneSuite.read("fetch main and merge");
        assertEquals("sync", both.action);
        assertEquals("main", both.branch);
        assertEquals("", both.path);

        PhoneSuite.GitOrder origin = PhoneSuite.read("git fetch origin main and merge to fix conflicts");
        assertEquals("sync", origin.action);
        assertEquals("main", origin.branch);

        PhoneSuite.GitOrder named = PhoneSuite.read("merge origin/main in app");
        assertEquals("merge", named.action);
        assertEquals("main", named.branch);
        assertEquals("app", named.path);

        PhoneSuite.GitOrder update = PhoneSuite.read("update from main");
        assertEquals("sync", update.action);
        assertEquals("main", update.branch);

        assertEquals("fetch", PhoneSuite.read("fetch origin/master").action);
        assertEquals("master", PhoneSuite.read("fetch origin/master").branch);
        assertNull(PhoneSuite.read("fetch the image of a bicycle"));
        assertNull(PhoneSuite.read("update the login screen"));
        assertNull(PhoneSuite.read("can you merge main?"));
        assertNull(PhoneSuite.read("bring"));
    }
}
