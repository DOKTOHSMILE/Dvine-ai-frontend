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

/** Writing work: documents, post versions for social media, and long pieces written to a plan. Moved out of MainActivity. */
final class WritingTurns {
    private final MainActivity a;

    WritingTurns(MainActivity a) {
        this.a = a;
    }

    /**
     * Makes a real file (PDF, Word, Excel, HTML, CSV, Markdown, JSON, text) from the last reply, the whole chat, or a
     * document the model writes on the spot; saved in Downloads/Ibiko and shown under the reply. Works offline.
     */
    boolean makeDocument(final String text) {
        if (!a.asksForFile(text)) {
            return false;
        }
        final ai.ibiko.act.FileMaker.Format fmt = ai.ibiko.act.FileMaker.of(text);
        final String lower = text.toLowerCase(java.util.Locale.US);
        final boolean ofChat = lower.matches("(?s).*\\b(our|this|the whole) (chat|conversation)\\b.*");
        final boolean ofThat = lower.matches("(?s).*\\b(of|from|as|into|turn|convert|save|export|put) (that|this|it|the above|your (answer|reply))\\b.*")
                || lower.matches("(?s).*\\b(that|this|it) (as|into|to) (a |an )?(pdf|word|docx|excel|spreadsheet|html|csv|markdown|text|json)\\b.*");
        final String persona = a.stage.agent();
        final int id = a.nextAsk();
        final long since = System.currentTimeMillis();
        a.stopPlay();
        a.armThink("Writing the document", "Making the " + fmt.extension.toUpperCase(java.util.Locale.US));
        final String source = ofChat && a.chats != null ? a.chats.transcript(60000) : ofThat ? a.lastReply() : null;
        a.hub.worker.execute(() -> {
            String content = source;
            if (content == null || content.trim().isEmpty()) {
                if (a.model == null || !a.model.ready() && !a.model.remoteOn()) {
                    a.handler.post(() -> {
                        a.endThink();
                        a.play(LocalTurn.speakAs(persona, "I need a model to write that document. Ask me after it has loaded."), null);
                    });
                    return;
                }
                content = a.model.task(a.WRITE_DOC, text, ai.ibiko.chat.Tuning.BIG_TOKENS.get(a.prefs), piece -> a.alive(id));
                content = content == null ? "" : content.replaceAll("(?s)<think>.*?(</think>|$)", "").trim();
            }
            final String body = content;
            String title = "Ibiko document";
            java.util.regex.Matcher h = java.util.regex.Pattern.compile("(?m)^#\\s+(.+)$").matcher(body);
            if (h.find()) {
                title = h.group(1).trim();
            } else if (ofChat) {
                title = a.chats.current().title;
            }
            String name = title.replaceAll("[^A-Za-z0-9 _-]", "").trim().replaceAll("\\s+", "-");
            name = (name.isEmpty() ? "document" : name.length() > 40 ? name.substring(0, 40) : name) + "." + fmt.extension;
            String said;
            try {
                if (body.isEmpty()) {
                    throw new java.io.IOException("nothing was written");
                }
                byte[] bytes = ai.ibiko.act.FileMaker.any(fmt, title, body);
                android.net.Uri uri = ai.ibiko.chat.ui.Downloads.save(a, name, fmt.mime, bytes);
                // checked before it is said: the file is read back and must hold what was written
                String wrong = ai.ibiko.gate.Verify.saved(() -> a.getContentResolver().openInputStream(uri), bytes.length);
                if (wrong != null) {
                    throw new java.io.IOException(wrong);
                }
                new ai.ibiko.chat.WorkLog(a).artifact(name, uri.toString(), fmt.name() + " \u00B7 "
                        + (bytes.length < 1024 ? bytes.length + " B" : bytes.length / 1024 + " KB"), fmt.mime);
                said = "Made " + name + " and saved it in Downloads/Ibiko. Tap it below to open or share it.";
            } catch (java.io.IOException | RuntimeException e) {
                said = "I couldn't make the file: " + e.getMessage() + ".";
            }
            final String reply = said;
            a.handler.post(() -> {
                if (!a.alive(id)) {
                    return;
                }
                a.endThink();
                a.attachWork(null, since); // the file, as a pill under this reply
                a.play(LocalTurn.speakAs(persona, reply), null);
            });
        });
        return true;
    }

    /**
     * "Write a Facebook post about success and tag John": the model writes several versions (professional, personal,
     * short), they are shown in the chat and in a picker, and the one the pilot picks waits for their yes like any
     * post. People are tagged only by name; random people are not.
     */
    void postVersions(final ai.ibiko.social.Platform platform, String words) {
        final String agent = a.stage.agent();
        if (ai.ibiko.social.PostDrafts.randomTags(words)) {
            a.play(LocalTurn.speakAs(agent, "I won't tag random people: it reads as spam to them and gets accounts limited."
                    + " Tell me who to tag by name and I'll add them. Writing the post now."), null);
        }
        final java.util.List<String> tags = ai.ibiko.social.PostDrafts.tags(words);
        final String about = ai.ibiko.social.PostDrafts.withoutTags(words);
        a.armThink("Writing " + ai.ibiko.social.PostDrafts.VERSIONS + " versions for " + platform.shown, "Writing");
        new Thread(() -> {
            String answer = a.model == null ? null : a.model.task("You write social media posts for the user, in their own voice.",
                    ai.ibiko.social.PostDrafts.prompt(platform.shown, about), Math.max(1200, ai.ibiko.chat.Tuning.CHAT_TOKENS.get(a.prefs) * 3),
                    piece -> true, ai.ibiko.llm.TaskSize.Tier.STRONG);
            final java.util.List<String> versions = ai.ibiko.social.PostDrafts.parse(answer);
            a.handler.post(() -> {
                a.endThink();
                if (versions.isEmpty()) {
                    String why = a.model == null ? null : a.model.problem();
                    a.play(LocalTurn.speakAs(agent, "I couldn't write the post" + (why == null || why.isEmpty() ? "." : ": " + why + ".")), null);
                    return;
                }
                StringBuilder shown = new StringBuilder("Here " + (versions.size() == 1 ? "is the post" : "are " + versions.size()
                        + " versions") + " for " + platform.shown + (tags.isEmpty() ? "" : ", tagging " + String.join(", ", tags))
                        + ". Pick one below:");
                for (int i = 0; i < versions.size(); i++) {
                    shown.append("\n\n**Version ").append(i + 1).append("**\n").append(versions.get(i));
                }
                if (a.autoPost()) {
                    shown.append("\n\nAuto-post is on, so version 1 is going out now.");
                }
                if (a.chatPane != null) {
                    a.chatPane.assistant(agent, shown.toString());
                }
                if (a.autoPost()) {
                    String first = versions.get(0);
                    String app = platform == ai.ibiko.social.Platform.X ? "x" : "facebook";
                    if (ai.ibiko.social.PostDrafts.tooLong(app, first) == null) {
                        a.offerOrPost(new PendingAction(PendingAction.Kind.SHARE, first, app, System.currentTimeMillis(), tags, null));
                        return;
                    }
                }
                a.pickVersion(platform, versions, tags);
            });
        }, "ibiko-post-versions").start();
    }

    /**
     * The Blueprint for writing ({@link ai.ibiko.agent.WritingPlan}): the outline first, checked for loopholes, then
     * each section written with the plan and what is already written, then the piece in one. Each phase shows in the
     * steps; the plan is shown too, so the user sees what is coming.
     */
    void runWritingPlan(final String persona, final String task) {
        final int id = a.nextAsk();
        a.stopPlay();
        a.armThink("Understanding the request", "Writing");
        final ai.ibiko.llm.TaskSize.Tier strong = ai.ibiko.llm.TaskSize.Tier.STRONG;
        final int room = Math.max(1500, ai.ibiko.chat.Tuning.CHAT_TOKENS.get(a.prefs) * 4);
        final String system = "You are " + a.labelOf(persona) + ", writing for the user. You plan a piece before you write it.";
        new Thread(() -> {
            a.activity("Drawing the plan: the outline");
            String outline = a.model.task(system, ai.ibiko.agent.WritingPlan.outlinePrompt(task), room, piece -> a.alive(id), strong);
            if (outline == null || !a.alive(id)) {
                a.handler.post(() -> {
                    a.endThink();
                    String why = a.model.problem();
                    a.play(LocalTurn.speakAs(persona, "I couldn't plan it" + (why == null || why.isEmpty() ? "." : ": " + why + ".")), null);
                });
                return;
            }
            a.activity("Checking the plan for gaps");
            String checked = a.model.task(system, ai.ibiko.agent.WritingPlan.checkPrompt(task, outline), room, piece -> a.alive(id), strong);
            if (checked != null && ai.ibiko.agent.WritingPlan.sections(checked).size() >= 2) {
                outline = checked;
            }
            final String plan = outline;
            final java.util.List<String> sections = ai.ibiko.agent.WritingPlan.sections(plan);
            a.handler.post(() -> {
                if (a.alive(id) && a.chatPane != null) {
                    a.chatPane.assistant(persona, "The plan, checked:\n\n" + plan.trim());
                }
            });
            java.util.List<String> parts = new java.util.ArrayList<String>();
            StringBuilder written = new StringBuilder();
            // the same hands as the coding agents: on an API, with the Workers tool on, each section has its own writer
            // and they write at the same time; the plan keeps them in one voice and one order
            if (a.model.remoteOn() && a.chatTools(persona).workers() && sections.size() >= 3) {
                a.activity("Writing " + sections.size() + " sections at once");
                final java.util.List<String> secs = sections;
                java.util.List<String> got = ai.ibiko.agent.Squad.all(secs, (n, s) -> {
                    String one = a.alive(id) ? a.model.remoteTask(system, ai.ibiko.agent.WritingPlan.sectionAlone(task, plan, n, secs.size(), s),
                            room, strong) : null;
                    if (one != null) {
                        a.activity("Wrote " + n + " of " + secs.size() + ": " + a.clip(s.replaceAll(":.*$", "")));
                    }
                    return one;
                }, ai.ibiko.chat.Tuning.workersAtOnce(a.prefs, a.model.remoteOn()), () -> !a.alive(id));
                for (String one : got) {
                    if (one != null && !one.trim().isEmpty() && !one.startsWith("Error:") && !one.startsWith("Stopped before")) {
                        parts.add(one.trim());
                    }
                }
                if (parts.size() != sections.size()) {
                    parts.clear(); // a section was lost: written one after another below, as before
                }
            }
            for (int i = parts.size(); i < sections.size() && a.alive(id); i++) {
                a.activity("Writing " + (i + 1) + " of " + sections.size() + ": " + a.clip(sections.get(i).replaceAll(":.*$", "")));
                String part = a.model.task(system, ai.ibiko.agent.WritingPlan.sectionPrompt(task, plan, written.toString(), i + 1, sections.get(i)),
                        room, piece -> a.alive(id), strong);
                if (part == null) {
                    break;
                }
                parts.add(part);
                written.append(part.trim()).append("\n\n");
            }
            final String piece = ai.ibiko.agent.WritingPlan.join(ai.ibiko.agent.WritingPlan.title(plan), parts);
            final boolean whole = parts.size() == sections.size();
            a.handler.post(() -> {
                if (!a.alive(id)) {
                    return;
                }
                a.endThink();
                if (piece.isEmpty()) {
                    a.play(LocalTurn.speakAs(persona, "I planned it but couldn't write it: " + a.model.problem() + "."), null);
                    return;
                }
                if (a.chatPane != null) {
                    a.chatPane.assistant(persona, piece + (whole ? "" : "\n\n(Stopped after " + parts.size() + " of " + sections.size()
                            + " sections. Say \u201ccontinue\u201d to write the rest.)"));
                }
                a.note("Say \u201cmake it a PDF\u201d or \u201ca Word document\u201d to save it as a file.");
            });
        }, "ibiko-writing-plan").start();
    }
}
