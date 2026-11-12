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

/** Long agent work: the running mark, carrying on after a restart, the partial-reply backup, workload rounds and the saved work per persona. Moved out of MainActivity. */
final class AgentWork {
    private final MainActivity a;

    AgentWork(MainActivity a) {
        this.a = a;
    }

    /**
     * The work under way, kept in the settings while it runs: its persona, chat and task (null clears it). A start
     * that finds it means the app was closed or crashed mid-work ({@link #offerToCarryOn}).
     */
    void markRunning(String persona, String task) {
        if (a.prefs == null) {
            return;
        }
        if (persona == null) {
            a.prefs.edit().remove("running_persona").remove("running_task").remove("running_chat").apply();
            return;
        }
        a.prefs.edit().putString("running_persona", persona).putString("running_task", task)
                .putString("running_chat", a.chats == null ? "" : a.chats.current().id).apply();
    }

    /**
     * At start: work that was under way when the app closed or crashed. Its steps were saved one by one, so it can
     * carry on from the last one instead of starting again; a reply cut off mid-way is put back in its chat.
     */
    void offerToCarryOn() {
        if (a.prefs == null || a.chats == null) {
            return;
        }
        String partial = a.prefs.getString("partial_reply", null), partialChat = a.prefs.getString("partial_chat", null);
        if (partial != null && !partial.trim().isEmpty()) {
            a.prefs.edit().remove("partial_reply").remove("partial_chat").apply();
            String back = a.chats.current().id;
            if (partialChat != null && a.chats.use(partialChat)) {
                a.chats.add("assistant", "text", partial.trim() + "\n\n(Cut off when Ibiko closed. Say \u201cretry\u201d to ask again.)",
                        a.chats.current().persona);
                a.chats.use(back);
            }
        }
        final String persona = a.prefs.getString("running_persona", null), task = a.prefs.getString("running_task", null),
                chat = a.prefs.getString("running_chat", null);
        if (persona == null || task == null) {
            return;
        }
        markRunning(null, null);
        String who = "Ibiko";
        String shown = task.length() > 80 ? task.substring(0, 80) + "\u2026" : task;
        String record = ai.ibiko.scribe.Scribe.pending(a.chatSpace());
        new androidx.appcompat.app.AlertDialog.Builder(a)
                .setTitle("Carry on where it stopped?")
                .setMessage(who + " was working on \u201c" + shown + "\u201d when Ibiko closed. Its work so far is saved,"
                        + " step by step." + (record.isEmpty() ? "" : "\n\nScribe: " + record))
                .setPositiveButton("Carry on", (d, w) -> {
                    if (chat != null && a.chats.use(chat)) {
                        a.chatPane.show();
                    }
                    a.selectPersona(persona);
                    a.noteUser("continue");
                    a.runAgent("continue", persona, !"ibiko".equals(persona));
                })
                .setNegativeButton("Not now", null)
                .show();
    }

    void backupPartial(String soFar) {
        long now = System.currentTimeMillis();
        if (a.prefs == null || a.chats == null || soFar == null || now - a.partialSavedAt < 2000) {
            return;
        }
        a.partialSavedAt = now;
        a.prefs.edit().putString("partial_reply", soFar.length() > 60_000 ? soFar.substring(soFar.length() - 60_000) : soFar)
                .putString("partial_chat", a.chats.current().id).apply();
    }

    void clearPartial() {
        if (a.prefs != null) {
            a.prefs.edit().remove("partial_reply").remove("partial_chat").apply();
        }
    }

    /**
     * The master specification's self-audit: the gauge shows how full the model's working memory is; when it is
     * nearly full (and no long work is running), the conversation is summed up, the memory cleared down to that
     * summary, and the user told in one line, so replies stay quick and nothing fails for want of room.
     */
    void checkWorkload() {
        final ai.ibiko.stage.WorkloadGauge g = a.findViewById(R.id.workload);
        if (a.model == null) {
            return;
        }
        // Ibiko 1 has no context window to fill; the models' is the last call's
        final float load = a.ibikoOwn() ? 0f : a.model.workload();
        if (g != null) {
            g.set(load);
        }
        a.prefs.edit().putString("usage_today", a.model.usage.save()).apply(); // today's numbers survive a closed app
        if (a.ibikoOwn() || load < ai.ibiko.stage.WorkloadGauge.HOT || a.compacting || a.model.coding || !a.prefs.getBoolean("auto_clear", true)) {
            return;
        }
        a.compacting = true;
        final String persona = a.stage.agent();
        a.hub.worker.execute(() -> { // on the model's worker: its lock is never waited on by the screen
            final String said = a.model.historyText();
            if (said.split("\n").length < 4) {
                a.handler.post(() -> a.compacting = false); // too little to sum up
                return;
            }
            final String keeper = ai.ibiko.agent.AgentBoard.start("Memory keeper", "summing up the conversation to clear memory", a.brainLabel());
            String summary;
            try {
                summary = a.model.task(a.SUM_UP, said.length() > 20000 ? said.substring(said.length() - 20000) : said, 400, null);
            } finally {
                ai.ibiko.agent.AgentBoard.end(keeper);
            }
            summary = summary == null ? "" : summary.replaceAll("(?s)<think>.*?(</think>|$)", "").trim();
            a.model.compact(summary);
            final boolean kept = !summary.isEmpty();
            a.handler.post(() -> {
                a.compacting = false;
                if (g != null) {
                    g.set(0.1f); // the memory is nearly empty again; the next reply measures it
                }
                if (a.chatPane != null) {
                    a.chatPane.assistant(persona, "My working memory was " + Math.round(load * 100) + "% full, so I "
                            + (kept ? "summed up our conversation and cleared the rest" : "cleared it")
                            + " to stay quick. The chat itself is all still here.");
                }
            });
        });
    }

    /**
     * A round of long work ended: its progress note goes into the chat now, as a message of its own, so the user
     * sees what is happening without waiting for the end; the work goes on underneath.
     */
    void progressNote(int id, String persona, int round, String note) {
        if (!a.alive(id) || a.chatPane == null || note == null || note.trim().isEmpty()) {
            return;
        }
        String shown = ai.ibiko.chat.ReplyText.prepare(note);
        if (shown.isEmpty() || shown.startsWith("(nothing:")) {
            return;
        }
        if (ai.ibiko.agent.CodeAgent.unfinished(shown) || ai.ibiko.agent.CodeAgent.promise(shown)) {
            a.chatPane.activityStep(shown);
            return;
        }
        a.chatPane.assistant(persona, shown);
    }

    /** "continue", "go on", "give me the result", "what did you find": the last task goes on, not a new one. */
    static boolean carriesOn(String said) {
        String t = said == null ? "" : said.trim().toLowerCase(java.util.Locale.US).replaceAll("[.!?]+$", "");
        return t.matches("^(continue|go on|keep going|carry on|proceed|resume|finish( it| the (audit|report|task|job))?|"
                + "(now )?(give|show|send|share) me the (full |complete |final )?(result|results|report|findings|audit)( of the (audit|review|task))?|"
                + "what did you find|what are the findings|where is the (report|result)|and\\?|so\\?)$");
    }

    /**
     * The agent's work so far for this persona in this chat (its tool calls and results), kept on the phone to carry
     * on from. Kept per chat: "continue" in a new chat never picks up another chat's job.
     */
    String agentWork(String persona) {
        java.io.File f = agentFile(workKey(persona));
        try {
            return f.isFile() ? new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8) : "";
        } catch (java.io.IOException e) {
            return "";
        }
    }

    /** "ibiko-c12": the persona's work in the chat open now. */
    String workKey(String persona) {
        return persona + "-" + (a.chats == null ? "" : a.chats.current().id);
    }

    java.io.File agentFile(String key) {
        return new java.io.File(a.getFilesDir(), "agent-" + key.replaceAll("[^A-Za-z0-9_-]", "") + ".txt");
    }

    /** Saves the work under {@code key} ({@link #workKey}, taken when the work started, so a chat switch can't move it). */
    void saveAgentWork(String key, String work) {
        if (work == null || work.isEmpty()) {
            return;
        }
        String w = work.length() > 400_000 ? work.substring(work.length() - 400_000) : work;
        try {
            java.nio.file.Files.write(agentFile(key).toPath(),
                    w.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException ignored) {
            // carrying on is a bonus
        }
    }
}
