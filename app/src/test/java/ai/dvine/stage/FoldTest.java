package ai.dvine.stage;

import static org.junit.Assert.assertArrayEquals;

import ai.dvine.chat.ChatBook;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class FoldTest {
    private static ChatBook.Message m(String role, String kind) {
        return new ChatBook.Message(role, kind, "x", "ibiko");
    }

    @Test
    public void aFinishedTurnsProcessIsOneRowAboveItsAnswer() {
        List<ChatBook.Message> chat = Arrays.asList(m("user", "text"), m("assistant", "activity"), m("assistant", "text"),
                m("assistant", "activity"), m("assistant", "text"), m("user", "text"), m("assistant", "text"));
        // steps, a note, more steps fold (1 to 3); the answer (4) and a plain reply (6) show
        assertArrayEquals(new int[] { -1, 3, -1, -1, -1, -1, -1 }, ChatPane.folds(chat, 0, false));
    }

    @Test
    public void theTurnStillWorkingAndOneWithNoAnswerAreLeftOpen() {
        List<ChatBook.Message> live = Arrays.asList(m("user", "text"), m("assistant", "activity"), m("assistant", "text"));
        assertArrayEquals(new int[] { -1, -1, -1 }, ChatPane.folds(live, 0, true));
        List<ChatBook.Message> stopped = Arrays.asList(m("user", "text"), m("assistant", "text"), m("assistant", "activity"));
        assertArrayEquals("nothing after the steps: the notes stay readable", new int[] { -1, -1, -1 }, ChatPane.folds(stopped, 0, false));
        List<ChatBook.Message> picture = Arrays.asList(m("user", "text"), m("assistant", "activity"), m("assistant", "image"),
                m("assistant", "activity"), m("assistant", "text"));
        assertArrayEquals("a picture in between is never hidden", new int[] { -1, -1, -1, -1, -1 }, ChatPane.folds(picture, 0, false));
    }
}
