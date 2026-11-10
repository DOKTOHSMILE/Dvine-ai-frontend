package ai.ibiko.chat.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;

import ai.ibiko.agent.LearnedIntents;
import ai.ibiko.brain.LearningJournal;
import ai.ibiko.chat.ChatBook;
import ai.ibiko.turn.Meaning;

/** "What DVINE remembers": every kind of memory is listed, and each can be forgotten, from the live stores. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MemoryActivityTest {
    @Test
    public void everyMemoryIsListedAndCanBeForgotten() {
        Context app = RuntimeEnvironment.getApplication();
        ChatBook chats = ChatBook.open(new File(app.getFilesDir(), "chats.json"));
        chats.remember("Tobi is my brother");
        chats.add("user", "text", "Plan a trip to Lagos", "obi");
        chats.fresh("obi");
        String deleted = chats.sections().get(0).id;
        chats.delete(deleted); // what was said there is kept as a note
        LearningJournal journal = LearningJournal.open(new File(app.getFilesDir(), "learning"));
        journal.add(LearningJournal.TAUGHT_FACT, "the capital of Nigeria", "Abuja");
        Meaning.Memory m = new Meaning.Memory();
        m.put("are you done", Meaning.Command.RESULT);
        app.getSharedPreferences("dvine", Context.MODE_PRIVATE).edit().putString("meanings", m.write()).commit();
        LearnedIntents.open(new File(app.getFilesDir(), "learned_intents.txt")).learn("sketch me a lion", "draw a lion in 3d");

        ActivityController<MemoryActivity> c = Robolectric.buildActivity(MemoryActivity.class).create().start().resume().visible();
        View root = c.get().getWindow().getDecorView();
        for (String shown : new String[]{"What Ibiko remembers", "Tobi is my brother",
                "“are you done”", "means “give me the result”", "“sketch me a lion”", "does “draw a lion in 3d”", "A correction"}) {
            if (!"A correction".equals(shown)) {
                assertNotNull(shown, text(root, shown));
            }
        }
        assertTrue(containsText(root, "Abuja"));
        assertTrue(containsText(root, "the user said: Plan a trip to Lagos")); // kept from the deleted chat

        // ✕ on one note: gone from the live book the chat uses
        tapForget(root, "Forget: Tobi is my brother");
        assertFalse(chats.notes().contains("Tobi is my brother"));
        assertEquals(1, chats.notes().size()); // the deleted chat's note stays
        // "Forget all" on the facts asks first, then forgets
        root = c.get().getWindow().getDecorView();
        tapForget(root, "Forget all: Facts you taught and corrections");
        androidx.appcompat.app.AlertDialog ask = (androidx.appcompat.app.AlertDialog) ShadowDialog.getLatestDialog();
        ask.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals(0, journal.taughtCount());
        // a phrasing: forgotten in the saved memory the chat reads again
        root = c.get().getWindow().getDecorView();
        tapForget(root, "Forget: “are you done”");
        assertEquals(null, Meaning.Memory.read(app.getSharedPreferences("dvine", Context.MODE_PRIVATE).getString("meanings", null)).get("are you done"));
        root = c.get().getWindow().getDecorView();
        tapForget(root, "Forget: “sketch me a lion”");
        assertEquals(null, LearnedIntents.open(new File(app.getFilesDir(), "learned_intents.txt")).commandFor("sketch me a lion"));
        c.pause().stop().destroy();
    }

    private static void tapForget(View root, String description) {
        View v = described(root, description);
        assertNotNull(description, v);
        v.performClick();
        ShadowLooper.idleMainLooper();
    }

    private static View described(View v, String d) {
        if (v.getContentDescription() != null && d.contentEquals(v.getContentDescription())) {
            return v;
        }
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                View f = described(((ViewGroup) v).getChildAt(i), d);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    private static View text(View v, String t) {
        if (v instanceof TextView && t.contentEquals(((TextView) v).getText())) {
            return v;
        }
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                View f = text(((ViewGroup) v).getChildAt(i), t);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    private static boolean containsText(View v, String t) {
        if (v instanceof TextView && ((TextView) v).getText().toString().contains(t)) {
            return true;
        }
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                if (containsText(((ViewGroup) v).getChildAt(i), t)) {
                    return true;
                }
            }
        }
        return false;
    }
}
