package ai.ibiko;

import ai.ibiko.stage.StageMode;
import ai.ibiko.turn.LocalTurn;

/** The coding agent at work on the phone's files. The chat screen starts it; this is not the screen. */
final class AgentSession {
    private final MainActivity a;

    AgentSession(MainActivity a) {
        this.a = a;
    }

    void run(final String task, final String persona, final boolean onlyReading) {

        final String workAt = a.workKey(persona); // this chat's: saved here even if the user opens another chat meanwhile
        // Ibiko in Plan mode reads and hands in a plan; "continue" carries on with the same planning
        final boolean planning = !onlyReading && "ibiko".equals(persona) && ai.ibiko.agent.IbikoMode.of(a.prefs) == ai.ibiko.agent.IbikoMode.PLAN;
        final boolean readOnly = onlyReading || planning;
        final int id = a.nextAsk();
        final long since = System.currentTimeMillis(); // what this reply opens or makes is shown under it
        a.stopPlay();
        a.stage.setMode(StageMode.THINKING);
        String who = "Ibiko";
        a.armThink(planning ? "Planning" : readOnly ? "Reading the files" : "Thinking", "Writing");
        final java.io.File workspace = a.chatSpace();
        final ai.ibiko.connect.ConnectorStore connectors = new ai.ibiko.connect.ConnectorStore(a);
        final String earlier = a.chats == null ? "" : a.chats.transcript(a.model.remoteOn() ? 12000 : Math.max(1500, a.model.window()),
                a.model.remoteOn() ? 6000 : Math.max(220, a.model.window() / 6)); // what was already found, as much as the model can hold
        final String tools = a.connectorTools();
        a.model.coding = true;
        a.showIbikoMode(true); // the agent's mode in the header while it works
        final String agentId = ai.ibiko.config.ModelRegistry.agentIdFor(persona); // Obi reads, Ivie reviews, Ibiko writes
        final ai.ibiko.config.ModelRegistry.Agent agentRow = ai.ibiko.config.ModelRegistry.of(a).agent(agentId);
        final int taskTier = ai.ibiko.chat.Tuning.TASK_TIER.get(a.prefs); // 0 = auto
        final int brainTier = ai.ibiko.chat.Tuning.BRAIN_TIER.get(a.prefs);
        final ai.ibiko.llm.TaskSize.Tier stepTier = brainTier > 0 ? ai.ibiko.llm.TaskSize.Tier.of(brainTier)
                : (taskTier > 0 ? ai.ibiko.llm.TaskSize.Tier.of(taskTier) : ai.ibiko.llm.TaskSize.Tier.FAST);
        final ai.ibiko.llm.TaskSize.Tier reportTier = taskTier > 0 ? ai.ibiko.llm.TaskSize.Tier.of(taskTier)
                : ai.ibiko.llm.TaskSize.Tier.STRONG;
        final String sectionKey = a.chats == null || a.chats.current() == null ? null
                : a.chats.current().group == null || a.chats.current().group.isEmpty() ? a.chats.current().id : a.chats.current().group;
        final ai.ibiko.scribe.Scribe[] scribed = {null}; // the Scribe writes every step down, once it is open
        final ai.ibiko.agent.CodeAgent.Host host = new ai.ibiko.agent.PhoneHost(a, a.prefs, a.handler, connectors, id, () -> a.latestJob(id),
                line -> {
                    ai.ibiko.scribe.Scribe sc = scribed[0];
                    if (sc != null) {
                        sc.step(ai.ibiko.agent.StepDetail.words(line));
                    }
                    if (a.chatPane != null) {
                        a.chatPane.activityStep(line); // with its detail, opened by a tap
                    }
                    a.updateThoughtStatus(ai.ibiko.agent.StepDetail.words(line), true);
                }, () -> a.showIbikoMode(true)).onNote(note -> {
                    if (a.chatPane == null || note == null) {
                        return;
                    }
                    String n = note.trim();
                    // a finding that only says what comes next is a step, not another chat bubble to continue
                    if (ai.ibiko.agent.CodeAgent.promise(n) || ai.ibiko.agent.CodeAgent.unfinished(n)) {
                        a.chatPane.activityStep(n);
                    } else {
                        a.chatPane.assistant(persona, n);
                    }
                }).onSteer(a.steer::drain);
        new Thread(() -> {
            final StringBuilder live = new StringBuilder();
            final long[] shown = {0};
            final ai.ibiko.agent.CodeAgent.Brain brain = new ai.ibiko.agent.CodeAgent.Brain() {
                        @Override
                        public String ask(String system, String transcript) {
                            // a step is a tool call or a short note: the fast model. The report uses the strong one.
                            return asked(system, transcript, ai.ibiko.chat.Tuning.AGENT_TOKENS.get(a.prefs), stepTier);
                        }

                        @Override
                        public String askLong(String system, String transcript) {
                            // the final report: room to write all of it, on the strong model
                            return asked(system, transcript, Math.max(12000, ai.ibiko.chat.Tuning.AGENT_TOKENS.get(a.prefs) * 3),
                                    reportTier);
                        }

                        @Override
                        public String askQuiet(String system, String text) {
                            // a survey reader or a worker: not streamed into the chat, and it stops with the task. On an API it
                            // does not wait for the device model's lock, so the readers and workers really run at once
                            if (!a.alive(id)) {
                                return null;
                            }
                            if (a.model.remoteOn()) {
                                String got = a.model.remoteTask(system, text, ai.ibiko.chat.Tuning.AGENT_TOKENS.get(a.prefs), stepTier);
                                if (got != null || !a.alive(id)) {
                                    return got;
                                }
                            }
                            return a.model.task(system, text, ai.ibiko.chat.Tuning.AGENT_TOKENS.get(a.prefs), piece -> a.alive(id), stepTier);
                        }

                        @Override
                        public int readers() {
                            // the Workers setting (Settings \u203A Advanced); the device's own model answers one call at a time
                            return ai.ibiko.chat.Tuning.workersAtOnce(a.prefs, a.model.remoteOn());
                        }

                        /**
                         * One step, its words streamed into the chat as they are written (as ChatGPT does), unless
                         * they turn out to be a tool call, which only shows in the steps.
                         */
                        private String asked(String system, String transcript, int tokens, ai.ibiko.llm.TaskSize.Tier tier) {
                            synchronized (live) {
                                live.setLength(0);
                            }
                            a.handler.post(() -> {
                                if (a.alive(id) && a.chatPane != null) {
                                    a.chatPane.stepEnded(); // writing from the step before is kept, not wiped
                                }
                            });
                            return a.model.task(system, transcript, tokens, piece -> {
                                if (!a.modelPiece(id, piece)) {
                                    return false;
                                }
                                if (piece == null || "\u0001".equals(piece)) {
                                    return a.alive(id);
                                }
                                final String sofar;
                                synchronized (live) {
                                    live.append(piece);
                                    sofar = live.toString();
                                }
                                String all = ai.ibiko.agent.CodeAgent.unlabel(sofar.replaceAll("(?s)<think>.*?(</think>|$)", "").replaceAll("</?final>", "").trim()); // the answer shows as it is written
                                // a tool call hides only itself: the writing before it stays (a report followed by a
                                // call that saves it used to vanish as soon as the call began)
                                int cut = ai.ibiko.chat.ReplyText.toolStart(all);
                                String t = cut < 0 ? all : all.substring(0, cut).trim();
                                boolean tool = cut >= 0 && t.isEmpty();
                                long now = System.currentTimeMillis();
                                if (!tool && t.length() > 0 && now - shown[0] > 120) {
                                    shown[0] = now;
                                    a.handler.post(() -> {
                                        if (a.alive(id) && a.chatPane != null) {
                                            a.chatPane.setLive(t);
                                        }
                                    });
                                } else if (tool && now - shown[0] > 120) {
                                    shown[0] = now;
                                    a.handler.post(() -> {
                                        if (a.alive(id) && a.chatPane != null) {
                                            a.chatPane.setLive(null);
                                        }
                                    });
                                }
                                return a.alive(id);
                            }, tier);
                        }

                        @Override
                        public String why() {
                            return a.model.problem(); // shown with "the model didn't answer", so the cause is visible
                        }
                    };
            // long work (Ibiko changing things): rounds of steps, a progress note in the chat after each, until it is
            // done, stopped, or the time limit; the phone's foreground service keeps it going while minimised
            // every agent (Ibiko changing things, Obi or Ivie reading files) except a plan being drawn up
            final boolean rounds = !planning && !ai.ibiko.agent.Blueprint.pointed(task)
                    && a.prefs.getBoolean("ibiko_keep_going", true);
            final ai.ibiko.scribe.RunMode pace = ai.ibiko.scribe.RunMode.of(ai.ibiko.chat.Tuning.RUN_MODE.get(a.prefs));
            a.model.watchIdleMs = pace.idleMs;
            a.model.watchCapMs = pace.replyCapMs;
            a.model.watchReadMs = pace.readMs;
            final long workMs = pace.ordinal() <= ai.ibiko.scribe.RunMode.BALANCED.ordinal()
                    ? Math.max(a.MAX_WORK_MS, pace.workMs) : pace.workMs;
            final ai.ibiko.scribe.Scribe scribe = new ai.ibiko.scribe.Scribe(workspace);
            // one record for this chat's project, whatever it is asked: a follow-up adds to it instead of starting another
            scribe.begin(persona, task, sectionKey);
            scribed[0] = scribe;
            if (ai.ibiko.scribe.Scribe.worth(task)) {
                a.handler.post(() -> {
                    if (a.alive(id) && a.chatPane != null) {
                        a.chatPane.activityStep("Scribe opened the project record");
                    }
                });
            }
            // the agent's own row, not the model's: this crew has its own workers, steps and side
            final int agentSteps = agentRow != null && agentRow.maxSteps > 0 ? agentRow.maxSteps : ai.ibiko.chat.Tuning.AGENT_STEPS.get(a.prefs);
            final java.util.function.Supplier<ai.ibiko.agent.CodeAgent> make = () -> new ai.ibiko.agent.CodeAgent(workspace, brain, host, tools,
                    a.model.window() * 3 > 24_000 ? 24_000 : a.model.window() * 3)
                    .useGit(connectors.git().maxFiles(ai.ibiko.chat.Tuning.CLONE_FILES.get(a.prefs)).maxFileBytes(pace.fileBytes))
                    .steps(rounds ? Math.min(pace.roundSteps, agentSteps) : agentSteps)
                    .as(who, readOnly).planning(planning).keepGoing(rounds).squad(a.chatTools(persona).workers())
                    .ownRecord(scribe.recordPath()) // only this project's record and notes; never another project's
                    .handover(false).checkpoint(w -> {
                        a.saveAgentWork(workAt, w);
                        scribe.checkpoint(w);
                    })
                    .compact(a.model.window() < 6000); // a small model's memory goes to the work, not the walkthroughs
            ai.ibiko.llm.ReplyService.hold(a);
            a.markRunning(persona, task); // if the app is closed or crashes, the next start offers to carry on
            a.backgroundTask = task;
            final String lead = ai.ibiko.agent.AgentBoard.start(who + (planning ? " \u00B7 planner" : readOnly ? " \u00B7 reader" : " \u00B7 lead"), task, a.brainLabel());
            final java.util.List<String> changed = new java.util.ArrayList<String>();
            ai.ibiko.agent.CodeAgent agent = make.get();
            String result;
            try {
                if (a.carriesOn(task)) {
                    agent.resume(a.agentWork(persona)); // "continue", "give me the result": the same work goes on
                }
                // An audit used to be answered by a planner that cannot open files. The chat then showed the
                // tool call and "the step could not be done". The coding agent does the job, so the files are read.
                final String stands = scribe.resume();
                final String asked = ai.ibiko.scribe.Scribe.worth(task) || !stands.isEmpty() ? task
                        + (stands.isEmpty() ? "" : "\n\n" + stands + "\nCarry on from that point. Read only the files the next"
                                + " pending step needs; do not start a second project or re-read what is listed as done.")
                        : task + a.ibiko1().methods().hint(task).replaceFirst("^ ", "\n\n");
                final String ahead = a.ibiko1().premeditate(task).trim();
                result = agent.run(asked + "\n\n" + ahead, earlier); // the agent thinks ahead too
                long began = System.currentTimeMillis();
                int round = 1;
                int spun = 0;
                while (rounds && agent.outOfSteps() && !host.stopped() && round < a.MAX_ROUNDS
                        && System.currentTimeMillis() - began < workMs) {
                    if (ai.ibiko.agent.CodeAgent.unfinished(result) && ++spun > 6) {
                        break; // six status lines in a row: stop and say so, instead of spinning
                    }
                    if (!ai.ibiko.agent.CodeAgent.unfinished(result)) {
                        spun = 0;
                    }
                    a.saveAgentWork(workAt, agent.work());
                    changed.addAll(agent.changed());
                    final String note = result;
                    final int r = round;
                    a.handler.post(() -> a.progressNote(id, persona, r, note));
                    String sofar = agent.work();
                    agent = make.get();
                    agent.resume(sofar);
                    result = agent.run(MainActivity.CARRY_ON, "");
                    round++;
                }
                if (rounds && agent.outOfSteps()) {
                    result = result + "\n\n(" + (System.currentTimeMillis() - began >= workMs ? "Stopped after "
                            + workMs / 3_600_000 + " hours" : "Paused after " + round + " rounds")
                            + ". Say \u201ccontinue\u201d to keep going.)";
                }
            } finally {
                a.model.watchIdleMs = 0;
                a.model.watchCapMs = 0;
                a.model.watchReadMs = 0;
                ai.ibiko.llm.ReplyService.release(a);
                ai.ibiko.agent.AgentBoard.end(lead);
            }
            a.markRunning(null, null); // finished (or stopped): nothing to carry on after a restart
            a.backgroundTask = null;
            final String answer = result;
            final String passedTo = agent.handedTo();
            changed.addAll(agent.changed());
            scribe.finish(answer, host.stopped(), changed);
            a.saveAgentWork(workAt, agent.work());
            if (!readOnly) {
                a.recordIbikoFiles(workspace, changed);
            }
            a.handler.post(() -> {
                a.model.coding = false;
                a.refreshSend(); // the job is over: Stop goes back to Send
                a.showIbikoMode(false); // the job is over: the mode goes back into the Tools sheet
                a.handler.post(a.releaseHeld); // a message held while Ibiko worked goes now, even if this page moved on
                a.refreshSend();
                if (!a.alive(id) || a.isFinishing()) {
                    return;
                }
                a.endThink();
                if (passedTo != null) {
                    a.handOver(persona, task, passedTo); // not Ibiko's work: Obi or Ivie answers it
                    return;
                }
                a.attachWork(readOnly ? null : changed, since); // the diff, pull requests and files, under this reply
                String spoken = ai.ibiko.chat.ReplyText.prepare(answer);
                a.play(LocalTurn.speakAs(persona, spoken.isEmpty() ? answer : spoken), null);
                if (planning && answer != null && !answer.trim().isEmpty() && !host.stopped()) {
                    a.approvePlan(task, answer);
                }
            });
        }, "ibiko-agent").start();
        }
}
