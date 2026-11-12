package ai.ibiko.learn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Ibiko's own script: one character a word, and numbers coded by their value. */
public class GlyphsTest {
    @Test
    public void oneCharacterAWordAndBackExactly() {
        WordBook.open(ShorthandTest.lexicon());
        String s = Glyphs.script("Abuja is the capital of Nigeria.");
        assertEquals("6 words, 5 spaces, the stop, and the capitals' marks: 14 characters", 14, Glyphs.length(s));
        assertEquals("Abuja is the capital of Nigeria.", Glyphs.text(s));
        String hard = "NASA  launched Apollo 11 in 1969,\nnot 007.";
        assertEquals(hard, Glyphs.text(Glyphs.script(hard)));
        String telephone = Glyphs.script("telephone");
        assertEquals("a nine-letter word is one character", 1, Glyphs.length(telephone));
        assertTrue("its letter is Ibiko's own (private use)",
                Character.getType(telephone.codePointAt(0)) == Character.PRIVATE_USE);
        String name = Glyphs.script("xqzibikoword");
        assertEquals("a word outside the dictionary is one character too", 1, Glyphs.length(name));
        assertEquals("xqzibikoword", Glyphs.text(name));
    }

    @Test
    public void numbersAreCodedByTheirValue() {
        assertEquals(Symbols.NUMBER + 1969, Symbols.id("1969"));
        assertEquals("1969", Symbols.word(Symbols.id("1969")));
        assertEquals("#1ip", Shorthand.codes("1969"));
        assertEquals("Apollo 11 landed in 1969.", Shorthand.words(Shorthand.codes("Apollo 11 landed in 1969.")));
        assertEquals("a leading zero stays a word of its own", -1, Symbols.number("007"));
        assertEquals("007", Shorthand.words(Shorthand.codes("007")));
        assertEquals("3.14", Shorthand.words(Shorthand.codes("3.14")));
    }
}
