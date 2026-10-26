# Build status — Track M (Android)

Status words: NOT STARTED / IN PROGRESS / BLOCKED / VERIFIED. Plan: `DVINE_MASTER_BUILD_HANDBOOK.md`.

| Stage | Status | Evidence | Next |
|---|---|---|---|
| M0 Toolchain | VERIFIED | `gradle assembleDebug` passes on SDK 35 / build-tools 35 (cloud session, 2026-10-26) | — |
| M1 Logo + icons | VERIFIED (build) | `tools/make_logo.py` → `docs/brand/dvine_logo_2048.png`, `mipmap-*/ic_launcher*.png`; manifest references them | check on a launcher |
| M2 Nano-dot faces and body | VERIFIED (JVM) / device pending | v1.4: sculpted dot heads (`DotHead`), posable anatomy body with 13 poses (`DotBody`), 3D drawings and objects (`Forms`); `NanoSwarmTest` (7 tests: even surface sampling, every form fits and settles, every pose on the floor, speech/blink, drawings have depth); off-device render `docs/brand/face_preview.png` | frame-rate check on a mid-range phone |
| v1.5 Living body | VERIFIED (JVM) / device pending | head-first, zoom-out to body on request; 12 exercises and stunts; talking gestures; 4 acted scenes (`brain/Scenes`); body-part taps; joint limits (`MotionTest`, 5 tests) | watch the zoom and frame rate on a phone |
| v1.6 On-device engine | VERIFIED (JVM) | `engine/` (Supervisor, Skills, Command, Affect, WorkingMemory, Episodes, Engine): validated commands, priorities, stop, self-report, mood; episodes and favourites via the server (`EngineTest`, 8 tests) | device run; server episode store needs Track S V4 |
| v1.7 3D wireframe human | VERIFIED (JVM) / device pending | handbook §28: anatomical male/female meshes (`human/`), 37-bone rig with fingers, IK, collisions; face rig and visemes; ray touch on 45 body parts; self-inspection and identity-preserving rebuild; OpenGL ES 3 renderer (`render/`); `HumanTest` (12), `WireRendererTest` (2); sheets `docs/brand/human_*.png` | run on a phone: frame rate, shaders, touch |
| v1.8 Clarity, thoughts, moves, drawing | VERIFIED (JVM) / device pending | handbook §28.1: low-poly assets (3.4k/3.6k triangles); thought bubble; jump/roll/lie and question-form move requests (`ActionsAndOnboardingTest`); body-dots-to-drawing scatter; 12 advanced 3D drawings (`NanoSwarmTest`); film strips `docs/brand/draw_scatter_*.png` | watch the scatter and the bubble on a phone |
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
| M6 Connectivity + sync | VERIFIED (JVM) / device pending | `Connectivity`, `SessionClient` (device sign-in, no secret in the app), `KnowledgeStore`, `SyncWorker` (packs, learning, episodes) against Track S S-K, S-L, S-E; inert while no https server is set ("set server to https://…") | point a phone at a deployed server |
| M7 Online client | VERIFIED (JVM + local server) / device pending | `net/DeskClient`: questions the phone can't answer go to the server desk when online; thought bubble while waiting; THINK lines in the bubble, SAY lines spoken; one desk room kept, recreated on 404, re-sign-in on 401, offline answer on any failure; `DeskClientTest` (4) and a run against the real backend | real model behind the desk (Track S Phase 2: provider to be chosen) |
| M8 Voice polish | NOT STARTED | — | — |

Commands run: `gradle testDebugUnitTest assembleDebug` → 80 tests passed (incl. Robolectric LaunchTest), APK built (v0.10.0). `gradle :app:generateHumanAssets` rebuilds the 3D assets.
Not run: `lintDebug` (dependency download rate-limited in the sandbox), any on-device test.

Known limits:
- No device or emulator test yet; frame rate of the GL wireframe human and of the nano-dot swarm (up to 10k dots) is unmeasured.
- Deep hip bends stretch a few crotch edges (two-bone linear skinning); visemes are text-timed, not audio-driven.
- Offline brain is rules + retrieval over the pack, not a language model; free-form reasoning needs the server.
