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
        assertEquals("a word outside the dictionary: the mark and two letters, the same on every device", 3, Glyphs.length(name));
        assertEquals("its id comes from its letters, not from the order words were met", Symbols.hashed("xqzibikoword"), Symbols.id("xqzibikoword"));
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

    /** Packed: about half the bytes for prose, the same text back, the same bytes from any engine. */
    @Test
    public void packedTextIsSmallerExactAndTheSameEverywhere() throws Exception {
        WordBook.open(ShorthandTest.lexicon());
        String prose = "The capital of Nigeria is Abuja. It was chosen because it lies in the centre of the country,"
                + " and the government moved there from Lagos in 1991. Changes were planned carefully.";
        byte[] pack = Pack.pack(prose);
        assertEquals(prose, Pack.unpack(pack));
        assertTrue(pack.length + " of " + prose.length(), pack.length < prose.length() * 6 / 10);
        org.junit.Assert.assertArrayEquals("the same text packs to the same bytes", pack, Pack.pack(prose));
        String odd = "Tabs\there, a\r\nWindows line, an emoji \uD83D\uDE00, iPhone, 007, 3.14 and  two spaces.";
        assertEquals(odd, Pack.unpack(Pack.pack(odd)));
        assertEquals(odd, Glyphs.text(Glyphs.script(odd)));
        // a brain file kept in codes reads back the same, and plain files written before still read
        java.io.File f = java.io.File.createTempFile("brain", ".tsv");
        CodeFile.write(f, "what capital nigeria\tAbuja is the capital.\t3\t0\n");
        assertTrue("kept as codes, not words", java.nio.file.Files.readAllBytes(f.toPath())[0] == 'I');
        assertEquals("what capital nigeria\tAbuja is the capital.\t3\t0", CodeFile.lines(f).get(0));
        java.nio.file.Files.write(f.toPath(), "plain\tline\n".getBytes("UTF-8"));
        assertEquals("plain\tline", CodeFile.lines(f).get(0));
    }
}
