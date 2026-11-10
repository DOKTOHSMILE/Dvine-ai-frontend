package ai.ibiko.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

/** A file read splits into its note, a gutter of numbers and the code, so no number runs into the code. */
public class NumberedCodeTest {
    @Test
    public void aReadFileSplitsIntoNoteGutterAndCode() {
        NumberedCode c = NumberedCode.of(Arrays.asList(
                "Lines 1–12 of 861 in this file. This is not the whole file.",
                "1\tpackage ai.ibiko.llm;", "2\t", "9\timport java.util.List;", "10\timport java.util.Map;",
                "11\t\treturn x;", "a line that ran on"), false, 0);
        assertTrue(c.note.startsWith("Lines 1–12 of 861"));
        assertTrue(c.numbered());
        assertEquals(Arrays.asList(1, 2, 9, 10, 11, 0), c.numbers);
        assertEquals("import java.util.Map;", c.code.get(3)); // "10import" can't happen: the number is its own column
        assertEquals(" 1\n 2\n 9\n10\n11\n  ", c.gutter());
        assertTrue(c.source().contains("    return x;")); // a tab is four spaces
    }

    @Test
    public void outputWithoutNumbersStaysAsItIs() {
        NumberedCode c = NumberedCode.of(Arrays.asList(" BUILD SUCCESSFUL", " 3 tests"), true, 0);
        assertFalse(c.numbered());
        assertEquals("", c.note);
        assertEquals(Arrays.asList("BUILD SUCCESSFUL", "3 tests"), c.code);
    }

    @Test
    public void theSheetShowsAtMostItsShareAndCountsTheRest() {
        String[] many = new String[60];
        for (int i = 0; i < many.length; i++) {
            many[i] = (i + 1) + "\tline " + (i + 1);
        }
        assertEquals(24, NumberedCode.of(Arrays.asList(many), false, 24).code.size());
        assertEquals(60, NumberedCode.count(Arrays.asList(many), false));
    }
}
