package ai.dvine.llm;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

/**
 * The three think tanks on the phone's own model (a desktop build of the same engine). Runs only with
 * DVINE_NATIVE_LIB and DVINE_MODEL set.
 */
public class OfflineCouncilTest {
    @Test
    public void threeTanksOnTheOfflineModel() {
        String lib = System.getenv("DVINE_NATIVE_LIB"), gguf = System.getenv("DVINE_MODEL");
        if (lib == null || gguf == null) {
            return;
        }
        assertTrue(NativeLlm.loadFrom(lib));
        File f = new File(gguf);
        LocalModel m = new LocalModel(f.getParentFile(), new ModelSpec("test", f.getName(), "", f.length(), "", "-"), true);
        m.context(8192);
        assertTrue(m.load(4));
        long t0 = System.currentTimeMillis();
        ai.dvine.agent.Council.Result r = ai.dvine.agent.Council.run((sys, p) -> m.task(sys, p, 600, null),
                "Should I quit my job to start a phone repair business in Lagos?", "", "obi",
                (line, who, said) -> System.out.println("COUNCIL " + who + " (" + line + "):\n" + said + "\n"), () -> false);
        System.out.println("COUNCIL answer after " + (System.currentTimeMillis() - t0) / 1000 + " s:\n" + (r == null ? "null" : r.answer));
        assertTrue(r != null && r.answer.length() > 40);
    }
}
