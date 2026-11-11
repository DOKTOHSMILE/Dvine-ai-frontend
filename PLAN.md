# The chat answer, the download bar, and a smaller main screen (0.99.90)

- [x] A status line ("I'll fix those paths", "Alright, I am continuing now") stays with the steps. It is not another chat bubble, and the steps of a turn still running fold into one "Working" row.
- [x] A download (the model, a clone, a package) has a progress bar on the chat screen. A silent git clone shows the bar at once.
- [x] The plan and the file tools use memory/projects/<name>, the folder the clone is in. projects/<name> is that same folder. Correcting a step's path is not a dropped step.
- [x] MainActivity no longer holds the coding job, the phone actions, photograph lookup, or the header and composer. Those are their own classes.

# Listen in the human voice; Settings steady (0.99.89)

- [x] Sentences wait for the human voice while it loads; the device voice only on failure, with the reason in Settings.
- [x] A voice choice redraws only the voice options.

# Settings split into pages (0.99.88)

- [x] Settings opens as a list of sections; each section is its own page, built only when opened.

# Facts about the app under the download (0.99.87)

- [x] A slow, looping roll of facts about Ibiko under the running line; the download is never slowed.

# Always running; Back to the background; reading while it downloads (0.99.86)

- [x] KeepAlive: a foreground service until the pilot closes Ibiko (notification Close, or swiped away).
- [x] Back moves Ibiko to the background instead of quitting.
- [x] Abilities, quotes and facts run across the download screen.

# The app icon a little smaller; downloads carry on (0.99.85)

- [x] The face 46 of 108 units, a teal ring round it in every launcher shape.
- [x] A broken download carries on from where it stopped (app files, voices, Linux; models already did).

# Ibiko's live face; the icon zoomed out (0.99.84)

- [x] The working mark drawn live: blinks, glides, breathes; a look for thinking, reading, writing, working, replying.
- [x] The app icon: the face smaller on a teal background, curved corners showing in every launcher.

# Big code without crashing; approvals; careful plans (0.99.83)

- [x] Code and HTML viewers take big text by file; big code shown as a list of lines.
- [x] Connector calls that change something ask first; "Allow for this task"; asking back on once.
- [x] Each plan step has a check and is ticked only after it passed; steps in order; '## Repository' for GitHub work.

# A human voice; speech only when asked; one folder per repository (0.99.82)

- [x] Kokoro and Piper voices on the device (sherpa-onnx); no pitch shifting.
- [x] One voice, a man's or a woman's, from Settings; speech only for a reply just asked for, on screen.
- [x] The voice downloaded with the app files on the first start, one progress bar.
- [x] PLAN.md names the repository's folder; a clone in memory/projects is readable by the agent.
- [ ] Measure Kokoro's speed on the device; switch to Piper by itself when it can't keep up.

# A clone is not stopped at 3 minutes (0.99.81)

- [x] Git clone (and apk, wget, curl) may run for 2 hours. The shell is not a terminal, so git prints nothing while it fetches, and the old clock killed it at 3 minutes with an empty card.
- [x] Any other command still stops after 3 minutes of silence. Output keeps it going, up to 2 hours.

# First-start download; Ibiko 1 runs code and the app (0.99.80)

- [x] ConceptNet out of the app, downloaded on the first start as "Downloading app files"; no opening without it.
- [x] Ibiko 1 code tasks as approved shell commands (typed commands, pasted code, files, clone, install).
- [x] Ibiko 1 works every app control a plain sentence can ask for.
- [ ] Ibiko 1 working through multi-step code tasks on its own (plan, run, read the result, next step).

# Comprehension: what a message means (0.99.79)

- [x] Open English WordNet and ConceptNet downloaded and built into sorted tables, read by binary search.
- [x] Universal Comprehension Layer v1: intent, operations, word senses, pronouns, object parts; "Understood: …" in the foundation.
- [x] Offline answers: meanings, synonyms, opposites, uses, abilities, parts, places, materials.
- [ ] ATOMIC 2020 for the intent engine (why people do things, what follows).
- [ ] The reasoning engine (cause and effect) and action planning from the blueprint.

# More built-in knowledge; the terminal as Termux shows it (0.99.78)

- [x] Countries' codes and regions, the elements in full, Nigeria's states, the planets (2,122 facts).
- [x] Terminal: short greeting, smaller text, bright keys, selection with a floating Copy / Paste / More bar.
- [x] A dictionary (Open English WordNet, CC BY 4.0): definitions offline (0.99.79).

# Reports never vanish; downloads in the notification bar (0.99.77)

- [x] Writing on the live line kept at each step's end; a tool call hides only itself.
- [x] Linux, GitHub clones and terminal downloads show progress in the notification bar.
- [ ] More built-in knowledge (next).

# The pilot's icon and thinking eyes; one dialog style (0.99.76)

- [x] The app icon from the pilot's art; the eyes GIF on the working line.
- [x] A waiting dialog for the GitHub list, safe from double taps.
- [x] One standard style for every dialog.

# Each project's backups for its own agents only (0.99.75)

- [x] Own notes and record only; other projects' and the task states refused in file tools, listings, search and named shell paths.
- [ ] Confine shell commands to the project folder (a whole-workspace grep still reaches everything).

# Ibiko's own mark (0.99.74)

- [x] The robot icon replaced by an original abstract mark: three petals of light round a spark, in a broken orbit.

# The send button never sticks on Stop (0.99.73)

- [x] Redrawn on every stream change and when a job ends; while Stop shows it checks again each second.
- [ ] A finished plan marked DONE with its date, and dated ticks (proposed, waiting for the pilot's yes).

# Workers from the setting; plans cannot be wiped (0.99.72)

- [x] The Workers setting sets workers, readers and writers (default 8, up to 16; two at once on the device's model).
- [x] A shell command that would delete or overwrite a plan file is refused; appending still works.

# Carrying on from the .blueprint folder; a cuter icon (0.99.71)

- [x] After a break, the agent reads .blueprint/<project>/SURVEY.md and MAP.md first; a cut-off reading carries on.
- [x] The icon redrawn: body, waving arm, heart core, star antenna, kawaii eyes, nebula and orbit.

# CodeAssist can see R again (0.99.70)

- [x] The phone compiles `ai.ibiko`. The copy of the project CodeAssist had indexed still said `ai.dvine`, so the generated `R` was not `ai.ibiko.R` and every screen failed with "R cannot be resolved".
- [x] That stale index and the old Gradle snapshot are no longer what a pull restores. Close the project, pull, open it again, then build.

# Ibiko's icon (0.99.69)

- [x] A cute, futuristic robot: adaptive icon (background, foreground, monochrome) and PNGs from the same design.

# The package is ai.ibiko (0.99.68)

- [x] Application ID, namespace, manifest and every Java package renamed to ai.ibiko.
- [x] Both native libraries rebuilt with the new names.
- [ ] Install on the device (a new app: the model downloads again).

# Answered during a job on the device (0.99.67)

- [x] A message sent while a job runs on the device's own model is answered between two of the job's steps.
- [x] Every visible string checked for old names: none shows.

# The device's model starts in seconds (0.99.66)

- [x] No hidden thinking on the device's model unless asked.
- [x] Short instructions for the device's model (~560 tokens instead of ~1,900); context capped.
- [x] No reload when a smaller question comes; the model kept five minutes off screen.
- [x] The steps say how much was read and when the first word came.
- [ ] Measure on the pilot's device; then the graphics chip (Vulkan) as an experiment.

# The truth ledger (0.99.65)

- [x] Every sentence judged true or false is kept, both sides; recognised again; mistakes told to every brain.

# The speaker hum (0.99.64)

- [x] No audio stream opened unless something is playing; closed, not paused, when it stops.
- [ ] Confirm on the pilot's device with a connected speaker.

# Question everything (0.99.63)

- [x] Truth G8: every sentence learned and every reply asked "true or false?"; false is not learned and is corrected.
- [ ] Wider witnesses: confirmed answers and fresh sources as judges, not only the built-in reference and firm facts.

# A foundation of truth; a Think pill (0.99.62)

- [x] The Truth formula: all that is known, ranked by source, settled, said with how sure; under every brain's answer.
- [x] The paperclip replaced by a Think pill (deep thinking on or off); Media attaches.
- [x] The GitHub icon redrawn inside its frame.

# Learning from its own replies; the chat screen remade (0.99.61)

- [x] The Reflection formula: every reply judged by what comes next; lessons and taste go with the next request.
- [x] One tools button; the Tools sheet remade; header, logo and mode pill fixed.

# The hands for writing; population (0.99.60)

- [x] Long pieces written a section a writer, at once, on an API.
- [x] Population with its year for 193 countries.
- [x] Send and Dismiss at a full touch size.
- [ ] The hands for research (several pages read at once) and social (several posts drafted at once).

# Ibiko thinks ahead (0.99.59)

- [x] The Foresight formula: what this pilot usually needs next, and the look-before check before any answer or action.

# Quick chips over the message box (0.99.58)

- [x] Tools, Code, Media, Explore, More over the box; each opens what it names.

# The app is Ibiko; jobs in the background; nothing read twice (0.99.57)

- [x] One name everywhere a person looks or a model is told: Ibiko.
- [x] A message sent during an agent job is answered beside it; an instruction for the job goes to it.
- [x] The survey keeps each part as it is read and carries on after a break; the map is sorted with times.
- [x] Plans are added to, never replaced.
- [x] 2,379 built-in facts.
- [x] The composer chips (Tools, Code, Media, Explore, More), in 0.99.58.
- [ ] Rename internal keys and classes (obi, ivie, ibiko) once old data can be migrated.
- [ ] The full Wikidata set when it is reachable, and more subjects.

# The job stays on screen until it is done (0.99.56)

- [x] A `git clone` still lands in `memory/projects/`, and the counting-objects log is not a code card at all.
- [x] A message sent while a reply is on screen stays under that reply. The same words are not written again underneath it.
- [x] "Listed the files…" and "I'll map it next" stay in the step line. An audit does not end on them.
- [x] A reply cut off at a sentence ("logic." + "Found") is two sentences, not one word. A DNS blip is tried again inside the agent too.

# Clones stay in the workspace, and a job is not cut off (0.99.55)

- [x] Every `git clone`, including `git -c … clone` and a command that also mentions `memory/projects`, writes into `memory/projects/`. A repo already sitting in `/root/workspace` is moved there. A clone that only has `.git` is checked out.
- [x] Git's "Counting objects" lines are never a code card, including while the reply is still streaming.
- [x] A message sent during a reply is placed under the words already on screen, not above them.
- [x] A DNS blip ("Unable to resolve host") and an empty reply are tried again. A short "I'll do that next" does not end an audit.

# Clones land in the workspace (0.99.54)

- [x] `git clone` writes into `memory/projects/`, the folder the workspace screen shows, not Linux's home.
- [x] The clone's "Counting objects" log is one line, not a code view.
- [x] A message sent while a reply is still being written appears under that reply, not above it.
- [x] A reply that is only a tool call is run, not thrown away as "the server sent no answer".

# One assistant, the organisation, one record (0.99.53)

- [x] Command codes never reach the conversation.
- [x] Ibiko answers everything when a model is in use; real work starts the agents by code.
- [x] The organisation: ARCH, CPGC, ROLX, SCRB and ten specialists; time on every part; hands under each agent.
- [x] One record file a project with an exact resume point; workers kept to the project's folder.
- [x] DVINE 1 answers from facts shipped in the app (starter set).
- [ ] Remove Obi and Ivie from the code itself (classes, voices, canvas wiring, tests).
- [ ] The full built-in dataset (run tools/build_world_facts.py when Wikidata allows; then more subjects).
- [ ] The organisation for features outside the coding agent.

# An audit reads the files (0.99.52)

- [x] An audit is done by the coding agent, which can open files. It is no longer a plan that prints the tool call and then says the step could not be done.

# One summary, icons on a tap, learning the method (0.99.51)

- [x] A finished turn: the process in one row at the top, one summary under it.
- [x] Copy, Listen and the rest show on a tap of the reply.
- [x] DVINE 1 learns how an answer was reached from every reply, with no tap.
- [ ] DVINE 1 composing new methods from parts of old ones.

# Only the brain commands; workers; tools grid (0.99.50)

- [x] With a model in use, no wording runs a function: the brain gives a code.
- [x] The lead model starts workers on parts of the real work.
- [x] One tools grid for the group, every tool, small switches.
- [ ] The whole UI exercised on a device (only a static pass was done).

# The group chat only, voices, device fixes (0.99.49)

- [x] The group chat is permanent: no setting, no separate pages.
- [x] Each reply: the persona's icon and name; Listen reads only their words, in their voice.
- [x] African voices first (Nigerian, then other African English).
- [x] Device fixes: the right name on each line, gateway tags never shown, commands to Ibiko and the built-in Linux, no web search for commands, a quieter survey.
- [x] Share › DVINE from other apps.
- [ ] Two or three personas working at the same time in the group.
- [ ] Offline neural voices.

# Group chat without buttons (0.99.48)

- [x] The persona buttons are hidden in the group chat; the message chooses who answers, Obi by default.

# Group chat and auto-post (0.99.47)

- [x] One thread for Obi, Ivie and Ibiko; names on replies; buttons and @names pick who answers.
- [x] Auto-post on your behalf, off by default.
- [x] The version choices (post, edit, schedule, copy; X length) that 0.99.46 announced but did not contain.
- [ ] Two or three personas working at the same time in the group.

# The right persona, writing plans, social screen (0.99.46)

- [x] A task given to the wrong persona goes to the right page, as if sent there.
- [x] Stories, scripts and reports: outline, check, write section by section.
- [x] Social accounts: icons, counts, quick actions, posting settings, scheduled posts, recent activity.
- [x] Each post version: post, edit, schedule or copy; X length checked.
- [x] One group chat for Obi, Ivie and Ibiko (built in 0.99.47).

# Posts, tags, comments and the Scribe (0.99.45)

- [x] With an API chosen, the device's model never answers instead, even offline.
- [x] app/module.toml is bumped with build.gradle (a test checks they match).
- [x] The Scribe records every step and hands the done list to the next round.
- [x] Three post versions to pick from; named people tagged; comments under a post by its link; each after a yes.
- [ ] Watch a person's new posts and draft a comment for each, posted after a yes (not unattended).

# Voice, survey readers and posting (0.99.44)

- [x] Dictation keeps what was said before a pause; the mic is the standard icon, red and pulsing while recording.
- [x] The most natural voice is picked first; online voices and a voice download page in Settings.
- [x] Several readers survey the code at once in the Blueprint's first phase and write a map.
- [x] Facebook and X posts go out from the signed-in account after a yes; scheduled ones by themselves.
- [ ] An on-device neural voice (Piper through sherpa-onnx) for human voices without the internet.

# The Blueprint and a standard shell (0.99.43)

- [x] A real task goes: understand the code, write the blueprint, check it for gaps, build, verify.
- [x] Nothing but PLAN.md changes before the check passes; additions mid-task go into the plan and it is checked again.
- [x] Repeated calls and repeated failures are stopped; "done" with unticked steps goes back to them.
- [x] The Shell screen looks and works like a standard terminal.

# One Linux (0.99.42)

- [x] Chat, Ibiko, the task system and the Shell screen run commands in the same Alpine, through `LinuxHub.run`.
- [x] Alpine installs itself the first time anything needs it; the small built-in shell answers only when it cannot be installed, and says so.
- [x] No Termux permission, package query or engine path left in use.

# Audit fixes (0.99.41)

- [x] A job's replies, notes and steps go to the page it was asked on, never to another chat or page.
- [x] "Try again" asks the message before it once; it never asks itself.
- [x] Stop shows only on the page that is working; a held message is sent when the other work ends, and says it is waiting.
- [ ] Pages run their jobs in parallel (needs the MainActivity split, #73).
- [ ] Remove the Termux path and the 18 unused classes.

# A real terminal (0.99.40)

- [x] The shell is a terminal device: every key goes to Alpine; vi, nano, top, htop and less draw full screens; CTRL-C and CTRL-Z reach the program.
- [x] Pinch changes the text size and the program is told the new size; a drag scrolls back.
- [x] One shell for the shell screen, chat and Ibiko; leaving the screen leaves a program open.

# Same folder (0.99.39)

- [x] A shell command from chat starts in the coding workspace, the same folder the file tools see. `Dvine-ai-frontend` is `/workspace/Dvine-ai-frontend`, not an empty home directory.

# The shell, as one piece (0.99.36)

DVINE has one Alpine Linux. It is not a picture of a terminal.

- Open **Shell** and type. The prompt is `localhost:~#`. The folder stays (`~` is `/root`). Tab completes a name. CTRL-R recalls an older command. FIND scrolls to a match. COPY copies the session. CTRL-C stops the program. Colors and rewritten progress lines are shown.
- `pkg install` uses Alpine 3.22, the phone's DNS, and http repositories. `/work` is the app's files. `/storage` and `/sdcard` are there when the phone allows them.
- Leaving the shell screen does not stop Linux. Opening it again continues the same shell. A command from chat uses that shell too, and a failed command shows `[exit 1]`.
- A reply is no longer cut off because 3 or 10 minutes passed while it was still working.

# One shell (0.99.35)

- [x] While the shell screen is open, a command from chat runs in that shell. A second Linux is not started.
- [x] The text appears in the session, and chat gets the same text back.
- [x] A command that fails shows its exit code, for example `[exit 1]`.

# Search and one folder (0.99.34)

- [x] CTRL-R recalls an older command that contains what you typed. FIND scrolls to the next match in the session.
- [x] While a command runs, the prompt shows its name and the seconds.
- [x] A command from chat starts in the folder the shell was left in, and leaves the folder for the next one.

# Shell without a terminal device (0.99.33)

- [x] When the phone will not open a terminal device, the shell still stays open. The folder and variables last. Each command's text is read from a file while it runs.
- [x] The prompt shows how many seconds a command has been running.
- [x] COPY puts the session on the clipboard.

# Typing (0.99.32)

- [x] The prompt accepts a command while the shell is still starting, and runs it when the shell is ready.
- [x] Tab completes a command or a file name from the Linux files. Several matches are listed.
- [x] CTRL-A, CTRL-E, CTRL-W and CTRL-L move to the start, the end, delete a word, and clear the screen.

# Terminal text (0.99.31)

- [x] Output is drawn like a terminal. Colors show, a line can rewrite itself, and `clear` wipes the screen instead of printing control codes.

# Lasting shell (0.99.30)

- [x] When the phone allows a terminal device, one Alpine shell stays open. `cd` and variables last, and the next command does not start Linux again.
- [x] Ctrl-C is sent to that program. If a terminal device cannot be opened, each command still runs on its own, as before.
- [x] `/work`, and `/storage` or `/sdcard` when they exist, are visible inside the shell.

# Session (0.99.29)

- [x] New output is added on the end. The session is not rebuilt, so it does not jump.
- [x] The session and the up-arrow history are still there after you leave the screen.
- [x] Opening the shell again does not print the welcome a second time. Autofill does not cover the prompt.

# Keyboard (0.99.28)

- [x] Keys go to a real prompt line. The session is not redrawn on every letter, so the screen stays still and the keyboard stays open.

# Live output (0.99.27)

- [x] A command's text is shown as it is written, not only after it finishes. The folder is saved to a file, so that marker is not mixed into the output.

# Scroll (0.99.26)

- [x] The session no longer redraws on a timer, and a sideways scroller no longer sits inside the vertical one. A drag scrolls. It is not pulled back down unless you were already at the bottom.

# Standard terminal (0.99.25)

- [x] The title bar and the separate command box are gone. The prompt and the block cursor are the last line of the session, as in a normal terminal.
- [x] The extra keys are a plain bar, not buttons. `clear` and `exit` do what they say. Tap the session to open the keyboard.

# Terminal scroll (0.99.24)

- [x] New output scrolls to the last line after the text has been measured. Scrolling up to read stays put until the next command.

# Package install (0.99.23)

`pkg install` failed with "Function not implemented" and then "DNS: transient error". Alpine 3.24's package tool uses calls this phone rejects, and DNS was pointed at public servers the network can block.

- [x] Linux is replaced with Alpine 3.22.5 the first time the shell opens. Opening it downloads about 4 MB again.
- [x] Repositories use http. apk still checks the package signature.
- [x] DNS is the phone's own servers, with 8.8.8.8 and 1.1.1.1 only as fallback.
- [x] `PROOT_NO_SECCOMP` is not set. Setting it stopped proot from fixing the blocked calls.

# Linux terminal (0.99.22)

- [x] Each command finishes and prints, instead of a pipe that can hold the output back.
- [x] The folder stays between commands. The prompt shows it (`~` is `/root`).
- [x] `/work` is the app's files. `/storage` and `/sdcard` are mounted when the phone has them.
- [x] CTRL then C stops the command that is running.

# Linux terminal (0.99.21)

- [x] The shell screen compiles on CodeAssist. Stopping a command does not call `Process.pid()`, which that Android library does not have.
- [x] Once Alpine is installed, chat and Ibiko's `run` use it. Until then they keep the small Java shell.
- [x] A hard link that appears in the archive before its file is still unpacked, so Alpine's own tools are not dropped.

# Linux terminal (0.99.20)

The shell screen is Alpine Linux, not the short Java command list. Termux is not opened.

- [x] First open downloads Alpine 3.24 (about 4 MB) and unpacks it inside the app.
- [x] `proot` and its loader ship in the app (`libproot.so`, `libproot_loader.so`) so the phone can run them.
- [x] `pkg search`, `pkg install`, `pkg upgrade`, `pkg uninstall` and `pkg list` run Alpine's `apk`.
- [x] The screen has the extra keys (ESC, CTRL, ALT, arrows, home, end, page) and pinches to zoom. Double-tap sets the size back.
- [x] CTRL then C stops the running command. The shell stays open, so `cd` and installed packages last.
- [ ] python, git and ssh are not preinstalled. `pkg install python3 git openssh` adds them. The agent's `run` tool still uses the small Java shell.

# Shell crash (0.99.19)

A command with a single `&` (a background job, or a link like `a=1&b=2`) made the shell add tokens forever on the agent thread until the phone ran out of memory. The main thread was not running the shell. It froze because the process was out of memory.

- [x] A lone `&` is refused. The splitter stops if it cannot move forward, and a command is refused past 64 KB or 4,096 parts.
- [x] A failure in the shell is returned as text. It does not kill the agent thread.

# DVINE's own shell (0.99.17)

The shell runs inside the app. Termux is not required and is not opened.

- [x] `ShellSession` runs these commands for real: cd, pwd, ls, mkdir, rmdir, touch, cat, cp, mv, rm, find, grep, echo, printf, head, tail, sort, wc, chmod, date, env, which, clear, help, unzip.
- [x] Chains `&&` `||` `;`, pipes `|`, redirects `>` `>>` `<`, and `$NAME`. Paths stay inside the workspace. `..` and `rm -r` are refused.
- [x] The workspace folders (projects, scripts, downloads, temporary, memory, logs) are created on the device.
- [x] Chat and Ibiko's `run` tool both use this shell. A program that is not bundled (python, node, git, pkg, a compiler) returns exit 127. It is not pretended and not sent to Termux.
- [x] The terminal screen is `chat/ui/TerminalActivity`. Tools › Commands on this device opens it. Run, Stop and Clear use the same shell.
- [ ] No BusyBox binary and no JNI process bridge. Python, git and compilers are not bundled.

# MainActivity split (0.99.15)

The order is in `docs/DVINE_MASTER_BUILD_HANDBOOK.md` §10. The canvas is the next piece after the drawer, the tools sheet, the model menu and DVINE 1.

- [x] Ivie's 3D canvas and its full-screen mode live in `main/IvieCanvas.java`. `MainActivity` still decides when to show it, and Back still leaves full screen first.
- [x] The social order (connect, help, post, search, trending) lives in `main/SocialFlow.java`. The screen still speaks, opens the sign-in pages, and runs the search.
- [ ] The gateway routes and `carryOut` are still in `MainActivity`.

# PLAN — DVINE independent learner (per memory/projects/independent-learner/DESIGN.md)

Design is settled. This checklist is the build order; tick each item when its test passes.

- [x] **T1 — T7 confidence.** `LearnerFacts`: fact strength `n` and confirmation time `t` as two extra tab columns on the existing `facts` file, backward compatible (3-column lines load as strength 1). Rules: first sight 1, repeat +1 (cap 5), contradiction −2 then drop at 0, accepted answer +1, corrected answer −2, 30-day decay −1. Answer from strongest with n ≥ 2; tie within 1 → "which is right?".
- [x] **T2 — T8 feedback.** Correction within two messages *and* mentioning the answer → −2 and a miss; acceptance → +1 and a hit. `learner-answers.jsonl` ring buffer of 2000. Open-question queue `learner-open.json`, cleared when a fact reaches n ≥ 2; asked 3× with no fact → logged as unknown.
- [x] **T3 — T6 shape learner.** Candidate values (proper noun, quoted phrase, number/date, noun phrase after a known verb) → shape with the value masked; caps: ≤2 words each side, no wildcard-heavy shape, no question/negation, 200 rules, 4000 pattern chars, PII masked first. Promote at seen ≥ 6, hit/seen ≥ 0.5, rejected/seen < 0.4; prune at seen ≥ 20 & hit/seen < 0.25 or 30 days idle past 60 days old. File `learner-rules.json`.
- [x] **T4 — T9 consolidation.** `NightLearnJob` review pass, ~2s CPU budget: promote/prune, decay >30d, merge duplicate objects, reorder profile brief by strength, one `LearningLog` paragraph.
- [x] **T5 — T10 score.** `score = 100*(0.45*A + 0.25*K + 0.15*R + 0.15*U)`, A Laplace-smoothed `(correct+1)/(answered+2)`. Render with the four counts (shown in "what have you learned", Memory, Settings and the Workload sheet).
- [x] **T6 — wiring.** Feed the message pipeline (Facts/Knowledge/PilotProfile path) into the learner; `clear()` wipes rules/answers/open queue too.
- [x] **T7 — tests.** Plain JUnit under `app/src/test/java/ai/ibiko/learn/`: smoothing, contradiction, correction window, queue fill/clear, rule promote/prune, PII mask, backward-compatible load. Run `./gradlew testDebugUnitTest` if available.

Tests live in `app/src/test/java/ai/ibiko/learn/`. No new dependencies.

# Social Intelligence System (0.99.2)

Design and status: `docs/SOCIAL_INTELLIGENCE.md`.

- [x] M1 Repository audit: `social/Platform`, `SocialWeb`, `SocialLimits`, `SocialPage`, `SocialAccounts` and the `social.*` connector tools exist and are reused.
- [x] M2 Data models (`SocialPost`, `SocialProfile`, `SentimentReport`, `InvestigationReport`) and `SocialCapabilityRegistry` (9 capabilities, availability, JSON).
- [x] M3 Provider framework: `SocialProvider`; `RedditPublicProvider` (public API), `AccountProvider` (the user's signed-in X, Instagram, Facebook, Reddit), `UnsupportedProvider` (TikTok). Credentials stay in `SocialWeb`.
- [x] M4 Post investigation (`ClaimExtractor`, web evidence per factual claim, earliest close match as possible original).
- [x] M5 Similar posts (`SemanticPostMatcher`: TF-IDF cosine, duplicates removed).
- [x] M6 Sentiment (`SentimentReport`: negation-aware word list, themes, concerns, questions, sample disclosure).
- [x] M7 Own profile (`social.profile.read_own`, the user's sign-in; no business insights).
- [x] M8 Public profile (`social.profile.read_public`; private accounts reported, not bypassed).
- [x] M9 Chat integration: the brain's code `§SI`, told only when the chat's Social accounts tool is on; results back to the same brain.
- [~] M10 Security and tests: `SocialPrivacyGuard`, `SocialIntelTest` with fake providers. Not yet run against live platforms from a device (Reddit refuses the build server's network).
- [ ] Dashboard; official API adapters (need the user's registered apps).

# Checked against the code (0.96.4)

- [x] Unit tests compile and pass: 0.96.3 brought `CrewTest`/`PlanTest` back in line; the full suite runs on every release.
- [x] The APK builds with the native engine. The ready-built engine in `jniLibs` was from 0.85.0 (KleidiAI on, the wide batch), so the 0.96.2 "stop pinning every core" C++ never shipped; it is refreshed now (`gradle --no-configuration-cache :app:refreshNativeEngine`).
- [x] Large API models: thinking models outside GPT-5/o-series/R1 got a short-answer allowance and came back empty; a reply was cut at 3 minutes of writing; the connection gave up after 180 s of silence. Fixed in 0.96.4 (see `docs/BUILD_STATUS.md`).
- [x] PR #140 conflicted with main: its `EngineContext` rewrite removed `decide()` (used by the tests and the offline memory sizing) and nothing called its replacement; its other changes were already on main. The useful `RemoteBrain` parts are in 0.96.4.
- [ ] Items below marked "not in the repo" were written as done but no code for them exists. Treat them as open.
- [ ] `T20c.kt` and `T20main.kt` are not in the repository (already gone).


# Model Registry and task/brain tiers (0.96.0)

- [ ] (not in the repo, checked 0.96.4) `config/model-registry.toml` at the repo root: models (id, name, provider, api,
      contextWindow, reasoning, needsApiKey), agents (id, name, brain, provider, maxSteps,
      maxTokens, side, description), providers (id, api key env, docs).
- [ ] (checked 0.96.4: `ModelRegistry` is a fixed table of three agents, Obi 20 steps, Ivie 20, Ibiko 40; no file, no `Toml` reader) `app/src/main/java/ai/ibiko/config/ModelRegistry.java`: reads that file from the app's own
      files (or `~/Dvine/config/`), copies the bundled one on first launch, parses TOML with a
      small `Toml` reader (`T[]` arrays of tables, `#` comments, quotes, escapes).
- [ ] (no `ModelBrain` class, checked 0.96.4) `ModelBrain`: one object per agent row, so the crew runs several brains at once.
- [x] `Tuning.TASK_TIER` and `Tuning.BRAIN_TIER`: both default to 0 = `auto` (the old behaviour).
- [x] `MainActivity`: a heavy job takes the crew workers, side and step count from its own agent
      row, not from the model's row; a message inside a run goes to the active agent (the step's
      brain when it is one) instead of a new brain; the registry shows in the run line.
- [ ] The unit tests were not run in this pass: the new record test compiles here, and the full
      suite runs at the next build.
- [ ] Not timed on a phone in this pass.
  with `side > 1` it splits the job into parts and runs them on a fixed thread pool
  (`Executors.newFixedThreadPool(min(side, parts))`); with `side == 1` it runs them one after another.
  Parts are capped at `Crew.MOST = 5` (also the number written into the planner prompt).
- `MainActivity` (line ~2196): `crew = Crew.run(..., side ? 3 : 1)` where `final boolean side = model.remoteOn()`.
  → **multi-worker only ever ran with an API model**, always 3, not adjustable. That is the gap.
- `LocalModel.task(...)` is `synchronized` (one request on the phone model at a time), while
  `LocalModel.remoteTask(...)` is the API path with no lock. So several workers on the phone model are
  **safe**: the extra workers simply queue on the model's lock between their steps.
- `Tuning` is the user-facing knob list (Settings › Advanced); each `Knob` has a key, a label, a
  sentence of help, a default, a min and a max, and `Tuning.ALL` drives the Settings UI.

## Steps

- [x] 1. `Tuning.WORKERS` — new knob `adv_workers`, "Mini agent workers at once", 1..8, default 4,
      added to `Tuning.ALL` so it shows up in Settings › Advanced.
- [x] 2. `MainActivity` — read the knob (`final int workers = Math.max(1, Tuning.WORKERS.get(prefs))`),
      build the worker `CodeAgent` when `workers > 1` (was `side`), and pass `workers` to
      `Crew.run(...)` instead of `side ? 3 : 1`.
- [~] 3. (0.96.4: `Crew` is one agent again — `plan(workers)` sets how many parts the plan may have, the parts run one after another; there is no thread pool) `Crew` — honour a count above `MOST`: the planner prompt must be able to ask for up to
      `max(MOST, side)` parts, so 8 workers are not silently 5. Keep the pool size `min(side, parts)`.
      Done: `Crew.plan(workers)` returns `max(MOST, workers)` and `planPrompt(workers)` writes that
      number; `run` passes `plan(workers)` to the planner and keeps `min(side, parts.size())` for the pool.
- [ ] 4. Tests — run the existing agent tests (`CrewTest`, `AgentTest`) and add one that runs
      `side > 1` and checks every part comes back, in order, and that `side == 1` still works.
- [ ] 5. Compile check in Termux (`javac` over `app/src/main/java`, generated `R.java` + AndroidX
      jars from `~/alljars.txt`) — 0 errors.
- [ ] 6. `git status` → commit → push / PR.

## Rules

- Behaviour change is limited to the worker count; the crew's split/report/write flow stays as it is.
- One `write_file`/`edit_file` per change, read the file back, then test — never claim a compile or
  a push that the tool did not report.
- `run` only has toybox; `javac`, `java`, `git` go through the `termux` tool (project copied in,
  command runs inside it).


# Scribe and adaptive run mode (0.94.0)

- [x] Limitation audit in `docs/limitation-audit.md`. Class A timers widen with the run mode. Class B and C limits stay.
- [x] `RunMode` (quick, balanced, deep, extended, autonomous) drives reply watch, shell, Termux, round length, job clock and clone file size.
- [x] Settings knob `adv_run_mode` (default balanced, so existing phones keep today's limits).
- [x] `Scribe` writes `memory/projects/<job>/` (plan, map, progress, checkpoints, next actions) and a `TaskStateRepository` record. Tokens are redacted. A reply that says "continue" stays paused.
- [x] Carry-on dialog shows the open Scribe record.
- [ ] Native llama.cpp KV-cache and thread affinity were not retuned on a device in this pass. The thread knob already exists.
- [ ] A separate on-screen dashboard activity was not added. Progress is the Scribe files plus the carry-on line, so the chat layout stays as it is.

The full directive's phases that need a device (Termux sessions, GitHub push of a real repo, JNI benchmarks) are not claimed as tested here. The unit test covers the record, redaction, and the deep-mode budgets.


# Project survey and resume (0.94.1)

- [x] `ProjectSurvey` reads the workspace (capped at 2,000 files, skips `memory`, `.git`, `node_modules`, `build`) and names languages and build files.
- [x] Scribe writes the plan and the map from that survey. An Android tree is called an Android project. An empty folder is not treated as a project to replace.
- [x] Starting the same instruction again resumes the open job. The plan file is kept. The map is read again.
- [x] The workload sheet lists open and recent jobs with the next outstanding step.
- [x] The agent is told to read `PROJECT_PLAN.md` and `PROJECT_MAP.md` before creating files.
- [ ] Native llama.cpp KV-cache and thread affinity still need a device.


# Recovery against the files (0.94.2)

- [x] `Reconcile` checks every path a job claims. A path with `..` or outside the workspace counts as missing.
- [x] Scribe marks a job completed only when every claimed file is on disk. A long reply is not enough.
- [x] A missing file is written back onto the outstanding list and into `RECOVERY.md`.
- [x] Resuming reads the claims again. A file that has since appeared is counted; one that has not stays outstanding.
- [ ] Native llama.cpp KV-cache and thread affinity still need a device.


# Termux sessions (0.94.3)

- [x] A Termux command from Ibiko is written down before it starts. If the app dies, the session stays RUNNING and is not called finished.
- [x] A wait that ends while the command may still be running is TIMED_OUT. The reply says it is not verified and was not resumed mid-way.
- [x] Secrets in the command text are stripped from the session file.
- [x] The allow-listed planner now knows install Python, install Node, git status, installed packages, Gradle or Python tests, an Android compile, unzip of one named zip, and clone of one https address. Install, compile, unzip and clone need confirmation. A zip path with `..` and a clone URL with a password are refused. Push is not guessed.
- [x] The workload sheet shows an unverified command when one is open.
- [ ] A live Termux process still cannot be resumed at its instruction pointer. The record is the recovery.


# The 10-minute first-word cutoff (0.94.4)

- [x] The cutoff was timed from the start of the job, and every phase change ("Reasoning", "Replying") set the word count back to zero. After ten minutes a live job looked like it had never started, and the screen posted "didn't start answering in 10 minutes" and cancelled it.
- [x] A job is stopped for silence only when there are no words, the model is not inside a call, and no step has arrived for the whole wait. Reading the prompt is not a failure. A reasoning ping or "Explored 1 file" resets the wait.
- [x] The native watch no longer cancels a call that has not produced its first token yet.


# Streaming file access (0.94.5)

- [x] `read_file` reads a line at a time. A request for three lines of a 5,000-line file does not hold the whole file as one string, and the reply still says how long the file really is.
- [x] `search` walks each file a line at a time instead of loading it whole.
- [x] `search` with `query="name:File.java"` finds a file by name and does not open its contents.
- [x] Zip extraction already refuses entries that would write outside the folder. That was left as it is.


# Git calls that finish only when GitHub says so (0.94.6)

- [x] Clone, pull and push write a record before they talk to GitHub. The record becomes finished only when the call returns. If the app stops in the middle, git status says the call was never confirmed and is not done.
- [x] A failed push is recorded as failed, not as pushed. The chat says "Pushing" before the call and does not say "Committed and pushed" until the tool result comes back.
- [x] git diff shows the changed lines. git log shows recent commits. git branches lists the branch names. None of these download the repository again.


# Composer and file transfer (0.94.7)

- [x] The text field no longer folds when a file is attached. The chip row is held to the screen width, and the field keeps its own height.
- [x] The on-device model receives the words taken out of an attached file, not only the file's name. A later turn keeps a short excerpt.
- [x] An API model still gets a PDF, a spreadsheet or an mp3 as the file itself, and also the extracted words. A video goes as its frames, not as a raw video part the API refuses.

# Command authorization (0.94.8)

- [x] Pause, resume, cancel and stop of a task go through DvineCommandGateway. Chat, model text, code, documents and a spoken reply do not.
- [x] The send button still stops the current reply. A long-press on the microphone opens command mode for one message, which is the only spoken path that can pause, resume or cancel a task.
- [x] Saying "stop" or "pause" in ordinary conversation no longer stops the work, the download, or the body.


# Fetch main and merge on the phone (0.94.9)

- [x] `git fetch` lists main (or another branch) on the phone. It downloads file ids, not the files, and it does not change the folder.
- [x] `git merge` brings that branch in. The same branch is a pull. Another branch is a three-way merge: edits that do not overlap are combined on the phone, and overlapping edits stay as conflict markers plus a `.theirs` copy.
- [x] A push is refused while a `.theirs` file or conflict markers remain. The branch pointer is not moved, so a clean merge shows up as a local change to push.
- [x] "fetch main and merge", "merge origin/main" and "update from main" run on the phone with no model call when there is one clone, or the sentence names the folder.
- [x] The coding model is told the phone is its suite: list, read, search, edit and git run on the device, and a merge returns only the conflict pieces.


# 0.95.5 limitations audit and the first fixes

- [x] `docs/LIMITATIONS.md` writes down every limitation found in this pass, ranked P1 to P5, with what stops it and the smallest next step.
- [x] A heavy job calls `Crew.run` with its arguments in the order the current `run` takes, so the first step no longer reads as "already done" and starts the model.
- [x] `controlWord` returns the word, not a boolean, so the caller can print the word it stopped on.
- [x] The silence cutoff only applies when the job has no text at all, does not reset itself on a phase change, and the native watch does not cancel a call that has not produced its first token.
- [x] A reply over 16,000 characters is streamed a chunk at a time, and a reply over 2,000 characters says how long it is before the chunks start.
- [x] A local PDF, spreadsheet, email, or plain text can be read by the tools, and the tool list says so.
- [ ] (no Download action for `make_file` found, checked 0.96.4) A document built with `make_file` is saved and shared through a UI action, not only handed back to the model. Done: `Download` saves it to `Downloads` with a share link and a toast.
- [ ] (no `TermuxActivity`, checked 0.96.4; the workload sheet shows an unfinished command's line) A Termux session is watched on screen, so a command that is still running can be seen, stopped, and resumed. Done: `TermuxActivity` lists RUNNING and STALE sessions, shows the command, can cancel one and relaunch it; the workload sheet points there.
- [x] The chat shows which model and which provider are in use, so an empty or wrong reply can be blamed on the right one. Done: the toolbar line shows the brain and provider, and the offline banner names the brain.
- [x] The wording around connection and account: the app works and answers on its own, and without the connection the app uses the small local model for free, with no account required. The connection and account are what unlock the capable cloud model, not the local one.
- [ ] Native llama.cpp KV-cache and thread affinity were not retuned on a device. The thread knob already exists.
- [ ] The unit tests were run in this pass as a compile of the new record test; the full suite runs at the next build.

Additions from the 0.95.6 audit (checked 0.96.4: `ToolTimeouts` and `ProviderCredentials` are not in the code, so none of this landed): `ToolTimeouts` runs the command on a daemon thread so a download, a kill, or a cancel reaches the phone inside the timeout; `ProviderCredentials` now covers the GitHub token, the Dvine key, the OpenAI key and the session token, and `Scribe` strips them from every line it records; the on-disk record uses the local model path in the app's own files. `T20c.kt` and `T20main.kt` at the workspace root are stray test files from an earlier run and are not part of the app; they should be deleted with the user's consent.

The full directive's phases that need a device (Termux sessions, GitHub push of a real repo, JNI benchmarks) are not claimed as tested here. The unit test covers the record, redaction, and the deep-mode budgets.
