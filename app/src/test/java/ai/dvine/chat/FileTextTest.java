package ai.dvine.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

/** The words read out of office files, a zip's listing, and nothing from a file with no words. */
public class FileTextTest {
    private static byte[] zip(String... nameAndBody) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (int i = 0; i < nameAndBody.length; i += 2) {
                z.putNextEntry(new ZipEntry(nameAndBody[i]));
                z.write(nameAndBody[i + 1].getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }

    @Test
    public void wordExcelPowerPointAndZip() throws Exception {
        byte[] docx = zip("word/document.xml", "<w:document><w:body><w:p><w:r><w:t>Quarterly plan</w:t></w:r></w:p>"
                + "<w:p><w:r><w:t>Ship 0.57 &amp; test it.</w:t></w:r></w:p></w:body></w:document>");
        assertEquals("Quarterly plan\nShip 0.57 & test it.", FileText.of("plan.docx", null, docx));

        byte[] xlsx = zip("xl/sharedStrings.xml", "<sst><si><t>Month</t></si><si><t>Jan</t></si></sst>",
                "xl/worksheets/sheet1.xml", "<sheetData><row><c t=\"s\"><v>0</v></c><c t=\"inlineStr\"><is><t>Sales</t></is></c></row>"
                        + "<row><c t=\"s\"><v>1</v></c><c><v>120</v></c></row></sheetData>");
        assertEquals("sheet1:\nMonth\tSales\nJan\t120", FileText.of("sales.xlsx", null, xlsx));

        byte[] pptx = zip("ppt/slides/slide2.xml", "<p:sld><a:p><a:r><a:t>Results</a:t></a:r></a:p></p:sld>",
                "ppt/slides/slide1.xml", "<p:sld><a:p><a:r><a:t>Welcome</a:t></a:r></a:p></p:sld>");
        String slides = FileText.of("deck.pptx", null, pptx);
        assertTrue(slides, slides.indexOf("Slide 1:\nWelcome") < slides.indexOf("Slide 2:\nResults"));

        String listing = FileText.of("src.zip", "application/zip", zip("a/Main.java", "class Main {}"));
        assertTrue(listing, listing.contains("a/Main.java"));
        assertNull(FileText.of("song.mp3", "audio/mpeg", new byte[]{1, 2, 3}));
    }
}
