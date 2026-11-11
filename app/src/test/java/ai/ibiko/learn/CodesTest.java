package ai.ibiko.learn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.Test;

import ai.ibiko.chat.ChatBook;

/** Sentences filed under ids and tags; a question pulls only the ids about it, and only those are read back. */
public class CodesTest {
    @Test
    public void tagsPullTheIdsOfTheRightSentences() {
        Codes c = new Codes();
        c.addMessage("m1", "We moved the build to Gradle 8. The invoices now print in naira.");
        c.addMessage("m2", "Lagos traffic is worst at six. The Gradle wrapper lives in the tools folder.");
        assertEquals("one id a sentence", 4, c.size());
        assertEquals("We moved the build to Gradle 8.", c.read("m1#0"));
        assertTrue(c.ids("gradle").contains("m1#0") && c.ids("gradle").contains("m2#1"));
        List<String> hit = c.recall("where is the gradle wrapper?", 3, 2);
        assertEquals("m2#1", hit.get(0));
        assertFalse("nothing about invoices is read", hit.contains("m1#1"));
        String back = c.readBack(c.recall("invoice printing", 3, 1), 500);
        assertTrue(back, back.startsWith("[m1#1] The invoices now print in naira."));
        assertTrue(c.recall("weather in Abuja", 3, 1).isEmpty());
    }

    @Test
    public void aChatRecallsOnlyWhatItSaidAboutTheQuestion() throws Exception {
        File dir = java.nio.file.Files.createTempDirectory("codes").toFile();
        ChatBook book = new ChatBook(new File(dir, "chats.json"));
        book.open("Work", "ibiko");
        book.add("user", "text", "My shop sells shea butter in Kano.", "ibiko");
        book.add("assistant", "text", "Noted: shea butter, Kano.", "ibiko");
        for (int i = 0; i < 10; i++) {
            book.add("user", "text", "Question " + i + " about the weather", "ibiko");
            book.add("assistant", "text", "Answer " + i + " about rain.", "ibiko");
        }
        String recalled = book.recall("what does my shop sell?", 6, 1000);
        assertTrue(recalled, recalled.contains("shea butter") && !recalled.contains("rain"));
        assertTrue("with its code", recalled.matches("(?s)^\\[m[0-9a-z]+#0\\] .*"));
        assertEquals("a short chat is all in the brain's memory already", "",
                new ChatBook(new File(dir, "other.json")).recall("shop", 6, 1000));
    }
}
