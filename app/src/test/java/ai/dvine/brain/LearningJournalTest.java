package ai.dvine.brain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class LearningJournalTest {
    @Test
    public void sharingIsOnByDefaultAndStopSharingHoldsEverything() throws Exception {
        File dir = TestPack.tempDir();
        LearningJournal j = new LearningJournal(dir);
        j.add(LearningJournal.TAUGHT_FACT, "my city", "Sapele");
        assertEquals(1, j.pendingCount());
        j.setConsent(false);
        assertTrue(j.pending(10).isEmpty());
        assertEquals(0, j.pendingCount());
        assertTrue(!new LearningJournal(dir).consent());
        j.setConsent(true);
        List<LearningJournal.Entry> p = j.pending(10);
        assertEquals(1, p.size());
        String body = LearningJournal.syncBody("dev-1", p);
        assertTrue(body.contains("\"kind\":\"TAUGHT_FACT\""));
        assertTrue(!body.contains("uploaded"));
        j.markUploaded(Collections.singletonList(p.get(0).id));
        assertTrue(new LearningJournal(dir).pending(10).isEmpty());
        assertTrue(new LearningJournal(dir).consent());
    }

    @Test
    public void secretsAreDropped() throws Exception {
        LearningJournal j = new LearningJournal(TestPack.tempDir());
        assertNull(j.add(LearningJournal.TAUGHT_FACT, "my password", "hunter2"));
        assertNull(j.add(LearningJournal.TAUGHT_FACT, "my account", "0123456789012"));
        assertEquals(0, j.all().size());
    }

    @Test
    public void forget() throws Exception {
        File dir = TestPack.tempDir();
        LearningJournal j = new LearningJournal(dir);
        j.add(LearningJournal.TAUGHT_FACT, "my dog", "Rex");
        j.add(LearningJournal.TAUGHT_FACT, "my cat", "Tom");
        assertTrue(j.forgetLast());
        assertNull(new LearningJournal(dir).recall("my cat"));
        assertEquals("Rex", new LearningJournal(dir).recall("my dog"));
        j.forgetAll();
        assertEquals(0, new LearningJournal(dir).all().size());
    }
}
