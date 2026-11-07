# DVine master-engineering limitation audit — preliminary, before new implementation

**Audit date:** 2026-11-07  
**Scope:** source and existing engineering documentation surfaced for the DVine Android repository before work on the master-engineering directive.  
**Status:** preliminary baseline; not a claim that every source file, native dependency, device configuration, or runtime path was exhaustively audited.

## Executive summary

DVine already has meaningful groundwork: RunMode presets, persistent file-backed task records, a Scribe project record, project survey/reconciliation, foreground model/download services, cooperative model cancellation, a Termux bridge, provider fallback and paginated GitHub repository listing. The request therefore calls for integration and verified gap-closing, not a second task manager or a duplicate memory subsystem.

The principal verified gaps at this stage are:

- Fixed watchdog ceilings still terminate live inference in all run modes; long budgets are not unbounded and stall detection is timer-based.
- Native inference is CPU-only in the app wrapper and requires explicit model/token/context budgets. Device memory fallback is bounded; context limits are architectural/resource safeguards, not safe to remove blindly.
- Termux uses a synchronous result wait, a 180-second default, and a 600 KB compressed project-transfer limit. A timed-out process is not recoverable mid-command.
- The task state repository is atomic file-per-task storage, not SQLite. Scribe provides project records and checkpoints, but is a Java service integrated at the Ibiko agent call site rather than a separately scheduled process.
- Existing Scribe verifies claimed files' existence, but the inspected completion code does not establish that build/test/command acceptance criteria passed before marking the record complete.
- Android foreground execution is best effort, and cannot guarantee an unrestricted process survives arbitrary OS termination. WorkManager/foreground services and durable checkpoints are supported alternatives, not guarantees of uninterrupted work.
- The current DirectCommands/model-classification path and legacy app controls need a repository-wide follow-up audit after the separate command-gateway patch; task-control authorization and this autonomy directive must remain separate security boundaries.

## Classification

A = artificial software restriction; B = security/resource safeguard; C = Android/OS policy; D = physical device limitation; E = network/external-provider limit; F = model architecture limit. More than one class can apply.

| Area and observed control | Evidence/behavior observed | Classification | Audit disposition |
|---|---|---|---|
| Local-model idle/write/read watchdogs | `LocalModel` has 60 s default idle, 180 s default write cap, 600 s default read cap; RunMode overrides, including finite Autonomous budgets. | A, D | Candidate for progress-aware policy; retain genuine-stall/resource recovery, remove the fixed live-output wall cap only with a tested heartbeat policy. |
| Local generation budget and context | Native `Engine::reply` loops only to requested `max_tokens`; rejects prompt + requested output beyond loaded context. `PromptFit` and Java fitting reduce requests. | F, D, B | Preserve model/context safety. Add budget continuation/checkpoint integration rather than claiming unlimited model output. |
| Model loading and allocation | `LocalModel.load` tries configured context, then the 2,048-token fallback; C++ allocates model/context and retries KV cache format, CPU threads from Java. | D, F | Preserve graceful fallback. Benchmark actual devices before adjusting context/threads. |
| Native CPU execution | App wrapper sets `n_gpu_layers = 0`, uses C++ llama context, batch 512, slot count and cancellation flag; no measured benchmark data surfaced. | D, F | GPU/accelerator use cannot be inferred as available. Native optimization requires device/build benchmarks and JNI/native regression tests. |
| JNI streaming and cancel | JNI calls Java sink for token fragments; callback false ends generation; `nCancel` calls engine cancel. The JNI handle lifetime is shared between calls/cancel/free. | A, D | Audit concurrency/lifetime before changing; retain cooperative cancellation. Native synchronization and race tests remain open. |
| Output continuation | Remote provider continuation is capped at four rounds; local calls use finite token budget. | A, E, F | Candidate for resumable chunks; avoid unbounded loops/costs and preserve provider limits. |
| Termux time budget | `TermuxRunner.TIMEOUT_MS` default is 180,000 ms; timed-out work may still run in Termux and is explicitly not resumed mid-command. RunMode passes longer finite budgets. | A, C, E | Replace synchronous waiting with durable session/result tracking where Termux APIs permit. Never report a timeout as success. |
| Termux transfer size | `TermuxRunner.MOST_BYTES` is 600,000 compressed bytes; project is not copied above that size. | B, C | Replace with chunked/private-file transfer after protocol validation; preserve sandbox and bounded-memory behavior. |
| Shell command duration | Phone host uses a mode-specific finite shell budget. | A, D | Keep configurable budgets and process monitoring; don't run blocking shell code on UI thread. |
| Agent concurrency | `Crew` uses a fixed thread pool up to configured worker count; local model calls serialize on its lock. | B, D | Preserve resource arbitration; multi-agent concurrency cannot imply parallel local inference. |
| Long agent loop | MainActivity uses finite round count and finite work time, with RunMode budgets and Scribe checkpoints. | A, C | Add durable continuation and verify each stage; avoid promising process-lifetime execution. |
| Clone file count/size | Clone file count is user-configurable; GitHub file-size cap is mode-dependent and larger files are skipped/not loaded. | B, D, E | Keep bounded memory, pagination/streaming, and explicit skipped-file reporting. |
| GitHub operations | Existing integration uses API-mediated clone/push/PR operations and confirmations; repo listing pagination surfaced in existing audit. | B, E | Preserve auth, confirmations and rollback; large repository support requires paginated/streamed implementation plus tests. |
| Persistent task state | `TaskStateRepository` stores each task in a temp file, fsyncs, then renames; malformed records are skipped. It is not SQLite or a transaction across task+documentation files. | B | Existing durable baseline; improve cross-record consistency/atomic replacement and verify recovery rather than adding a second manager. |
| Scribe/project memory | `Scribe` writes Markdown/JSON plus task record, project survey, checkpoints, and reconciles claimed files. Completion may mark complete on file existence, not proven test/build results. | A, B | Extend events and evidence verification. Preserve readable formats and task repository ownership. |
| Conversation history/context | Local model retains a bounded number of turns and clips summaries/replies; native context is finite. Project records are separate from model history. | B, F | Keep separate persistent project memory; use summaries/retrieval, not unlimited prompt growth. |
| Background service | Reply/download services are foreground services/wake-lock users but lifecycle/process can still be killed; work recovery is user-visible at next start. | C, D | Use supported foreground-service/WorkManager paths for eligible work, durable checkpoints, and accurately report limits. |
| Filesystem access | App sandbox and selected SAF/URI content are used; access depends on granted permissions/persistable URI grants. | B, C | Do not bypass Android scoped storage; unify access around granted URIs and stream large files. |
| Network/provider | Remote model has provider/HTTP timeouts, limited retries/fallback, per-provider limits and finite windows. Connectivity loss is external. | E | Queue/retry idempotent work with status; do not treat provider limits as arbitrary local restrictions. |
| CPU/RAM/thermal/storage | Model size, context, number of workers, Termux, and Android process all consume finite resources; no runtime pressure benchmark was available in this audit. | D, C | Add resource-aware admission/checkpointing; no blind thread/context increases. |

## Existing architecture to preserve and integrate

- `DvineTaskManager` and `TaskStateRepository`: one task lifecycle and durable task records.
- `Scribe`, `ProjectSurvey`, and `Reconcile`: existing project-memory/planning/recovery foundation.
- `RunMode`: configured time/resource presets.
- `LocalModel`, `NativeLlm`, `dvine_llm.cpp`, `dvine_llm_jni.cpp`: local llama.cpp path and cancellation/streaming integration.
- `TermuxRunner`, `TermuxExecutionService`, `PhoneHost`: distinct Android/Termux shell paths.
- `GitHubGit`, existing connectors, and existing confirmation policies.
- Existing chat/workspace/project UI and agent board.

## Preliminary implementation checklist

- [x] Inspect existing limitation-audit document and representative model, native/JNI, task, Scribe, Termux, agent, provider and lifecycle implementations.
- [x] Record initial limits and classify the observed causes before the autonomy implementation phase.
- [ ] Exhaustively enumerate every occurrence of the requested search terms across tracked source and build files.
- [ ] Add progress/heartbeat-based inference execution policies and test local cancellation/continuation.
- [ ] Verify and improve Scribe completion evidence and event integration.
- [ ] Verify task/project recovery against actual filesystem state and interrupted writes.
- [ ] Add durable Termux session tracking/chunked transfer or document the specific Termux API limitation.
- [ ] Audit SAF URI retention and large-file streaming paths.
- [ ] Audit GitHub pagination, size limits, conflict and rollback paths.
- [ ] Add resource-pressure monitoring and scheduler behavior with device-safe fallbacks.
- [ ] Run JVM/Robolectric tests, Android compile, native/ARM64 build, and any available device benchmarks.
- [ ] Update `PLAN.md` with only verified completion states.

## Verification limits

The repository code-search tool returned incomplete results and some queries failed transiently. The reviewed source files and existing `docs/limitation-audit.md` provide a grounded preliminary inventory, not proof that every listed token or mechanism across the complete repository has been examined. No local Android device, Termux installation, downloaded model, GitHub test repository, or native benchmark runner is available through the current GitHub API tools; no runtime performance or end-to-end result is claimed here.
