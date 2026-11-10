package ai.ibiko.act;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Real files from what the model wrote: PDF, Word, Excel, HTML, CSV. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FileMakerTest {
    static final String DOC = "# Lagos transport\n\n## Buses\n\nThe **BRT** carries 200,000 people a day.\n\n- Fast lanes\n- Cheap fares\n\n"
            + "| Mode | Riders |\n|---|---|\n| BRT | 200000 |\n| Ferry | 3000 |\n";

    @Test
    public void everyFormatIsARealFile() throws Exception {
        Map<String, String> docx = unzip(FileMaker.make(FileMaker.Format.DOCX, "t", DOC));
        assertTrue(docx.keySet().toString(), docx.containsKey("word/document.xml") && docx.containsKey("[Content_Types].xml"));
        assertTrue(docx.get("word/document.xml").contains("Lagos transport") && docx.get("word/document.xml").contains("The BRT carries"));
        Map<String, String> xlsx = unzip(FileMaker.make(FileMaker.Format.XLSX, "t", DOC));
        String sheet = xlsx.get("xl/worksheets/sheet1.xml");
        assertTrue(sheet, sheet.contains("<t>Mode</t>") && sheet.contains("<c r=\"B2\"><v>200000</v></c>")); // numbers as numbers
        String html = new String(FileMaker.make(FileMaker.Format.HTML, "t", DOC), StandardCharsets.UTF_8);
        assertTrue(html.contains("<h1>Lagos transport</h1>") && html.contains("<b>BRT</b>") && html.contains("<li>Fast lanes</li>")
                && html.contains("<td>Ferry</td>"));
        assertEquals("Mode,Riders\nBRT,200000\nFerry,3000\n", new String(FileMaker.make(FileMaker.Format.CSV, "t", DOC), StandardCharsets.UTF_8));
        byte[] pdf = FileMaker.any(FileMaker.Format.PDF, "Lagos transport", DOC); // here: the plain writer (no native PdfDocument)
        assertTrue(new String(pdf, 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF"));
        java.io.File shots = new java.io.File("build/screens");
        shots.mkdirs();
        java.nio.file.Files.write(new java.io.File(shots, "sample.pdf").toPath(), pdf); // to open with a real reader
        StringBuilder longer = new StringBuilder("# Report\n\n");
        for (int i = 0; i < 200; i++) {
            longer.append("Paragraph ").append(i).append(": a sentence about the findings and what to do about them.\n\n");
        }
        String big = new String(FileMaker.simplePdf("Report", longer.toString()), StandardCharsets.ISO_8859_1);
        assertTrue(big.contains("(Paragraph 199: a sentence") && big.endsWith("%%EOF\n"));
        assertTrue("several pages", big.split("/Type\\s*/Page[^s]").length > 3);
    }

    @Test
    public void theFormatComesFromTheNameOrTheWords() {
        assertEquals(FileMaker.Format.PDF, FileMaker.of("make a PDF of that"));
        assertEquals(FileMaker.Format.DOCX, FileMaker.of("save this as a Word document"));
        assertEquals(FileMaker.Format.XLSX, FileMaker.of("report.xlsx"));
        assertEquals(FileMaker.Format.XLSX, FileMaker.of("put it in a spreadsheet"));
        assertEquals(FileMaker.Format.TXT, FileMaker.of("notes"));
        assertTrue(ai.ibiko.MainActivity.asksForFile("make a PDF of that"));
        assertTrue(ai.ibiko.MainActivity.asksForFile("Write a Word document about Lagos transport"));
        assertTrue(!ai.ibiko.MainActivity.asksForFile("write a PDF parser in Java"));
        assertTrue(!ai.ibiko.MainActivity.asksForFile("what is a PDF?"));
    }

    private static Map<String, String> unzip(byte[] zip) throws Exception {
        Map<String, String> out = new HashMap<String, String>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                out.put(e.getName(), new String(z.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return out;
    }
}
