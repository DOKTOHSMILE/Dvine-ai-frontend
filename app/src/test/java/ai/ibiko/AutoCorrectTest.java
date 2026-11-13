package ai.ibiko;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import ai.ibiko.chat.ChatBook.Message;

/** A correction is taken only right after a reply to a question. */
public class AutoCorrectTest {
    private static List<Message> chat(String... roleText) {
        List<Message> ms = new ArrayList<Message>();
        for (int i = 0; i + 1 < roleText.length; i += 2) {
            ms.add(new Message(roleText[i], "text", roleText[i + 1], "ibiko"));
        }
        return ms;
    }

    @Test
    public void theLastQuestionAndItsReply() {
        assertArrayEquals(new String[] {"What is the capital of Ghana?", "Kumasi."},
                AutoCorrect.lastExchange(chat("user", "hi", "assistant", "Hello!", "user", "What is the capital of Ghana?", "assistant", "Kumasi.")));
        assertNull("the person spoke since the reply", AutoCorrect.lastExchange(chat("user", "What is X?", "assistant", "Y.", "user", "ok")));
        assertNull("no question before it", AutoCorrect.lastExchange(chat("assistant", "Hello!")));
    }
}
