package ai.ibiko.learn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.Test;

/** Every English word a fixed id from the dictionary; the interpreter turns words into short codes and back. */
public class ShorthandTest {
    static File lexicon() {
        File f = new File("src/main/assets/knowledge/lexicon.tsv");
        return f.isFile() ? f : new File("app/src/main/assets/knowledge/lexicon.tsv");
    }

    @Test
    public void everyDictionaryWordHasAFixedId() {
        assertTrue(WordBook.open(lexicon()));
        assertEquals(152_137, WordBook.size());
        assertEquals(WordBook.FIRST + 135_258, WordBook.id("telephone")); // its place in the sorted dictionary
        assertEquals("telephone", WordBook.word(WordBook.id("telephone")));
        assertEquals("noun", WordBook.pos(WordBook.id("telephone")));
        assertEquals("adjective", WordBook.pos(WordBook.id("luminous")));
        assertEquals("determiner", WordBook.pos(WordBook.id("the")));
        assertEquals(0, WordBook.id("zzqxv"));
        assertEquals("a dictionary word's id is fixed, not made", WordBook.id("kumquat"), Symbols.id("kumquat"));
        assertTrue("a word outside it gets its own", Symbols.id("ibikozz") >= Symbols.CUSTOM);
    }

    @Test
    public void wordsBecomeShortCodesAndBack() {
        WordBook.open(lexicon());
        String codes = Shorthand.codes("Abuja is the capital of Nigeria.");
        assertEquals("6 words, 5 spaces, the stop: every character has its code", 12, codes.split(" ").length);
        assertEquals("the space has its own code", Shorthand.code(WordBook.id(" ")), codes.split(" ")[1]);
        assertTrue(codes, codes.startsWith("^")); // a capital is marked, the word filed in lower case
        assertEquals("Abuja is the capital of Nigeria.", Shorthand.words(codes));
        assertEquals("NASA launched it (in 1969), didn't it?", Shorthand.words(Shorthand.codes("NASA launched it (in 1969), didn't it?")));
        assertEquals("2x9m".length(), Shorthand.code(WordBook.id("telephone")).length());
        String spaced = "Two  spaces,\na new line\tand a tab.";
        assertEquals("spacing comes back exactly", spaced, Shorthand.words(Shorthand.codes(spaced)));
        assertEquals("space", WordBook.pos(WordBook.id(" ")));
    }

    @Test
    public void apiAnswersAreConfirmedOrOverturned() throws Exception {
        File dir = java.nio.file.Files.createTempDirectory("box").toFile();
        BrainBox box = new BrainBox(new File(dir, "brainbox.tsv"));
        String q = "what is the capital of Australia";
        box.learnTalk(q, "The capital of Australia is Sydney.");
        assertEquals("V2: one sighting, kept", "The capital of Australia is Sydney.", box.recallTalk(q));
        box.learnTalk(q, "I couldn't reach api.example.com (Unable to resolve host). Tap Retry.");
        assertEquals("V1: a failure is never learned", 1, box.talkAbout(q).size());
        box.learnTalk(q, "The capital of Australia is Canberra.");
        assertEquals("V4: one answer against does not overturn", "The capital of Australia is Sydney.", box.recallTalk(q));
        box.learnTalk(q, "Canberra is the capital of Australia.");
        List<String[]> kept = box.talkAbout(q);
        boolean sydneyFalse = false;
        for (String[] k : kept) {
            sydneyFalse |= k[0].contains("Sydney") && "false".equals(k[3]);
        }
        assertTrue("V5: said otherwise twice, more than it was confirmed: marked false", sydneyFalse);
        assertEquals("V6: the new answer takes its place", "The capital of Australia is Canberra.", box.recallTalk(q));
        assertTrue(Corroborate.agrees("Canberra is the capital of Australia.", "The capital of Australia is Canberra."));
        assertFalse(Corroborate.overturned(5, 2));
    }
}
