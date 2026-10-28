## Personas

The three voices are Obi, Ivie, and Ibiko. "EV" is treated as Ivie, because that is how speech recognition shortens her name. Aka is not a persona in this codebase; the app asks which of the three you mean.

## Date and ordinary questions

"What is today's date?" is read from the phone clock. It is not an edit, and it is not the line "I have not changed anything." Other questions, including news, go on to the existing brain. This build does not add a separate news provider and will not invent headlines.

## Files

"Open a document", "select an image", or "examine a video" opens the system picker. The app reads the file the phone returns. An image is shown. A PDF shows the first page and says the words were not extracted. A video shows one frame if the phone can decode it. "Learn from this" stores a note in the learning journal. That is not model retraining.

## Tool path (0.27.0)

This build adds one task lifecycle and a checked tool path. It does not replace the pod, the offline brain, or the existing 3D board.

## What you can say

- "Check my phone's available storage and explain whether I have enough room for this project."
- "Pause this task." / "Resume the motorcycle model." / "Cancel the current task."
- "Find the motorcycle image in my project folder and show it to me."
- "Use that motorcycle image to build an editable 3D model."
- "Remove the front wheel, but leave the fork, brake and the rest untouched."
- "Connect to my development server." / "Inspect the latest application logs and explain the error."

Holding the microphone does not cancel a task that is already running. A new sentence is read first. Only pause, cancel, or a switch stops that generation.

## Termux

The app looks for the Termux package and the `com.termux.permission.RUN_COMMAND` permission. It will not invent a shell path.

Until a result bridge is connected, a command sent to Termux is **not** reported as finished. Read-only checks such as storage fall back to the phone's own storage report and say so.

Commands are an allow-list of argument lists. A sentence is never pasted into `sh -c`.

## SSH

SSHJ is the library to use for a real socket (`com.hierynomus:sshj:0.39.0` or newer). This build keeps the session, host-key check, and credential seal behind `SshTransport` so a wrong host key is refused and a password is never written into a task record or a spoken line.

There is no live SSH socket in this commit. Without a configured transport the app says it has not connected.

Save a profile only through `SshCredentialStore` with a strong seal. The Android Keystore seal is the right production seal; a seal that is not strong throws instead of writing the secret in the clear.

First connection requires the host-key fingerprint to be accepted. A later change is a failure.

## Images and 3D

Image type is read from the file header (PNG, JPEG, WebP). Filename hints are marked uncertain when no detector is loaded. The app will not claim it saw parts it did not measure.

A single photo becomes a named, editable approximation (GLB node names can also be imported). Hidden surfaces and real measurements are not invented. The solid is drawn on the existing board.

## Desk

`Dvine-ai-backend` accepts authenticated `POST /api/v1/interpret` and `POST /api/v1/research`. Research text is untrusted. Commands found in a page are removed from the note and are not executed.

## Tests run here

`OpsPipelineTest` — 26 tests, JUnit 4, no device and no Gradle:

```
javac … ai/dvine/ops/**/*.java
java org.junit.runner.JUnitCore ai.dvine.ops.OpsPipelineTest
```

Not run here: `gradle testDebugUnitTest`, `assembleDebug`, `mvn test`, a phone, a live SSH server, Termux on a device. Those remain the checks that have to pass on your machine before this is called verified on Android.
