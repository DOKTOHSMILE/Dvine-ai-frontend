package ai.ibiko.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/** The terminal screen, checked with typed sequences and with what vim, nano and less really print. */
public class TermEmulatorTest {
    private static final String E = "\u001b";

    private static String real(String name) throws Exception {
        try (InputStream in = TermEmulatorTest.class.getResourceAsStream("/term/" + name + ".raw")) {
            // the recorder adds line ends of its own when the program is stopped: not part of what the program drew
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replaceAll("[\\r\\n]+$", "");
        }
    }

    @Test
    public void textWrapsScrollsAndIsKeptAbove() {
        TermEmulator t = new TermEmulator(10, 3, 100);
        t.write("abcdefghijKLM\r\nrow2\r\nrow3\r\nrow4");
        assertEquals("row2", t.screen()[0]);
        assertEquals("row4", t.screen()[2]);
        assertEquals(2, t.scrollback());
        assertEquals("abcdefghij", t.line(0).text());
        assertTrue("a wrapped line is one line when copied", t.text().startsWith("abcdefghijKLM\nrow2\n"));
        assertEquals(4, t.cursorX());
        assertEquals(2, t.cursorY());
    }

    @Test
    public void cursorMovesEraseAndColors() {
        TermEmulator t = new TermEmulator(20, 5, 0);
        t.write("hello world" + E + "[1;7H" + E + "[K" + E + "[31;44mred" + E + "[0m" + E + "[3;1H" + E + "[38;5;196mX" + E + "[38;2;1;2;3mY");
        assertEquals("hello red", t.screen()[0]);
        assertEquals(1, t.row(0).fg[6]);
        assertEquals(4, t.row(0).bg[6]);
        assertEquals(196, t.row(2).fg[0]);
        assertEquals(TermEmulator.RGB | 0x010203, t.row(2).fg[1]);
        assertEquals(0xFF010203, TermEmulator.argb(t.row(2).fg[1], true));
        t.write(E + "[2J" + E + "[H");
        assertEquals("", t.screen()[0]);
        assertEquals(0, t.cursorY());
    }

    @Test
    public void scrollRegionInsertAndDeleteLines() {
        TermEmulator t = new TermEmulator(10, 5, 10);
        t.write("1\r\n2\r\n3\r\n4\r\n5");
        t.write(E + "[2;4r" + E + "[2;1H" + E + "[M"); // delete line 2 inside rows 2..4
        assertEquals("1", t.screen()[0]);
        assertEquals("3", t.screen()[1]);
        assertEquals("4", t.screen()[2]);
        assertEquals("", t.screen()[3]);
        assertEquals("5", t.screen()[4]);
        t.write(E + "[L");
        assertEquals("", t.screen()[1]);
        assertEquals("3", t.screen()[2]);
        assertEquals("nothing above the screen from a region", 0, t.scrollback());
    }

    @Test
    public void theTerminalAnswersWhatProgramsAsk() {
        TermEmulator t = new TermEmulator(80, 24, 0);
        t.write("ab" + E + "[6n");
        assertEquals(E + "[1;3R", t.takeReply());
        t.write(E + "[c");
        assertEquals(E + "[?1;2c", t.takeReply());
        assertEquals("", t.takeReply());
    }

    @Test
    public void lineDrawingAndWideCharacters() {
        TermEmulator t = new TermEmulator(10, 2, 0);
        t.write(E + "(0lqk" + E + "(B|");
        assertEquals("┌─┐|", t.screen()[0]);
        t.write("\r\n中!");
        assertEquals("中!", t.screen()[1]);
        assertEquals("a wide character takes two cells", 3, t.cursorX());
    }

    @Test
    public void resizeKeepsTheLinesNearTheCursor() {
        TermEmulator t = new TermEmulator(10, 4, 10);
        t.write("1\r\n2\r\n3\r\n4");
        t.resize(10, 2);
        assertEquals("3", t.screen()[0]);
        assertEquals("4", t.screen()[1]);
        assertEquals(2, t.scrollback());
        assertEquals(1, t.cursorY());
        t.resize(20, 6);
        assertEquals("4", t.screen()[1]);
    }

    @Test
    public void vimDrawsItsScreen() throws Exception {
        TermEmulator t = new TermEmulator(80, 24, 100);
        t.write(real("vim"));
        assertTrue(t.altScreen());
        assertTrue(t.appCursorKeys());
        String[] s = t.screen();
        assertEquals("      1 HELLO line one", s[0]);
        assertEquals("      2 line two", s[1]);
        assertEquals("      3 line three", s[2]);
        assertEquals("~", s[5]);
        assertEquals(":set nu", s[23]);
    }

    @Test
    public void leavingVimBringsTheShellScreenBack() throws Exception {
        TermEmulator t = new TermEmulator(80, 24, 100);
        t.write(real("vimquit"));
        assertFalse(t.altScreen());
        assertFalse(t.appCursorKeys());
        String[] s = t.screen();
        assertEquals("BEFORE", s[0]);
        assertEquals("$ echo AFTER", s[1]);
        assertEquals("AFTER", s[2]);
        assertEquals("$ exit", s[3]);
    }

    @Test
    public void nanoDrawsItsScreen() throws Exception {
        TermEmulator t = new TermEmulator(80, 24, 100);
        t.write(real("nano"));
        String[] s = t.screen();
        assertTrue(s[0], s[0].contains("GNU nano") && s[0].contains("f.txt") && s[0].contains("Modified"));
        // the recording stops in the middle of nano redrawing the line: its last code deletes one character
        assertEquals("abcine one", s[1]);
        assertEquals("line two", s[2]);
        assertTrue(s[22], s[22].startsWith("^G Help") && s[22].contains("^O Write Out"));
        assertTrue(s[23], s[23].startsWith("^X Exit"));
        assertEquals("the title bar is reversed", TermEmulator.REVERSE, t.row(0).attr[3] & TermEmulator.REVERSE);
        assertEquals(3, t.cursorX());
        assertEquals(1, t.cursorY());
    }

    @Test
    public void lessPagesInASmallScreen() throws Exception {
        TermEmulator t = new TermEmulator(40, 10, 100);
        t.write(real("less"));
        String[] s = t.screen();
        assertEquals("8", s[0]);
        assertEquals("16", s[8]);
        assertEquals(":", s[9]);
    }

    @Test
    public void keysAreSentAsXtermSendsThem() {
        assertEquals(E + "[A", TermKeys.key(TermKeys.UP, false, false, false));
        assertEquals(E + "OA", TermKeys.key(TermKeys.UP, false, false, true));
        assertEquals(E + "[1;5C", TermKeys.key(TermKeys.RIGHT, true, false, false));
        assertEquals("\r", TermKeys.key(TermKeys.ENTER, false, false, false));
        assertEquals("\u007f", TermKeys.key(TermKeys.BACKSPACE, false, false, false));
        assertEquals(E + "[5~", TermKeys.key(TermKeys.PAGE_UP, false, false, false));
        assertEquals("\u0003", TermKeys.typed("c", true, false));
        assertEquals("\u001a", TermKeys.typed("Z", true, false));
        assertEquals(E + "x", TermKeys.typed("x", false, true));
        assertEquals("ls\r", TermKeys.typed("ls\n", false, false));
        assertEquals(E + "[200~a\rb" + E + "[201~", TermKeys.paste("a\nb", true));
        assertEquals("a\rb", TermKeys.paste("a\r\nb", false));
    }

    @Test
    public void thePromptIsRecognised() {
        assertTrue(TermSession.atPrompt(false, "~ $ ", true));
        assertTrue(TermSession.atPrompt(false, "/workspace/Dvine-ai-frontend $ ", true));
        assertFalse(TermSession.atPrompt(false, "~ $ ls", true));
        assertTrue(TermSession.atPrompt(false, "localhost:~# ", true));
        assertTrue(TermSession.atPrompt(false, "localhost:/work/app# ", true));
        assertFalse("something is half typed", TermSession.atPrompt(false, "localhost:~# ls", true));
        assertFalse("a full-screen program is open", TermSession.atPrompt(true, "localhost:~# ", true));
        assertFalse("a program is asking something", TermSession.atPrompt(false, "Password: ", true));
    }

    @Test
    public void chatOutputIsCutOutOfTheStream() {
        ChatCapture c = new ChatCapture();
        StringBuilder shown = new StringBuilder();
        // the typed line is echoed, the script redraws it, then the two marker lines surround the output
        String[] chunks = {" . ~/.dv\r\n", E + "[1A\r" + E + "[2Klocalhost:~# ls\r\n", "DVINE_BE", "GIN:\r\n" + E + "[1;34mbin" + E + "[0m\r\nf.txt\r\nDVINE_",
                "STATUS:2__/work\r\nlocalhost:/work# "};
        for (String chunk : chunks) {
            shown.append(c.feed(chunk));
        }
        assertTrue(c.done());
        assertEquals(2, c.exit());
        assertEquals("/work", c.cwd());
        assertEquals("bin\nf.txt\n", c.text());
        assertFalse("the markers are not shown", shown.toString().contains("DVINE_"));
        assertTrue(shown.toString().endsWith("localhost:/work# "));
        assertTrue(shown.toString().contains("localhost:~# ls"));
        assertEquals("after it is done, everything is shown", "x", c.feed("x"));
        String script = ChatCapture.script("echo 'it''s'", "localhost:~# echo");
        assertTrue(script, script.contains("eval 'echo '\\''it'\\'''\\''s'\\''' </dev/null") && script.contains("echo DVINE_BEGIN:")
                && script.contains("DVINE_STATUS:${__dvs}__$(pwd)"));
    }

    @Test
    public void aMarkerInsideALineIsNotTheEnd() {
        ChatCapture c = new ChatCapture();
        c.feed("DVINE_BEGIN:\r\n");
        c.feed("grep found DVINE_STATUS:9__/x in a file\r\n");
        assertFalse(c.done());
        c.feed("DVINE_STATUS:0__/root\r\n");
        assertTrue(c.done());
        assertEquals("grep found DVINE_STATUS:9__/x in a file\n", c.text());
    }
}
