package ai.ibiko.dvine1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import ai.ibiko.dvine1.formulas.Foresight;
import java.io.File;
import java.nio.file.Files;
import org.junit.Test;

public class ForesightTest {
    private static final long MIN = 60_000L;

    @Test
    public void aHabitIsLearnedFromWhatFollowsWhatAndOfferedOnlyWhenSure() throws Exception {
        File f = new File(Files.createTempDirectory("fore").toFile(), "foresight.tsv");
        Foresight fs = new Foresight(f);
        long t = 1_000_000L;
        // three sessions: after fixing a bug the pilot asks to push it
        for (int i = 0; i < 3; i++) {
            fs.heard("Fix the login crash in the app", Methods.kind("Fix the login crash in the app"), t);
            fs.heard("Push it to GitHub", Methods.kind("Push it to GitHub"), t + MIN);
            t += 24 * 60 * MIN; // P7: the next day is a new session
        }
        Foresight.Guess g = fs.next(Methods.kind("Fix the upload error"));
        assertNotNull("seen three times: offered", g);
        assertEquals("Push it to GitHub", g.example);
        assertTrue(g.p >= Foresight.SURE);
        String hint = fs.hint(Methods.kind("Fix the upload error"));
        assertTrue(hint, hint.contains("Think ahead") && hint.contains("Push it to GitHub") && hint.contains("never do it unasked"));
        assertEquals("kept on the device", g.example, new Foresight(f).next(Methods.kind("Fix the save bug")).example);
    }

    @Test
    public void notSureNotOfferedButTheLookBeforeCheckIsAlwaysThere() throws Exception {
        Foresight fs = new Foresight(new File(Files.createTempDirectory("fore").toFile(), "foresight.tsv"));
        fs.heard("Fix the login crash", Methods.kind("Fix the login crash"), 0);
        fs.heard("Push it to GitHub", Methods.kind("Push it to GitHub"), MIN);
        assertNull("seen once: no habit yet", fs.next(Methods.kind("Fix the save bug")));
        assertTrue(fs.hint(Methods.kind("Fix the save bug")).contains("what could go wrong"));
        // P7: a long gap is a new session, not a habit
        Foresight gap = new Foresight(new File(Files.createTempDirectory("fore").toFile(), "foresight.tsv"));
        for (int i = 0; i < 5; i++) {
            gap.heard("Fix the login crash", Methods.kind("Fix the login crash"), i * 2 * Foresight.SESSION_MS);
            gap.heard("What is the weather", Methods.kind("What is the weather"), i * 2 * Foresight.SESSION_MS + Foresight.SESSION_MS + 1);
        }
        assertNull(gap.next(Methods.kind("Fix the save bug")));
    }

    @Test
    public void aHabitThatStopsFadesAndPrivateDetailsAreMasked() throws Exception {
        Foresight fs = new Foresight(new File(Files.createTempDirectory("fore").toFile(), "foresight.tsv"));
        long t = 0;
        for (int i = 0; i < 4; i++) {
            fs.heard("Fix the login crash", Methods.kind("Fix the login crash"), t);
            fs.heard("Email the report to val@example.com", Methods.kind("Email the report to val@example.com"), t + MIN);
            t += 2 * MIN;
        }
        Foresight.Guess g = fs.next(Methods.kind("Fix the save bug"));
        assertNotNull(g);
        assertTrue(g.example, !g.example.contains("val@example.com"));
        for (int i = 0; i < 12; i++) { // the pilot now pushes after a fix, every time
            fs.heard("Fix the login crash", Methods.kind("Fix the login crash"), t);
            fs.heard("Push it to GitHub", Methods.kind("Push it to GitHub"), t + MIN);
            t += 2 * MIN;
        }
        assertEquals("the newer habit wins", "Push it to GitHub", fs.next(Methods.kind("Fix the save bug")).example);
    }
}
