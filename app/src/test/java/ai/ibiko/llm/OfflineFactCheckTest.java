package ai.ibiko.llm;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

/** The fact check on the phone's own model (desktop build), with fixed evidence. Runs with IBIKO_NATIVE_LIB and IBIKO_MODEL. */
public class OfflineFactCheckTest {
    @Test
    public void judgesAClaimAgainstEvidence() {
        String lib = System.getenv("IBIKO_NATIVE_LIB"), gguf = System.getenv("IBIKO_MODEL");
        if (lib == null || gguf == null) {
            return;
        }
        assertTrue(NativeLlm.loadFrom(lib));
        File f = new File(gguf);
        LocalModel m = new LocalModel(f.getParentFile(), new ModelSpec("test", f.getName(), "", f.length(), "", "-"), true);
        m.context(8192);
        assertTrue(m.load(4));
        FactCheck.Result r = FactCheck.run((s, p) -> m.task(s, p, 300, null),
                q -> "Britannica: Abuja officially became the capital of Nigeria on 12 December 1991, replacing Lagos."
                        + " Wikipedia: Lagos has an estimated metropolitan population of about 16 to 21 million.",
                "When did Abuja become Nigeria's capital, and how many people live in Lagos?",
                "Abuja became Nigeria's capital in 1976, and Lagos has about 16 million people.", () -> false);
        for (FactCheck.Claim c : r.claims) {
            System.out.println("FACT " + c.verdict + " | " + c.text + " | " + c.source + " | " + c.fix);
        }
        System.out.println("FACT correction: " + r.correction());
        assertTrue(r.count(FactCheck.Verdict.CONTRADICTED) >= 1);
    }
}
