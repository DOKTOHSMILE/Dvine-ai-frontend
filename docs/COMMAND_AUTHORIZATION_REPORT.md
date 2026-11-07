# Command authorization (0.94.8)

## Original trigger

`AssistantCore.hear()` used to pass every utterance to `VoiceInterruptionManager`, whose regular expressions turned "pause", "resume" and "cancel" into task-state changes. That matcher is now a no-op (pull request 126). Three other paths still treated the words as authority:

- The chat message gate paused or resumed speech when the message was exactly "pause" or "continue".
- `DirectCommands` carried out `COMMAND: stop` when the user's own message was exactly "stop", "cancel", "abort" or "be quiet", and that called `stopGeneration()`.
- `OfflineBrain` answered "stop" / "freeze" / "that's enough" with a body `STOP` command.
- "stop the download" called `hub.stopDownload()` from the sentence alone.

## Correction

`ai.dvine.ops.task.DvineCommandGateway` is the only component that changes a task. It accepts a typed action and a task id from two sources: a button event, or one voice-command session opened by the interface (`beginVoiceCommandMode`). Model JSON, code, documents and chat text are not sources. `STOP` still fails closed, because the task lifecycle has no separate stop state; it is not turned into cancel.

`AssistantCore.hear()` only talks. `AssistantCore.command(session, text, confirmed)` is the command-mode path. It reads the utterance with `CommandPhrasing` after the session is already open. Questions, quotes, "someone told me", "if I", JSON and code comments spend the session and change nothing. A named task is the only target. A vague resume asks which task. Cancel asks for a second command-mode "yes" before it runs.

On the phone, a long-press of the microphone opens that session for the next message. The send button still stops the current reply, because it is a button, not a word. Ordinary "stop" in the composer does not.

## Files

- `app/src/main/java/ai/dvine/ops/task/DvineCommandGateway.java`
- `app/src/main/java/ai/dvine/ops/task/CommandPhrasing.java` (new)
- `app/src/main/java/ai/dvine/ops/AssistantCore.java`
- `app/src/main/java/ai/dvine/MainActivity.java`
- `app/src/main/java/ai/dvine/main/DirectCommands.java`
- `app/src/main/java/ai/dvine/brain/OfflineBrain.java`
- `app/src/main/java/ai/dvine/ops/task/DvineTaskManager.java`
- `app/src/test/java/ai/dvine/ops/DvineCommandGatewayTest.java`
- `app/src/test/java/ai/dvine/ops/OpsPipelineTest.java`
- `app/src/test/java/ai/dvine/brain/ActionsAndOnboardingTest.java`

## Tests

`DvineCommandGatewayTest` covers discussion, code comments, quoted speech, hypotheticals, model JSON, streamed chunks, command-mode pause of one named task, cancel that waits for confirmation, and a question inside command mode. `OpsPipelineTest` now expects chat "pause" / "resume" / "cancel" to leave the task alone, and the command-mode path to change only the named task.

## Remaining

- There is no separate on-screen Pause or Cancel button yet. Command mode is a long-press on the microphone, then one message.
- Task `STOP` is rejected on purpose until the task controller has stop semantics. The send button stops the current model reply only.
- A new message still interrupts the reply already on screen, so the new message can be answered. That is not a task cancel.
- "stop spinning" still stops a shape animation. "stop" while a yes/no confirmation is open still means no. Neither one pauses or cancels a project.
- `CodeAgent` treats a reply that is only the word "stop" as the model echoing a word, and tells it to keep going. It does not stop the task.
