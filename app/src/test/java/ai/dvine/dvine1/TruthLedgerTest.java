package ai.dvine.dvine1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import ai.dvine.dvine1.formulas.Truth;
import ai.dvine.dvine1.formulas.Truth.Verdict;
import ai.dvine.dvine1.formulas.TruthLedger;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** G9: the true and the false are both kept, recognised again, and told to the brain. */
public class TruthLedgerTest {
    @Test
    public void bothSidesAreKeptAndRecognisedAgainAfterARestart() throws Exception {
        BuiltIn world = new BuiltIn(new FileInputStream("src/main/assets/knowledge/world_facts.tsv"));
        File f = new File(Files.createTempDirectory("ledger").toFile(), "truth.tsv");
        TruthLedger ledger = new TruthLedger(f);
        List<Truth.Checked> checked = Truth.question("Lagos is the capital of Nigeria. The capital of Ghana is Accra. Ghana is lovely.",
                Arrays.asList(world::check));
        ledger.record(checked, "out", 1000);
        ledger.record(checked.subList(0, 1), "in", 2000); // the same mistake again
        assertEquals(1, ledger.counts()[0]);
        assertEquals(1, ledger.counts()[1]);

        TruthLedger again = new TruthLedger(f); // after the app restarts
        Truth.Checked c = again.check("Lagos is the capital of Nigeria!");
        assertEquals(Verdict.FALSE, c.verdict);
        assertEquals("The capital of Nigeria is Abuja.", c.correction);
        assertEquals("checked before", c.source);
        assertEquals(Verdict.TRUE, again.check("The capital of Ghana is Accra.").verdict);
        assertEquals(Verdict.UNKNOWN, again.check("Ghana is lovely.").verdict);
        TruthLedger.Entry e = again.entries().get(again.entries().size() - 1);
        assertEquals(2, e.seen);
        assertEquals("in", e.way);

        String told = again.about("what is the capital of nigeria", 3);
        assertTrue(told, told.contains("found false (never repeat these): “Lagos is the capital of Nigeria.”, in fact: The capital of Nigeria is Abuja."));
        assertTrue(told, told.contains("found true: “The capital of Ghana is Accra.”"));
        again.forget("Lagos is the capital of Nigeria.");
        assertEquals(0, again.counts()[1]);
    }
}
