package ai.ibiko.learn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

/** Every word one id for good; the mind's stores find by ids and give the same answers as reading everything. */
public class SymbolsTest {
    @Test
    public void oneWordOneId() {
        int a = Symbols.id("gradle");
        assertEquals(a, Symbols.id("gradle"));
        assertNotEquals(a, Symbols.id("kotlin"));
        assertEquals(-1, Symbols.known("neverseenwordxyz"));
        assertEquals(0, Symbols.id(""));
    }

    @Test
    public void factsAreFoundByIdAmongThousands() throws Exception {
        File dir = java.nio.file.Files.createTempDirectory("facts").toFile();
        ai.ibiko.ibiko1.Facts facts = new ai.ibiko.ibiko1.Facts(new File(dir, "facts.tsv"));
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 3000; i++) {
            many.append("Inventor").append(i).append(" invented the gadget").append(i).append(". ");
        }
        facts.learn(many.toString(), "", 2, 0L);
        facts.learn("Alexander Graham Bell invented the telephone.", "", 2, 0L); // two sightings: strong enough to answer
        String a = facts.answer("Who invented the telephone?");
        assertTrue(a, a != null && a.contains("Bell"));
        assertTrue("by id: " + facts.lastRead() + " of " + facts.size() + " facts read", facts.lastRead() <= 2);
    }
}
