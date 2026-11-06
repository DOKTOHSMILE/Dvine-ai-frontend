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
