package ai.ibiko.gate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClaimGuardTest {
    @Test
    public void claimsOfThingsDoneAreNoticed() {
        assertEquals("sent", ClaimGuard.claimed("Done! I've sent the message to your landlord."));
        assertEquals("saved", ClaimGuard.claimed("I have saved the file to your Downloads."));
        assertEquals("posted", ClaimGuard.claimed("Great news. I posted it on X just now."));
        assertEquals("scheduled", ClaimGuard.claimed("I've just scheduled the meeting for Friday."));
        assertEquals("set a reminder", ClaimGuard.claimed("I've set a reminder for 8 PM."));
    }

    @Test
    public void offersAndOtherPeoplesActsAreNot() {
        assertNull(ClaimGuard.claimed("I can send it once you say yes."));
        assertNull(ClaimGuard.claimed("You sent the file yesterday, so check your email."));
        assertNull(ClaimGuard.claimed("Shall I post it on X?"));
        assertNull(ClaimGuard.claimed("I have created a plan below: first, list your costs."));
        assertNull(ClaimGuard.claimed(null));
    }

    @Test
    public void theReplySaysNothingWasDoneUnlessItWas() {
        String r = ClaimGuard.check("I've sent the message.", false);
        assertTrue(r.startsWith("I've sent the message."));
        assertTrue(r.contains("Nothing was actually sent"));
        assertEquals("I've sent the message.", ClaimGuard.check("I've sent the message.", true));
        assertEquals("Paris is the capital.", ClaimGuard.check("Paris is the capital.", false));
    }

    @Test
    public void aSavedFileIsReadBack() {
        byte[] five = {1, 2, 3, 4, 5};
        assertNull(Verify.saved(() -> new java.io.ByteArrayInputStream(five), 5));
        assertTrue(Verify.saved(() -> new java.io.ByteArrayInputStream(five), 6).contains("5 bytes, not the 6"));
        assertTrue(Verify.saved(() -> null, 5).contains("can't be opened"));
        assertTrue(Verify.saved(() -> {
            throw new java.io.FileNotFoundException("gone");
        }, 5).contains("gone"));
    }
}
