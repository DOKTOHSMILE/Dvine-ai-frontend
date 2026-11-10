package ai.ibiko.connect;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The user's Gmail through Google's own mail servers (IMAP), signed in with their address and an app password
 * (Google Account › Security › 2-Step Verification › App passwords): no Google Cloud project, no sign-in page. It reads
 * the inbox, searches it with Gmail's own search words, opens one mail, and writes drafts into Gmail's Drafts, so
 * nothing is sent without the user. The mailbox is opened read-only (EXAMINE), so reading never marks mail as read.
 * Pure Java; the connection is behind {@link Wire} so it can be tested without Google.
 */
public final class Gmail {
    /** Opens a connection to a mail server. */
    public interface Wire {
        Socket open(String host, int port) throws IOException;
    }

    /** Google's servers over TLS. */
    public static final Wire TLS = (host, port) -> javax.net.ssl.SSLSocketFactory.getDefault().createSocket(host, port);

    /** One mail as listed: its id (the UID), who sent it, the subject, the date and the start of its text. */
    public static final class Mail {
        public final long uid;
        public final String from, subject, date, snippet;

        Mail(long uid, String from, String subject, String date, String snippet) {
            this.uid = uid;
            this.from = from;
            this.subject = subject;
            this.date = date;
            this.snippet = snippet;
        }

        public String line() {
            return "- [" + uid + "] " + date + " · " + from + " · " + subject + (snippet.isEmpty() ? "" : " — " + snippet);
        }
    }

    static final String HOST = "imap.gmail.com";
    static final int PORT = 993;
    static final int TIMEOUT_MS = 20_000;

    private final String user, password;
    private final Wire wire;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private int tag;

    public Gmail(String user, String password, Wire wire) {
        this.user = user == null ? "" : user.trim();
        this.password = password == null ? "" : password.replace(" ", "").trim(); // app passwords are shown in groups of 4
        this.wire = wire == null ? TLS : wire;
    }

    /** Signs in and out once: null when it works, else why. */
    public String test() {
        try {
            open();
            return null;
        } catch (IOException e) {
            return e.getMessage();
        } finally {
            close();
        }
    }

    /** The newest {@code n} mails in the inbox, newest first. */
    public List<Mail> latest(int n) throws IOException {
        try {
            open();
            return fetch(last(uids("UID SEARCH ALL"), n));
        } finally {
            close();
        }
    }

    /** Mails matching Gmail's own search words ("from:bank invoice newer_than:7d"), newest first. */
    public List<Mail> search(String words, int n) throws IOException {
        try {
            open();
            return fetch(last(uids("UID SEARCH X-GM-RAW " + quote(words == null ? "" : words)), n));
        } finally {
            close();
        }
    }

    /** One mail in full (its text, up to {@code most} characters), or null when there is no such mail. */
    public String read(long uid, int most) throws IOException {
        try {
            open();
            List<byte[]> parts = new ArrayList<byte[]>();
            command("UID FETCH " + uid + " (BODY.PEEK[HEADER.FIELDS (FROM TO CC SUBJECT DATE CONTENT-TYPE CONTENT-TRANSFER-ENCODING)]"
                    + " BODY.PEEK[TEXT]<0." + Math.max(1000, most * 3) + ">)", parts);
            if (parts.size() < 2) {
                return null;
            }
            String head = latin(parts.get(0));
            String text = Mime.text(head, parts.get(1));
            StringBuilder b = new StringBuilder();
            for (String h : new String[]{"From", "To", "Cc", "Date", "Subject"}) {
                String v = Mime.header(head, h);
                if (!v.isEmpty()) {
                    b.append(h).append(": ").append(v).append('\n');
                }
            }
            b.append('\n').append(text.length() > most ? text.substring(0, most) + "…" : text);
            return b.toString();
        } finally {
            close();
        }
    }

    /** Writes a draft into Gmail's Drafts: the user reads it there and sends it, or asks for a change. */
    public void draft(String to, String subject, String body) throws IOException {
        String mail = "From: " + user + "\r\nTo: " + oneLine(to) + "\r\nSubject: " + Mime.encodeHeader(oneLine(subject))
                + "\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: 8bit\r\n\r\n"
                + (body == null ? "" : body.replace("\r\n", "\n").replace("\n", "\r\n")) + "\r\n";
        byte[] bytes = mail.getBytes(StandardCharsets.UTF_8);
        try {
            open();
            String t = "a" + (++tag);
            send(t + " APPEND \"[Gmail]/Drafts\" (\\Draft) {" + bytes.length + "}\r\n");
            String go = line();
            if (go == null || !go.startsWith("+")) {
                throw new IOException("Gmail didn't take the draft" + (go == null ? "" : " (" + go + ")"));
            }
            out.write(bytes);
            send("\r\n");
            finish(t, new ArrayList<byte[]>());
        } finally {
            close();
        }
    }

    // ---- the IMAP conversation

    private void open() throws IOException {
        if (user.isEmpty() || password.isEmpty()) {
            throw new IOException("the Gmail address and app password aren't set");
        }
        socket = wire.open(HOST, PORT);
        socket.setSoTimeout(TIMEOUT_MS);
        in = socket.getInputStream();
        out = socket.getOutputStream();
        String hello = line();
        if (hello == null || !hello.startsWith("* OK")) {
            throw new IOException("Gmail didn't answer");
        }
        try {
            command("LOGIN " + quote(user) + " " + quote(password), new ArrayList<byte[]>());
        } catch (IOException e) {
            throw new IOException("Gmail refused the sign-in: check the address and use an app password, not the account"
                    + " password (Google Account › Security › 2-Step Verification › App passwords)");
        }
        command("EXAMINE INBOX", new ArrayList<byte[]>()); // read-only: nothing is marked as read
    }

    private void close() {
        try {
            if (out != null) {
                send("a" + (++tag) + " LOGOUT\r\n");
            }
        } catch (IOException ignored) {
            // closing anyway
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // closed
        }
        socket = null;
        in = null;
        out = null;
    }

    private List<Long> uids(String search) throws IOException {
        List<String> lines = command(search, new ArrayList<byte[]>());
        List<Long> out = new ArrayList<Long>();
        for (String l : lines) {
            if (l.startsWith("* SEARCH")) {
                for (String n : l.substring(8).trim().split("\\s+")) {
                    if (n.matches("\\d+")) {
                        out.add(Long.parseLong(n));
                    }
                }
            }
        }
        return out;
    }

    private static List<Long> last(List<Long> all, int n) {
        List<Long> out = new ArrayList<Long>(all.subList(Math.max(0, all.size() - Math.max(1, n)), all.size()));
        java.util.Collections.reverse(out); // newest first
        return out;
    }

    private List<Mail> fetch(List<Long> ids) throws IOException {
        List<Mail> out = new ArrayList<Mail>();
        if (ids.isEmpty()) {
            return out;
        }
        StringBuilder set = new StringBuilder();
        for (Long id : ids) {
            set.append(set.length() == 0 ? "" : ",").append(id);
        }
        List<byte[]> parts = new ArrayList<byte[]>();
        List<String> lines = command("UID FETCH " + set + " (UID BODY.PEEK[HEADER.FIELDS (FROM SUBJECT DATE CONTENT-TYPE"
                + " CONTENT-TRANSFER-ENCODING)] BODY.PEEK[TEXT]<0.1500>)", parts);
        // each message: a "* n FETCH (UID u …" line and its two literals (the headers, then the start of the text)
        int p = 0;
        java.util.Map<Long, Mail> byId = new java.util.HashMap<Long, Mail>();
        for (String l : lines) {
            Matcher m = Pattern.compile("^\\* \\d+ FETCH \\(.*?UID (\\d+)").matcher(l);
            if (!m.find() || p + 1 >= parts.size()) {
                continue;
            }
            long uid = Long.parseLong(m.group(1));
            String head = latin(parts.get(p));
            String text = Mime.text(head, parts.get(p + 1)).replaceAll("\\s+", " ").trim();
            p += 2;
            byId.put(uid, new Mail(uid, Mime.header(head, "From"), Mime.header(head, "Subject"), Mime.header(head, "Date"),
                    text.length() > 160 ? text.substring(0, 160) + "…" : text));
        }
        for (Long id : ids) {
            if (byId.containsKey(id)) {
                out.add(byId.get(id));
            }
        }
        return out;
    }

    /** Sends a tagged command; the untagged lines (literals go to {@code literals}); throws on NO or BAD. */
    private List<String> command(String c, List<byte[]> literals) throws IOException {
        String t = "a" + (++tag);
        send(t + " " + c + "\r\n");
        return finish(t, literals);
    }

    private List<String> finish(String t, List<byte[]> literals) throws IOException {
        List<String> lines = new ArrayList<String>();
        while (true) {
            String l = line();
            if (l == null) {
                throw new IOException("Gmail closed the connection");
            }
            StringBuilder whole = new StringBuilder(l);
            Matcher lit = Pattern.compile("\\{(\\d+)\\}$").matcher(l);
            while (lit.find()) {
                int n = Integer.parseInt(lit.group(1));
                literals.add(bytes(n));
                String rest = line();
                if (rest == null) {
                    break;
                }
                whole.append(" ").append(rest);
                lit = Pattern.compile("\\{(\\d+)\\}$").matcher(rest);
            }
            String done = whole.toString();
            if (done.startsWith(t + " ")) {
                if (done.startsWith(t + " OK")) {
                    return lines;
                }
                throw new IOException(done.substring(t.length()).trim());
            }
            lines.add(done);
        }
    }

    private void send(String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private String line() throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                b.write(c);
            }
        }
        if (c == -1 && b.size() == 0) {
            return null;
        }
        return new String(b.toByteArray(), StandardCharsets.UTF_8);
    }

    private byte[] bytes(int n) throws IOException {
        byte[] buf = new byte[n];
        int got = 0;
        while (got < n) {
            int r = in.read(buf, got, n - got);
            if (r < 0) {
                break;
            }
            got += r;
        }
        return got == n ? buf : java.util.Arrays.copyOf(buf, got);
    }

    static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String oneLine(String s) {
        return s == null ? "" : s.replaceAll("[\\r\\n]+", " ").trim();
    }

    private static String latin(byte[] b) {
        return new String(b, StandardCharsets.ISO_8859_1);
    }

    /** Just enough MIME to read a mail: headers (with =?UTF-8?…?= words), the plain text part, its transfer encoding. */
    static final class Mime {
        private Mime() {
        }

        /** A header's value ("Subject"), unfolded and decoded; "" when it isn't there. */
        static String header(String head, String name) {
            Matcher m = Pattern.compile("(?im)^" + Pattern.quote(name) + ":[ \\t]*(.*(?:\\r?\\n[ \\t]+.*)*)").matcher(head);
            if (!m.find()) {
                return "";
            }
            return decodeWords(m.group(1).replaceAll("\\r?\\n[ \\t]+", " ").trim());
        }

        /** "=?UTF-8?B?…?=" and "=?utf-8?Q?…?=" words as text. */
        static String decodeWords(String v) {
            Matcher m = Pattern.compile("=\\?([^?]+)\\?([bBqQ])\\?([^?]*)\\?=").matcher(v);
            StringBuffer b = new StringBuffer();
            while (m.find()) {
                String text;
                try {
                    Charset cs = Charset.forName(m.group(1));
                    byte[] raw = m.group(2).equalsIgnoreCase("B") ? Base64.getMimeDecoder().decode(m.group(3))
                            : qp(m.group(3).replace('_', ' '));
                    text = new String(raw, cs);
                } catch (RuntimeException e) {
                    text = m.group(3);
                }
                m.appendReplacement(b, Matcher.quoteReplacement(text));
            }
            m.appendTail(b);
            return b.toString().replaceAll("\\?=\\s+=\\?", "?==?");
        }

        static String encodeHeader(String v) {
            for (char c : v.toCharArray()) {
                if (c > 126) {
                    return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(v.getBytes(StandardCharsets.UTF_8)) + "?=";
                }
            }
            return v;
        }

        /** The readable text of a mail's body: its plain part (or its HTML part without tags), decoded. */
        static String text(String head, byte[] body) {
            String type = header(head, "Content-Type").toLowerCase(Locale.ROOT);
            String raw = latin(body);
            if (type.startsWith("multipart/")) {
                Matcher b = Pattern.compile("boundary=\"?([^\";]+)\"?").matcher(header(head, "Content-Type"));
                String boundary = b.find() ? b.group(1) : null;
                String plain = null, html = null;
                for (String part : boundary == null ? new String[]{raw} : raw.split("--" + Pattern.quote(boundary))) {
                    int split = part.indexOf("\r\n\r\n");
                    if (split < 0) {
                        split = part.indexOf("\n\n");
                    }
                    if (split < 0) {
                        continue;
                    }
                    String ph = part.substring(0, split);
                    String pb = part.substring(split).trim();
                    String pt = header(ph, "Content-Type").toLowerCase(Locale.ROOT);
                    if (pt.startsWith("multipart/")) {
                        String inner = text(ph, pb.getBytes(StandardCharsets.ISO_8859_1));
                        if (!inner.isEmpty() && plain == null) {
                            plain = inner;
                        }
                    } else if (pt.startsWith("text/plain") && plain == null) {
                        plain = decode(ph, pb);
                    } else if (pt.startsWith("text/html") && html == null) {
                        html = stripTags(decode(ph, pb));
                    }
                }
                return (plain != null ? plain : html != null ? html : "").trim();
            }
            String t = decode(head, raw.trim());
            return (type.startsWith("text/html") ? stripTags(t) : t).trim();
        }

        private static String decode(String head, String body) {
            String cte = header(head, "Content-Transfer-Encoding").toLowerCase(Locale.ROOT);
            Matcher cs = Pattern.compile("charset=\"?([^\";\\s]+)").matcher(header(head, "Content-Type"));
            Charset charset = StandardCharsets.UTF_8;
            try {
                if (cs.find()) {
                    charset = Charset.forName(cs.group(1));
                }
            } catch (RuntimeException ignored) {
                // UTF-8
            }
            byte[] raw;
            try {
                raw = cte.contains("base64") ? Base64.getMimeDecoder().decode(body.replaceAll("[^A-Za-z0-9+/=]", ""))
                        : cte.contains("quoted-printable") ? qp(body.replaceAll("=\\r?\\n", ""))
                        : body.getBytes(StandardCharsets.ISO_8859_1);
            } catch (RuntimeException e) {
                raw = body.getBytes(StandardCharsets.ISO_8859_1); // a cut-off base64 tail: as it is
            }
            return new String(raw, charset);
        }

        static byte[] qp(String s) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '=' && i + 2 < s.length() && isHex(s.charAt(i + 1)) && isHex(s.charAt(i + 2))) {
                    b.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
                    i += 2;
                } else {
                    b.write(c & 0xff);
                }
            }
            return b.toByteArray();
        }

        private static boolean isHex(char c) {
            return Character.digit(c, 16) >= 0;
        }

        static String stripTags(String html) {
            return html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ").replaceAll("(?i)<br\\s*/?>|</p>|</div>", "\n")
                    .replaceAll("<[^>]+>", " ").replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                    .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replaceAll("[ \\t]+", " ")
                    .replaceAll("\\n\\s*\\n+", "\n\n").trim();
        }
    }
}
