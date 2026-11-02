package ai.dvine.chat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The words inside a file, for a model that can't take the file itself: Word (.docx), Excel (.xlsx), PowerPoint
 * (.pptx) and their OpenDocument cousins (.odt, .ods, .odp) are zips of XML, so their text is read straight out;
 * an EPUB likewise; a .zip, .jar or .apk is listed (names and sizes); RTF loses its markup. Null when there are no
 * words to give (a PDF, a sound, a video: those go as the file, or as pictures). Pure Java.
 */
public final class FileText {
    public static final int MAX = 200_000;

    private FileText() {
    }

    public static String of(String name, String mime, byte[] bytes) {
        String n = name == null ? "" : name.toLowerCase(Locale.US);
        String m = mime == null ? "" : mime.toLowerCase(Locale.US);
        try {
            if (n.endsWith(".docx") || m.contains("wordprocessingml")) {
                return clip(xmlText(entries(bytes, "word/document.xml", "word/footnotes.xml"), "w:p"));
            }
            if (n.endsWith(".pptx") || m.contains("presentationml")) {
                return clip(slides(bytes));
            }
            if (n.endsWith(".xlsx") || m.contains("spreadsheetml")) {
                return clip(sheets(bytes));
            }
            if (n.matches(".*\\.(odt|ods|odp)$") || m.contains("opendocument")) {
                return clip(xmlText(entries(bytes, "content.xml"), "text:p|table:table-row"));
            }
            if (n.endsWith(".epub") || m.contains("epub")) {
                return clip(xmlText(entriesMatching(bytes, ".*\\.x?html?$"), "p|h1|h2|h3|li"));
            }
            if (n.matches(".*\\.(zip|jar|apk|aar)$") || m.contains("zip")) {
                return clip(listing(bytes));
            }
            if (n.endsWith(".rtf") || m.contains("rtf")) {
                String t = new String(bytes, StandardCharsets.UTF_8).replaceAll("\\\\'[0-9a-f]{2}", "").replaceAll("\\\\[a-z]+-?\\d* ?", "")
                        .replaceAll("[{}]", "");
                return clip(t.trim());
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
        return null;
    }

    /** The XML of these zip entries, joined. */
    private static String entries(byte[] zip, String... names) throws IOException {
        StringBuilder b = new StringBuilder();
        for (String name : names) {
            String x = entry(zip, name);
            if (x != null) {
                b.append(x).append('\n');
            }
        }
        return b.toString();
    }

    private static String entry(byte[] zip, String name) throws IOException {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (e.getName().equals(name)) {
                    return read(in);
                }
            }
        }
        return null;
    }

    private static String entriesMatching(byte[] zip, String regex) throws IOException {
        TreeMap<String, String> found = new TreeMap<String, String>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (e.getName().toLowerCase(Locale.US).matches(regex)) {
                    found.put(e.getName(), read(in));
                }
            }
        }
        return String.join("\n", found.values());
    }

    private static String slides(byte[] zip) throws IOException {
        TreeMap<Integer, String> found = new TreeMap<Integer, String>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("ppt/slides/slide(\\d+)\\.xml").matcher(e.getName());
                if (m.matches()) {
                    found.put(Integer.parseInt(m.group(1)), read(in));
                }
            }
        }
        StringBuilder b = new StringBuilder();
        for (java.util.Map.Entry<Integer, String> s : found.entrySet()) {
            b.append("Slide ").append(s.getKey()).append(":\n").append(xmlText(s.getValue(), "a:p")).append("\n\n");
        }
        return b.toString().trim();
    }

    /** Each sheet's rows, tab-separated, with the shared strings put back in. */
    private static String sheets(byte[] zip) throws IOException {
        List<String> shared = new ArrayList<String>();
        String ss = entry(zip, "xl/sharedStrings.xml");
        if (ss != null) {
            java.util.regex.Matcher si = java.util.regex.Pattern.compile("(?s)<si>(.*?)</si>").matcher(ss);
            while (si.find()) {
                shared.add(si.group(1).replaceAll("(?s)<[^>]+>", "").trim());
            }
        }
        TreeMap<String, String> found = new TreeMap<String, String>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (e.getName().matches("xl/worksheets/sheet\\d+\\.xml")) {
                    found.put(e.getName(), read(in));
                }
            }
        }
        StringBuilder b = new StringBuilder();
        for (java.util.Map.Entry<String, String> s : found.entrySet()) {
            b.append(s.getKey().replaceAll(".*/(sheet\\d+)\\.xml", "$1")).append(":\n");
            java.util.regex.Matcher row = java.util.regex.Pattern.compile("(?s)<row[^>]*>(.*?)</row>").matcher(s.getValue());
            while (row.find()) {
                List<String> cells = new ArrayList<String>();
                java.util.regex.Matcher c = java.util.regex.Pattern.compile("(?s)<c([^>]*)>(.*?)</c>").matcher(row.group(1));
                while (c.find()) {
                    java.util.regex.Matcher v = java.util.regex.Pattern.compile("(?s)<v>(.*?)</v>|<t[^>]*>(.*?)</t>").matcher(c.group(2));
                    String val = v.find() ? (v.group(1) != null ? v.group(1) : v.group(2)) : "";
                    if (c.group(1).contains("t=\"s\"")) {
                        try {
                            val = shared.get(Integer.parseInt(val.trim()));
                        } catch (RuntimeException ignored) {
                            // a broken index: the number stays
                        }
                    }
                    cells.add(unescape(val));
                }
                b.append(String.join("\t", cells)).append('\n');
            }
            b.append('\n');
        }
        return b.toString().trim();
    }

    private static String listing(byte[] zip) throws IOException {
        StringBuilder b = new StringBuilder("Contents:\n");
        int n = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null && n < 500) {
                b.append(e.getName()).append(e.isDirectory() ? "" : " (" + Math.max(0, e.getSize()) + " bytes)").append('\n');
                n++;
            }
        }
        return n == 0 ? null : b.toString();
    }

    /** The words in the XML, one line per paragraph-like element. */
    private static String xmlText(String xml, String paragraphs) {
        if (xml == null || xml.isEmpty()) {
            return null;
        }
        String t = xml.replaceAll("(?s)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("</(" + paragraphs + ")>", "\n").replaceAll("<w:tab/>|<text:tab/>", "\t")
                .replaceAll("(?s)<[^>]+>", "");
        t = unescape(t).replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\\n\\s*\\n+", "\n").trim();
        return t.isEmpty() ? null : t;
    }

    private static String unescape(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }

    private static String read(ZipInputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0 && out.size() < 8_000_000) {
            out.write(b, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String clip(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        return s.length() <= MAX ? s : s.substring(0, MAX) + "\n…(the rest is left out)";
    }
}
