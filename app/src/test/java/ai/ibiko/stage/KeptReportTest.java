package ai.ibiko.stage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import ai.ibiko.chat.ReplyText;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** A report the AI is writing never vanishes: not when a tool call follows it, not when the next step begins. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class KeptReportTest {
    static final String REPORT = "## Audit report\n\n" + "The chat screen keeps every reply and every step. ".repeat(12);

    @Test
    public void aToolCallHidesOnlyItself() {
        assertEquals(-1, ReplyText.toolStart(REPORT));
        String withCall = REPORT + "\n<tool name=\"write_file\" path=\"REPORT.md\">...";
        assertEquals(REPORT.length() + 1, ReplyText.toolStart(withCall));
        assertEquals(0, ReplyText.toolStart("<tool name=\"list_files\" path=\".\"/>"));
        assertEquals(0, ReplyText.toolStart("<read_file path=\"a.txt\"/>"));
        assertEquals("ordinary writing is not a tool", -1, ReplyText.toolStart("I compared a < b and b > c in the file."));
    }

    @Test
    public void theNextStepKeepsTheWritingAndTheFinalAnswerIsNotShownTwice() throws Exception {
        ActivityController<ai.ibiko.MainActivity> c = Robolectric.buildActivity(ai.ibiko.MainActivity.class).create().start().resume().visible();
        java.lang.reflect.Field f = ai.ibiko.MainActivity.class.getDeclaredField("chatPane");
        f.setAccessible(true);
        ChatPane pane = (ChatPane) f.get(c.get());
        java.lang.reflect.Field bookF = ChatPane.class.getDeclaredField("book");
        bookF.setAccessible(true);
        ai.ibiko.chat.ChatBook book = (ai.ibiko.chat.ChatBook) bookF.get(pane);
        int before = book.current().messages.size();
        pane.setLive(REPORT);
        pane.stepEnded(); // the next step starts
        assertEquals("the report is kept as a message", before + 1, book.current().messages.size());
        assertTrue(book.current().messages.get(before).body.contains("Audit report"));
        pane.assistant("ibiko", REPORT); // the final answer repeats it
        assertEquals("not shown twice", before + 1, book.current().messages.size());
        // a short passing note is still cleared, not kept
        pane.setLive("Reading the files now.");
        pane.stepEnded();
        assertEquals(before + 1, book.current().messages.size());
        c.pause().stop().destroy();
    }
}
