# Build status — Track M (Android)

Status words: NOT STARTED / IN PROGRESS / BLOCKED / VERIFIED. Plan: `DVINE_MASTER_BUILD_HANDBOOK.md`.

| Stage | Status | Evidence | Next |
|---|---|---|---|
| M0 Toolchain | VERIFIED | `gradle assembleDebug` passes on SDK 35 / build-tools 35 (cloud session, 2026-10-26) | — |
| M1 Logo + icons | VERIFIED (build) | `tools/make_logo.py` → `docs/brand/dvine_logo_2048.png`, `mipmap-*/ic_launcher*.png`; manifest references them | check on a launcher |
| M2 Nano-dot faces and body | VERIFIED (JVM) / device pending | v1.4: sculpted dot heads (`DotHead`), posable anatomy body with 13 poses (`DotBody`), 3D drawings and objects (`Forms`); `NanoSwarmTest` (7 tests: even surface sampling, every form fits and settles, every pose on the floor, speech/blink, drawings have depth); off-device render `docs/brand/face_preview.png` | frame-rate check on a mid-range phone |
| v1.5 Living body | VERIFIED (JVM) / device pending | head-first, zoom-out to body on request; 12 exercises and stunts; talking gestures; 4 acted scenes (`brain/Scenes`); body-part taps; joint limits (`MotionTest`, 5 tests) | watch the zoom and frame rate on a phone |
| M3 Zero-screen stage | IN PROGRESS | `StageView` replaces `MatrixStageView`: no buttons; swipe, tap, drag, long-press; online dot; subtitle; sparkles; sketch canvas | device screenshot |
| v1.1 Confirm-to-act | VERIFIED (JVM) | `ActionsAndOnboardingTest`: share asks → yes → hand-off intent; copy → clipboard; no cancels; money refused | try on device with WhatsApp/X |
| v1.1 First-run name | VERIFIED (JVM) | `Onboarding` parse tests; greeting uses name | device run |
| v1.1 Hands + gestures | VERIFIED (JVM) | `HandsTest` (41 gestures finite); render in `docs/brand/face_preview.png` | tune poses on device |
| v1.1 Sketch canvas | VERIFIED (JVM) | `HandsTest.sketchesHaveStrokes` | more drawings |
| v1.1 Voices + ambience | IN PROGRESS | `TalkBridge` en-NG voice ranking; `AmbientPad` synth | listen on a phone with Google TTS en-NG |
| v1.1 Offline up to 1 GB | IN PROGRESS | `KnowledgeStore` merges `shards/*.json`; SQLite shards planned (M10) | shard download flow |
| M4 Offline brain | VERIFIED (JVM) | `OfflineBrainTest`, `CalculatorTest`, `MiniJsonTest` | airplane-mode demo on device |
| M5 Learning journal | VERIFIED (JVM) | `LearningJournalTest`; v1.2 general facts, answer-after-unknown, corrections override pack (`ThoughtsAndLearningTest`) | — |
| v1.2 Basic knowledge | VERIFIED (JVM) | core pack v2 (142 facts, 45 wisdom); WordNet dictionary shard 20,775 words; nearest-meaning pivot | device load time |
| v1.2 Inner thoughts | VERIFIED (JVM) | THINK lines with text, whisper voice in `TalkBridge.whisper`; toggle by voice | listen on device |
| M6 Connectivity + sync | IN PROGRESS | `Connectivity`, `KnowledgeStore`, `SyncWorker` compile; inert while `api_base` is empty | needs Track S endpoints S-K, S-L |
| M7 Online client | NOT STARTED | — | after S-K/S-L |
| M8 Voice polish | NOT STARTED | — | — |

Commands run: `gradle testDebugUnitTest assembleDebug` → 54 tests passed (incl. Robolectric LaunchTest), APK built (v0.6.0).
Not run: `lintDebug` (dependency download rate-limited in the sandbox), any on-device test.

Known limits:
- No device or emulator test yet; frame rate of the nano-dot swarm (up to 10k dots) is unmeasured.
- Offline brain is rules + retrieval over the pack, not a language model; free-form reasoning needs the server.
