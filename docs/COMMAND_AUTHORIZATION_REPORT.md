# Command authorization gateway engineering report

## Finding

Task interruption was previously decided from arbitrary recognized speech. `AssistantCore.hear()` passed every transcription to `VoiceInterruptionManager.decide()`, whose regular expressions matched natural-language text and selected pause, resume, cancel, or task-switch actions before ordinary conversation routing. This made a text matcher an authorization mechanism. Model response handling was a separate path, but the single shared task-control policy was not enforced at a dedicated typed authorization boundary.

## Correction

- Added `DvineCommandGateway` as the typed boundary for pause/resume/cancel/stop requests. It accepts task IDs and enumerated actions, validates task existence and state, checks the source capability, and requires confirmation for destructive commands.
- Added separate dedicated-button and explicitly activated, one-use voice-command paths. Merely receiving microphone text does not activate command mode. Model-originated control requests are rejected.
- Ordinary `AssistantCore.hear()` now routes conversational input to the orchestrator without consulting a text-based interruption recognizer. The legacy `VoiceInterruptionManager` is retained only for source compatibility and no longer interprets any text.
- Control targets are task-specific. Cancellation is routed to the selected task; other running tasks are unaffected. `STOP` is registered but fails closed because the existing task lifecycle has no distinct stop semantics; it is not silently aliased to cancellation.
- Added regression coverage for conversational/hypothetical/quoted/code text, model self-authorization rejection, explicit voice mode, confirmation, and targeting among multiple tasks.

## Files changed

- `app/src/main/java/ai/dvine/ops/task/DvineCommandGateway.java` (new)
- `app/src/main/java/ai/dvine/ops/AssistantCore.java`
- `app/src/main/java/ai/dvine/ops/task/VoiceInterruptionManager.java`
- `app/src/test/java/ai/dvine/ops/DvineCommandGatewayTest.java` (new)

## Verification status

Automated tests have been added but were not executed in this environment. No claim is made that the full acceptance criteria are met: dedicated Android button and voice-mode UI wiring, all model/agent/JNI entry paths, STOP lifecycle semantics, and repository-wide searches/build verification remain to be integrated or verified. The gateway is in place at `AssistantCore`, and this report deliberately distinguishes implemented changes from outstanding integration work.
