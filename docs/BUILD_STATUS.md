# Build status — Track M (Android)

Status words: NOT STARTED / IN PROGRESS / BLOCKED / VERIFIED. Plan: `DVINE_MASTER_BUILD_HANDBOOK.md`.

| Stage | Status | Evidence | Next |
|---|---|---|---|
| M0 Toolchain | VERIFIED | `gradle assembleDebug` passes on SDK 35 / build-tools 35 (cloud session, 2026-10-26) | — |
| M1 Logo + icons | VERIFIED (build) | `tools/make_logo.py` → `docs/brand/dvine_logo_2048.png`, `mipmap-*/ic_launcher*.png`; manifest references them | check on a launcher |
| M2 Procedural faces | VERIFIED (JVM) / device pending | `WireHeadTest` (5 tests); off-device render `docs/brand/face_preview.png`. Old `LowPolyHead`, `LatticeControlMatrix`, `gl/*` removed | frame-rate check on a mid-range phone |
| M3 Screen + states | IN PROGRESS | real ONLINE/OFFLINE badge; larger face; bottom fade | device screenshot |
| M4 Offline brain | VERIFIED (JVM) | `OfflineBrainTest`, `CalculatorTest`, `MiniJsonTest` | airplane-mode demo on device |
| M5 Learning journal | VERIFIED (JVM) | `LearningJournalTest` (consent, redaction, forget, restart) | — |
| M6 Connectivity + sync | IN PROGRESS | `Connectivity`, `KnowledgeStore`, `SyncWorker` compile; inert while `api_base` is empty | needs Track S endpoints S-K, S-L |
| M7 Online client | NOT STARTED | — | after S-K/S-L |
| M8 Voice polish | NOT STARTED | — | — |

Commands run this session: `gradle testDebugUnitTest assembleDebug` → 21 tests passed, APK built.
Not run: `lintDebug` (dependency download rate-limited in the sandbox), any on-device test.

Known limits:
- No device or emulator test yet; frame rate of the Canvas wireframe is unmeasured.
- Session token source is a stub (`() -> null`), so learning upload stays off until sign-in exists (M7).
- Offline brain is rules + retrieval over the pack, not a language model; free-form reasoning needs the server.
