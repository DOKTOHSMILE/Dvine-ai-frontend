package ai.ibiko.ibiko1.comprehend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import org.junit.Test;

/** Dictionary and common-sense questions answered offline, and the large files unpacked once from the app. */
public class LookupTest {
    static Lookup lookup() {
        return new Lookup(ComprehensionTest.dict(), ComprehensionTest.concepts());
    }

    private static void has(String q, String part) {
        String a = lookup().answer(q);
        System.out.println(q + " -> " + a);
        assertTrue(q + " -> " + a, a != null && a.toLowerCase().contains(part));
    }

    @Test
    public void wordsTheirMeaningsSynonymsAndOpposites() {
        has("What does happy mean?", "joy");
        has("define knives", "knife");
        has("meaning of bank", "(noun)");
        has("synonym of big", "large");
        has("what is the opposite of happy", "unhappy");
        assertNull(lookup().answer("what does zzzqqq mean"));
        assertNull("not a dictionary question", lookup().answer("take the red tyre off the car"));
    }

    @Test
    public void commonSense() {
        has("What is a knife used for?", "cut");
        has("what can a bird do", "fly");
        has("parts of a car", "engine");
        has("where would you find a pillow", "bed");
    }

    @Test
    public void theLargeFilesAreUnpackedOnce() throws Exception {
        File dir = Files.createTempDirectory("knowledge").toFile();
        java.util.concurrent.atomic.AtomicInteger opened = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Function<String, java.io.InputStream> was = ai.ibiko.ibiko1.Plug.asset;
        ai.ibiko.ibiko1.Plug.asset = p -> {
            opened.incrementAndGet();
            try {
                return new FileInputStream(new File("../ibiko1/src/main/assets", p));
            } catch (java.io.IOException e) {
                return null;
            }
        };
        try {
            File a = ai.ibiko.ibiko1.Store.unpacked(dir, "local_facts.tsv");
            File b = ai.ibiko.ibiko1.Store.unpacked(dir, "local_facts.tsv");
            assertEquals(a, b);
            assertEquals(new File("../ibiko1/src/main/assets/knowledge/local_facts.tsv").length(), a.length());
            assertEquals("read from the app only the first time", 1, opened.get());
            assertNull(ai.ibiko.ibiko1.Store.unpacked(dir, "nothing.tsv"));
        } finally {
            ai.ibiko.ibiko1.Plug.asset = was;
        }
    }
}
