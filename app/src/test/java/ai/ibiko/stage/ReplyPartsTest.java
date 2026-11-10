package ai.ibiko.stage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import ai.ibiko.agent.StepGroups;

public class ReplyPartsTest {
    @Test
    public void aReplyComesInShortPartsEachHeardOnItsOwn() {
        List<String> p = ChatPane.paragraphs("First I read the file.\n\n## What I found\n\nThe loop never ends.\n- one\n- two\n\n"
                + "```java\nint a = 1;\n\nint b = 2;\n```\n\nDone.");
        assertEquals(4, p.size());
        assertEquals("First I read the file.", p.get(0));
        assertEquals("a heading stays with what it names", "## What I found\nThe loop never ends.\n- one\n- two", p.get(1));
        assertTrue("code stays whole", p.get(2).startsWith("```java") && p.get(2).contains("int b = 2;") && p.get(2).endsWith("```"));
        assertEquals("Done.", p.get(3));
        assertEquals(Arrays.asList("One line."), ChatPane.paragraphs("One line."));
        assertTrue(ChatPane.paragraphs("\n\n").isEmpty());
    }

    @Test
    public void stepsBecomeRowsLikeClaudeCode() {
        List<String> words = Arrays.asList("ls -la", "git status", "Edited app/Main.java", "Searching the web for x",
                "a", "b", "c", "d");
        List<String> titles = Arrays.asList("$ ls -la", "$ git status", null, null, null, null, null, null);
        List<Long> secs = new ArrayList<Long>(Arrays.asList(1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L));
        List<ChatPane.Row> rows = ChatPane.rows(StepGroups.fold(words, titles, secs), words, titles);
        assertEquals("Ran 2 commands", rows.get(0).label);
        assertEquals(0, rows.get(0).from);
        assertEquals(2, rows.get(0).to);
        assertEquals("one file edited is named", "Edited Main.java", rows.get(1).label);
        // five plain steps in a row fold into one row, named by the latest step (never "and 4 more steps")
        assertEquals("d", rows.get(2).label);
        assertEquals(3, rows.get(2).from);
        assertEquals(8, rows.get(2).to);
        assertEquals(3, rows.size());
        // one command alone shows the command
        List<String> one = Arrays.asList("Checked the branch");
        List<String> t1 = Arrays.asList("$ git status -sb");
        assertEquals("Ran git status -sb", ChatPane.rows(StepGroups.fold(one, t1, Arrays.asList(1L)), one, t1).get(0).label);
    }

    @Test
    public void aLongChatDrawsOnlyItsLatestMessages() {
        List<ai.ibiko.chat.ChatBook.Message> m = new ArrayList<ai.ibiko.chat.ChatBook.Message>();
        for (int i = 0; i < 100; i++) {
            m.add(new ai.ibiko.chat.ChatBook.Message(i % 3 == 0 ? "user" : "assistant", "text", "m" + i, "obi", null));
        }
        assertEquals("a short chat is drawn whole", 0, ChatPane.firstShown(m.subList(0, 20), ChatPane.WINDOW));
        int from = ChatPane.firstShown(m, ChatPane.WINDOW);
        assertTrue(from >= 100 - ChatPane.WINDOW && from < 100 - ChatPane.WINDOW + 8);
        assertEquals("it starts at a question, never mid-turn", "user", m.get(from).role);
        assertEquals("Show earlier adds more", 0, ChatPane.firstShown(m, ChatPane.WINDOW * 4));
    }
}
