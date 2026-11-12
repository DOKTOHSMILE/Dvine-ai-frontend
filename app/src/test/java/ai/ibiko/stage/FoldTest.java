package ai.ibiko.stage;

import static org.junit.Assert.assertArrayEquals;

import ai.ibiko.chat.ChatBook;
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
    public void theTurnStillWorkingFoldsItsStepsAndANoteWithNoAnswerStays() {
        List<ChatBook.Message> live = Arrays.asList(m("user", "text"), m("assistant", "activity"), m("assistant", "text"));
        assertArrayEquals("the steps fold; the note after them stays", new int[] { -1, 1, -1 }, ChatPane.folds(live, 0, true));
        List<ChatBook.Message> between = Arrays.asList(m("user", "text"), m("assistant", "activity"), m("assistant", "text"),
                m("assistant", "activity"));
        assertArrayEquals("notes between steps fold into the one working row", new int[] { -1, 3, -1, -1 }, ChatPane.folds(between, 0, true));
        List<ChatBook.Message> stopped = Arrays.asList(m("user", "text"), m("assistant", "text"), m("assistant", "activity"));
        assertArrayEquals("a stopped turn's steps are one row too; the note before them stays", new int[] { -1, -1, 2 },
                ChatPane.folds(stopped, 0, false));
        // the pilot's screenshot: "Thinking ›" and "Checking the blueprint ›" hung apart after Stop; now one row
        List<ChatBook.Message> hung = Arrays.asList(m("user", "text"), m("assistant", "activity"), m("assistant", "activity"));
        assertArrayEquals(new int[] { -1, 2, -1 }, ChatPane.folds(hung, 0, false));
        List<ChatBook.Message> picture = Arrays.asList(m("user", "text"), m("assistant", "activity"), m("assistant", "image"),
                m("assistant", "activity"), m("assistant", "text"));
        assertArrayEquals("a picture in between is never hidden", new int[] { -1, -1, -1, -1, -1 }, ChatPane.folds(picture, 0, false));
    }
}
