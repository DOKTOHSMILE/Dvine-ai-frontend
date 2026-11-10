package ai.dvine.dvine1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import ai.dvine.dvine1.formulas.Reflection;
import java.io.File;
import java.nio.file.Files;
import org.junit.Test;

public class ReflectionTest {
    @Test
    public void theNextMessageSaysHowTheLastReplyWent() {
        assertEquals(Reflection.Outcome.CORRECTED, Reflection.outcome("capital of Australia", "No, that's wrong, it's Canberra"));
        assertEquals(Reflection.Outcome.CORRECTED, Reflection.outcome("fix the bug", "Still broken, the app crashes"));
        assertEquals(Reflection.Outcome.RETRIED, Reflection.outcome("capital of Australia", "try again"));
        assertEquals("the same request asked again", Reflection.Outcome.RETRIED,
                Reflection.outcome("what is the capital of Australia", "the capital of Australia, what is it"));
        assertEquals(Reflection.Outcome.RESTYLED, Reflection.outcome("explain photosynthesis", "too long, make it shorter"));
        assertEquals(Reflection.Outcome.ACCEPTED, Reflection.outcome("explain photosynthesis", "thanks"));
        assertEquals("a new, similar question is not a retry", Reflection.Outcome.ACCEPTED,
                Reflection.outcome("What is the capital of Australia?", "What is the capital of Brazil?"));
        assertEquals("moving on is acceptance", Reflection.Outcome.ACCEPTED, Reflection.outcome("explain photosynthesis", "now draw a cat"));
    }

    @Test
    public void lessonsAndTasteAreLearnedAndHandedToTheNextReply() throws Exception {
        File f = new File(Files.createTempDirectory("refl").toFile(), "reflection.tsv");
        Reflection r = new Reflection(f);
        String kind = Methods.kind("What is the capital of Australia?");
        r.observe(kind, "What is the capital of Australia?", "No, it's Canberra not Sydney");
        r.observe(kind, "What is the capital of Brazil?", "Wrong, it's Brasilia");
        String hint = r.hint(Methods.kind("What is the capital of Canada?"));
        assertTrue(hint, hint.contains("Canberra not Sydney") && hint.contains("do not repeat the mistake"));
        assertTrue("two corrections: slow down and check", hint.contains("check each fact"));
        r.observe("k", "explain", "too long, shorter please");
        r.observe("k", "explain", "make it shorter");
        assertTrue(r.taste().contains("short"));
        assertTrue("kept on the device", new Reflection(f).hint(kind).contains("Canberra"));
    }

    @Test
    public void goodRepliesAndOldMistakesFade() throws Exception {
        Reflection r = new Reflection(new File(Files.createTempDirectory("refl").toFile(), "reflection.tsv"));
        r.observe("k", "fix it", "no, that's not it");
        r.observe("k", "fix it", "wrong again");
        assertTrue(r.score("k") <= Reflection.CAREFUL);
        for (int i = 0; i < 6; i++) {
            r.observe("k", "fix it", "thanks, that works");
        }
        assertTrue("good replies win it back", r.score("k") > 0);
        assertTrue(!r.hint("k").contains("check each fact"));
        r.judged("k", false);
        assertTrue(r.score("k") < 3);
        Reflection masked = new Reflection(new File(Files.createTempDirectory("refl").toFile(), "reflection.tsv"));
        masked.observe("m", "email me", "No, send it to val@example.com");
        assertTrue(!masked.hint("m").contains("val@example.com"));
    }
}
