package ai.ibiko.llm;

import static org.junit.Assert.assertNotNull;

import java.io.File;

import org.junit.Test;

/** The interpreter on the phone's own model (desktop build). Runs with IBIKO_NATIVE_LIB and IBIKO_MODEL. */
public class OfflineInterpreterTest {
    @Test
    public void pidginAndFrenchInAndBack() {
        String lib = System.getenv("IBIKO_NATIVE_LIB"), gguf = System.getenv("IBIKO_MODEL");
        if (lib == null || gguf == null) {
            return;
        }
        org.junit.Assert.assertTrue(NativeLlm.loadFrom(lib));
        File f = new File(gguf);
        LocalModel m = new LocalModel(f.getParentFile(), new ModelSpec("test", f.getName(), "", f.length(), "", "-"), true);
        m.context(4096);
        org.junit.Assert.assertTrue(m.load(4));
        Interpreter.Brain b = (s, t, n) -> m.task(s, t, n, null);
        String[][] cases = {{"Abeg, wetin be the capital of Ghana?", "PIDGIN"}, {"I no sabi how to cook jollof rice, abeg teach me", "PIDGIN"},
                {"Pourquoi le ciel est bleu ?", "FRENCH"}, {"Bawo ni, kini oruko re?", "YORUBA"}};
        for (String[] c : cases) {
            long t0 = System.currentTimeMillis();
            String en = Interpreter.toEnglish(b, Interpreter.Language.valueOf(c[1]), c[0]);
            System.out.println("INTERP in  " + c[1] + " | " + c[0] + " -> " + en + " (" + (System.currentTimeMillis() - t0) + " ms)");
        }
        long t0 = System.currentTimeMillis();
        String back = Interpreter.fromEnglish(b, Interpreter.Language.PIDGIN,
                "The capital of Ghana is Accra. It sits on the coast and has about two million people.");
        System.out.println("INTERP out PIDGIN -> " + back + " (" + (System.currentTimeMillis() - t0) + " ms)");
        back = Interpreter.fromEnglish(b, Interpreter.Language.FRENCH,
                "The sky looks blue because air scatters blue light more than red light. Run `ls` to list files.");
        System.out.println("INTERP out FRENCH -> " + back);
        assertNotNull(Interpreter.toEnglish(b, Interpreter.Language.FRENCH, "Bonjour, comment ça va ?"));
    }
}
