# Deep analysis & multi-worker mini agents task tracking

## Completed Audit & Fixes History

- **Model connection analysis (`docs/model-connection-analysis.md`)**: Mapped `RemoteBrain`, `ProviderStore`, `Providers`, session tokens, call sites, local brain vs. remote paths, connectivity, and tests.
- **LocalModel fixes (`ibiko/localmodel-fixes`, PR #91)**: 
  - Orphaned Javadoc moved/relabeled above `warm()`, `forget()`, and `remoteTask`.
  - Watchdog documentation updated to match constants (`IDLE_MS` 60s, write cap 180s, `READ_MS` 600s); timeout reason now survives `cancel()`.
  - Failover `problem` guarded at both fallback sites so stale API problems don't misreport phone model failures.
- **Round 2 & 3 (Voice, Dictation, Termux & Tools)**:
  - Dictation updated as a hold-to-continue session (stopped explicitly via toggle).
  - Voice pitch and robotic edge softened via `VoiceTuning.warm()` and updated Obi/Ivie defaults.
  - Run tool prompt clarified (one command per tool call; `a && b` treated as a single command).

---

# Task: multi-worker mini agents, on every build

User request: *"every build let me run the multi workers — that's the main feature I want enabled."*
So: the crew of mini agents must be able to run several workers **at once**, on **every** build of
the app — with the phone's own model as well as with an API model — and the number of workers must
be something the user controls, not a hidden `remoteOn()` switch.

## What the code does today (read before changing)

- `ai.dvine.agent.Crew.run(brain, helper, task, progress, stopped, int side)`:
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
- [x] 3. `Crew` — honour a count above `MOST`: the planner prompt must be able to ask for up to
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

