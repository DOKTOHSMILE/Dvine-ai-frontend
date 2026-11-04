package ai.dvine.chat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NotesRecordTest {
    @Test
    public void whatNoLongerFitsIsKeptAsNotes() throws Exception {
        java.io.File f = java.nio.file.Files.createTempFile("chats", ".json").toFile();
        f.delete();
        ChatBook b = new ChatBook(f);
        b.forPersona("obi");
        b.add("user", "text", "My shop is in Yaba and I sell phones.", "obi");
        b.reply("obi", "Noted: a phone shop in Yaba. That's a busy market with good foot traffic.");
        for (int i = 0; i < 30; i++) {
            b.add("user", "text", "Question number " + i + " about pricing and stock for the week ahead, in detail.", "obi");
            b.reply("obi", "Answer " + i + ": keep two weeks of stock and price ten percent under the market. More words follow here.");
        }
        b.add("user", "text", "Where is my shop again?", "obi");
        String t = b.transcript(1500, 300);
        if (System.getenv("SHOW_NOTES") != null) {
            System.out.println("=====\n" + t + "\n=====");
        }
        assertTrue(t, t.length() <= 1500 + 80);
        assertTrue("the start is in the notes", t.contains("Notes from earlier in this chat") && t.contains("Yaba"));
        assertTrue("the latest message is there whole", t.endsWith("User: Where is my shop again?"));
        // for a reply: what is older than the model's own last 6 turns
        String older = b.olderNotes(7, 1200);
        assertTrue(older, older.contains("Yaba") && !older.contains("Question number 28"));
        // a short chat needs no notes
        ChatBook small = new ChatBook(null);
        small.add("user", "text", "Hello", "obi");
        assertFalse(small.transcript(1500, 300).contains("Notes from earlier"));
        assertTrue(small.olderNotes(7, 1200).isEmpty());
    }
}
