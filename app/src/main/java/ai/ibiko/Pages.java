package ai.ibiko;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import ai.ibiko.act.Actions;
import ai.ibiko.brain.BrainAnswer;
import ai.ibiko.brain.LearningJournal;
import ai.ibiko.brain.OfflineBrain;
import ai.ibiko.brain.Onboarding;
import ai.ibiko.brain.PendingAction;
import ai.ibiko.stage.StageMode;
import ai.ibiko.stage.StageListener;
import ai.ibiko.stage.StageView;
import ai.ibiko.net.Connectivity;
import ai.ibiko.net.KnowledgeStore;
import ai.ibiko.net.SyncWorker;
import ai.ibiko.turn.LocalTurn;
import ai.ibiko.turn.TurnLine;
import ai.ibiko.turn.TurnRoute;
import ai.ibiko.voice.AmbientPad;
import ai.ibiko.voice.ListenBridge;
import ai.ibiko.voice.ListenCallback;
import ai.ibiko.voice.TalkBridge;

/** Chat pages: switching persona or chat, the persona bar, the starter, and whether a chat is busy. Moved out of MainActivity. */
final class Pages {
    private final MainActivity a;

    Pages(MainActivity a) {
        this.a = a;
    }

    void selectPersona(String persona) {
        a.unreadPages.remove(persona);
        leavePage(persona);
        a.personaTabs.select(persona);
        enterPage(persona);
    }

    void selectPersona(String persona, boolean fromTap) {
        a.unreadPages.remove(persona);
        leavePage(persona);
        a.personaTabs.select(persona, fromTap);
        enterPage(persona);
    }

    /** Leaving a page: its job keeps running, but its live text and progress leave the screen with it. */
    void leavePage(String next) {
        if (a.stage == null || next == null || next.equals(a.stage.agent())) {
            return;
        }
        Integer job = a.latestOf.get(a.stage.agent());
        if (job != null && a.writing.contains(job)) {
            if (a.chatPane != null) {
                a.chatPane.setLive(null);
            }
            a.endThink();
        }
    }

    /** Opening a page whose job is still writing: it says so (its reply lands here when it is done). */
    void enterPage(String page) {
        Integer job = a.latestOf.get(page);
        if (job != null && a.writing.contains(job)) {
            a.armThink("Still working\u2026", "Still working\u2026");
        }
    }

    /**
     * The Obi · Ivie · Ibiko buttons are for the three-page chat. In the group chat they are hidden: who answers comes
     * from the message ({@link ai.ibiko.agent.Handoff#groupRoute}), as in a group of people.
     */
    void showPersonaBar() {
        android.view.View bar = a.findViewById(R.id.personaTabs);
        if (bar != null) {
            bar.setVisibility(a.groupChat() ? android.view.View.GONE : android.view.View.VISIBLE);
        }
    }

    void wireStarter(int id, String text) {
        android.view.View chip = a.findViewById(id);
        if (chip != null) {
            chip.setOnClickListener(v -> a.answer(text));
        }
    }

    /**
     * What one run belongs to: the page (three pages), or the conversation and the persona addressed (the group: Ibiko
     * coding doesn't make a question to Obi wait as if it were for Ibiko).
     */
    String pageKey() {
        String id = a.chats == null || a.chats.current() == null ? "" : a.chats.current().id;
        return a.groupChat() && a.stage != null ? id + "#" + a.stage.agent() : id;
    }

    boolean thisChatBusy() {
        if (!a.modelGoing()) {
            return false;
        }
        String here = pageKey();
        if (a.runChat != null) {
            return a.runChat.equals(here);
        }
        // work that did not start from a message here (an agent's job): busy only on the page it writes to
        return a.chats == null || a.runAgent == null || a.chats.onScreen(a.runAgent);
    }

    boolean runningElsewhere() {
        if (!a.modelGoing() || a.runChat == null || a.chats == null || a.chats.current() == null) {
            return false;
        }
        return !a.runChat.equals(pageKey());
    }
}
