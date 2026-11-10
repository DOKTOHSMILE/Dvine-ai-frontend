package ai.ibiko.act;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Turns what a model wrote (Markdown-ish text: # headings, - lists, **bold**, | tables |) into a real file of the
 * format asked for, on the phone, with no network and any model: PDF (A4 pages), Word (.docx), Excel (.xlsx), HTML,
 * Markdown, CSV, JSON or plain text. The model thinks it; DVINE makes the file. PDF needs Android's PdfDocument, so it
 * is drawn by {@link #pdf} (Android only); the rest are pure Java.
 */
public final class FileMaker {
    private FileMaker() {
    }

    /** A format DVINE can make: its file extension and MIME type. */
    public enum Format {
        PDF("pdf", "application/pdf"),
        DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        HTML("html", "text/html"),
        MD("md", "text/markdown"),
        CSV("csv", "text/csv"),
        JSON("json", "application/json"),
        TXT("txt", "text/plain");

        public final String extension, mime;

        Format(String extension, String mime) {
            this.extension = extension;
            this.mime = mime;
        }
    }

    /** The format a name or a word asks for ("report.pdf", "word", "excel", "spreadsheet"); TXT when unknown. */
    public static Format of(String nameOrWord) {
        String t = nameOrWord == null ? "" : nameOrWord.trim().toLowerCase(Locale.US);
        if (t.endsWith(".pdf") || t.matches("(?s).*\\bpdf\\b.*")) {
            return Format.PDF;
        }
        if (t.endsWith(".docx") || t.endsWith(".doc") || t.matches("(?s).*\\b(word|docx)( document| file| doc)?\\b.*")) {
            return Format.DOCX;
        }
        if (t.endsWith(".xlsx") || t.endsWith(".xls") || t.matches("(?s).*\\b(excel|xlsx|spreadsheet)\\b.*")) {
            return Format.XLSX;
        }
        if (t.endsWith(".html") || t.endsWith(".htm") || t.matches("(?s).*\\b(html|web page|webpage)\\b.*")) {
            return Format.HTML;
        }
        if (t.endsWith(".md") || t.matches("(?s).*\\bmarkdown\\b.*")) {
            return Format.MD;
        }
        if (t.endsWith(".csv") || t.matches("(?s).*\\bcsv\\b.*")) {
            return Format.CSV;
        }
        if (t.endsWith(".json") || t.matches("(?s).*\\bjson\\b.*")) {
            return Format.JSON;
        }
        return Format.TXT;
    }

    /** The file's bytes, for every format but PDF (which {@link #pdf} draws). */
    public static byte[] make(Format f, String title, String content) throws IOException {
        String text = content == null ? "" : content.trim();
        switch (f) {
            case DOCX:
                return docx(title, text);
            case XLSX:
                return xlsx(rows(text));
            case HTML:
                return html(title, text).getBytes(StandardCharsets.UTF_8);
            case CSV:
                return csv(rows(text)).getBytes(StandardCharsets.UTF_8);
            case PDF:
                throw new IOException("PDF is drawn with Android's PdfDocument (FileMaker.pdf)");
            default:
                return (text + "\n").getBytes(StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------ the text as blocks

    /** One piece of the document: a heading (level 1..3), a list item, a paragraph, or a table row. */
    public static final class Block {
        public final String kind, text;
        public final int level;
        public final List<String> cells;

        Block(String kind, String text, int level, List<String> cells) {
            this.kind = kind;
            this.text = text;
            this.level = level;
            this.cells = cells;
        }
    }

    /** The text as blocks: # headings, - or 1. list items, | table | rows, paragraphs (lines joined). */
    public static List<Block> blocks(String text) {
        List<Block> out = new ArrayList<Block>();
        StringBuilder para = new StringBuilder();
        for (String raw : text.replace("\r", "").split("\n")) {
            String line = raw.trim();
            boolean special = line.isEmpty() || line.startsWith("#") || line.matches("^([-*+]|\\d+[.)])\\s+.*") || line.startsWith("|")
                    || line.startsWith("```");
            if (special && para.length() > 0) {
                out.add(new Block("p", para.toString(), 0, null));
                para.setLength(0);
            }
            if (line.isEmpty() || line.startsWith("```") || line.matches("^\\|?\\s*:?-{2,}.*")) {
                continue; // blank lines, code fences and table rulers
            }
            if (line.startsWith("#")) {
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') {
                    level++;
                }
                out.add(new Block("h", line.substring(level).trim(), Math.min(3, level), null));
            } else if (line.matches("^([-*+]|\\d+[.)])\\s+.*")) {
                boolean numbered = line.matches("^\\d+[.)].*");
                out.add(new Block("li", numbered ? line : line.replaceFirst("^[-*+]\\s+", ""), numbered ? 1 : 0, null));
            } else if (line.startsWith("|")) {
                List<String> cells = new ArrayList<String>();
                for (String c : line.replaceAll("^\\|", "").replaceAll("\\|$", "").split("\\|")) {
                    cells.add(c.trim());
                }
                out.add(new Block("tr", line, 0, cells));
            } else {
                para.append(para.length() == 0 ? "" : " ").append(line);
            }
        }
        if (para.length() > 0) {
            out.add(new Block("p", para.toString(), 0, null));
        }
        return out;
    }

    /** The words without Markdown marks (**bold** → bold, `code` → code, [text](url) → text). */
    public static String plain(String s) {
        return s.replaceAll("\\*\\*(.+?)\\*\\*", "$1").replaceAll("__(.+?)__", "$1").replaceAll("`([^`]*)`", "$1")
                .replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");
    }

    /** The rows of a table: Markdown | rows |, else CSV-like lines, else one cell per line. */
    static List<List<String>> rows(String text) {
        List<List<String>> out = new ArrayList<List<String>>();
        for (Block b : blocks(text)) {
            if ("tr".equals(b.kind)) {
                out.add(b.cells);
            }
        }
        if (!out.isEmpty()) {
            return out;
        }
        for (String line : text.split("\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            List<String> cells = new ArrayList<String>();
            for (String c : line.split(line.contains("\t") ? "\t" : ",", -1)) {
                cells.add(c.trim());
            }
            out.add(cells);
        }
        return out;
    }

    // ------------------------------------------------------------ formats

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String html(String title, String text) {
        StringBuilder b = new StringBuilder("<!doctype html>\n<html><head><meta charset=\"utf-8\"><title>").append(esc(title == null ? "Document" : title))
                .append("</title><style>body{font-family:sans-serif;max-width:46em;margin:2em auto;padding:0 1em;line-height:1.5}"
                        + "table{border-collapse:collapse}td{border:1px solid #ccc;padding:4px 8px}</style></head><body>\n");
        boolean inList = false, inTable = false;
        for (Block k : blocks(text)) {
            if (inList && !"li".equals(k.kind)) {
                b.append("</ul>\n");
                inList = false;
            }
            if (inTable && !"tr".equals(k.kind)) {
                b.append("</table>\n");
                inTable = false;
            }
            switch (k.kind) {
                case "h":
                    b.append("<h").append(k.level).append('>').append(inline(k.text)).append("</h").append(k.level).append(">\n");
                    break;
                case "li":
                    if (!inList) {
                        b.append("<ul>\n");
                        inList = true;
                    }
                    b.append("<li>").append(inline(k.text)).append("</li>\n");
                    break;
                case "tr":
                    if (!inTable) {
                        b.append("<table>\n");
                        inTable = true;
                    }
                    b.append("<tr>");
                    for (String c : k.cells) {
                        b.append("<td>").append(inline(c)).append("</td>");
                    }
                    b.append("</tr>\n");
                    break;
                default:
                    b.append("<p>").append(inline(k.text)).append("</p>\n");
            }
        }
        b.append(inList ? "</ul>\n" : "").append(inTable ? "</table>\n" : "").append("</body></html>\n");
        return b.toString();
    }

    private static String inline(String s) {
        return esc(s).replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>").replaceAll("`([^`]*)`", "<code>$1</code>");
    }

    static String csv(List<List<String>> rows) {
        StringBuilder b = new StringBuilder();
        for (List<String> r : rows) {
            for (int i = 0; i < r.size(); i++) {
                String c = plain(r.get(i));
                b.append(i == 0 ? "" : ",").append(c.matches(".*[\",\\n].*") ? "\"" + c.replace("\"", "\"\"") + "\"" : c);
            }
            b.append('\n');
        }
        return b.toString();
    }

    static byte[] docx(String title, String text) throws IOException {
        StringBuilder body = new StringBuilder();
        for (Block k : blocks(text)) {
            switch (k.kind) {
                case "h":
                    body.append(para(plain(k.text), true, k.level == 1 ? 32 : k.level == 2 ? 28 : 24, ""));
                    break;
                case "li":
                    body.append(para(plain(k.text), false, 22, k.level == 1 ? "" : "• "));
                    break;
                case "tr":
                    body.append(para(String.join("    ", k.cells), false, 22, ""));
                    break;
                default:
                    body.append(para(plain(k.text), false, 22, ""));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            put(z, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                    + "</Types>");
            put(z, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                    + "</Relationships>");
            put(z, "word/document.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
                    + body + "</w:body></w:document>");
        }
        return out.toByteArray();
    }

    private static String para(String text, boolean bold, int halfPoints, String prefix) {
        return "<w:p><w:r><w:rPr>" + (bold ? "<w:b/>" : "") + "<w:sz w:val=\"" + halfPoints + "\"/></w:rPr><w:t xml:space=\"preserve\">"
                + esc(prefix + text) + "</w:t></w:r></w:p>";
    }

    static byte[] xlsx(List<List<String>> rows) throws IOException {
        StringBuilder sheet = new StringBuilder();
        for (int r = 0; r < rows.size(); r++) {
            sheet.append("<row r=\"").append(r + 1).append("\">");
            List<String> cells = rows.get(r);
            for (int c = 0; c < cells.size(); c++) {
                String ref = column(c) + (r + 1), v = plain(cells.get(c));
                if (v.matches("-?\\d+(\\.\\d+)?")) {
                    sheet.append("<c r=\"").append(ref).append("\"><v>").append(v).append("</v></c>");
                } else {
                    sheet.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t>").append(esc(v)).append("</t></is></c>");
                }
            }
            sheet.append("</row>");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            put(z, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                    + "</Types>");
            put(z, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>");
            put(z, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                    + "<sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(z, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                    + "</Relationships>");
            put(z, "xl/worksheets/sheet1.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>" + sheet
                    + "</sheetData></worksheet>");
        }
        return out.toByteArray();
    }

    static String column(int i) {
        StringBuilder b = new StringBuilder();
        for (int n = i + 1; n > 0; n = (n - 1) / 26) {
            b.insert(0, (char) ('A' + (n - 1) % 26));
        }
        return b.toString();
    }

    private static void put(ZipOutputStream z, String name, String xml) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(xml.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }

    // ------------------------------------------------------------ PDF (Android)

    /** A4 PDF of the text: headings larger and bold, lists with bullets, tables as spaced rows, pages as needed. */
    public static byte[] pdf(String title, String text) throws IOException {
        final int w = 595, h = 842, margin = 56; // A4 in points
        android.graphics.pdf.PdfDocument doc = new android.graphics.pdf.PdfDocument();
        int pageNo = 1;
        android.graphics.pdf.PdfDocument.Page page = doc.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(w, h, pageNo).create());
        android.graphics.Canvas canvas = page.getCanvas();
        float y = margin;
        List<Block> all = new ArrayList<Block>();
        if (title != null && !title.trim().isEmpty() && (blocks(text).isEmpty() || !"h".equals(blocks(text).get(0).kind))) {
            all.add(new Block("h", title.trim(), 1, null));
        }
        all.addAll(blocks(text));
        for (Block k : all) {
            android.text.TextPaint p = new android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFF1A1A1A);
            String words;
            float before;
            switch (k.kind) {
                case "h":
                    p.setTextSize(k.level == 1 ? 20f : k.level == 2 ? 16f : 13.5f);
                    p.setFakeBoldText(true);
                    words = plain(k.text);
                    before = k.level == 1 ? 10f : 8f;
                    break;
                case "li":
                    p.setTextSize(11f);
                    words = (k.level == 1 ? "" : "•  ") + plain(k.text);
                    before = 2f;
                    break;
                case "tr":
                    p.setTextSize(10.5f);
                    words = String.join("     ", k.cells);
                    before = 1f;
                    break;
                default:
                    p.setTextSize(11f);
                    words = plain(k.text);
                    before = 6f;
            }
            android.text.StaticLayout layout = android.text.StaticLayout.Builder.obtain(words, 0, words.length(), p, w - 2 * margin)
                    .setLineSpacing(2f, 1f).build();
            if (y + before + layout.getHeight() > h - margin && y > margin) {
                doc.finishPage(page);
                page = doc.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(w, h, ++pageNo).create());
                canvas = page.getCanvas();
                y = margin;
            }
            y += before;
            canvas.save();
            canvas.translate(margin, y);
            layout.draw(canvas);
            canvas.restore();
            y += layout.getHeight();
        }
        doc.finishPage(page);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.writeTo(out);
        doc.close();
        return out.toByteArray();
    }

    /** Any format's bytes, on Android (PDF included: Android's drawing first, the plain writer if that fails). */
    public static byte[] any(Format f, String title, String content) throws IOException {
        if (f != Format.PDF) {
            return make(f, title, content);
        }
        try {
            return pdf(title, content == null ? "" : content);
        } catch (RuntimeException | IOException androidFailed) {
            return simplePdf(title, content == null ? "" : content);
        }
    }

    /**
     * A PDF written by hand, in pure Java: A4 pages, Helvetica (bold for headings), wrapped lines, page breaks. Latin
     * text only (other letters show as "?"); Android's drawing ({@link #pdf}) is used first on the phone for that.
     */
    public static byte[] simplePdf(String title, String text) throws IOException {
        final float w = 595, h = 842, margin = 56;
        List<StringBuilder> pages = new ArrayList<StringBuilder>();
        StringBuilder page = new StringBuilder();
        pages.add(page);
        float y = h - margin;
        List<Block> all = new ArrayList<Block>();
        List<Block> body = blocks(text);
        if (title != null && !title.trim().isEmpty() && (body.isEmpty() || !"h".equals(body.get(0).kind))) {
            all.add(new Block("h", title.trim(), 1, null));
        }
        all.addAll(body);
        for (Block k : all) {
            float size;
            boolean bold = false;
            String words;
            float before;
            switch (k.kind) {
                case "h":
                    size = k.level == 1 ? 20f : k.level == 2 ? 16f : 13.5f;
                    bold = true;
                    words = plain(k.text);
                    before = k.level == 1 ? 10f : 8f;
                    break;
                case "li":
                    size = 11f;
                    words = (k.level == 1 ? "" : "- ") + plain(k.text);
                    before = 2f;
                    break;
                case "tr":
                    size = 10.5f;
                    words = String.join("     ", k.cells);
                    before = 1f;
                    break;
                default:
                    size = 11f;
                    words = plain(k.text);
                    before = 6f;
            }
            int perLine = Math.max(10, (int) ((w - 2 * margin) / (size * (bold ? 0.58f : 0.52f))));
            y -= before;
            for (String line : wrap(words, perLine)) {
                if (y - size < margin) {
                    page = new StringBuilder();
                    pages.add(page);
                    y = h - margin;
                }
                y -= size * 1.3f;
                page.append("BT /").append(bold ? "F2" : "F1").append(' ').append(size).append(" Tf ").append(margin).append(' ')
                        .append(String.format(Locale.US, "%.1f", y)).append(" Td (").append(pdfText(line)).append(") Tj ET\n");
            }
        }
        // objects: 1 catalog, 2 pages, 3 font, 4 bold font, then each page and its content
        List<String> objs = new ArrayList<String>();
        objs.add("<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            kids.append(5 + i * 2).append(" 0 R ");
        }
        objs.add("<< /Type /Pages /Kids [" + kids.toString().trim() + "] /Count " + pages.size() + " >>");
        objs.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        objs.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
        for (int i = 0; i < pages.size(); i++) {
            objs.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents "
                    + (6 + i * 2) + " 0 R >>");
            byte[] stream = pages.get(i).toString().getBytes(StandardCharsets.ISO_8859_1);
            objs.add("<< /Length " + stream.length + " >>\nstream\n" + pages.get(i) + "endstream");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<Integer>();
        out.write("%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1));
        for (int i = 0; i < objs.size(); i++) {
            offsets.add(out.size());
            out.write(((i + 1) + " 0 obj\n" + objs.get(i) + "\nendobj\n").getBytes(StandardCharsets.ISO_8859_1));
        }
        int xref = out.size();
        StringBuilder x = new StringBuilder("xref\n0 " + (objs.size() + 1) + "\n0000000000 65535 f \n");
        for (int o : offsets) {
            x.append(String.format(Locale.US, "%010d 00000 n \n", o));
        }
        x.append("trailer\n<< /Size ").append(objs.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        out.write(x.toString().getBytes(StandardCharsets.ISO_8859_1));
        return out.toByteArray();
    }

    static List<String> wrap(String words, int perLine) {
        List<String> out = new ArrayList<String>();
        StringBuilder line = new StringBuilder();
        for (String word : words.split("\\s+")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > perLine) {
                out.add(line.toString());
                line.setLength(0);
            }
            line.append(line.length() == 0 ? "" : " ").append(word);
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    /** Text inside a PDF string: brackets and backslashes escaped, Latin-1 only. */
    static String pdfText(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '(' || c == ')' || c == '\\') {
                b.append('\\').append(c);
            } else if (c == '\u2019' || c == '\u2018') {
                b.append('\'');
            } else if (c == '\u201C' || c == '\u201D') {
                b.append('"');
            } else if (c == '\u2013' || c == '\u2014') {
                b.append('-');
            } else if (c == '\u2022') {
                b.append('-');
            } else {
                b.append(c < 256 ? c : '?');
            }
        }
        return b.toString();
    }
}
