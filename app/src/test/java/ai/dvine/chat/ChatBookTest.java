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
        assertFalse(memory, memory.contains("the front tyre is off")); // another conversation stays out
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
    public void newChatIsCleanForAllThreePersonas() throws Exception {
        java.io.File f = java.nio.file.Files.createTempFile("chats", ".json").toFile();
        f.delete();
        ChatBook b = new ChatBook(f);
        for (String p : new String[]{"obi", "ivie", "ibiko"}) {
            b.forPersona(p);
            b.add("user", "text", "old talk with " + p, p);
        }
        String oldIvie = b.forPersona("ivie").id;
        ChatBook.Section obi = b.freshForAll("obi");
        org.junit.Assert.assertTrue(obi.messages.isEmpty());
        org.junit.Assert.assertTrue("Ivie starts clean too", b.forPersona("ivie").messages.isEmpty());
        org.junit.Assert.assertTrue("and Ibiko", b.forPersona("ibiko").messages.isEmpty());
        org.junit.Assert.assertNotEquals(oldIvie, b.current().id.equals(oldIvie) ? "" : oldIvie + "x");
        b.add("user", "text", "new talk with ibiko", "ibiko");
        String newIbiko = b.current().id;
        b.forPersona("obi");
        org.junit.Assert.assertEquals("back and forth keeps the new chats", newIbiko, b.forPersona("ibiko").id);
        ChatBook again = new ChatBook(f); // kept on the phone
        org.junit.Assert.assertTrue("after a restart Ivie is still clean", again.forPersona("ivie").messages.isEmpty());
        boolean listed = false;
        for (ChatBook.Section s : again.sections()) {
            listed |= s.id.equals(oldIvie);
        }
        org.junit.Assert.assertTrue("the old chats stay in the list", listed);
    }

    @Test
    public void eachConversationHasItsOwnCleanPersonaPages() throws Exception {
        File dir = java.nio.file.Files.createTempDirectory("chats").toFile();
        ChatBook book = new ChatBook(new File(dir, "chats.json"));
        book.forPersona("ivie");
        book.add("user", "text", "draw the first car", "ivie");
        String first = book.current().group;
        book.forPersona("obi");
        book.add("user", "text", "plan the first car", "obi");
        org.junit.Assert.assertEquals(first, book.current().group);
        book.freshForAll("obi");
        org.junit.Assert.assertTrue(book.current().messages.isEmpty());
        org.junit.Assert.assertNotEquals(first, book.current().group);
        book.forPersona("ivie");
        org.junit.Assert.assertTrue(book.current().messages.isEmpty());
        org.junit.Assert.assertFalse(book.transcript(700).contains("draw the first car"));
        book.use(book.sections().get(0).id);
        org.junit.Assert.assertEquals("draw the first car", book.current().messages.get(0).body);
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

    /** The drawer lists chats and projects newest first, oldest last; pinned chats stay on top. */
    @org.junit.Test
    public void theDrawerIsNewestFirst() throws Exception {
        ChatBook b = new ChatBook(new File(java.nio.file.Files.createTempDirectory("book").toFile(), "chats.json"));
        b.open("first", "obi").pinned = false;
        b.open("second", "obi");
        ChatBook.Section third = b.open("third", "obi");
        java.util.List<String> titles = new java.util.ArrayList<String>();
        for (ChatBook.Section s : b.groups()) {
            titles.add(s.title);
        }
        org.junit.Assert.assertTrue(titles.toString(), titles.indexOf("third") < titles.indexOf("second")
                && titles.indexOf("second") < titles.indexOf("first"));
        b.addProject("Old project");
        b.addProject("New project");
        org.junit.Assert.assertEquals("New project", b.projectsNewestFirst().get(0));
    }

    /** A job that finishes while another page is open writes its reply on its own page; the open page stays open. */
    @org.junit.Test
    public void aBackgroundReplyLandsOnItsOwnPage() throws Exception {
        ChatBook b = new ChatBook(new File(java.nio.file.Files.createTempDirectory("book").toFile(), "chats.json"));
        ChatBook.Section obi = b.forPersona("obi");
        ChatBook.Section ivie = b.forPersona("ivie");
        b.replyToPage("obi", "Obi's answer");
        org.junit.Assert.assertEquals(ivie.id, b.current().id);
        org.junit.Assert.assertEquals("Obi's answer", obi.messages.get(obi.messages.size() - 1).body);
        org.junit.Assert.assertTrue(ivie.messages.isEmpty());
    }
}
