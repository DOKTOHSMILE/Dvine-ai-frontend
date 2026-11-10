# DVINE — Master Build Handbook

Version 2.0 · rewritten on 2026-11-03 for app 0.89.0. This is the plan we build from now. It describes the app as it is and where it is heading.

Earlier versions planned a face-first app with 3D human bodies. That design was retired at 0.26.0. Its full text, including the ANBA, ACMS and SVRE blueprints, is kept in `docs/archive/HANDBOOK_v1_history.md`. Old references such as "handbook §28" in `BUILD_STATUS.md` point to that file.

Names: **O-bìì** (Obi), **Ēèvié** (Ivie) and **Ibiko**. Product: **DVINE**.

---

## 0. How to use this handbook

1. **Each change ships as its own version.**
   - Bump `versionName` and `versionCode` in `app/build.gradle`.
   - Run the full unit suite and `assembleDebug`.
   - Put the APK in `releases/` and its SHA-256 in `releases/README.md`.
   - Add one row to `docs/BUILD_STATUS.md` (newest first) and one to `docs/DVINE_ALL_PROMPTS_STATUS.md`.
   - Then commit, push, open a PR, and merge.
2. **Status words are fixed:** `NOT STARTED`, `IN PROGRESS`, `BLOCKED`, `VERIFIED (how)`, `RETIRED`. A checkbox is never evidence. "Phone pending" means nobody has checked it on a phone yet.
3. **Two tracks:**
   - **Track M** is this repository (Android). It works offline on its own.
   - **Track S** is `Dvine-ai-backend` (Spring Boot): desk rooms, knowledge packs, learning intake, the sandbox runner, and the "DVINE server" model.
   - They meet only through the contracts in §9.
4. **Sections 1–8 describe the app as it is.** §10 is the work ahead, in order. §11 is the version history.

---

## 1. The product today

DVINE is a voice-and-chat assistant. Three personas think together and answer as one.

**The screen**
- A 3D stage at the top shows a floating pod in space. The pod shows what DVINE is doing: its rim pulses while listening, light runs round its rings while thinking, and it beats with the voice.
- A chat in the style of Claude sits below the stage. It has a steps sheet, a composer, and a drawer for chats, Projects and Workspace.
- There are no faces and no bodies.

| | Obi | Ivie | Ibiko |
|---|---|---|---|
| Role in the council | Plans: structure, assumptions, feasibility, risk | Checks against the real world, people and UI/UX; owns the 3D design canvas | Builds: the coding agent (files, git, sandbox, shell) |
| Colour | Cyber cyan `#00E5FF` | Warm gold `#FFB300` | Emerald / mint |
| Voice (phone TTS) | pitch 0.80, rate 0.98, male en-NG preferred | pitch 0.97, rate 1.00, female en-NG preferred | robot lift: ×1.10 pitch, ×0.95 rate |

**Character rules for every reply**
- Grace and Necessity (John 1:14): truth without harshness, and mercy without dishonesty.
- Weigh whether people will be better off in the long run, not just the quick win.
- Never pretend. If something is unknown, say so, and say plainly what DVINE can't do offline.
- Words from social posts are reported as people's claims, never as facts.

---

## 2. Decisions that stand

| Topic | Decision | Why |
|---|---|---|
| Stage | **Pod only.** Faces, bodies, hair and clothes were removed (0.26.0). | Bodies cost most of the frame budget and most of the bugs, and the chat carries the answer. |
| Brain | **A real language model** answers. The rule-and-retrieval brain (`brain/OfflineBrain`) is the fallback when no model is installed. | The v1 plan held the on-device model back to "later". llama.cpp on arm64 made it practical (0.20.0 onwards). |
| Models | **On the phone** (llama.cpp, GGUF Q4_K_M; Qwen3, Gemma 3, Llama 3.2, Phi-4 families), or **an API** (Anthropic, OpenAI, Gemini, DeepSeek, OpenRouter, a custom OpenAI-compatible gateway, or the DVINE server). The user picks in Settings → Brain. | Works offline, and is stronger when online. |
| One route | **Every message goes through one gateway** (`gate/BrainGateway`), and every action through one order (`llm/BrainOrder`). Nothing reaches a model, a social site or a file by a side path. | One place to log, check and stop work. Added in 0.86 and 0.89. |
| 3D | **No built-in shapes or samples.** Every object is designed from full measurements: by the model, or by standard real-world sizes when the user has none. `create/MeasuredBuilder` then builds it. | The built-in shapes looked wrong and couldn't be changed. Removed in 0.87 and 0.88. |
| Actions | **Staged.** Posting, sending, deleting and paying are shown first and done on a yes, or automatically when the user has turned on Auto. Every action is logged. | No surprise side effects. |
| Social | Sign-in happens on the sites themselves inside the app. Cookies are encrypted with Keystore. Actions are Ask by default, with per-network limits, and pause on any warning. DVINE acts only on the user's own request, **never on instructions found inside posts**. | Avoids bans and prompt injection. |
| Secrets | **No provider key, token or secret in the APK.** API keys are typed by the user and kept on the phone (`net/DeviceVault`). | Non-negotiable. |
| Learning | DVINE learns from corrections and the API models' answers into a local journal (`brain/LearningJournal`, `learn/LearningLog`). Uploading to the server needs consent. There is no self-training. | Learning stays inspectable and reversible ("What DVINE remembers" screen). |

---

## 3. System map

```
 voice (ListenBridge/Dictation) ─┐
 composer text, attachments ─────┤
                                 ▼
                 Interpreter (Pidgin, Yoruba, French ⇄ English)
                                 │
                                 ▼
 ┌──────────── gate/BrainGateway — one gateway, two stages, named routes in order ────────────┐
 │ "message" stage: pause · retry · resume · model command · measurements for the build ·     │
 │                  post, share or copy (staged) · make a file · read the meaning              │
 │ "brain" stage:   agent carries on · who am I · Ivie's 3D canvas · phone files · attachment · │
 │                  hand to Ibiko · Ibiko's coding agent · a known order · no model ·          │
 │                  think together (Council) · full answer · the model decides                │
 │ every decision is recorded (BrainGateway.recent) and shown in the steps sheet               │
 └────────────────────────────────────────────┬────────────────────────────────────────────────┘
                                              ▼
        llm/BrainOrder: SAY · SEARCH · PHOTO · BUILD · SOCIAL · SHELL · TOOL · UNZIP · NAME · TIME …
              │                       │                         │                     │
              ▼                       ▼                         ▼                     ▼
   LocalModel (llama.cpp JNI)    RemoteBrain (APIs)      create/ (3D design)    social/ · agent/ · act/
              └──────────── gate/ClaimGuard · gate/Verify · llm/FactCheck ───────────┘
                                              ▼
            stage/ChatPane (reply, steps sheet) · voice/TalkBridge (+ ReadAloudNotice) · render/pod
```

**What a message passes through, in order**
1. It is translated to English if needed, then back again for the reply (`llm/Interpreter`).
2. The **message stage** takes controls and staged actions first.
3. The **brain stage** gives the brain the message. Plain words are turned into an order (`BrainOrder.decide`). For example, "build a two-storey house" becomes `DO BUILD …`, and "check trending on X" becomes `DO SOCIAL TRENDING`.
4. `carryOut` performs the order. Results are checked (`ClaimGuard`, `Verify`, and `FactCheck` for factual answers) before they are shown and spoken.
5. While DVINE is busy, new messages wait in a queue (`hold`/`releaseBeat`). Stop cancels the running work.

---

## 4. Module map (`app/src/main/java/ai/dvine`)

| Package | What lives there |
|---|---|
| `MainActivity` | Activity wiring, the two gateway stages, `carryOut`. About 5,100 lines; splitting it is in §10. |
| `gate/` | `BrainGateway` (named routes, decisions), `ClaimGuard`, `Verify`. |
| `llm/` | `BrainOrder`, `LocalModel` + `NativeLlm` (engine bridge), `RemoteBrain` + `Providers` + `ProviderStore`, `ModelSpec`/`ModelHub`/`ModelDownloader` (download and choice of model), `PersonaPrompt`, `Interpreter`, `FactCheck`, `PromptFit`, `ThinkPace`, `ReplyService`/`ReplyLease` (a reply survives leaving the app), `UsageLog`. |
| `cpp/` | `dvine_llm.cpp` on vendored llama.cpp: 3 KV sequence slots (`pick_slot`/`read_into`/`drop_slot`), warm instructions after load and on persona switch, Q8_0 KV with flash attention above 4,096 context, and KleidiAI kernels on arm64 (`third_party/kleidiai` v1.32.0). |
| `agent/` | Ibiko's `CodeAgent` (modes Auto / Accept edits / Plan), `Council` (three think tanks), `Crew` (mini agents), `Handoff`, `SandboxShell`, `PhoneHost`, `StepGroups`/`StepDetail` (the "Ran / Explored / Edited" steps). |
| `create/` | `MeasuredDesign` (prompts: BRIEF and PARTS for the phone's model; EXPAND, PARTS_FULL and FIX for APIs; CHANGE), `MeasuredBuilder`, `ObjectRequestInterpreter`, `CreationDirector`, `IvieStudio`, part trees, `GeometryValidator`, OBJ/glTF import and export. |
| `render/` | `pod/` (`PodRenderer`: space, or the Blender-style studio with grid and axes), `field/` (particle display, shapes, glTF, mesh export), `StageRenderer`, `GlApi`/`AndroidGl`. |
| `stage/` | `StageView`, `ChatPane`, `ThinkingMark` (the thinking star that glances left and right), `TypeBar`, `WorkloadGauge`. |
| `chat/` | Chat book (chats, pins, rename), `MessageParts`, `ReplyText`, `LiveReply`, `WorkLog`, code-language detection, attachments. |
| `voice/` | `TalkBridge` (TTS), `ListenBridge` and `Dictation` (hold to continue), `VoiceTuning`, `SentenceStream`, `ReadAloudNotice` (pause and stop from the notification), `AmbientPad`. |
| `social/` | `Platform` (Facebook, X, Instagram, Reddit, …), `SocialAccounts`, `SocialWeb` (hidden WebView reads), `SocialAsk`, `SocialLimits`, `SocialPage`. |
| `connect/` | Connectors screen, GitHub sign-in and git, `LineDiff`. |
| `act/` | `Actions`, `ActionLog`, `FileMaker` (PDF, DOCX, XLSX, … made on the phone). |
| `brain/`, `engine/`, `turn/` | The offline rule brain, the learning journal, context examination, the supervisor/skills engine, turn routing and the two-persona coordinator. |
| `net/` | Connectivity, desk client and session (Track S), web search and fetch, `DeviceVault`. |
| `ops/` | Termux, SSH, recon and vision helpers for Ibiko. These are demo-grade and not routed to yet (see §10). |

Toolchain: Java 17, AGP 8.7.3, Gradle 8.9, compileSdk/targetSdk 35, minSdk 34, NDK 27.2, arm64-v8a only. Tests use JUnit 4 and Robolectric (sdk 35).

---

## 5. Subsystems

### 5.1 Models and the engine
- **Choosing a model.** Settings → Brain picks the phone's model or an API. An API has a strong model for real work and a fast one for simple questions. If an API fails, DVINE falls back to the phone's model within its memory and says so (`takeSwitchNote`).
- **Offline model.**
  - Downloaded once. Resumable, checked by SHA-256, and runs in a foreground service.
  - The default memory is 8,192 tokens; this is a setting.
  - When the phone's model is slow three times, DVINE gives speed advice.
  - The speed test lives in Settings.
- **Engine refresh.** The native engine is rebuilt with `assembleDebug → refreshNativeEngine → assembleDebug`, and `PrebuiltEngineTest` compares the build IDs.

### 5.2 Personas, council and agents
- **Council.** Obi plans, Ivie checks and Ibiko builds. They take one extra round only when Ivie disagrees on something material, and then give one answer. Simple questions skip the council.
- **Mini agents** (`agent/Crew`) split heavy jobs on one model. Each agent has its own memory slot. Online, the helpers run in parallel.
- **Ibiko.**
  - Edits files, runs git with GitHub, and works in a sandbox shell.
  - Reports only what actually ran.
  - Asks before anything destructive.
  - Long work (plan → build → test) can run for hours and survives minimising the app.

### 5.3 Chat and steps
- **Replies.** They are typed out live, and code opens in the code viewer. Pills under each reply: copy, read aloud, retry, edit a sent message.
- **Steps sheet.**
  - Groups the work the way Claude Code does: "Ran N commands", "Explored", "Edited", with code and diffs you can open.
  - Each step shows its own time.
  - It opens at half height, and you drag its body to full screen.
- **Send button.** It shows Send while you type and Stop while DVINE works. Messages sent during work wait their turn.
- **Workload ring.** Shows how full the working memory is, and clears it when it is nearly full.

### 5.4 Voice
- **Listening.** Hold to talk. Dictation continues until the record button ends it. The microphone never opens by itself.
- **Speaking.** Replies are spoken sentence by sentence as they stream.
- **Read aloud.** Shows a media notification with pause and stop, so you can control it from the notification panel.
- **Inner thoughts.** Spoken softer, lower and slower than the reply.

### 5.5 3D design (Ivie's canvas)
1. You say what to make. If the request has no sizes, DVINE asks for them. "You choose" or "I don't know" means standard real-world sizes.
2. **Every model, the device's and an API's (since 0.89.9),** follows the same four steps: it expands the request into a full design specification (EXPAND), writes the brief (BRIEF), gives every part's exact coordinates (PARTS_FULL), then fixes any problems (FIX). Part lines use the format `name | shape | material | W x H x D unit | where`. The position can be absolute ("at x,y,z") or relative: on, under, in front of, beside, offsets, or repeat N spread evenly.
3. The model in use does all of it; the device's model never takes over from an API (0.89.8). Each step shows in the chat as it starts.
4. `MeasuredBuilder` builds the parts.
   - It accepts ft, in, m, cm and mm, swapped columns and table headers.
   - An unknown shape becomes a box, and a part it can't place is put on the ground. Both cases give a warning.
5. **Canvas controls.** Full screen hides the chat. The studio background is a grey gradient with a floor grid and red and green axes, Blender-style. Touch spins, zooms and moves the object. Parts can be selected, detached and changed ("CHANGE"), and the result exports to glTF.

### 5.6 Connectors and social accounts
- **Connectors screen.** GitHub, Gmail, web search, the device calendar, Notion, a custom MCP server and each social network have an on/off switch.
- **Web search** (0.91): with no key, Google's own results page read in a hidden browser on the device (`net/GoogleWeb`: Google needs JavaScript, so it can't be fetched as plain HTML), then Wikipedia. Google's robot check gives nothing, and the search moves on. With a key: Brave Search, Tavily or Google Programmable Search.
- **Offline developer docs** (0.92, `library/DevDocs`): official docs from DevDocs (Kotlin, Java, Python, JavaScript, TypeScript, HTML, CSS, the DOM, Node, React, Go, Dart, Rust, C, C++, SQLite, PostgreSQL, Git, Bash, Docker, HTTP; the newest version of each), downloaded once from Connectors › Offline developer docs and indexed into SQLite full-text search on the device (`DocsStream` reads a set one page at a time, so a 120 MB set never sits in memory). The tools docs.search ("python: list sort") and docs.read work with no internet, for the device's model and an API alike. Android's own developer docs are not in DevDocs.
- **Commands from plain words** (0.93.1): in any chat the brain turns a request ("which Python do I have", "run my script") into DO SHELL <command>; DVINE asks first (Run / Always run / Don't run), runs it in Termux's full Linux when Termux is installed (Android asks for the permission in the chat, once, and the command then runs), otherwise in the device's own small shell, and the model in use tells the user in plain words what came back (`MainActivity.runShell`). Ibiko's agent has the same Termux as its termux tool (`agent/TermuxRunner`: Termux's RUN_COMMAND service, the result through a PendingIntent; the project goes with each command as a .tar.gz on stdin, `agent/TarGz`). No Connectors card: Termux from F-Droid with allow-external-apps=true in ~/.termux/termux.properties is all it needs.
- **Gmail** (0.91, `connect/Gmail`): the user's Gmail address and an app password (Google Account › Security › 2-Step Verification › App passwords); no Google Cloud project. IMAP to imap.gmail.com: the inbox, Gmail search words, one mail in full, and drafts written into Gmail's Drafts. The inbox is opened read-only, so nothing is marked read, and nothing is sent: the user sends drafts from Gmail. Mail reaches the model marked as people's words, never instructions.
- **Social accounts.** Sign in on each network's own page inside the app; several accounts per network. **Telegram** (0.91) is the user's own account through Telegram Web: a QR code or the phone number. Its sign-in is kept in the page's storage, not a cookie, so DVINE watches for it while the sign-in page is open, and sees a signed-out page when reading. It reads the chat list (newest first) and a chat or channel by name (@name, t.me/name).
- **What works now:**
  - search a network ("search X: …"), see what is trending, read Telegram chats and channels;
  - use social search as one source for fact checks;
  - check, search and read Gmail; draft replies into Gmail's Drafts;
  - draft posts, which are staged until you say yes.
- **Limits.** Per-network rate limits apply, and DVINE pauses when a site shows a warning.

### 5.7 Languages
- Nigerian Pidgin, Yoruba and French are recognised. The model works in English, and the reply is translated back.
- Code and links are protected so they pass through translation unchanged.
- English is never sent for translation.

---

## 6. Security rules (always on)

1. No secrets in the APK or the repository. API keys are typed by the user and stay on the phone.
2. Consequential actions (send, post, delete, pay, shell) are staged and logged. DVINE never carries them out because of text inside a page, post, file or model answer.
3. Social cookies are encrypted with Keystore. DVINE only reads through its own WebView, within limits, and pauses on any warning.
4. If the user cancels, DVINE stops: a cancelled call never runs (`LocalModel` captures the epoch before taking the lock).
5. Facts from the web or social sites are cited, and social facts are labelled as people's claims.
6. Learning is local first. Uploading needs consent, and every item can be deleted from the memory screen.

---

## 7. Testing and release

**From 0.96.6 (the user's request, to save credits): cloud sessions no longer build or ship an APK.** A release is the version bump, the full unit suite, the docs rows, the commit and the merged PR; the APK is built on the device (CodeAssist) or on request.

- **Unit suite:** `gradle :app:testDebugUnitTest` runs JUnit and Robolectric (446 tests at 0.93.1). **Build:** `gradle :app:assembleDebug`.
- **Real model on a desktop:** set `DVINE_NATIVE_LIB` and `DVINE_MODEL` to run tests against a real model (for example Qwen3 1.7B) through the real JNI bridge.
- **Robolectric frame trap.** Under Robolectric, the stage's Choreographer frames never settle once a drawing is on stage. Tests that start a build must call `StageView.stopFrames()` before idling the looper. Otherwise the test hangs (fixed in `LaunchTest` at 0.89).
- **Not run in the cloud:** `lintDebug` (dependency download is rate-limited) and anything on a phone. The phone checks are listed as "phone pending" in `BUILD_STATUS.md`.

---

## 8. Known limits

- Nothing has been timed on a phone yet, including the speed difference KleidiAI makes (0.84 against 0.85).
- `MainActivity` is still about 5,100 lines.
- The `ops/` lanes (Termux, SSH, recon, vision) are not routed to.
- Designing 3D with an API model has not been tested end to end in the cloud, because there is no key there. With the phone's Qwen3 1.7B, an 18-part building took about 70 s and came back valid, with warnings.

---

## 9. Contracts with Track S (server)

| Contract | Phone side | Server side |
|---|---|---|
| Sign-in | `net/SessionClient`: device sign-in, no secret in the app | auth and sessions |
| Desk | `net/DeskClient`: one desk room kept, recreated on 404, sign-in again on 401 | desk rooms, task events |
| DVINE server model | the `DVINE_SERVER` provider (OpenAI protocol, no key) | a big model (Qwen3 14B/32B) behind the gateway |
| Knowledge and learning | `net/KnowledgeStore`, `net/SyncWorker` | versioned, hashed packs; learning intake review queue |
| Sandbox | Ibiko's remote workspace | Linux sandbox: workspace, runs, files, egress proxy |

---

## 10. Work ahead, in order

| # | Work | State |
|---|---|---|
| 1 | **0.89.0** — released: one route for builds and social jobs, measurements asked, the API design brief, the studio, full-screen canvas, the glancing star, replies in parts between the steps. | VERIFIED (JVM + Robolectric) / phone pending |
| 2 | **Replies in short parts** — built in 0.89.0 (each part with its own Copy and Listen; steps kept as rows between the notes). 0.89.1: the model writes a short note before a step itself (capable models; not the small phone model, to keep it fast). | VERIFIED (JVM + Robolectric) / phone pending |
| 3 | **Social actions and automations** (part 2/3): post, reply and schedule on your own request; Ask/Auto; logs; limits per network. | VERIFIED (JVM) / phone pending: 0.99.4 scheduled and recurring posts (§SP), approved replies (§AC), action log |
| 4 | **Phone speed.** Time 0.84 against 0.85 (KleidiAI); build variants that use i8mm and dotprod. | Waiting on the user's phone |
| 5 | **Split `MainActivity`.** Move the gateway routes, `carryOut`, the canvas and the social flow into their own classes. | IN PROGRESS (0.99.15: social orders are `main/SocialFlow`; canvas is `main/IvieCanvas`; gateway and carryOut still in MainActivity) |
| 6 | **Ibiko's real lanes.** 0.99.18: the shell has a screen (Tools › Commands on this device). Python, git and compilers are not bundled. No BusyBox binary yet. | IN PROGRESS |
| 7 | **Learning.** Corrections into the journal by meaning; the semantic dictionary. | IN PROGRESS (parts since 0.65; 0.99.4 answers by intent with check/cancel; 0.99.6 brain box shapes and the pilot profile) |
| 8 | Voice polish (M8). | NOT STARTED |

Retired and not coming back unless asked: faces (line-art, wireframe, hologram, FACS), 3D bodies, hair, skin and clothes, gestures with the bodies, walking and the shared two-body scene, the built-in 3D shapes and samples.

---

## 11. Version history (short)

| Versions | What arrived |
|---|---|
| 0.1–0.25 | Foundations: offline rule brain and knowledge pack, learning journal, sync with Track S, the online desk client, the on-device model (0.20), and the face and body eras (retired). |
| 0.26 | The pod stage replaces faces and bodies. The brain interprets and DVINE carries out the order. Hold to talk. Drawings made part by part. |
| 0.27–0.44 | Tool path, part trees and detaching parts, sparse photo views, cited answers, guards, photographs instead of stand-in shapes. |
| 0.45–0.47 | The chat sheet over the 3D stage; the chat is what the brain reads. |
| 0.48–0.52 | AI providers and Usage, Claude-style chat, day and night themes, connectors, Ibiko the coding agent, git with GitHub. |
| 0.53–0.62 | Keyboard and dictation, live steps, Settings, web search, attachments, Ivie's studio, full reports, pills, the steps sheet. |
| 0.63–0.68 | A choice of offline models, more memory, reading the meaning of a message, Obi first, the speed test, Ibiko's modes, prompt caching in the engine. |
| 0.69–0.74 | Personas from the master specification, the memory screen, editing a sent message, step timers, long work for hours, the drawer, crash recovery. |
| 0.75–0.81 | Three think tanks, staged actions, fact checks, mini agents, the workload ring, files in many formats, hand-off to Ibiko, council roles. |
| 0.82 | Interpreter for Pidgin, Yoruba and French. |
| 0.83 | Social accounts: sign in on the sites, several accounts, limits, social search and fact checks. |
| 0.84 | Faster offline answers (KV slots, warm instructions), the read-aloud notification, grouped steps, trending, social networks in Connectors. |
| 0.85 | KleidiAI kernels on arm64; a cancelled call never runs. |
| 0.86 | One gateway for every message, with checked results. |
| 0.87 | The 21 built-in shapes deleted. |
| 0.88 | Every built-in 3D sample deleted; 3D only from measurements; messages queue while busy; social switches and names; the sheet drags by its body. |
| 0.89 | One route for builds and social jobs, measurements asked, the API design prompt, the studio, the full-screen canvas, the thinking star that glances like an eye, replies in short parts between the steps with voice on each, the voice compile fix. 0.89.1: the model narrates its own work between the steps. 0.89.2: crash reports saved to Download/DVINE. 0.89.3: a long chat draws only its latest messages (the freeze on Send). 0.89.4: Ibiko finishes instead of ending on a plan; fewer notes. 0.89.5: explanations like Claude's, a paragraph at each turning point. 0.89.6: New chat is clean for all three personas. 0.89.7: true full screen, a real 3D floor, tyres as rings, designs get 15 minutes. 0.89.8: "device" not "phone", the chosen model does every task. 0.89.9: the device's model works like an API model. |
| 0.90 | A busy model is waited for and named; a new task starts its own plan. 0.90.1: a notes record so the device's model remembers the whole chat. |
| 0.91 | Google search with no key, Gmail with an app password, Telegram with the user's own account. |
| 0.92 | Offline developer docs, searched with no internet. |
| 0.93 | Ibiko codes with a real Linux shell (Termux). 0.93.1: commands from plain words in any chat, the result told in plain words. 0.93.2: Telegram's sign-in check fixed. |
| 0.94–0.96 | Built on the device (CodeAssist): task control only through the command gateway, git fetch and merge on the device, streamed file reads and search, no 10-minute cutoff, lower offline RAM and token spend, the main screen split into `main/` helpers, the model probe. 0.96.3: everything wired and checked — tests compile and pass again, the native build fixed, the offline memory sized from the model's header before loading. 0.96.4: large API models answer (room to think, no write cap, ten-minute connection wait), the shipped engine refreshed. 0.96.5: Claude Opus answers (room for its thinking, no temperature). 0.96.6: the custom gateway speaks Anthropic's format too (Auto finds it); no more APKs from cloud sessions. |
| 0.97 | Settings in closed sections with numbers edited in their own editor (no sliders in the list), job length Deep by default; tidier providers and drawer; a code viewer with a line-number column, colours and sideways scrolling. 0.97.1: every offline model listed with download/delete and memory needs; no offline model kept in memory while an API answers; the photo picker; BrainBox, DVINE's own learning brain. |
| 0.98 | One model call per message, the order on its first line; with an API selected the API alone decides what DVINE does; every screen clear of the status bar; drawer newest first; docs/PERFORMANCE_ANALYSIS.md. |
| 0.99 | DVINE as a suit (docs/SUIT_ARCHITECTURE.md): functions only by the brain's machine codes, never by words; the Cortex reads every message and computes arithmetic exactly; deep thinking for any brain. 0.99.1: each page runs its own job; tools per chat from a button by the mic; the persona toggle; the drawer swipe. 0.99.2: the Social Intelligence System (docs/SOCIAL_INTELLIGENCE.md). 0.99.3: the brain picker lists DVINE 1.0 (DVINE's own brain), API models and offline models, the one in use clearly marked. 0.99.4: DVINE 1 · 0.1 researches, asks 👍🏽/👎🏽 and remembers confirmed answers by intent; scheduled and recurring posts (§SP). 0.99.5: the rating uses DVINE's own check and cross icons, not emoji. 0.99.6: the internet kept alive off screen, replies kept, no-internet said and the message sent when back; check/cancel on every reply; brain box shapes; the pilot profile. 0.99.7: DVINE 1 answers what/who questions from the subject's own encyclopedia entry, learns from statements, says what it learned. 0.99.8: check/cancel beside Copy and Listen; DVINE 1 thinks in steps; three named pages per chat; night learning. 0.99.9: night learning only from usage; swipe right anywhere opens the drawer; pages by their buttons only. 0.99.10: Workload, Usage and the workers board measure and show properly; tool icons and their settings. 0.99.11: a learning schedule at any hours with language formulas (Grammar, Facts, Lexicon); the drawer follows the finger. 0.99.12: DVINE 1 in its own folder with an endpoint and the app-functions file; narrated work with a closing summary; the ambient hum off; MainActivity split begun. 0.99.13: less heat during tasks (slower redraws, no Wi-Fi lock, gathered saves, hidden 3D stage stops). 0.99.14: Ivie's 3D canvas and full screen moved to main/IvieCanvas. 0.99.15: social orders moved to main/SocialFlow. 0.99.16: DVINE's own Linux runs unzip with no Termux. 0.99.17: the shell runs inside the app (files, pipes, redirects). Termux is not used. 0.99.18: a terminal screen opens from Tools. 0.99.37: big API models get full replies, 24 exchanges, a stable cache prefix and the right effort. 0.99.38: DVINE 1 learns on its own (PLAN.md T1–T7): fact strength, the pilot's word on answers, sentence shapes, parts of speech, a learning score; every formula in `dvine1/formulas/` (docs/FORMULAS.md). 0.99.39: shell commands start in the coding workspace. 0.99.40: a real Linux terminal (terminal device, xterm screen, vi/nano/htop) shared by the shell screen and chat. 0.99.41: the audit (docs/AUDIT_2026-11-10.md): each job writes to its own page, no retry loop, one copy of DVINE 1's memory, pinned Linux downloads. 0.99.42: one Linux: chat, Ibiko, the task system and the Shell screen all run in the same Alpine through `LinuxHub.run`. 0.99.43: the Blueprint (understand, plan, check, build, verify) for every real task; a standard Shell screen. 0.99.44: dictation keeps words across pauses, a recording effect, the most natural voices, survey readers for the Blueprint, posting from the pilot's account. 0.99.45: the API stays the brain offline; module.toml in step; the Scribe records every step; post versions, named tags, comments under a post. 0.99.46: tasks go to the right persona; Ivie and Obi plan long writing first; a fuller Social accounts screen. 0.99.47: the group chat (one thread for Obi, Ivie and Ibiko); auto-post on the pilot's behalf. 0.99.48: no persona buttons in the group; the words choose who answers. 0.99.49: the group chat is the only chat; persona icons, each one's own voice on Listen, African voices first; commands go to Ibiko and the built-in Linux; Share › DVINE. 0.99.50: with a model in use only the brain's codes run functions (hand-over, writing plan, think together, yes/no by code); workers on real work; one tools grid for the group. 0.99.51: a finished turn is one process row and one summary; icons on a tap; DVINE 1 learns the way answers are reached (`dvine1/Methods`). 0.99.52: an audit is done by the coding agent, which reads the files. 0.99.53: one assistant (Ibiko); the agent organisation (`agent/Org`) with timed parts and hands; one record file a project; codes never shown; built-in facts for DVINE 1. 0.99.54–0.99.56 (main): clones in memory/projects, a running job kept on screen. 0.99.57: the app is Ibiko; long jobs in the background; surveys carry on; plans only grow; 2,379 built-in facts. 0.99.58: the quick chips over the message box (Tools, Code, Media, Explore, More). |

The full evidence for every version is in `docs/BUILD_STATUS.md`.
