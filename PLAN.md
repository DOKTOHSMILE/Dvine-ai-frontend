# DVine engineering plan

## Master autonomy upgrade checklist

- [x] Baseline audit documented in `docs/MASTER_LIMITATION_AUDIT.md` before feature changes; audit is explicitly preliminary.
- [x] Existing `Scribe` is reused; no second task manager or memory store introduced.
- [x] Scribe now refuses to complete tasks on file existence or narrative claims alone; it requires a structured successful verification result and reconciled claimed files.
- [ ] Wire Scribe verification evidence to actual build/test/command results, without allowing model prose to assert success.
- [ ] Test Scribe failure, recovery, file verification and successful evidence paths.
- [ ] Exhaustively search tracked Java/C++/Gradle/resources for the directive's limit keywords and classify results.
- [ ] Implement progress-aware inference watchdog and resumable output policy, retaining explicit user cancellation and resource safeguards.
- [ ] Implement durable Termux command/session tracking within Termux/Android supported APIs.
- [ ] Improve streaming file transfer and SAF permission recovery.
- [ ] Add resource-pressure-aware scheduling and tests.
- [ ] Verify GitHub pagination/large-file/conflict/rollback behavior.
- [ ] Build/test JVM, Robolectric, Android and ARM64 native targets; record unavailable hardware/external integration tests honestly.
- [ ] Update architecture, benchmark and delivery reports only as each item is verified.

## Constraints that remain intentional

- Model context, generation, memory, CPU and device thermal limits are real architecture/device limits; don't raise blindly.
- Android may terminate background processes. Use supported foreground services/WorkManager and persistent checkpoints; don't promise uninterrupted execution.
- Termux processes that exit or lose their owner cannot resume their instruction pointer; recover through logs, idempotent commands and checkpoints.
- Keep app sandboxing, scoped storage, credentials, command authorization, destructive confirmation and provider/OS policy.
