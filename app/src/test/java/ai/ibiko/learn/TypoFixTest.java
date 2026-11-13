package ai.ibiko.learn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;

import org.junit.Before;
import org.junit.Test;

/** Typos fixed with the dictionary and word order the app ships; everything that isn't a typo left exactly as it was. */
public class TypoFixTest {
    @Before
    public void open() {
        WordBook.open(ShorthandTest.lexicon());
        if (!WordRank.ready()) {
            WordRank.load(ShorthandTest.lexicon());
        }
    }

    private static String fixed(String text) {
        return TypoFix.fix(text, TypoFix.DEVICE, Collections.<String>emptySet()).text;
    }

    @Test
    public void clearTyposAreFixed() {
        assertEquals("I will receive the package tomorrow", fixed("I will recieve the pakage tomorow"));
        assertEquals("Please explain the difference", fixed("Please explian the diffrence"));
        TypoFix.Result r = TypoFix.fix("recieve it", TypoFix.DEVICE, Collections.<String>emptySet());
        assertEquals("Read “recieve” as “receive”.", r.said());
        assertEquals("a capital at a sentence's start stays", "Receive it now.", fixed("Recieve it now."));
    }

    @Test
    public void whatIsNotATypoIsLeftAlone() {
        String[] same = {
            "He went home and the children played",          // inflected and irregular forms
            "She pushed the commits and pulled the repos",
            "Abeg wetin dey happen for Lagos",               // Pidgin, short words
            "Ask Chinedu about the Doktohsmile account",      // names: capitals mid-sentence
            "I'm sure it doesn't matter",                    // contractions
            "git push origin main",                          // a command
            "the value is `recieve` in config.json",         // code
            "see https://example.com/recieve/pakage",        // a link
            "send it to ada@recieve.ng",                     // an address
            "the NASA and HTTPS standards",                  // capitals
            "version v2beta and build123",                   // letters with digits
        };
        for (String s : same) {
            assertEquals(s, s, fixed(s));
        }
        assertNull(TypoFix.fix("nothing wrong here", TypoFix.DEVICE, Collections.<String>emptySet()).said());
    }

    @Test
    public void thePersonsOwnWordsAreTheirs() throws Exception {
        File f = new File(Files.createTempDirectory("own").toFile(), "own_words.tsv");
        OwnWords own = new OwnWords(f);
        // a word the dictionary lacks, with no clear fix: theirs once they've used it in two messages
        own.heard("meet me at Zubbystores tomorrow", TypoFix.DEVICE);
        assertFalse(own.own().contains("zubbystores"));
        own.heard("zubbystores opens at nine", TypoFix.DEVICE);
        assertTrue(new OwnWords(f).own().contains("zubbystores"));
        // a habitual misspelling keeps being fixed until they say to keep it
        own.heard("I recieve mail", TypoFix.DEVICE);
        own.heard("did you recieve it", TypoFix.DEVICE);
        assertFalse(own.own().contains("recieve"));
        own.keep("recieve");
        assertEquals("kept: theirs now", "I recieve mail",
                TypoFix.fix("I recieve mail", TypoFix.DEVICE, new HashSet<String>(own.own())).text);
    }

    @Test
    public void knownInAnyForm() {
        for (String w : new String[] {"running", "studies", "pushed", "children", "went", "goes", "emails", "downloaded"}) {
            assertTrue(w, TypoFix.known(w, TypoFix.DEVICE));
        }
        assertFalse(TypoFix.known("recieve", TypoFix.DEVICE));
    }
}
