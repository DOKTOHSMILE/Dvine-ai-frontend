package ai.dvine.connect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/** Gmail against a small IMAP server on this machine that answers the way Gmail does. */
public class GmailTest {
    static final String HEAD1 = "From: =?UTF-8?B?QWRhIE9rZWtl?= <ada@bank.ng>\r\nSubject: Your statement\r\nDate: Mon, 3 Nov 2026 09:00:00 +0100\r\n"
            + "Content-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: quoted-printable\r\n\r\n";
    static final String TEXT1 = "Your balance is =E2=82=A6250,000.\r\nThank you.\r\n";
    static final String HEAD2 = "From: Shop <hi@shop.com>\r\nSubject: Sale\r\nDate: Sun, 2 Nov 2026 10:00:00 +0100\r\n"
            + "Content-Type: multipart/alternative; boundary=\"b1\"\r\n\r\n";
    static final String TEXT2 = "--b1\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n50% off today.\r\n--b1\r\nContent-Type: text/html\r\n\r\n<p>50% off</p>\r\n--b1--\r\n";

    final List<String> heard = new ArrayList<String>();
    String password = "abcd efgh ijkl mnop";

    private Gmail.Wire server() throws Exception {
        final ServerSocket ss = new ServerSocket(0);
        Thread t = new Thread(() -> {
            try {
                while (true) {
                    try (Socket s = ss.accept()) {
                        serve(s);
                    }
                }
            } catch (Exception ignored) {
                // closed
            }
        });
        t.setDaemon(true);
        t.start();
        return (host, port) -> new Socket("127.0.0.1", ss.getLocalPort());
    }

    private void serve(Socket s) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1));
        OutputStream out = s.getOutputStream();
        w(out, "* OK Gimap ready\r\n");
        String l;
        while ((l = in.readLine()) != null) {
            synchronized (heard) {
                heard.add(l);
            }
            String tag = l.split(" ")[0];
            String c = l.substring(tag.length() + 1);
            if (c.startsWith("LOGIN")) {
                w(out, c.contains("\"abcdefghijklmnop\"") ? tag + " OK signed in\r\n" : tag + " NO [AUTHENTICATIONFAILED] Invalid credentials\r\n");
            } else if (c.startsWith("EXAMINE")) {
                w(out, "* 2 EXISTS\r\n" + tag + " OK [READ-ONLY] INBOX selected\r\n");
            } else if (c.startsWith("UID SEARCH X-GM-RAW")) {
                w(out, "* SEARCH 41\r\n" + tag + " OK done\r\n");
            } else if (c.startsWith("UID SEARCH")) {
                w(out, "* SEARCH 40 41\r\n" + tag + " OK done\r\n");
            } else if (c.startsWith("UID FETCH")) {
                StringBuilder r = new StringBuilder();
                if (c.contains("41")) {
                    r.append("* 2 FETCH (UID 41 BODY[HEADER.FIELDS (FROM SUBJECT DATE)] {").append(HEAD1.length()).append("}\r\n").append(HEAD1)
                            .append(" BODY[TEXT]<0> {").append(TEXT1.getBytes(StandardCharsets.ISO_8859_1).length).append("}\r\n").append(TEXT1).append(")\r\n");
                }
                if (c.contains("40")) {
                    r.append("* 1 FETCH (UID 40 BODY[HEADER.FIELDS (FROM SUBJECT DATE)] {").append(HEAD2.length()).append("}\r\n").append(HEAD2)
                            .append(" BODY[TEXT]<0> {").append(TEXT2.length()).append("}\r\n").append(TEXT2).append(")\r\n");
                }
                w(out, r + tag + " OK done\r\n");
            } else if (c.startsWith("APPEND")) {
                int n = Integer.parseInt(c.replaceAll(".*\\{(\\d+)\\}$", "$1"));
                w(out, "+ go ahead\r\n");
                char[] buf = new char[n];
                int got = 0;
                while (got < n) {
                    got += in.read(buf, got, n - got);
                }
                synchronized (heard) {
                    heard.add("DRAFT:" + new String(buf));
                }
                in.readLine();
                w(out, tag + " OK [APPENDUID 1 9] appended\r\n");
            } else if (c.startsWith("LOGOUT")) {
                w(out, "* BYE\r\n" + tag + " OK bye\r\n");
                return;
            } else {
                w(out, tag + " BAD unknown\r\n");
            }
        }
    }

    private static void w(OutputStream out, String s) throws Exception {
        out.write(s.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    @Test
    public void readsTheInboxSearchesOpensAndDrafts() throws Exception {
        Gmail g = new Gmail("me@gmail.com", password, server());
        assertNull("an app password works with its spaces", g.test());
        List<Gmail.Mail> inbox = g.latest(10);
        assertEquals(2, inbox.size());
        assertEquals("newest first", 41, inbox.get(0).uid);
        assertEquals("Ada Okeke <ada@bank.ng>", inbox.get(0).from);
        assertEquals("Your statement", inbox.get(0).subject);
        assertTrue(inbox.get(0).snippet, inbox.get(0).snippet.contains("₦250,000"));
        assertEquals("the plain part of a multipart mail", "50% off today.", inbox.get(1).snippet);
        assertEquals(1, g.search("from:bank", 10).size());
        String one = g.read(41, 2000);
        assertNotNull(one);
        assertTrue(one, one.startsWith("From: Ada Okeke") && one.contains("Your balance is ₦250,000."));
        g.draft("ada@bank.ng", "Thanks", "Thank you, Ada.");
        boolean drafted = false, readOnly = false;
        synchronized (heard) {
            for (String h : heard) {
                drafted |= h.startsWith("DRAFT:") && h.contains("To: ada@bank.ng") && h.contains("Thank you, Ada.");
                readOnly |= h.contains("EXAMINE INBOX");
            }
        }
        assertTrue("written into Drafts, not sent", drafted);
        assertTrue("the inbox is opened read-only, so nothing is marked read", readOnly);
    }

    @Test
    public void aWrongPasswordSaysWhatToUse() throws Exception {
        String why = new Gmail("me@gmail.com", "wrong", server()).test();
        assertNotNull(why);
        assertTrue(why, why.contains("app password"));
        assertNotNull(new Gmail("", "", server()).test());
    }
}
