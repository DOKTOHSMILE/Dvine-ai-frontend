package ai.dvine.llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ai.dvine.ops.pipe.ConfirmGate;

public class ReplyLeaseTest {
    @Test
    public void anOldTimerDoesNotOwnTheNewReply() {
        ReplyLease lease = new ReplyLease();
        int first = lease.open();
        int second = lease.open();
        assertFalse(lease.mine(first));
        assertTrue(lease.mine(second));
    }

    @Test
    public void aWorldQuestionCanBeCheckedWithoutTheModel() {
        assertTrue(WorldLookup.shouldSearch(true, false, false, true));
        assertFalse(WorldLookup.shouldSearch(false, false, false, true));
        assertFalse(WorldLookup.shouldSearch(true, true, false, true));
        assertFalse(WorldLookup.shouldSearch(true, false, true, true));
        assertFalse(WorldLookup.shouldSearch(true, false, false, false));
    }

    @Test
    public void onlyYesOrNoSettlesAPendingAction() {
        assertEquals(ConfirmGate.Choice.YES, ConfirmGate.read("Yes."));
        assertEquals(ConfirmGate.Choice.NO, ConfirmGate.read("no"));
        assertEquals(ConfirmGate.Choice.OTHER, ConfirmGate.read("what is a triangle"));
    }

    @Test
    public void rememberedAnswersAreShortened() {
        String longAnswer = "a".repeat(500);
        assertEquals(420, LocalModel.clipMemory(longAnswer).length());
        assertTrue(LocalModel.clipMemory(longAnswer).endsWith("..."));
        assertEquals("short", LocalModel.clipMemory(" short "));
    }
}
