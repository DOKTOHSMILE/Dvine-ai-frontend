package ai.dvine.library;

import java.io.IOException;
import java.io.Reader;

/**
 * Reads a DevDocs set's db.json (one big JSON object: each page's path and its HTML) one page at a time, so a 100 MB
 * set is indexed without holding it in memory. Also turns a page's HTML into plain text for the index. Pure Java.
 */
public final class DocsStream {
    /** Gets each page as it is read. */
    public interface Page {
        void page(String path, String html) throws IOException;
    }

    private final Reader in;
    private int peeked = -2;

    private DocsStream(Reader in) {
        this.in = in;
    }

    /** Every "path": "html" pair of the object in {@code in}, in order. */
    public static int each(Reader in, Page out) throws IOException {
        DocsStream s = new DocsStream(in);
        int n = 0;
        s.skipSpace();
        if (s.next() != '{') {
            throw new IOException("not a DevDocs page file");
        }
        while (true) {
            s.skipSpace();
            int c = s.peek();
            if (c == '}' || c == -1) {
                return n;
            }
            if (c == ',') {
                s.next();
                continue;
            }
            String key = s.string();
            s.skipSpace();
            if (s.next() != ':') {
                throw new IOException("a page without its text");
            }
            s.skipSpace();
            String value = s.peek() == '"' ? s.string() : s.skipValue();
            out.page(key, value);
            n++;
        }
    }

    private int peek() throws IOException {
        if (peeked == -2) {
            peeked = in.read();
        }
        return peeked;
    }

    private int next() throws IOException {
        int c = peek();
        peeked = -2;
        return c;
    }

    private void skipSpace() throws IOException {
        while (true) {
            int c = peek();
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                next();
            } else {
                return;
            }
        }
    }

    private String string() throws IOException {
        if (next() != '"') {
            throw new IOException("expected text");
        }
        StringBuilder b = new StringBuilder();
        while (true) {
            int c = next();
            if (c == -1) {
                throw new IOException("the file ended in the middle of a page");
            }
            if (c == '"') {
                return b.toString();
            }
            if (c != '\\') {
                b.append((char) c);
                continue;
            }
            int e = next();
            switch (e) {
                case 'n': b.append('\n'); break;
                case 't': b.append('\t'); break;
                case 'r': b.append('\r'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'u': {
                    char[] h = new char[4];
                    for (int i = 0; i < 4; i++) {
                        h[i] = (char) next();
                    }
                    try {
                        b.append((char) Integer.parseInt(new String(h), 16));
                    } catch (NumberFormatException ex) {
                        b.append('?');
                    }
                    break;
                }
                default: b.append((char) e); // \" \\ \/
            }
        }
    }

    /** A value that isn't text (not expected in db.json): skipped. */
    private String skipValue() throws IOException {
        int depth = 0;
        while (true) {
            int c = peek();
            if (c == -1) {
                return "";
            }
            if (c == '"') {
                string();
                continue;
            }
            if ((c == ',' || c == '}') && depth == 0) {
                return "";
            }
            if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
            }
            next();
        }
    }

    /** A page's HTML as plain text: code kept on its own lines, tags and entities gone, blank runs squeezed. */
    public static String text(String html) {
        if (html == null) {
            return "";
        }
        String t = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?i)<br\\s*/?>|</(p|div|pre|li|tr|h[1-6]|dt|dd|table)>", "\n")
                .replaceAll("(?i)<(pre|h[1-6]|li|dt)[^>]*>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&nbsp;", " ").replace("&amp;", "&");
        t = t.replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll(" *\\n *", "\n").replaceAll("\\n{3,}", "\n\n");
        return t.trim();
    }

    /** The page a path names ("operators/addition#syntax" is the page "operators/addition"). */
    public static String pageOf(String path) {
        int h = path == null ? -1 : path.indexOf('#');
        return h < 0 ? (path == null ? "" : path) : path.substring(0, h);
    }
}
