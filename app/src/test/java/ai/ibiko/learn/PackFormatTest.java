package ai.ibiko.learn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/** Pack format 2: codes by how often words are used, a fingerprint of that order, a checksum; old forms converted. */
public class PackFormatTest {
    @Before
    public void open() {
        WordBook.open(ShorthandTest.lexicon());
        if (!WordRank.ready()) {
            WordRank.load(ShorthandTest.lexicon());
        }
    }

    /** The shipped order is exactly what the dictionary's definitions count to: any device, the same codes. */
    @Test(timeout = 120000)
    public void theShippedOrderIsTheCountedOne() throws Exception {
        File shipped = new File(ShorthandTest.lexicon().getParentFile(), WordRank.FILE);
        assertArrayEquals("run WordRank.build after a dictionary change", WordRank.build(ShorthandTest.lexicon()),
                Files.readAllBytes(shipped.toPath()));
        assertTrue(WordRank.ready());
        assertTrue("the space is one of the first codes", WordRank.code(WordBook.id(" ")) < 32);
        assertTrue("'the' takes one byte", WordRank.code(WordBook.id("the")) < 32);
        assertTrue("a common word two bytes", WordRank.code(WordBook.id("water")) < 4096);
        assertTrue("a rare word more", WordRank.code(WordBook.id("plectania")) >= 4096);
    }

    @Test
    public void everyKindOfTextComesBackExactly() throws Exception {
        String[] texts = {
                "",
                "The capital of Nigeria is Abuja. It was chosen in 1991.",
                "Tabs\there,\ta\r\nWindows line\n\nand  two spaces,   three.",
                "the rapist", "therapist", "NASA and iPhone and McDonald's, re-verified",
                "Emoji 😀 and a family 👨‍👩‍👧 and café / café",
                "中文 العربية हिन्दी Yorùbá ẹ",
                "007, 3.14, 600000001 and -5", "walking changes appended quickly faster", "Doktohsmile ran `git sync` on repos/Dvine",
        };
        for (String t : texts) {
            byte[] p = Pack.pack(t);
            assertEquals(2, Pack.format(p));
            assertEquals(t, Pack.unpack(p));
            assertArrayEquals("the same text, the same bytes", p, Pack.pack(t));
        }
        assertNotEquals("two words are not one", Pack.unpack(Pack.pack("the rapist")), Pack.unpack(Pack.pack("therapist")));
    }

    @Test
    public void damageAndAnotherOrderAreRefusedNotMisread() throws Exception {
        String t = "Abuja is the capital of Nigeria. It has 3 million people.";
        byte[] p = Pack.pack(t);
        for (int i = 4; i < p.length; i++) {
            byte[] bad = p.clone();
            bad[i] ^= 0x10;
            try {
                // either refused, or (a code with the very same word, e.g. a mark listed twice) the same text: never
                // another text
                assertEquals("byte " + i + " changed and it read another text", t, Pack.unpack(bad));
            } catch (IOException expected) {
                // refused
            }
        }
        try {
            Pack.unpack(java.util.Arrays.copyOf(p, p.length - 3));
            fail("cut short and still read");
        } catch (IOException expected) {
            // refused
        }
    }

    /** Format 1 packs, made before, still read; and they are smaller in format 2. */
    @Test
    public void format1StillReadsAndFormat2IsSmaller() throws Exception {
        String t = "We fixed the Gradle path, then the tests passed and the build was shipped to the repository.";
        WordRank.drop();
        byte[] old;
        try {
            old = Pack.pack(t);
        } finally {
            WordRank.load(ShorthandTest.lexicon());
        }
        assertEquals(1, Pack.format(old));
        assertEquals(t, Pack.unpack(old));
        byte[] now = Pack.pack(t);
        assertEquals(2, Pack.format(now));
        assertTrue(now.length + " vs " + old.length, now.length - 12 < old.length - 7); // without the headers
    }

    /** A store kept as plain words or format 1 is read the same and kept from then on in format 2. */
    @Test
    public void oldStoresAreConvertedToFormat2() throws Exception {
        File dir = Files.createTempDirectory("brain").toFile();
        File plain = new File(dir, "knowledge.tsv");
        Files.write(plain.toPath(), "+\tOTHER\tWhat is Abuja?\tAbuja is the capital of Nigeria.\n".getBytes(StandardCharsets.UTF_8));
        File coded = new File(new File(dir, "deeper"), "brainbox.tsv");
        coded.getParentFile().mkdirs();
        WordRank.drop();
        try {
            CodeFile.write(coded, "how fast\tAs fast as this device allows.\n");
        } finally {
            WordRank.load(ShorthandTest.lexicon());
        }
        assertFalse(CodeFile.current(Files.readAllBytes(coded.toPath())));
        File log = new File(dir, "log.txt");
        Files.write(log.toPath(), "a plain log, not a store\n".getBytes(StandardCharsets.UTF_8));

        assertEquals("only coded files are swept", 1, CodeFile.upgradeAll(dir));
        assertTrue(CodeFile.current(Files.readAllBytes(coded.toPath())));
        assertEquals("how fast\tAs fast as this device allows.", CodeFile.lines(coded).get(0));
        assertEquals("a plain log, not a store\n", new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8));

        assertEquals("+\tOTHER\tWhat is Abuja?\tAbuja is the capital of Nigeria.", CodeFile.lines(plain).get(0));
        assertTrue("a plain store is converted when it is read", CodeFile.current(Files.readAllBytes(plain.toPath())));
        assertEquals("+\tOTHER\tWhat is Abuja?\tAbuja is the capital of Nigeria.", CodeFile.lines(plain).get(0));
    }

    /** A chat's conversation kept in the script (older versions) moves into the packed file, every sentence exact. */
    @Test
    public void aConversationInTheScriptIsConvertedExactly() throws Exception {
        File files = Files.createTempDirectory("files").toFile();
        String chat = "c1";
        File old = Identifier.file(files, chat);
        String a = "Answers you confirmed (12):", b = "- Pull the latest repo update → Progress so far: done.";
        Files.write(old.toPath(), ("user\tm1#0\t" + Glyphs.script("What have you learned?") + "\n"
                + "assistant\tm2#0\t" + Glyphs.script(a) + "\n"
                + "assistant\tm2#1\t" + Glyphs.script(b) + "\n").getBytes(StandardCharsets.UTF_8));
        long scriptBytes = old.length();
        assertEquals(1, Identifier.convertAll(files));
        assertFalse("the script file is gone once every line converted exactly", old.isFile());
        File packed = Identifier.packed(files, chat);
        assertTrue(packed.length() + " vs " + scriptBytes, packed.length() < scriptBytes);
        List<String[]> lines = Identifier.lines(files, chat);
        assertEquals(3, lines.size());
        assertArrayEquals(new String[]{"user", "m1#0", "What have you learned?"}, lines.get(0));
        assertArrayEquals(new String[]{"assistant", "m2#0", a}, lines.get(1));
        assertArrayEquals(new String[]{"assistant", "m2#1", b}, lines.get(2));
        // new messages go after them, in the same file
        Identifier.save(files, chat, "user", "m3", "Thanks. Now pull again.");
        Identifier.flush();
        lines = Identifier.lines(files, chat);
        assertEquals(5, lines.size());
        assertArrayEquals(new String[]{"user", "m3#1", "Now pull again."}, lines.get(4));
    }
}
