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

/** Actions that leave the app (a post, a share, a copy): staged with their exact text, done only on a yes. Moved out of MainActivity. */
final class StagedActions {
    private final MainActivity a;

    StagedActions(MainActivity a) {
        this.a = a;
    }

    /**
     * Staged actions: something that leaves the app (a post, a share, a copy) is shown with its exact text and where
     * it goes, and happens only on a yes ("send it", "yes", Send it), or at once when the user turned auto-approve on.
     * A post is handed to the app, where the user presses send: Ibiko says so, never that it was posted.
     */
    boolean stagedAction(String text) {
        long now = System.currentTimeMillis();
        String lower = text.trim().toLowerCase(java.util.Locale.US);
        ai.ibiko.brain.ActionPlanner.Reply r = a.stager.reply(lower, now);
        if (r == ai.ibiko.brain.ActionPlanner.Reply.CONFIRMED) {
            doAction(a.stager.take(), false);
            return true;
        }
        if (r == ai.ibiko.brain.ActionPlanner.Reply.CANCELLED) {
            closeStaged();
            a.play(LocalTurn.speakAs(a.stage.agent(), "Cancelled. Nothing was sent."), null);
            return true;
        }
        // a new post or share comes only from the brain's own code (§AC), never from the wording; this route only
        // answers the yes or no to one already shown
        return false;
    }

    /** The brain asked to share, post or copy (§AC): the action is shown first, and waits for the pilot's yes. */
    void stageAction(String what) {
        PendingAction p = a.stager.plan(what == null ? "" : what.trim(), a.lastReply(), System.currentTimeMillis());
        if (p == null) {
            a.play(LocalTurn.speakAs(a.stage.agent(), "I couldn't tell what to share or where."), null);
            return;
        }
        if (a.prefs.getBoolean("actions_auto", false) || a.autoPost() && p.kind != PendingAction.Kind.COPY) {
            a.stager.take();
            doAction(p, true); // the user chose auto-approve
            return;
        }
        showStaged(p);
        a.play(LocalTurn.speakAs(a.stage.agent(), ai.ibiko.brain.ActionPlanner.question(p)), null);
    }

    void showStaged(final PendingAction p) {
        closeStaged();
        boolean direct = p.kind != PendingAction.Kind.COPY
                && ai.ibiko.social.SocialPoster.ready(a, ai.ibiko.social.SocialPoster.direct(p.app));
        String where = p.kind == PendingAction.Kind.COPY ? "To: your clipboard"
                : direct ? "To: " + p.appName() + ", from your signed-in account (Ibiko presses Post)"
                + (p.tags.isEmpty() ? "" : "\nTagging: " + String.join(", ", p.tags))
                + (p.kind == PendingAction.Kind.COMMENT ? "\nUnder: " + p.target : "")
                : "To: " + p.appName() + " (it opens with this text; you press send there)";
        a.stagedCard = new androidx.appcompat.app.AlertDialog.Builder(a)
                .setTitle(p.kind == PendingAction.Kind.COPY ? "Copy this?" : "Post on " + p.appName() + "?")
                .setMessage(p.content + "\n\n" + where)
                .setPositiveButton(p.kind == PendingAction.Kind.COPY ? "Copy" : "Send it", (d, w) -> {
                    PendingAction now = a.stager.take();
                    if (now != null) {
                        doAction(now, false);
                    }
                })
                .setNegativeButton("Cancel", (d, w) -> a.stager.take()) // dropped quietly: Cancel is not a message
                .show();
    }

    void closeStaged() {
        if (a.stagedCard != null && a.stagedCard.isShowing()) {
            a.stagedCard.dismiss();
        }
        a.stagedCard = null;
    }

    /** Carries out an approved (or auto-approved) action and logs it; says what really happened. */
    void doAction(PendingAction p, boolean auto) {
        closeStaged();
        if (p == null) {
            return;
        }
        if (p.kind == PendingAction.Kind.COMMENT) {
            a.comment(p, auto);
            return;
        }
        final ai.ibiko.social.Platform net = p.kind == PendingAction.Kind.SHARE ? ai.ibiko.social.SocialPoster.direct(p.app) : null;
        if (net != null && ai.ibiko.social.SocialPoster.ready(a, net)) {
            // the pilot said yes: Ibiko posts it from their signed-in account, and says only what the network showed
            final String agent = a.stage.agent();
            a.note("Posting on " + net.shown + "\u2026");
            new Thread(() -> {
                ai.ibiko.social.SocialPoster.Result r = ai.ibiko.social.SocialPoster.post(a, net, p.content, p.tags, null);
                if (r.posted) {
                    a.ibiko1().readReply(p.content); // Ibiko 1 learns the language of what the pilot approved
                }
                a.handler.post(() -> {
                    if (r.posted) {
                        a.play(LocalTurn.speakAs(agent, r.said), null);
                        return;
                    }
                    boolean handed = ai.ibiko.act.Actions.share(a, p); // couldn't do it here: the app, for the pilot to press Post
                    ai.ibiko.act.ActionLog.add(a.getFilesDir(), "share", p.appName(), auto ? "auto-approved" : "approved",
                            handed ? "handed off" : "failed", p.content);
                    a.play(LocalTurn.speakAs(agent, r.said + (handed ? " I've opened " + p.appName() + " with it ready; press Post there."
                            : " Nothing was sent.")), null);
                });
            }, "ibiko-post").start();
            return;
        }
        boolean ok = p.kind == PendingAction.Kind.COPY ? ai.ibiko.act.Actions.copy(a, p.content) : ai.ibiko.act.Actions.share(a, p);
        ai.ibiko.act.ActionLog.add(a.getFilesDir(), p.kind.name().toLowerCase(java.util.Locale.US), p.appName(), auto ? "auto-approved" : "approved",
                ok ? (p.kind == PendingAction.Kind.COPY ? "done" : "handed off") : "failed", p.content);
        String said = !ok ? "I couldn't open " + p.appName() + ". Nothing was sent."
                : p.kind == PendingAction.Kind.COPY ? "Copied to your clipboard."
                : "Handed to " + p.appName() + " with your post. Press send there; I can't see whether it went out.";
        a.play(LocalTurn.speakAs(a.stage.agent(), said), null);
    }
}
