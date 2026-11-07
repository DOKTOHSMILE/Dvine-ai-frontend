# Command authorization gateway engineering report

## Finding

Task interruption was previously decided from arbitrary recognized speech. `AssistantCore.hear()` passed every transcription to `VoiceInterruptionManager.decide()`, whose regular expressions matched natural-language text and selected pause, resume, cancel, or task-switch actions before ordinary conversation routing. This made a text matcher an authorization mechanism. A second bypass existed in `MainActivity`'s `DirectCommands`: an AI classifier could return a `STOP` label that invoked `stopGeneration()` and interrupted the active response.

## Correction

- Added `DvineCommandGateway` as the typed boundary for pause/resume/cancel/stop requests. It accepts task IDs and enumerated actions, validates task existence and state, checks the source capability, and requires confirmation for destructive commands.
- Added separate dedicated-button and explicitly activated, one-use voice-command paths. Merely receiving microphone text does not activate command mode. Model-originated task-control requests are rejected.
- Ordinary `AssistantCore.hear()` now routes conversational input to the orchestrator without consulting a text-based interruption recognizer. The legacy `VoiceInterruptionManager` is retained only for source compatibility and no longer interprets any text.
- `DirectCommands` no longer treats a model-produced `STOP` classification as authorization to interrupt generation. The dedicated Stop UI control remains the explicit interruption path.
- Control targets are task-specific. Cancellation is routed to the selected task; other tasks are unaffected. `STOP` is registered but fails closed because the existing task lifecycle has no distinct stop semantics; it is not silently aliased to cancellation.
- Added regression coverage for conversational/hypothetical/quoted/code text, model self-authorization rejection, explicit voice mode, confirmation, and task targeting.

## Files changed

- `app/src/main/java/ai/dvine/ops/task/DvineCommandGateway.java` (new)
- `app/src/main/java/ai/dvine/ops/AssistantCore.java`
- `app/src/main/java/ai/dvine/ops/task/VoiceInterruptionManager.java`
- `app/src/main/java/ai/dvine/main/DirectCommands.java`
- `app/src/test/java/ai/dvine/ops/DvineCommandGatewayTest.java` (new)

## Verification status

Regression tests have been added but were not executed in this environment. Full Android build/test verification is outstanding. Dedicated UI wiring for task-specific controls and voice-command mode, STOP lifecycle semantics, and audit of all model, agent, JNI, and specialized-agent paths remain to be integrated or verified. Accordingly, this change does not claim that every final acceptance criterion is complete.
