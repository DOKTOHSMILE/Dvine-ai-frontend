package ai.dvine.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.Test;

import ai.dvine.chat.ChatBook;

public class MessagePartsTest {
    @Test
    public void aPageIsHtmlAndCodeStaysCode() {
        List<MessageParts.Part> parts = MessageParts.split(
                "Look.\n```html\n<html><body>Hi</body></html>\n```\n```java\nint x = 1;\n```");
        assertEquals(3, parts.size());
        assertEquals("text", parts.get(0).kind);
        assertEquals("html", parts.get(1).kind);
        assertEquals("code", parts.get(2).kind);
        assertEquals("java", parts.get(2).language);
        assertTrue(parts.get(2).body.contains("int x"));
    }

    @Test
    public void renamePinAndDeleteStayOnDisk() throws Exception {
        File dir = java.nio.file.Files.createTempDirectory("hist").toFile();
        File file = new File(dir, "chats.json");
        ChatBook book = new ChatBook(file);
        ChatBook.Section extra = book.open("Notes", "obi");
        book.pin(extra.id, true);
        book.rename(extra.id, "Pinned notes");
        ChatBook again = new ChatBook(file);
        again.use(extra.id);
        assertTrue(again.current().pinned);
        assertEquals("Pinned notes", again.current().title);
        again.remove(extra.id);
        assertEquals(1, again.sections().size());
    }
}
