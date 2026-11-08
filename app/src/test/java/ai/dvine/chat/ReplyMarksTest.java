package ai.dvine.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReplyMarksTest {
    @Test
    public void formatsAReply() {
        ReplyMarks.Result r = ReplyMarks.of("## Plan\n\n**Bold** and *slanted* and `code`.\n"
                + "- one item\n"
                + "See [the note](https://example.com/note).\n"
                + "Also https://example.com/plain.\n"
                + "> a quote\n"
                + "keep file_name as written");
        assertTrue(r.text.startsWith("Plan\n"));
        assertTrue(r.text.contains("Bold"));
        assertTrue(!r.text.contains("**"));
        assertTrue(r.text.contains("\u2022 one item"));
        assertTrue(r.text.contains("the note"));
        assertTrue(!r.text.contains("](http"));
        assertTrue(r.text.contains("https://example.com/plain"));
        assertTrue(r.text.contains("file_name"));
        assertTrue(kind(r, "Bold", ReplyMarks.BOLD));
        assertTrue(kind(r, "slanted", ReplyMarks.ITALIC));
        assertTrue(kind(r, "code", ReplyMarks.CODE));
        assertTrue(kind(r, "Plan", ReplyMarks.HEAD));
        assertTrue(kind(r, "a quote", ReplyMarks.QUOTE));
        assertTrue(link(r, "the note", "https://example.com/note"));
        assertTrue(link(r, "https://example.com/plain", "https://example.com/plain"));
    }

    @Test
    public void aFenceIsCodeNotMarks() {
        ReplyMarks.Result r = ReplyMarks.of("Before\n```\n**not bold**\n```\nAfter");
        assertEquals("Before\n**not bold**\nAfter", r.text);
        assertTrue(kind(r, "**not bold**", ReplyMarks.CODE));
    }

    private static boolean kind(ReplyMarks.Result r, String words, int kind) {
        int at = r.text.indexOf(words);
        for (ReplyMarks.Mark m : r.marks) {
            if (m.kind == kind && m.from == at && m.to == at + words.length()) {
                return true;
            }
        }
        return false;
    }

    private static boolean link(ReplyMarks.Result r, String words, String url) {
        int at = r.text.indexOf(words);
        for (ReplyMarks.Mark m : r.marks) {
            if (m.kind == ReplyMarks.LINK && m.from == at && url.equals(m.url)) {
                return true;
            }
        }
        return false;
    }
}
