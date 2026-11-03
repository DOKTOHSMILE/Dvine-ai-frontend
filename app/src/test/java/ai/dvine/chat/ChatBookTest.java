package ai.dvine.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

import ai.dvine.chat.ChatBook.Message;

public class ChatBookTest {
    @Test
    public void sectionsKeepTheirOwnTranscriptAndShareMemory() throws Exception {
        File dir = java.nio.file.Files.createTempDirectory("chats").toFile();
        ChatBook book = new ChatBook(new File(dir, "chats.json"));
        book.add("user", "text", "the front tyre is off", "ivie");
        book.remember("the motorcycle's front tyre was detached");
        ChatBook.Section second = book.open("Build notes", "ibiko");
        book.add("user", "text", "what did we do to the tyre?", "ibiko");
        String memory = book.memoryForPrompt(6);
        assertTrue(memory, memory.contains("front tyre was detached"));
        assertTrue(memory, memory.contains("the front tyre is off"));
        assertFalse(memory.contains("what did we do"));
        assertEquals("ibiko", second.persona);
        assertEquals(1, book.current().messages.size());
        book.use(book.sections().get(0).id);
        assertEquals("the front tyre is off", book.current().messages.get(0).body);

        ChatBook again = new ChatBook(new File(dir, "chats.json"));
        assertEquals(2, again.sections().size());
        again.use(again.sections().get(1).id);
        assertTrue(again.memoryForPrompt(6).contains("front tyre was detached"));
    }

    @Test
    public void aFencedReplyBecomesACodeBlock() {
        java.util.List<Message> parts = ChatBook.blocks("assistant", "ibiko",
                "Here is the method.\n```java\nint add(int a, int b) { return a + b; }\n```\nThat is all.");
        assertEquals(3, parts.size());
        assertEquals("text", parts.get(0).kind);
        assertEquals("code", parts.get(1).kind);
        assertTrue(parts.get(1).body.contains("return a + b"));
        assertEquals("text", parts.get(2).kind);
    }

    @Test
    public void theTranscriptIsOnlyTheOpenChat() throws Exception {
        File dir = java.nio.file.Files.createTempDirectory("chats").toFile();
        ChatBook book = new ChatBook(new File(dir, "chats.json"));
        book.add("user", "text", "keep this in chat one", "ivie");
        book.reply("ivie", "Noted in the first chat.");
        book.open("Second", "obi");
        book.add("user", "text", "only the second chat", "obi");
        String here = book.transcript(700);
        assertTrue(here, here.contains("only the second chat"));
        assertTrue(here.contains("This chat (obi)"));
        assertFalse(here.contains("keep this in chat one"));
        book.add("assistant", "image", "/tmp/not-a-real-photo.png", "obi");
        assertTrue(book.transcript(700).contains("[a photograph file is in this chat]"));
        assertFalse(book.transcript(700).contains("not-a-real-photo"));
    }

    @org.junit.Test
    public void chatsGoIntoProjectsAndProjectsAreKept() throws Exception {
        java.io.File f = java.nio.file.Files.createTempFile("chats", ".json").toFile();
        f.delete();
        ChatBook b = new ChatBook(f);
        org.junit.Assert.assertTrue(b.addProject("Harvana app"));
        org.junit.Assert.assertFalse(b.addProject("Harvana app")); // a name once
        b.add("user", "text", "Audit the wallet", "ibiko");
        String audit = b.current().id;
        b.moveTo(audit, "Harvana app");
        b.fresh("obi");
        b.add("user", "text", "Plan my week", "obi");
        b.moveTo(b.current().id, "Life admin"); // a project named here is made
        ChatBook again = new ChatBook(f); // kept on the phone
        org.junit.Assert.assertEquals(java.util.Arrays.asList("Harvana app", "Life admin"), again.projects());
        org.junit.Assert.assertEquals(1, again.inProject("Harvana app").size());
        org.junit.Assert.assertEquals(audit, again.inProject("Harvana app").get(0).id);
        again.removeProject("Harvana app"); // its chats stay, outside any project
        org.junit.Assert.assertEquals(java.util.Arrays.asList("Life admin"), again.projects());
        boolean kept = false;
        for (ChatBook.Section s : again.sections()) {
            if (s.id.equals(audit)) {
                kept = s.project == null;
            }
        }
        org.junit.Assert.assertTrue(kept);
    }
}
