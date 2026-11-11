package ai.ibiko.learn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.Test;

import ai.ibiko.chat.ChatBook;

/** Any text into ids and back into the same words; every conversation saved as ids in its own chat's folder. */
public class IdentifierTest {
    @Test
    public void textBecomesIdsAndIdsReadBack() {
        List<Identifier.Sentence> s = Identifier.identify("m1", "Abuja is the capital of Nigeria. It is in the middle.");
        assertEquals(2, s.size());
        assertEquals("m1#0", s.get(0).id);
        assertEquals("six words and the full stop", 7, s.get(0).size());
        assertEquals("one word, one code: 'is' is the same in both", s.get(0).codes.split(" ")[1], s.get(1).codes.split(" ")[1]);
        assertEquals("Abuja is the capital of Nigeria.", Identifier.read(s.get(0).codes));
        assertEquals("It is in the middle.", Identifier.read(s.get(1).codes));
    }

    @Test
    public void aConversationIsSavedAsIds() throws Exception {
        File dir = Files.createTempDirectory("ids").toFile();
        ChatBook book = new ChatBook(new File(dir, "chats.json"));
        book.open("Trip", "ibiko");
        book.add("user", "text", "Plan my trip to Lagos.", "ibiko");
        book.add("assistant", "text", "Flights first. Then a hotel in Ikoyi.", "ibiko");
        Identifier.flush();
        String chat = book.current().group;
        List<String> lines = Files.readAllLines(Identifier.file(dir, chat).toPath(), StandardCharsets.UTF_8);
        assertEquals(3, lines.size());
        String[] first = lines.get(0).split("\t");
        assertEquals("user", first[0]);
        assertEquals(book.current().messages.get(0).id + "#0", first[1]);
        assertEquals("Plan my trip to Lagos.", Identifier.read(first[2]));
        assertTrue(lines.get(2).startsWith("assistant\t" + book.current().messages.get(1).id + "#1\t"));
        assertEquals("Then a hotel in Ikoyi.", Identifier.read(lines.get(2).split("\t")[2]));
    }
}
