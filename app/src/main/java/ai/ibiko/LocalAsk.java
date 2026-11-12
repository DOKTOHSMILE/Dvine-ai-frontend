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

/** A question to the model on the device: the prompt, steering folded in, the streamed answer, and the agents of a discussion. Moved out of MainActivity. */
final class LocalAsk {
    private final MainActivity act;

    LocalAsk(MainActivity act) {
        this.act = act;
    }

    /**
     * The current model call has returned. Anything the user sent during it is read now, and the model continues
     * the same answer. The call that just finished is not cancelled, and neither is this one, unless the user stops.
     */
    String foldSteer(int id, String system, String question, String context, int length, String soFar,
            ai.ibiko.llm.NativeLlm.Sink sink) {
        String raw = soFar;
        for (int extra = 0; extra < 4 && act.alive(id) && act.steer.pending(); extra++) {
            String more = act.steer.drain();
            if (more == null || more.isEmpty()) {
                break;
            }
            act.activity("Reading what you just sent");
            String continued = act.model == null ? null : act.model.reply(system, more,
                    (context == null ? "" : context + "\n")
                            + "You were already working on: " + act.tail(question, 400)
                            + "\nThe user sent this while you worked. Read it and continue the same"
                            + " answer. Do not start over and do not stop.\n\nYour answer so far:\n" + act.tail(raw, 4000),
                    length, sink);
            if (continued != null && !continued.trim().isEmpty()) {
                raw = (raw == null || raw.trim().isEmpty() ? "" : raw.trim() + "\n\n") + continued.trim();
            }
        }
        return raw;
    }

    boolean askLocal(final String question, final BrainAnswer offline) {
        return askLocal(question, offline, false);
    }

    boolean askLocal(final String question, final BrainAnswer offline,
            final boolean research) {
        return askLocal(question, offline, research, ai.ibiko.chat.Tuning.CHAT_TOKENS.get(act.prefs));
    }

    /** {@code length}: the most the reply may run to (tokens); a big task gets room for a full answer. */
    boolean askLocal(final String question, final BrainAnswer offline,
            final boolean research, final int length) {
        return askLocal(question, offline, research, length, false);
    }

    /**
     * {@code acts}: one call that answers and may act. The model reads the message once, with the persona and the
     * orders it may give; an order on its reply's first line is hidden and carried out. This replaces a routing
     * call followed by a second call for the answer (the message read twice, the instructions twice).
     */
    boolean askLocal(final String question, final BrainAnswer offline,
            final boolean research, final int length, final boolean acts) {
        final int id = act.nextAsk();
        act.writing.add(id);
        final boolean ivie = act.stage.isIvie();
        final String agent = act.stage.agent();
        final String worker = ai.ibiko.agent.AgentBoard.start("Ibiko \u00B7 reply", question, act.brainLabel());
        act.replyWorkers.put(id, worker);
        act.stopPlay();
        act.stage.setMode(StageMode.THINKING);
        act.stage.activity("MODEL", "thinking offline");
        final List<TurnLine> stream = new ArrayList<TurnLine>();
        act.setStreamOpen(true);
        act.walker.follow(stream);
        final String system = act.systemFor(agent, acts);
        final ai.ibiko.suit.ChatTools pageTools = act.chatTools(agent);
        final java.util.List<String> taught;
        synchronized (act.brain) {
            taught = act.brain.taughtFor(question, 6);
        }
        // the interpreter: Pidgin, Yoruba or French is read in English, and the English reply put back
        final ai.ibiko.llm.Interpreter.Language lang = act.prefs.getBoolean("interpreter", true)
                ? ai.ibiko.llm.Interpreter.language(question) : null;
        final boolean online = act.prefs.getBoolean("fresh_facts", true) && act.connectivity != null
                && act.connectivity.isOnline();
        act.armThink("Thinking\u2026", "Thinking\u2026");
        // and the way that worked before, and what this is likely to lead to (premeditation)
        // the thought process (learn/Mind): the brain's sections read, recall, weigh and judge what was gathered about
        // this; each says what it did, shown as the thinking, and the thought it reaches goes to the brain as Ibiko's view
        final ai.ibiko.learn.Mind.Thought thought = act.brainBox().mind().think(question);
        if (thought.steps.size() >= 3) {
            for (ai.ibiko.learn.Mind.Step st : thought.steps) {
                act.activity(st.line());
            }
        }
        final String around = act.situation(ivie) + act.ibiko1().methods().hint(question) + act.ibiko1().premeditate(question)
                + (thought.said == null ? "" : "\nIbiko's own view from the answers it has gathered ("
                        + ai.ibiko.ibiko1.formulas.Probability.words(thought.sure) + "): " + thought.said
                        + " Use it if it helps; correct it if it is wrong.");
        act.hub.worker.execute(() -> {
            String asked = question;
            if (lang != null && act.alive(id)) {
                act.activity("Reading your " + lang.shown + " in English");
                String en = ai.ibiko.llm.Interpreter.toEnglish((s, t, n) -> act.model.task(s, t, n, null), lang, question);
                if (en != null) {
                    asked = en;
                    act.activity("Understood as: \u201c" + act.clip(en) + "\u201d");
                }
            }
            final String english = asked;
            // when the brain may act it asks for a search itself (\u00A7SR); the wording decides only for a plain answer
            final boolean look = online && (research || !acts && ai.ibiko.llm.FreshFacts.worthLooking(english));
            if (look) {
                act.activity("Searching the web for \u201c" + act.clip(english) + "\u201d");
            }
            // the web: search results and the best pages (a small model gets a shorter extract)
            String found = look && act.alive(id) ? new ai.ibiko.connect.ConnectorStore(act).research(english,
                    act.model.remoteOn() ? 3 : 1, act.model.remoteOn() ? 6000 : 1500) : null;
            if (found == null && look && act.alive(id)) {
                found = ai.ibiko.ops.mind.CitedReference.collect(english, url -> {
                    ai.ibiko.net.WebGet.Page p = ai.ibiko.net.WebGet.page(url, 6000, 400_000);
                    return p.body;
                }, System.currentTimeMillis());
            }
            final String reference = found;
            if (look) {
                final String hosts = reference == null ? "" : ai.ibiko.net.WebSearch.hosts(reference);
                act.handler.post(() -> {
                    if (act.alive(id)) {
                        act.activity(reference == null ? "Nothing current found; answering from what I know"
                                : hosts.isEmpty() ? "Found results" : "Read " + hosts);
                    }
                });
            }
            act.armThink("Thinking\u2026", "Thinking\u2026");
            // the notes record: the exchanges older than the model's own memory (its last few turns), in short, so
            // the device's model with its short memory still knows what this chat said earlier
            // memory codes first: the question's tags pull only the earlier sentences about it, read back by their ids;
            // the short notes of every earlier exchange only when nothing earlier is about this
            String recalled = act.chats == null ? "" : act.chats.recall(english, ai.ibiko.llm.LocalModel.HISTORY_TURNS + 1,
                    act.model.remoteOn() ? 2400 : 900);
            String older = !recalled.isEmpty() ? "Earlier in this chat, about this (by memory code):\n" + recalled
                    : act.chats == null ? "" : act.chats.olderNotes(ai.ibiko.llm.LocalModel.HISTORY_TURNS + 1, act.model.remoteOn() ? 2400 : 1200);
            String gathered = ai.ibiko.llm.PersonaPrompt.context(taught, reference, around)
                    + (older.isEmpty() ? "" : "\n" + older)
                    + "\n" + act.cortex().context(english) // Ibiko's reading of the message and any exact arithmetic
                    + act.pilotProfile().brief() // what the pilot said about themselves, for every brain
                    + (pageTools.brief().isEmpty() ? "" : "\n" + pageTools.brief());
            if (!act.model.remoteOn() && act.model.quick(english, 160) && gathered.length() > act.QUICK_CONTEXT_CHARS) {
                // an everyday message goes the speed test's way: little to read before the first word
                gathered = gathered.substring(0, act.QUICK_CONTEXT_CHARS);
            } else if (!act.model.remoteOn() && gathered.length() > act.DEVICE_CONTEXT_CHARS) {
                // the device's model reads all of it before its first word: what is said about this question first
                // (taught facts, the reference, the situation), the rest cut
                gathered = gathered.substring(0, act.DEVICE_CONTEXT_CHARS);
            }
            final String context = gathered;
            final ai.ibiko.llm.ActionTags tags = new ai.ibiko.llm.ActionTags();
            final boolean ivieVoice = "ivie".equals(agent);
            // interpreted: the English isn't shown or spoken as it comes; the translation is, when it is done
            final ai.ibiko.chat.LiveReply live = new ai.ibiko.chat.LiveReply(act.handler, lang == null && act.speakReply() && act.talk != null,
                    new ai.ibiko.chat.LiveReply.Out() {
                        @Override
                        public boolean current() {
                            return act.alive(id) && act.visible(id);
                        }

                        @Override
                        public void speak(String sentence) {
                            if (act.chatPane != null) {
                                String owner = act.chatPane.beginVoice();
                                if (owner != null) {
                                    act.talk.setOwner(owner); // said as it is written, and kept under the message it will be
                                }
                            }
                            act.talk.queue(ivieVoice, sentence);
                        }

                        @Override
                        public void show(String soFar) {
                            if (act.chatPane != null) {
                                act.chatPane.setLive(ai.ibiko.llm.ActionTags.strip(soFar));
                            }
                            act.backupPartial(ai.ibiko.llm.ActionTags.strip(soFar)); // a crash doesn't lose what was written
                        }
                    });
            // an order line at the start ("DO SEARCH …") is held back: it is for Ibiko, not shown or spoken
            final StringBuilder head = new StringBuilder();
            final int[] gate = {acts ? 0 : 1}; // 0 deciding, 1 showing, 2 inside the order line
            final ai.ibiko.llm.NativeLlm.Sink sink = text -> {
                if (!act.modelPiece(id, text)) {
                    return false;
                }
                if (text != null && !"\u0001".equals(text)) {
                    String piece = text;
                    if (gate[0] != 1) {
                        head.append(text);
                        String h = head.toString().replaceFirst("^\\s+", "");
                        if (gate[0] == 0) {
                            if (h.length() < 3 && !h.contains("\n")) {
                                return act.alive(id); // too short to tell yet
                            }
                            gate[0] = ai.ibiko.suit.FunctionCode.startsLikeCode(h) ? 2 : 1;
                        }
                        if (gate[0] == 2) {
                            int nl = h.indexOf('\n');
                            if (nl < 0) {
                                return act.alive(id); // still the order line
                            }
                            gate[0] = 1;
                            piece = h.substring(nl + 1);
                        } else {
                            piece = h;
                        }
                        if (piece.isEmpty()) {
                            return act.alive(id);
                        }
                    }
                    tags.add(piece);
                    if (lang == null) {
                        live.piece(piece);
                    }
                }
                return act.alive(id);
            };
            String raw;
            if (pageTools.deep()) {
                // deep thinking, switched on for this chat: the same brain plans, drafts, checks and revises
                raw = ai.ibiko.suit.DeepThink.think((sys, prompt) -> act.model.task(sys, prompt, length, null), system,
                        context + "\n\nThe user says: " + english, 3,
                        (pass, result) -> {
                            if (result != null && !result.isEmpty() && (pass.startsWith("Understood") || pass.startsWith("Reviewed"))) {
                                act.ibiko1().learnThinking(english, result); // Ibiko 1 learns how the brain thought it through
                            }
                            act.handler.post(() -> {
                                if (act.alive(id)) {
                                    act.activity(pass);
                                }
                            });
                        }, () -> !act.alive(id));
                if (raw != null) {
                    sink.onText(raw);
                }
            } else {
                raw = act.model.reply(system, english, context, length, sink);
            }
            raw = foldSteer(id, system, english, context, length, raw, sink);
            tags.flush();
            ai.ibiko.llm.BrainOrder wrote = acts ? ai.ibiko.llm.BrainOrder.leading(raw) : null;
            if (wrote != null && !act.model.remoteOn() && !ai.ibiko.llm.CodeFit.fits(wrote, question + " " + english)) {
                // a small model on the device picked an action the words don't ask for (plan mode for "Hello", a share
                // for "Can you write Java?"): nothing is carried out, and the question is answered plainly instead
                act.activity("That wasn't asked for: answering instead");
                String rest = ai.ibiko.llm.BrainOrder.withoutOrder(raw);
                if (rest == null || rest.trim().length() < 80) {
                    String plain = act.model.reply(act.systemFor(agent, false), english, context, length, null);
                    rest = plain == null ? rest : plain;
                }
                raw = rest;
                wrote = null;
            }
            final ai.ibiko.llm.BrainOrder order = wrote;
            if (order != null) {
                final String line = String.valueOf(ai.ibiko.suit.FunctionCode.parse(raw)); // the code itself, wherever it stood
                final String q = question;
                if (ai.ibiko.chat.ui.UsageActivity.learningOn(act.prefs)) {
                    act.hub.worker.execute(() -> act.brainBox().learn(q, line));
                } // Ibiko's own brain learns how this became an action
                raw = ai.ibiko.llm.BrainOrder.withoutOrder(raw);
            }
            String back = null;
            if (lang != null && raw != null && act.alive(id)) {
                act.activity("Putting the answer in " + lang.shown);
                back = ai.ibiko.llm.Interpreter.fromEnglish((s, t, n) -> act.model.task(s, t, n, null), lang,
                        ai.ibiko.llm.ActionTags.strip(raw));
                if (back == null) {
                    act.activity("Couldn't put it in " + lang.shown + "; here it is in English");
                }
            }
            final String all = back != null ? back : raw;
            final String englishBody = raw == null ? "" : ai.ibiko.llm.ReplyLayout.join(ai.ibiko.llm.ActionTags.strip(raw));
            act.handler.post(act::clearPartial); // the reply is complete: no backup needed
            final boolean spokeLive = live.end(all);
            final String switchTo = tags.switchTo();
            act.handler.post(() -> {
                act.writing.remove(id);
                String w = act.replyWorkers.remove(id);
                if (w != null) {
                    ai.ibiko.agent.AgentBoard.end(w, !act.alive(id) ? ai.ibiko.agent.AgentBoard.Outcome.STOPPED
                            : all == null || all.trim().isEmpty() ? ai.ibiko.agent.AgentBoard.Outcome.FAILED : ai.ibiko.agent.AgentBoard.Outcome.DONE);
                }
                if (!act.alive(id)) {
                    return;
                }
                String body = all == null ? ""
                        : ai.ibiko.llm.ReplyLayout.join(ai.ibiko.llm.ActionTags.strip(all));
                if (!act.visible(id)) {
                    // finished while another page is on screen: the reply goes to its own page, quietly
                    String kept = body;
                    if (order != null) {
                        kept = (kept.isEmpty() ? "" : kept + "\n\n") + "(Ready to " + order.kind.name().toLowerCase(java.util.Locale.US)
                                + (order.arg.isEmpty() ? "" : " \u201c" + order.arg + "\u201d") + ". Ask again here to carry it out.)";
                    }
                    if (act.chats != null && !kept.isEmpty()) {
                        act.keepVoice(kept);
                        act.chats.replyToPage(agent, kept, act.stampVoice());
                        act.unreadPages.add(agent); // a dot on that page's tab
                        act.personaTabs.update(act.stage.agent());
                    }
                    return;
                }
                if (!act.inFront && order == null && !body.isEmpty()) {
                    // finished while Ibiko is in the background: kept in its chat at once (nothing typed out to an
                    // empty screen, nothing lost if Android closes the screen), and a notification says it's there
                    act.endThink();
                    act.setStreamOpen(false);
                    if (act.chatPane != null) {
                        act.chatPane.setLive(null);
                    }
                    String kept = ai.ibiko.gate.ClaimGuard.check(body, false);
                    act.keepVoice(kept);
                    if (act.chats != null) {
                        act.chats.replyToPage(agent, kept, act.stampVoice());
                    }
                    ai.ibiko.llm.ReplyNotice.show(act, agent, kept);
                    final String learnQ = question, learnA = kept;
                    act.hub.worker.execute(() -> {
                        if (ai.ibiko.chat.ui.UsageActivity.learningOn(act.prefs)) { // Settings › Data and privacy › learning
                            act.brainBox().learnTalk(learnQ, learnA);
                        }
                        act.ibiko1().readReply(learnA);
                    });
                    return;
                }
                act.endThink();
                if (order != null) {
                    // the model asked Ibiko to act: its short sentence (if any) is shown, then the order is carried out
                    if (!body.isEmpty()) {
                        stream.add(new TurnLine(agent, "SAY", body));
                    }
                    act.setStreamOpen(false);
                    act.carryOut(agent, question, order);
                    return;
                }
                if (body.isEmpty()) {
                    String said = null;
                    for (TurnLine line : offline == null ? new ArrayList<TurnLine>() : offline.lines) {
                        if ("SAY".equals(line.cue) && line.text != null
                                && !line.text.trim().isEmpty()) {
                            said = act.spokenLine(line.text);
                        }
                    }
                    String why = act.model == null ? null : act.model.problem();
                    if (said == null && act.needsInternet() && (act.connectivity == null || !act.connectivity.isOnline())) {
                        // the connection dropped while the API was answering: said plainly, and asked again when back
                        if (act.chats != null && act.chats.current() != null) {
                            act.waitingOnline.add(new String[] { act.chats.current().id, question });
                        }
                        said = "The connection dropped while I was answering. I'll finish this the moment you're back online.";
                    }
                    if (said == null && act.unreachable(why) && !act.aboutPhoneFiles(question)) {
                        // the API's address can't be reached (a gateway down, a DNS failure): Ibiko 1, on this device,
                        // answers instead of leaving only an error; Retry still asks the API again
                        said = "My connection is down for a moment, so I'm answering from this device. Tap Retry for the"
                                + " full answer once it's back.";
                        final String again = question;
                        act.handler.postDelayed(() -> {
                            if (!act.isFinishing()) {
                                act.ibiko1().endpoint().chat(again);
                            }
                        }, 1200);
                    }
                    stream.add(new TurnLine(agent, "SAY", said != null ? said
                            : why != null && !why.isEmpty() && !"stopped".equals(why)
                                    ? "I couldn't finish that reply. Tap Retry and I'll try again." + act.detail(why)
                                    : "I couldn't finish that reply. Tap Retry and I'll try again."));
                } else {
                    // a reply that says it sent, saved or posted something when nothing was done says so plainly
                    body = ai.ibiko.gate.ClaimGuard.check(body, false);
                    act.keepVoice(body); // the rest of the voice, under this reply, while the words go on screen
                    TurnLine said = new TurnLine(agent, "SAY", body);
                    if (spokeLive) {
                        act.spokenLive.add(said);
                    }
                    stream.add(said);
                    // after it is shown: a correction follows only if one is needed (checked in English)
                    act.factCheck(id, agent, english, lang == null ? body : englishBody);
                    final String learnQ = question, learnA = body;
                    act.hub.worker.execute(() -> {
                        if (ai.ibiko.chat.ui.UsageActivity.learningOn(act.prefs)) { // Settings › Data and privacy › learning
                            act.brainBox().learnTalk(learnQ, learnA);
                        } // Ibiko grows with every answer
                        act.ibiko1().readReply(learnA); // and Ibiko 1 learns the language of it
                    });
                }
                if (switchTo != null && !switchTo.equals(agent)) {
                    act.walker.setAfter(() -> act.switchFace(switchTo, true));
                } // the microphone never opens by itself: only a tap on the mic
                act.setStreamOpen(false);
            });
        });
        return true;
    }

    ai.ibiko.turn.DualAgentCoordinator.Agent agentFor(final String persona,
            final boolean online) {
        return (question, heard) -> {
            BrainAnswer a;
            synchronized (act.brain) {
                a = act.brain.respond(question, persona);
            }
            List<String> say = new ArrayList<String>();
            if (a.needsOnline && online) {
                List<TurnLine> d = act.desk.ask(question, persona);
                if (d != null) {
                    for (TurnLine l : d) {
                        if ("SAY".equals(l.cue) && l.agent.equals(persona)) {
                            say.add(l.text);
                        }
                    }
                    if (!say.isEmpty()) {
                        return new ai.ibiko.turn.DualAgentCoordinator.Reply(say, true);
                    }
                }
            }
            for (TurnLine l : a.lines) {
                if ("SAY".equals(l.cue)) {
                    say.add(l.text);
                }
            }
            return new ai.ibiko.turn.DualAgentCoordinator.Reply(say, a.answered && !a.needsOnline);
        };
    }
}
