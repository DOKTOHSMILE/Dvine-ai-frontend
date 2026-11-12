# Ibiko — every prompt, built or not

One reading of the instructions sent for the Android app, checked against the code on `main` at **0.45.2** (`1f3a8d9`).

A row marked **Built** means the behaviour exists in the Java app and has been covered by a JVM unit test where a test is named in the notes. It does not mean a phone was used. Gradle was not run in the environment that wrote this file. **Partial** means a real piece exists and the rest of the request does not. **Not built** means it is absent, or it was refused on purpose so the app would not pretend.

The backend was deferred. It is not in this table except where a prompt asked for a server.

## Sources this file was compiled from

| ID | Document |
|---|---|
| P1 | SSH, Termux, internal vision, and 3D reconstruction |
| P2 | Conversation decomposition and brain interpretation |
| P3 | External LLM, three personas, hologram, particles, audio |
| P4 | Brain architecture upgrade |
| P5 | Intelligence repair, persona routing, files, reconstruction |
| P6 | Internet research, faster model, broader understanding |
| P7 | Persistent tasks; the microphone must not cancel work |
| P8 | Test before every GitHub release |
| P9 | One brain, shared memory, shared tools |
| P10 | Visual 3D build blueprint (phases 0–12) |
| P11 | Blueprint versus codebase audit |
| P12 | Later instructions in chat: no “I’m not sure I follow”, photos instead of fake objects, chat sections, theme crash, the repos in the two screenshots |

## Status

| # | What was asked | Source | Status | What is actually true |
|---|---|---|---|---|
| 1 | Offline Android app first. Backend later | P10, chat | Partial | The phone repo is the one being built. No backend service has been started |
| 2 | Personas Obi, Ivie, and Ibiko, discovered from the code, kept across a turn | P3, P5 | Built | Tabs and `selectPersona`. Ibiko is the coding role, not a second model |
| 3 | Text and voice take the same path into the brain | P2, P4 | Built | Both enter `answer()`. Rules run first. What they do not handle goes to the model once |
| 4 | Do not answer “I’m not sure I follow” and then wait forever | P12 | Built | That sentence was removed. A short request is not bounced. Switching chats clears “it” |
| 5 | The model must not be told to ask “Do you mean?” | P12 | Built | The persona prompt says to interpret and continue |
| 6 | One model pass, with a visible thinking line | P6, P12 | Built | The extra “what do you mean” pass was removed. A live line shows phase, seconds, or words |
| 7 | Do not kill a live answer at 25 seconds | P6 | Built | Idle 12 seconds, hard stop 90 seconds, generation token |
| 8 | Conversation broken into meaning, then a structured plan | P2, P4 | Partial | The decomposition, intent, and plan classes exist. `MainActivity.answer()` is still a long method beside them |
| 9 | Shared semantic memory and corrections that do not wipe general knowledge | P2, P4, P9 | Partial | Taught facts and a learning journal exist. Chat notes are separate. There is no graph database |
| 10 | Fact check with more than one source. Do not call one source verified | P5, P11 | Built | Wikipedia, Wikidata, DuckDuckGo, and public Reddit. Two agreeing sources are still not a proof |
| 11 | Google search | P12 | Not built | There is no Google key. The app says so. It does not pretend to have searched Google |
| 12 | Logged-in social search (X, Instagram, TikTok) | P12 | Not built | Reddit public titles only |
| 13 | Real news search | P5 | Not built | The app says it has no news source and does not invent headlines |
| 14 | Downloadable on-device model, Qwen3 1.7B, llama.cpp, arm64 | P3, P10 | Built | 0.47.0 to 0.47.7 dropped the call that hears "download your brain" (every message went to the brain, and with no brain installed nothing could read it); 0.47.8 handles the download words before the brain again. | Download, SHA-256, JNI, streamed speech. Phone speed was not measured here |
| 15 | Termux from natural language, argument list, not a shell string | P1 | Partial | An allow-list (`df`, `ps`, and a few others). Anything else is refused. “Install Termux” opens the app or the store page |
| 16 | SSH to a machine the user authorised | P1 | Partial | Connection, command, and file-browser classes exist. A destructive command waits for yes. Not proven on a device |
| 17 | Microphone on must not cancel the running task | P7 | Built | Task states, pause, resume, and a separate microphone path exist in the ops package |
| 18 | Several tasks, pause one, run another, resume the first | P7 | Partial | The task manager can hold more than one record. A full phone run of that story was not done |
| 19 | File, image, and video picker, and honest reading of what was picked | P5 | Partial | Picker kinds exist. A hostile text file is not spoken raw. PDF text is not claimed as a picture |
| 20 | Internal vision that understands an arbitrary photo | P1, P5 | Not built | There is no vision model. A photo is shown or measured only by the sparse solver when the test conditions hold |
| 21 | “Draw / design / sketch X” must look up real photographs | P12 | Built | Wikimedia Commons, up to four real file URLs. The first file that downloads is shown. Offline, it refuses |
| 22 | Crop the photograph and say it is not a measured model | P12 | Built | Centre crop. The spoken line says the views are not calibrated |
| 23 | Build a measured 3D object from web photos taken at different angles | P12 | Not built | Unrelated web photos are not a calibrated scan, so no mesh is made from them |
| 24 | Delete the lie that a pile of boxes is a pen | P12 | Built | Draw and design no longer call the shape planner. Stored globe, house, car, and similar figures are not put on the board for those requests |
| 25 | “Build a motorcycle” with detach, restore, undo, save, reopen | P10 | Built | That path is still the procedural model, and it says so. “Draw a motorcycle” is a photograph, not this model |
| 26 | Sparse photogrammetry, honest labels, not COLMAP | P10, P11 | Built | Calibrated 8-point solver. One photo is not measured. COLMAP and a dense scan are not in the app |
| 27 | A rigged person: raise an arm, both arms, wave, look up, undo | P10 | Built | Procedural bones and skin. Not a scanned body |
| 28 | On-screen part tree, undo, redo, catalogue | P10 | Built | The tree, history, and saved-model list exist |
| 29 | Dense nanoparticle body, diffusion image model, hologram as the product | P3, P5 | Not built | Particle and mesh code exist. They are not a dense scan and not a diffusion model |
| 30 | High-fidelity skin, clothes, hair, studio look | P3 | Partial | Shaders and looks exist and were checked in a WebGL replay, not as the current phone screen |
| 31 | Chat screen: sections, new chat, persona per section | P12 | Built | XML chat screen with Ivie, Obi, and Ibiko tabs, plus `ChatBook` sections |
| 32 | Shared memory across chat sections | P9, P12 | Built | A `remember …` note and the latest line of other chats are given to the model. The open chat’s own transcript is too |
| 33 | Code, image, and video in the chat | P12 | Partial | A fenced reply is monospace. A fetched photo is an image file. A video plays only if that file is already on the phone. No video is generated |
| 34 | App opens. The XML chat must not crash on theme | P12 | Built in code | Theme is `Theme.AppCompat`. Activity is `AppCompatActivity`. Not installed on a phone from here |
| 35 | Thinking chip on the chat screen | P3, chat layout | Partial | The layout has the chip. It is not proven that every long job drives it |
| 36 | Smaller captions, full sentence visible | P5, P12 | Built | Spoken line is type, up to six lines, not a two-line particle caption |
| 37 | Repos in the two screenshots (claude-mem, LightRAG, superpowers, public-apis, and the others) | P12 | Not built | They are desktop agent tools or a list of HTTP APIs. They were not copied into the app. The note is `docs/REPO_FIT.md` |
| 38 | One cognitive pipeline with an inspectable plan for every sentence | P4, P11 | Partial | Plans exist for known operations. Ordinary chat still goes to the model as language, not as a typed plan |
| 39 | Multi-agent fleet: research agent, comparison agent, visual agent, in parallel | P3, P9 | Not built | One model. Personas change the prompt. There is no pool of subordinate agents |
| 40 | Tool result verified before the app says it worked | P1, P8 | Partial | Termux and scene edits check an outcome. A spoken “done” is not proof for every tool |
| 41 | Tests before commit, then push | P8 | Partial | JVM tests were run for the Java changes. Gradle and a device were not |
| 42 | `gradle assembleDebug` and a phone smoke test | P4, P8 | Partial | 0.47.8: `gradle assembleDebug` and the 226 JVM tests pass in a cloud session with the Android SDK; the APK is `releases/ibiko-0.47.8-debug.apk`. No phone smoke test from here |
| 43 | Date and clock answered on the phone, not invented | early chat | Built | Clock and date are read from the device |
| 44 | Safe release: no secrets in the app, destructive actions confirmed | P1, P8 | Partial | Delete and remote destructive wording wait for yes. A GitHub token must not be stored in the repo |

| 45 | Add a custom API, like the AI providers sheet of a coding app (Anthropic, OpenAI, Gemini, OpenRouter, custom gateway, backup key, CA certificate) | P12 | Built (JVM) | 0.48.0: drawer → AI providers, or tap the model under the chat title. Keys encrypted on the phone. Tested against recorded API replies, not against a live key |
| 46 | A Usage screen like Claude's (context window, plan usage) | P12 | Built (JVM) | 0.48.0: the last reply's context window by part, today's requests and tokens, the brain and storage. There is no plan limit to show: spending is on the provider's own dashboard |

| 47 | The brain is downloaded with a button, asked for at first open, required unless an API key is linked | P12 | Built (JVM) | 0.49.0: the setup screen with Download and Add an API key; it returns until one is chosen |
| 48 | The chat laid out like Claude (buttons, drawer, sizes) | P12 | Built (rendered) | 0.49.0: see `docs/brand/chat_0_49/`. Checked as rendered screens, not on a phone |
| 49 | Switch automatically across three APIs | P12 | Built (JVM) | 0.49.0: primary and two backups; a failure before any words hands over; tested against recorded replies |

| 50 | Copy and Listen as icons; icons at 24 dp | P12 | Built (rendered) | 0.50.0: reply actions and the code block's Open and Copy are icons; 24 dp header and composer icons, 20 dp actions |
| 51 | Day and night theme | P12 | Built (rendered) | 0.50.0: follows the phone, or Light / Dark from Appearance in the drawer |
| 52 | Connectors | P12 | Built (JVM) | 0.50.0: web search, phone calendar, GitHub, Notion, a custom MCP server. Tested against recorded replies, not live services |
| 53 | Don't ask the user's name at start | P12 | Built | 0.50.0: the question is gone; "call me …" sets a name any time |

| 54 | Ibiko as the Claude Code of Ibiko, with that flexibility | P12 | Built (JVM) | 0.51.0: a step-by-step agent with files, search, shell commands (asked first, or Auto-accept), web pages and connectors, in its own folder |
| 55 | A cheaper model for simple questions, a stronger one for big tasks, switched automatically | P12 | Built (JVM) | 0.51.0: a fast and a strong model per API; real work gets the strong one and a full answer |
| 56 | Fix the attach icon | P12 | Built (rendered) | 0.51.0: a paperclip matching the mic |
| 57 | Remove the word "brain" from the interface | P12 | Built | 0.52.0: screens, notifications and spoken lines say "model" |
| 58 | Ibiko learns from every interaction with the custom APIs to build its own | P12 | Built (JVM) | 0.52.0: requests, replies, models and Ibiko's steps kept on the phone; used as examples for the phone's model; exported as a fine-tuning file. Hidden reasoning isn't returned by providers, so it can't be kept |
| 59 | Connect to GitHub: push, pull, commit | P12 | Built (JVM) | 0.52.0: Ibiko clones, shows status, pulls, commits and pushes, makes branches and opens pull requests, with the GitHub connector's token |
| 60 | Fix the voice lag (streaming listening with quick end of speech, the model's words to the voice a sentence at a time) | P12 | Built (JVM) | 0.53.0: on-device recognizer, turn closed 0.85 s after the last word, no routing call for real work, the reply shown and spoken sentence by sentence as it is written. Not measured on a phone |
| 61 | Fix the keyboard covering the fields | P12 | Built (Robolectric) | 0.53.0: the providers sheet and the other settings screens move up above the keyboard |
| 62 | The message box loses focus after Send and while the model works | P12 | Built (JVM) | 0.54.0: the chat list no longer takes the focus when it scrolls |
| 63 | Show what Ibiko is doing step by step, not just "thinking" | P12 | Built (rendered) | 0.54.0: a live steps panel that folds into "Worked for Ns" |
| 64 | Voice replies automatic | P12 | Built | 0.54.0: read aloud by default; Always / After the mic / Never in Settings |
| 65 | The mic puts the words in the box to edit, not send | P12 | Built | 0.54.0: dictation into the message box |
| 66 | Not Claude's exact colours | P12 | Built (rendered) | 0.54.0: Ibiko's own palette, violet accent |
| 67 | GitHub with email or username and password, or several ways in | P12 | Built (JVM) | 0.54.0: browser sign-in (email or username and password on github.com, approved with a code), a pre-filled token page, or a pasted token. Passwords can't be taken by apps (GitHub's rule since 2021) |
| 68 | A settings page with the offline model's download progress | P12 | Built (rendered) | 0.54.0: Settings with live progress, pause, resume, remove |
| 69 | The Add menu as a bottom sheet with standard icons | P12 | Built (rendered) | 0.54.0 |
| 70 | Ibiko with a custom API: tool calls shown as text, "the model didn't answer", "continue" resuming a download, promises instead of results | P12 | Built (JVM) | 0.55.0: every common tool-call form read; room for reasoning models; download notices off the chat; nudged to act |
| 71 | Long pastes become a file | P12 | Built (rendered) | 0.55.0: a "Pasted text" card |
| 72 | A picked image waits for instructions instead of being sent | P12 | Built (rendered) | 0.55.0: attachments in the box; images go to the model as images |
| 73 | A standard Settings page with good features | P12 | Built (rendered) | 0.55.0 |
| 74 | Clone repositories to the phone, let the models read and edit them, push; or work on the repo directly | P12 | Built (JVM) | 0.55.0: one-zip clone kept on the phone, public repos without sign-in; Ibiko reads, edits and pushes. Editing on GitHub without a clone is still open |
| 75 | Why is Wikipedia the only search source | P12 | Built (JVM) | 0.55.0: Brave, Tavily or Google with the user's key; otherwise Wikipedia's search, several articles; pages read and cited |
| 76 | Split MainActivity, remove unused and broken code; Ivie graphics, Obi chat, Ibiko code | P12 | Partly built | 0.55.0: 4,040 → 3,180 lines, 31 unused members gone, three classes moved out; personas organised and Ivie's 3D canvas. More of MainActivity can still move out |
| 77 | Models that don't take files or images (DeepSeek and others) read the cloned files on the phone and do their work | P12 | Built (JVM) | 0.56.0: refused images become notes; any persona reads the phone's files as text through the read-only agent; DeepSeek preset |
| 78 | Don't hard-code what should be open variables | P12 | Built (JVM) | 0.56.0: per-provider Advanced (images, reasoning, length, time, window, extra JSON) and Settings › Advanced (lengths, steps, paste, image size, web) |
| 79 | Connect Ivie to mesh export and part edit | P12 | Built (JVM) | 0.57.0: Ivie's studio: model-drawn parts, edits by name, undo, GLB/OBJ/STL/PLY to Downloads |
| 80 | Show the full result in a long message | P12 | Built (JVM) | 0.57.0: progress lines sent back for the full report; earlier findings and replies kept whole |
| 81 | Let the model receive files from the API, any type | P12 | Built (JVM) | 0.57.0: native PDF, text, audio and file parts; else extracted text, pages or frames; else a note |
| 82 | Diff, Pull requests and Artifacts for Ibiko | P12 | Built (rendered) | 0.57.0: the three chips on Ibiko's tab with their sheets |
| 83 | Keep writing findings until the report is done, not a short sentence | P12 | Built (JVM) | 0.58.0: a big task always gets its full report round; streamed as written; continue carries on |
| 84 | Tap a step to see code added in green and removed in red | P12 | Built (rendered) | 0.58.0 |
| 85 | Steps in words, not ls | P12 | Built (JVM) | 0.58.0 |
| 86 | All steps listed, not "+2 more" | P12 | Built | 0.58.0 |
| 87 | Make my own messages copyable | P12 | Built (Robolectric) | 0.58.1: a copy icon under each, text selectable |
| 88 | Stop the voice recording from starting by itself anywhere in the app | P12 | Built | 0.58.2: only a tap on the mic (or a hold on the stage) listens |
| 89 | No more status lines, questions or empty replies instead of the result; fix it once and for all | P12 | Built (JVM) | 0.59.0: the agent works until it hands in its answer; dropped connections retried |
| 90 | Layout fix: PR/Artifact pills attached to the reply, Auto-accept pill in the header, persona tabs in the composer | P12 | Built (Robolectric) | 0.60.0: each reply keeps its own Diff / Pull requests / Artifacts; "Auto"/"Ask" in the header; tabs beside attach; "Fixing … — digging into …" no longer ends Ibiko's work |
| 91 | Send double tap; persona icons; steps in a bottom sheet with a typewriter line; no trigger words, write until done; pick several files of any kind | P12 | Built (Robolectric) | 0.61.0: the model ends its turn by structure (tool call, <final>, or text confirmed once), cut-off replies continue until they end by themselves |
| 92 | Copy icon on my message only on a tap; standard check icon; teach the model push/pull and a local sandbox; better offline model? | P12 | Built (Robolectric) | 0.62.0: tap toggles copy, hidden on new messages; ic_check; git workflow + sandbox/ in Ibiko's prompt; clone limit a setting (5,000). Offline model: answered in chat, not changed |
| 93 | Yes: add more advanced offline models; delete to save space; sizes per family (like Opus/Sonnet/Haiku), switch any time | P12 | Built (Robolectric) | 0.63.0: Settings › Change the offline model (family, then size: Qwen3 0.6B–8B, Llama 3.2 1B/3B, Gemma 3 1B/4B, Phi-4 mini); Delete per model; quick switch from the chat header; not yet run on a phone |
| 94 | Server as an online model; offline memory 8,192 customizable with slowness advice; GPU speed | P12 | Partial | 0.64.0 + backend: /api/v1/llm relays to llama-server, Ibiko server provider signs in with the session; memory setting and advice built. GPU (Vulkan) not built: building upstream code was refused in the cloud session |
| 95 | Screenshot: "too long for the model's memory", a progress line as the answer, "My model is ready" inside a reply | P12 | Built (JVM) | 0.64.1: the offline take-over fits its memory and keeps the API's reason; a failed request after "are you done?" says so; download-finished is a note |
| 96 | Interpret every message by meaning, not words: a rephrased "are you done?" must still be understood, converted to the command and sent on | P12 | Built (Robolectric) | 0.65.0: turn/Meaning reads short messages into the commands Ibiko runs, acts on their wording, remembers each phrasing |
| 97 | Add the further commands (new chat, switch persona, stop, auto-accept, clear workspace, Settings) | P12 | Built (Robolectric) | 0.66.0: understood by meaning, run with the buttons' actions; clearing asks first |
| 98 | Screenshot: offline Gemma 3 1B keeps saying "too long for the model's memory" | P12 | Built (JVM) | 0.66.1: cut down further and retried until it fits; compact instructions for small memories; a plain message when nothing fits |
| 99 | Obi says nothing to "Hello, how are you?" on Gemma (the mediator); pictures to the sender's side; image viewer broken | P12 | Built (Robolectric + real model on desktop) | 0.66.2: the reading waits at most 4 s, then the message goes on; pictures right-aligned at their own shape; the viewer fits, zooms and pans |
| 100 | New chat shouldn't load the old chat; Obi first and the default; models in the foreground; safe fallback when a model is too strong (app stuck at start with Qwen3); how to measure speed | P12 | Built (Robolectric + real models on desktop) | 0.67.0: empty Obi chat on open and New chat; startup freeze fixed; ModelGuard falls back; counted foreground service; Settings › Test the speed |
| 101 | Screenshot: Gemma 3 1B takes 237 s for a greeting; "I couldn't finish that on this phone" | P12 | Built (JVM) | 0.67.1: fast cores only (setting too), no reading call offline, 10 minutes to the first word with a clear message |
| 102 | Make all the Advanced settings sliders | P12 | Built (Robolectric) | 0.67.2: a slider each, round stops over wide ranges, value beside the name |
| 103 | Code viewer identifies any language; XML inside a code viewer; fast typewriter; reply cancelled when leaving the app; "Retry" | P12 | Built (Robolectric) | 0.67.3: fence repair, unfenced code, language from the code, live code viewer typed out, a minute between words, retry asks again |
| 104 | Chat title editable; cancel the one-word photo search, let it go to the model with the chat context | P12 | Built (Robolectric) | 0.67.4: tap the title or hold a chat to rename; single words go to the model |
| 105 | Chat delete (knowledge kept), chat pin, status-bar text dark in light mode and vice versa | P12 | Built (Robolectric) | 0.67.5: hold a chat for Rename / Pin / Delete; bar icons follow the theme |
| 106 | Add these functions (Claude Code's Select mode: Auto, Accept edits, Plan) | P12 | Built (Robolectric) | 0.68.0: Ibiko's mode sheet; Plan reads, plans, then asks to carry it out |
| 107 | Does the model cache? To prevent reading long threads of replies over and over again | P12 | Built (desktop engine measured) | 0.68.1: the engine reuses everything already read; Claude prompt caching on; shipped engine refreshed |
| 108 | Back to the original first build files: apply what still fits (personas' character, wisdom framework, languages, voices) | P12 | Built (JVM) | 0.69.0: spec temperament, John 1:14 / Stoic / Frankl / Kahneman / Munroe, Pidgin-French-Yoruba, spec voice values |
| 109 | Fix this (Ibiko stuck 209 s with an 'ibiko:' step); make the typewriter faster like ChatGPT; memory review from the first build files | P12 | Built (Robolectric + desktop model) | 0.70.0: What Ibiko remembers; small-model answers land at once; ~500 chars/s word typewriter |
| 110 | Add edit to a user message: edit it, the system retries the new edit and removes the former response | P12 | Built (Robolectric) | 0.70.1: Edit beside Copy; the old message, its replies and the model's memory of them go; the edit is sent |
| 111 | Smaller copy/edit/play icons; Listen pauses and resumes; it keeps repeating (screenshot); remove 'Asking custom gateway' and add separators; crash printing a long audit report | P12 | Built (Robolectric) | 0.70.2 |
| 112 | Each process shows its timer by its side in the bottom sheet, synced with the work; the process bottom sheet opens at the middle and can be rolled to full screen | P12 | Built (Robolectric) | 0.71.0 |
| 113 | Models plan, build and test in continuous work that may take hours, online and offline; minimising doesn't kill it; drop replies bit by bit while processing | P12 | Built (JVM) | 0.72.0: rounds with progress notes, PLAN.md, 8-hour run |
| 114 | Add projects and workspace to the drawer (samples: Claude, Grok, Gemini, Meta AI, ChatGPT, DeepSeek) | P12 | Built (Robolectric) | 0.73.0 |
| 115 | Drop replies bit by bit while processing; a professional live thinking icon; the voice must not read format characters; a live backup so a crash doesn't restart a long process | P12 | Built (Robolectric) | 0.74.0 |
| 116 | The 3 think tanks all processing together for better output (spec item 1, extended) | P12 | Built (JVM + desktop model) | 0.75.0 |
| 117 | Social posting with approval; or on my account without asking via an auto-approve setting (staged actions first) | P12 | Built (Robolectric) | 0.76.0: staged actions, auto-approve, action log |
| 118 | Fact-check pass on factual answers (spec item 5) | P12 | Built (JVM + desktop model) | 0.77.0 |
| 119 | Multiple mini agents for heavy tasks, possible offline | P12 | Built (JVM + desktop model) | 0.78.0 |
| 120 | Model workload counter like an overheating indicator; auto-clear when too full, as the master plan says | P12 | Built (Robolectric) | 0.79.0 |
| 121 | Can the offline model create files in multiple formats (PDF etc.)? If not, build it into Ibiko; the counter goes down when the system releases stress | P12 | Built (Robolectric) | 0.80.0 |
| 122 | Obi/Ivie refer Ibiko's tasks with a button that moves it to a new Ibiko chat; mini agents offline, online and API for maximum performance; other files and zip/unzip; the stress indicator's tap shows stress data and the agents working with code names | P12 | Built (Robolectric) | 0.81.0 |
| 123 | Obi makes plans, Ivie matches them with real-world behaviour and works on UI/UX, Ibiko executes when it is about building something; refresh every 5 s | P12 | Built (JVM) | 0.81.1 |
| 124 | Interpreter: non-English (Pidgin, Yoruba, French) put into English for the model and the answer back into the user's language, online and offline | P12 | Built (JVM + desktop model) | 0.82.0 |
| 125 | SMM part 1: sign in to Facebook, X, Instagram and Reddit in the app, several accounts each, limits against bans, the model searches with the accounts, social posts in fact checks | P12 | Built (Robolectric) | 0.83.0 |
| 126 | Read-aloud in the notification with pause; steps like Claude Code; standard check icons; remove 'Asking again'; optimise the bridge to the downloaded model; trending posts on X; social accounts in Connectors | P12 | Built (desktop engine measured) | 0.84.0 |
| 127 | Deep audit (from another tool) of the Java–C++ bridge and the brain: checked; KleidiAI enabled for the phone, cancel race fixed, the rest reported with reasons; one-gateway brain left for the user's decision | P12 | Built (emulator-verified) | 0.85.0 |
| 128 | Merge the two brains: one gateway every message goes through, Brain 2's ideas (checks before done) in it, its demo lanes left out with reasons | P12 | Built (Robolectric) | 0.86.0 |
| 129 | Delete every built-in 3D shape (user chose the 21 built-in shapes); 3D built by the AI only | P12 | Built | 0.87.0 |
| 130 | Delete every built-in 3D sample; the model designs with full measurements, Ibiko builds; hold messages while work runs; social switch, editable names, standard check, how-to; drag the sheet by its body; rename 'Writing the answer' | P12 | Built (desktop model measured) | 0.88.0 |
| 131 | Measurements asked or standard sizes; the brain turns plain words into commands; API models get a full design brief with coordinates; one route for social and builds; full-screen Blender-style design space; remove "I'm here…"; fix VoiceTuning.parse; the thinking star glances like an eye; replies in short parts between the steps with voice on each; handbook v2 | P12 | Built (API design untested without a key) | 0.89.0 |
| 132 | Notes between the steps written by the model itself (continue of "format my message like this, broken by processes") | P12 | Built | 0.89.1 |
| 133 | The app is crashing: a debug catcher that saves crash logs to the Downloads folder to copy | P12 | Built (cause pending the first report) | 0.89.2 |
| 134 | The app froze on Send (ANR report from the crash catcher): draw only the latest messages | P12 | Built | 0.89.3 |
| 135 | Compare (Ibiko steps vs Claude Code): finish instead of ending on a plan, fewer notes, no check rows | P12 | Built | 0.89.4 |
| 136 | Make the work notes like Claude: consolidated explanations, then the steps, not a one-liner per step | P12 | Built | 0.89.5 |
| 137 | New chat starts a clean chat for all three personas, not only the one on screen | P12 | Built | 0.89.6 |
| 138 | Full screen 3D with the message box inside; deep look at the render (real floor grid, tyres as rings); designs cut off at 3 minutes; Listen per part, one Copy at the end | P12 | Built | 0.89.7 |
| 139 | Device instead of phone everywhere; the model in use does every task (the API derives the measurements); 15 dp icons | P12 | Built | 0.89.8 |
| 140 | The device's model works the same as API models: explanations, steps and the full design process | P12 | Built | 0.89.9 |
| 141 | Busy API errors retried and named correctly; a new task no longer resumes an old plan (from the gas price screenshot) | P12 | Built | 0.90.0 |
| 142 | A notes record to help the offline brain remember, since it has a short memory | P12 | Built | 0.90.1 |
| 143 | Basic Google (Gmail with an app password, Google search with no key, no project) and Telegram | P12 | Built (device check pending) | 0.91.0 |
| 144 | Developer docs (Google-made Kotlin, Go, Dart and the rest from DevDocs) searchable by the offline model for coding with no internet | P12 | Built (Android docs not available offline) | 0.92.0 |
| 145 | The model uses shell commands to do the coding: Termux (a real Linux) as Ibiko's termux tool | P12 | Built (device check pending) | 0.93.0 |
| 146 | No Termux card: in a regular chat the brain turns the words into a command, runs it, and the model tells the result in plain language | P12 | Built | 0.93.1 |
| 147 | Telegram asked to save before signing in: the sign-in check fixed, Not yet added | P12 | Built | 0.93.2 |
| 148 | Check the repo after many changes and make sure they are wired properly: tests compile and pass, the APK builds, the engine context sizer is used | P12 | Built | 0.96.3 |
| 149 | Check PLAN.md, the conflicting merge, and large API models that do not work (only small ones did) | P12 | Built (device check pending) | 0.96.4 |
| 150 | Close PR #140; a custom API Claude Opus model was failing to answer | P12 | Built (device check pending) | 0.96.5 |
| 151 | Read the docs for unfinished builds; Claude runs through the custom API (Anthropic format), not the Anthropic card; stop building APKs | P12 | Built (device check pending) | 0.96.6 |
| 152 | Redesign Settings (no sliders a finger moves by accident, sections hidden until opened, proper defaults), providers and drawer; code viewer with colours and line numbers | P12 | Built (device check pending) | 0.97.0 |
| 153 | Media picker like the photo picker; every offline model listed (download/delete, memory needs), folded; offline models dropped from memory with an API; Ibiko's own offline brain that learns the interactions and acts fast | P12 | Built (foundation; device check pending) | 0.97.1 |
| 154 | Pages under the status bar; double reads and RAM; the API (or offline model) alone triggers app functions, the brain box never clashes with an API and keeps learning; drawer newest first; a deep analysis of the build | P12 | Built (device check pending) | 0.98.0 |
| 155 | Ibiko as a suit (the brain is the model, the user the pilot); functions only by machine code from the brain, never by words; arithmetic, language, comprehension; learning, evolving, deep thinking | P12 | Built (foundation; deep thinking is switched on per chat in the next version) | 0.99.0 |
| 156 | Each persona page runs its own job; the three personas as one curved toggle; a tools button by the mic (GitHub repositories, each persona its own tools); keyboard and drawer; swipe to open the drawer | P12 | Built (device check pending) | 0.99.1 |
| 157 | Ibiko Social Intelligence System: investigate posts, similar posts, public sentiment, own and target profiles, as tools the selected brain calls | P12 | Built (fake-provider tests; device check pending) | 0.99.2 |
| 158 | Add Ibiko's own intelligence to the model list; mark the pick clearly (radio circle with check, coloured row) | P12 | Built (Robolectric test; device check pending) | 0.99.3 |
| 159 | Posts on the user's behalf (scheduled, recurring), smart engagement; Ibiko 1 researches what it doesn't know, asks 👍🏽/👎🏽 and learns by intent | P12 | Built (JVM and Robolectric tests; device check pending). Shilling (undisclosed promotion, fake personas) declined | 0.99.4 |
| 160 | No emoji for rating: custom check and cancel icons | P12 | Built | 0.99.5 |
| 161 | Check for correct, cancel for wrong (keeps learning); build what's left on the list; keep the internet alive when minimised, keep replies for when the screen is opened, say when there's no internet | P12 | Built (JVM and Robolectric; device check pending). From the list: brain box shapes and the pilot profile built; parallel brains, splitting MainActivity, the SSH terminal and voice polish not done (see handbook §10) | 0.99.6 |
| 162 | Screenshot: Ibiko 1's wrong answers for "what is a pig", searching statements and "what have you learned" | P12 | Built (fixture from the live encyclopedia; device check pending) | 0.99.7 |
| 163 | Rating icons beside Copy and Listen; Ibiko's own model thinks with steps; Obi, Ivie and Ibiko as 3 pages of 1 chat; bedtime auto-learning | P12 | Built (JVM and Robolectric; a real night pending) | 0.99.8 |
| 164 | Learn only from usage, nothing saved as a test feature; swipe left to right opens the drawer; no swipe between persona pages | P12 | Built (JVM and Robolectric; device check pending) | 0.99.9 |
| 165 | Workload, Usage and the workers display wired properly; the shoe screenshot; icons on the tools and their inner settings | P12 | Built (JVM and Robolectric; device check pending) | 0.99.10 |
| 166 | Learning at any time, not only at night; formulas for language understanding, sentence formulation and intent to parse any information; a standard drag drawer | P12 | Built (JVM and Robolectric; device check pending) | 0.99.11 |
| 167 | Split MainActivity; Ibiko model code in one detachable folder with its own endpoint, linked to the app-functions file; easier taps; narrate work and end with a summary; Settings in step; the ohmmm sound | P12 | Built (JVM and Robolectric; device check pending). MainActivity split is partial: drawer, tools, model menu and Ibiko 1 moved; the rest still to do | 0.99.12 |
| 168 | The device hums and overheats while Ibiko runs a task: find and fix | P12 | Built (seven causes fixed from reading the code; not measured on a device) | 0.99.13 |
| 169 | Bigger models not as effective or fast as they should be; Ibiko 1 self-sufficient, self-learning, self-evolving per PLAN.md; learn from every interaction, conversation and social reading; grammar (nouns, pronouns, adjectives, verbs…); all formula code in one traceable folder | P12 | Built (big models 0.99.37; learner T1–T7 0.99.38, JVM tests; device check pending) | 0.99.38 |
| 170 | Check the Linux engine and wire it properly: full Linux UI and functionality | P12 | Built (real terminal device, xterm screen, one shell for the screen and chat; JVM tests; device check pending) | 0.99.40 |
| 171 | Deep audit and diagnosis of the codebase; device bugs: Ibiko 1 "Try again" loop, gpt-5 thinking 2 minutes, Send busy on Ivie, a reply on a page never asked, chats leaking into other chats, Ibiko's nonsense step words, a message with no response | P12 | Built (11 fixes, docs/AUDIT_2026-11-10.md; JVM tests; device check pending) | 0.99.41 |
| 172 | Find the Linux environment I built before this merge and upgrade it: one Linux in the app, not separate ones | P12 | Built (one entry point `LinuxHub.run` for chat, Ibiko, tasks and the Shell screen; Termux removed from the manifest; JVM tests; device check pending) | 0.99.42 |
| 173 | A map of steps for every task: plan first, understand the task by auditing the codebase, draw a plan, scrutinise it for loopholes, then build fast without circles; user additions go into the plan and it is re-checked; no rewriting code that already exists | P12 | Built (agent/Blueprint, enforced in Ibiko's tool loop; JVM tests; device check pending) | 0.99.43 |
| 174 | Make the Shell screen standard (screenshot of a standard terminal) | P12 | Built (welcome, ~ $ prompt, two rows of extra keys, long-press copy/paste; device check pending) | 0.99.43 |
| 175 | Dictation clears old words after a pause; fix the mic icon and the record effect; human-like voices and what to download; the Blueprint with several workers reading the codebase line by line at once and documenting it | P12 | Built (JVM tests; device check pending) | 0.99.44 |
| 176 | A post to Facebook went to a web search; auto post: post directly from my account on my command | P12 | Built (after the pilot's yes, Facebook and X; device check pending) | 0.99.44 |
| 177 | Update build.toml with gradle; the Scribe documents every progress; API answers switching to the local model; show the post with several versions to pick; tag people; comment under a post; comment on a user's every new post and chat with repliers; Ibiko 1 learns | P12 | Built except the unattended watcher and auto-chat (each comment still needs the pilot's yes); JVM tests; device check pending | 0.99.45 |
| 178 | Ivie for stories and scripts and Obi for reports use the plan mechanism; a task given to the wrong persona moves to the right one's chat as if never sent to the first; group chat opinion; a more flexible poster; earlier unbuilt tasks; icons and standard features for the social media manager | P12 | Built (hand-off, writing plan, Social accounts screen, poster choices; group chat proposed, not built; JVM tests; device check pending) | 0.99.46 |
| 179 | A toggle to allow auto post on the pilot's behalf; build the group chat | P12 | Built (JVM tests; device check pending) | 0.99.47 |
| 180 | The bottom persona toggle is no longer needed with the group chat (screenshot) | P12 | Built (buttons hidden in the group; the words choose who answers; JVM tests; device check pending) | 0.99.48 |
| 181 | Persona icon beside the name on replies; Listen reads only the response, in that persona's voice; African voices | P12 | Built (icons and names; Listen per persona; African voices ranked first; the voices themselves must be installed on the device; device check pending) | 0.99.49 |
| 182 | Switch completely to the group chat, permanently; build what was missing | P12 | Built (the group is the only chat; Share › Ibiko added). Still not built: several personas at once, post watcher, offline neural voices | 0.99.49 |
| 183 | Device bugs (screenshots): `<severity>0</severity>` as a reply; Ibiko's line under Obi's name; "Pull it now" searched on the web; Obi pointing to Termux; survey reading bookkeeping files; squeezed Workload line | P12 | Built (JVM tests; device check pending) | 0.99.49 |
| 184 | Convert every word command to a code command: no word activates a command, only the model reads context and intent | P12 | Built for every route that ran a function from wording while a model is in use (new codes §HO §WP §TT §RF §CO §DR §YS §CX). Sizing heuristics still read wording; with no model the wording routes remain | 0.99.50 |
| 185 | Tools in the group chat: all tools available, rows to a grid, a small switch top right of each card | P12 | Built (JVM test; device check pending) | 0.99.50 |
| 186 | Models dispatch multi workers during real work, on command | P12 | Built (`workers` tool, `agent/Squad`; parallel on an API, in turn on the device model; device check pending) | 0.99.50 |
| 187 | Deep look at the whole UI: every button and widget does and shows what it says | P12 | Partial (static check of switches and buttons, stale texts fixed, hand-off button fixed; not exercised on a device) | 0.99.50 |
| 188 | When done, compress all reports into one essay-like summary with the process at the top (samples) | P12 | Built (one process row, the answer under it; Ibiko's summary in prose; device check pending) | 0.99.51 |
| 189 | Tap the text body to show and hide the bottom icons (copy, listen and the rest) | P12 | Built (JVM; device check pending) | 0.99.51 |
| 190 | Ibiko learns without the check being tapped; learns the process behind answers, masters thinking steps, evolves | P12 | Partial (`ibiko1/Methods`: ways of reaching answers learned from every reply, strengthened, weakened and dropped; a procedural memory, not model training) | 0.99.51 |
| 191 | Fix the Capital Oracle Dependabot alerts | other repo | Built (jackson-databind 2.18.12, capital-oracle-projects PR #19 merged; that module did not compile before or after) | n/a |
| 192 | Stop command codes leaking into the conversation | P12 | Built (JVM test; device check pending) | 0.99.53 |
| 193 | Why is the APK 6 MB; embed knowledge so Ibiko 1 works on install; push size with intelligence data | P12 | Partial (answer given; `ibiko1/BuiltIn` with a 375-fact starter file and a builder script; the large dataset is not in yet) | 0.99.53 |
| 194 | Merge all personas into Ibiko with every function; trace Obi and Ivie out of the codebase | P12 | Partial (Ibiko answers everything when a model is in use; Obi and Ivie code not removed) | 0.99.53 |
| 195 | Agent organisation: Scribe, UCIX, UCUX, CPGC, ROLX, ARCH, plus roles from how organisations are structured; mini workers under agents; timed, focused work | P12 | Built (`agent/Org`, role and minutes on each part, hands, time limit, send-back; JVM tests; behaviour with real models not measured) | 0.99.53 |
| 196 | One record file a project, a folder a section, agents limited to it, exact resume point | P12 | Built for new records and for workers' file tools (shell commands are not confined) | 0.99.53 |
| 197 | Apply the same technology to features and functions across the app | P12 | Partial (the hands now also write long pieces section by section at once, `Squad.all`; other features still one call at a time) | 0.99.60 |
| 198 | No more Ibiko, Obi and Ivie: just one assistant | P12 | Superseded by 201 (the user then chose Ibiko as the one name) | 0.99.57 |
| 199 | A new addition to the plan is added to the old plan, never replacing it; a priority moves to next; only a finished step is edited (ticked) | P12 | Built (`Blueprint.kept` refuses a write that drops or unticks a step; Scribe appends; JVM tests) | 0.99.57 |
| 200 | Download more knowledge and build self-evolving | P12 | Partial (2,765 shipped facts with population; learning from every reply via `ibiko1/Methods`, Facts and Foresight; no large corpus, Wikidata still rate-limited) | 0.99.60 |
| 201 | Rename the entire app Ibiko; no persona names anywhere | P12 | Built for everything a person sees and everything the model is told; internal keys, class names and old chats keep the old words | 0.99.57 |
| 202 | A survey that breaks off restarts from zero on "continue"; the Scribe must hold lines covered, files done, next file, current and next task | P12 | Built (parts kept as read, SURVEY.md progress, resume reads only what is left; RECORD.md NOW section) | 0.99.57 |
| 203 | The audit results: sorted with the time and date, a comprehensive report | P12 | Built (map sorted by path and line, contents list, time made, time each part was read) | 0.99.57 |
| 204 | Long agent jobs run in the background while the user keeps talking, with no disruption | P12 | Built on an API (`sideReply`, `§ST` to pass an instruction to the job); with the device model only, a message still goes to the job | 0.99.57 |
| 205 | Fix every UI and UX properly; the composer mockup (Tools, Code, Media, Explore, More, "Ask … anything") | P12 | Built (hint and names in 0.99.57; the chips row in 0.99.58; JVM test) | 0.99.58 |
| 206 | Check the repo and the user's own changes; keep every document and the README up to date | P12 | Built (0.99.53–0.99.56 recorded; README rewritten; two tests red on main fixed) | 0.99.57 |
| 207 | Give the AI the ability (a formula) to be premeditative | P12 | Built (`formulas/Foresight` P1–P7: habits of what follows what, the look-before check for every brain, offers never acted on unasked; JVM tests) | 0.99.59 |
| 208 | Read the requests made while the session was down and implement them | P12 | Built: rows 197, 200 and 205 taken further in 0.99.60; the rest of that batch was already built (198–206) | 0.99.60 |
| 209 | Audit the chat screen UI/UX: two tools buttons, remake the Tools sheet, remake buttons, solidify the design | P12 | Built (renders checked night and day; one tools button; Tools sheet remade; header, logo and mode pill fixed; JVM tests) | 0.99.61 |
| 210 | Give the AI a formula to learn from its own interactions and responses | P12 | Built (`formulas/Reflection` R1–R7; JVM tests) | 0.99.61 |
| 211 | Remove the tools and attachment buttons from the composer; what goes there now | P12 | Built (the tools button went in 0.99.61; the paperclip replaced by a Think pill; Media attaches; JVM tests) | 0.99.62 |
| 212 | The GitHub icon is cropped | P12 | Fixed (ic_branch redrawn inside its frame) | 0.99.62 |
| 213 | A formula to coin all its knowledge together on a foundation of truth | P12 | Built (`formulas/Truth` G1–G7, `Ibiko1.foundation`; JVM tests) | 0.99.62 |
| 214 | Question every data it takes in and gives out: is this true or false? Add to the formula | P12 | Built (`formulas/Truth` G8, `BuiltIn.check`, `Facts.check`, in and out; JVM tests) | 0.99.63 |
| 215 | The humming sound from the speaker while the AI runs | P12 | Fixed (no audio stream unless something plays; `AmbientPadTest`) | 0.99.64 |
| 216 | It also saves false data and true data | P12 | Built (`formulas/TruthLedger`, Truth G9, Memory card; JVM tests) | 0.99.65 |
| 217 | Less CPU burning: quantum maths for the engine | P12 | Answered (quantum maths needs quantum hardware; the real levers listed) | 0.99.65 |
| 218 | Even the 0.6B takes 40 minutes to start replying: look deep into it | P12 | Fixed in code (hidden thinking off on the device, instructions cut from ~1,900 to ~560 tokens, no reload or release churn, first-word time shown; device check pending) | 0.99.66 |
| 219 | The same long request again (rename to Ibiko, Scribe resume, audits sorted with time and date, UI/UX, background jobs) | P12 | Checked: built in 0.99.57; names searched again and none shows; a message during a job on the device's model is now answered between steps | 0.99.67 |
| 220 | Fix the app package name | P12 | Built (ai.ibiko everywhere, the user's choice; native libraries rebuilt; JVM tests) | 0.99.68 |
| 221 | Design a custom logo: a futuristic, cute AI bot icon | P12 | Built (adaptive, monochrome and PNG icons from one vector design; render test) | 0.99.69 |
| 222 | Make sure the AI reads the .blueprint folder to see its progress on the current task after the device closes or the connection breaks | P12 | Built (`Survey.whereItStood`, `Survey.unfinished`, carry-on reads it first; JVM tests) | 0.99.71 |
| 223 | The icon is too simple: make a complex, much cuter one | P12 | Built (redrawn with body, waving arm, heart core, star antenna, kawaii eyes, nebula and orbit; render test) | 0.99.71 |
| 224 | The plan file is added to, never replaced; a priority moves to the next to-do; only a completed step is edited (ticked) | P12 | Built (`Blueprint.kept` on write and edit, Scribe sections; now shell commands that wipe a plan are refused too; JVM tests) | 0.99.72 |
| 225 | Why only 4 workers when Settings says 8: more workers for different tasks, not just readers | P12 | Fixed (the setting was never read; it now sets workers, readers and writers, up to 16; hands up to 8; JVM tests) | 0.99.72 |
| 226 | How would the system mark a plan that has been completed? | P12 | Answered (steps ticked "- [x]", phases UNDERSTAND to VERIFY; DONE is never set and ticks carry no date: proposed) | 0.99.72 |
| 227 | The send button sometimes gets stuck showing the stop sign | P12 | Fixed (redrawn on every stream change and at a job's end; rechecks each second while Stop shows; Robolectric test) | 0.99.73 |
| 228 | The bot icon is too common: a private, generic and unique icon | P12 | Built (an abstract mark of three petals of light round a spark, in a broken orbit; render test) | 0.99.74 |
| 229 | Agents working in a project may read only that project's backup files, not every project's | P12 | Built (own notes and record only; others refused in file tools, listings, search and named shell paths; JVM tests) | 0.99.75 |
| 230 | Clicking GitHub takes time: a loading wait that prevents double clicks; every dialog to one standard | P12 | Built (waiting dialog with Cancel and a guard; Theme.Ibiko.Dialog for all 36 dialogs; renders) | 0.99.76 |
| 231 | Use the pilot's images: the app icon, and the GIF as the in-chat thinking animation | P12 | Built (adaptive icon from the art, themed eyes layer, PNGs; the GIF plays on the working line; tests) | 0.99.76 |
| 232 | Downloading anything, even in the terminal, shows progress in the notification bar | P12 | Built (Transfers + DownloadNotice; Linux, GitHub clones, terminal downloads; JVM tests) | 0.99.77 |
| 233 | A report the AI is writing suddenly disappears: find and fix it | P12 | Fixed (kept at each step's end and at the work's end; a tool call hides only itself; no duplicate; Robolectric test) | 0.99.77 |
| 234 | Start building more in-app knowledge (ask for data if needed) | P12 | Built (2,122 more facts from open sources and the project; matching improved; JVM tests); no data needed from the pilot for this round | 0.99.78 |
| 235 | The terminal compared with Termux (screenshots): text cut off, large text, dim keys, a copy dialog | P12 | Fixed (short greeting, 10 sp default, bright keys, Termux-style selection with a floating Copy/Paste/More bar) | 0.99.78 |
| 236 | Download interaction and comprehension data (ConceptNet, WordNet, …) | P12 | Built (Open English WordNet 2024 and ConceptNet 5.7 English as sorted tables; training sets such as SQuAD and GLUE not shipped, as they teach a model rather than answer) | 0.99.79 |
| 237 | The master comprehension and knowledge engine blueprint (8 engines, UCL) | P12 | Built in part (v1: intent, grammar of actions, word senses, context and pronouns, object parts, offline lookups; next: ATOMIC intents, reasoning, action planning) | 0.99.79 |
| 238 | ConceptNet downloaded on the first start, shown only as "Downloading app files"; the app does not open if it fails | P12 | Built (AppFiles, AppFilesActivity with Try again or Close) | 0.99.80 |
| 239 | The Ibiko brain runs code tasks and controls the app's commands; README updated | P12 | Built (CodeTasks as approved shell commands; every plain-sentence app control in Commands) | 0.99.80 |
| 240 | A human voice package instead of the high-pitched device voice that couldn't be linked | P12 | Built (Kokoro and Piper on the device through sherpa-onnx; device speech engines selectable) | 0.99.82 |
| 241 | Clean up speech that starts when it shouldn't; one voice, a man's or a woman's | P12 | Built (spoken only for a reply just asked for, on screen; no personas or pitch lift) | 0.99.82 |
| 242 | Every needed app file downloaded on startup (not the models), with a progress bar | P12 | Built (the table and the voice, one bar; a bar for voices in Settings) | 0.99.82 |
| 243 | The agent's plan names the folder a repository is pulled into, and the agent works there | P12 | Built (PLAN.md '## Repository'; a clone in memory/projects is readable) | 0.99.82 |
| 244 | The code viewer crashes the app on a large code file | P12 | Fixed (by file path, read off the main thread, a list of lines) | 0.99.83 |
| 245 | Structure the planning carefully not to make mistakes | P12 | Built (each step has a check and is ticked only after it passed; order and repository checked) | 0.99.83 |
| 246 | Add a permission check for approval (none seemed to exist) | P12 | Built (changing connector calls ask too; "Allow for this task" instead of a silent always; asking switched back on once) | 0.99.83 |
| 247 | The working face: zoomed out, curved edges, blinking, moving sideways smoothly, alive and not a short GIF; different looks for thinking, reading, writing, working, replying | P12 | Built (LiveFace drawn live, random blinks, a spring, five looks) | 0.99.84 |
| 248 | Zoom out the app icon with space for a background colour | P12 | Built (face at 54 of 108 units on teal; PNGs, round, store and themed layers) | 0.99.84 |
| 249 | Make the app icon a little smaller (screenshot) | P12 | Done (the face 46 of 108 units, a ring of teal round it in a circle launcher) | 0.99.85 |
| 250 | A download the network broke carries on instead of starting over | P12 | Built (Range requests, the part kept, retried by itself and on Try again) | 0.99.85 |
| 251 | Quotes, abilities and facts running across the screen while the app files download | P12 | Built (WaitLines, a new line every 6 s) | 0.99.86 |
| 252 | Enforce background running: Ibiko never dies unless the user closes it; Back doesn't quit | P12 | Built (KeepAlive service, Close in the notification, battery exemption asked once, Back to the background) | 0.99.86 |
| 253 | Keep the download screen busy with facts about the app, without disturbing the download | P12 | Built (a slow, looping roll of 20 app facts under the running line) | 0.99.87 |
| 254 | Break the Settings list into separate screens (whichever costs the device less) | P12 | Built (a list of sections; each section its own page, built only when opened) | 0.99.88 |
| 255 | Listen in the chat still plays the old voice | P12 | Fixed (sentences wait for the human voice while it loads; the device voice only when it fails, and why is shown) | 0.99.89 |
| 256 | Changing the voice refreshes Settings and folds what was open | P12 | Fixed (only the voice options redraw; a crash in the choice chips found and fixed) | 0.99.89 |
| 257 | No voice speech overlaps anywhere in the app | P12 | Built (AudioGate: one sound at a time; audio focus) | 0.99.92 |
| 258 | Faster voice start; keep it on standby | P12 | Built (one shared engine loaded at start, warmed up, kept loaded) | 0.99.92 |
| 259 | All voices the same, no fallback; a floating play control | P12 | Built (no device-voice fallback; PlayerBar with play/pause, 15 s back and forward, speed, time, close) | 0.99.92 |
| 260 | A tap on a part shows only its Listen and Copy | P12 | Built | 0.99.92 |
| 261 | Near the bottom, keep the chat at the bottom as new text comes | P12 | Built (within 120 dp it follows; scrolled up it stays) | 0.99.92 |
| 262 | Settings as their own fragments or activity files with XML; the Speak button (screenshot) | P12 | Partial (Settings: 13 Activities, each with its own XML, 0.99.95; the Speak button next) | 0.99.95 |
| 263 | Why is the response cut | P12 | Fixed (a reply cut by its length limit is carried on, whatever the limit) | 0.99.93 |
| 264 | Not every generation is 3D: remove 3D entirely; image generation from the API and other brains; an image tool in the app | P12 | Built (3D removed from functions, tools, routes and prompts; ImageMaker and §IM for every brain) | 0.99.93 |
| 265 | The app icon background should match the app's colour | P12 | Done (the accent purple) | 0.99.93 |
| 266 | The voice glitched, reading jargon; make it stable | P12 | Fixed (only sayable words reach the voice; each sentence is made whole before it plays, in short parts, with clean samples) | 0.99.94 |
| 267 | Build the image tool in the app, so tools can draw designs by command | P12 | Built (design/SvgDraw: the brain writes SVG, Ibiko draws it on the device; icons and logos never need an image service) | 0.99.94 |
| 268 | Keep building the learning mind across APIs and models | P12 | Partial (Ibiko 1 already learns every API answer and order through BrainBox; nothing new learned from pictures yet) | 0.99.94 |
| 269 | Smaller working face; head squeezed in height, not cut; calmer thinking eyes, smiling eyes on the answer, eyes by the running process | P12 | Built | 0.99.94 |
| 270 | Build the image generation tool in the app (a gateway refused gpt-image-1) | P12 | Built (the design board; a gateway is not sent a model it has not named) | 0.99.94 |
| 271 | The player: a loading ring while the voice starts, buttons spaced, sitting down by the Tools row | P12 | Built | 0.99.94 |
| 272 | The process sheet rows are too far apart | P12 | Fixed (rows closer together) | 0.99.94 |
| 273 | Too many hanging notifications | P12 | Fixed (one notification while Ibiko runs; thinking and downloads show inside it) | 0.99.94 |
| 274 | Split the MainActivity god class; move methods to classes | P12 | Partial (6,262 to 5,430 lines; seven classes; onCreate and the turn loop next) | 0.99.95 |
| 275 | Boost speed and efficiency across the app | P12 | Built (steps sheet cached and ticked in place; no idle background service; patterns built once) | 0.99.95 |
| 276 | Image generation in the background, pasted when complete | P12 | Built | 0.99.95 |
| 277 | Is "Ibiko is running" necessary? If not, remove it | P12 | Removed (a notification only while work runs) | 0.99.95 |
| 278 | Processing sheet: larger text, easy to tap | P12 | Built | 0.99.95 |
| 279 | Does Settings apply to Ibiko 1? | P12 | Fixed (web look-ups and learning now apply to it too) | 0.99.95 |
| 280 | Folders strictly per chat; no chat sees another's files; the workspace shows only this chat's; deleting a chat deletes its files | P12 | Built (files/ibiko/chats/<chat>; Linux commands mounted to that folder only) | 0.99.96 |
| 281 | The model only imagines the image; the image generator generates it | P12 | Built | 0.99.96 |
| 282 | Pulled data out of the memory folder; memory only for that chat's memories | P12 | Built (repos/ and memory/ apart; records in memory/records) | 0.99.96 |
| 283 | Treat Ibiko 1 as the other models, all in one folder with entry points, to publish it alone | P12 | Built (ibiko1/ library module, Ibiko1Model, README; its tests still in the app) | 0.99.96 |
| 284 | Too jammed information, hard to read: fix it across the app | P12 | Built (line spacing, padded rows, dividers, larger sheet text) | 0.99.96 |
| 285 | Default settings at their best for standard performance | P12 | Built (workers 4; others reviewed) | 0.99.96 |
| 286 | The animated Ibiko face on the splash screen | P12 | Built | 0.99.96 |
| 287 | A Retry button | P12 | Built | 0.99.96 |
| 288 | During the splash, set up in the background so the app runs smoothly; sync the speech, no delay | P12 | Built (Warmup while the splash shows; a short first piece of speech, played piece by piece) | 0.99.97 |
| 289 | Make Ibiko 1 sound in reasoning: find what is missing, research and improve | P12 | Built (Reasoner: compare, rank, count, list, chain, compute; values shown; false premises corrected) | 0.99.97 |
| 290 | Workers can be deployed to multitask, not just read files | P12 | Built (any task; they run during planning and in read-only runs too) | 0.99.97 |
| 291 | Embed Ibiko with a critical reasoning formula for deeper thinking | P12 | Built (formulas/Critical K1–K12, in Ibiko 1 and for every brain; Deep Thinking passes deepened) | 0.99.98 |
| 292 | Why is the name Dvine still traceable | P12 | Fixed (renamed everywhere in code, files and docs; settings carried over; the GitHub repository names are the owner's to change) | 0.99.99 |
| 293 | CodeAssist errors: ai.ibiko.dvine1 cannot be resolved; targetSdk 35 edge-to-edge warning | P12 | Fixed (module.toml builds Ibiko 1's sources into the app; the warning needs nothing, insets are handled) | 0.99.100 |
| 294 | Improve the Linux UI, UX and performance | P12 | Built (run-batched drawing, repeating arrow keys, key feedback, screen kept on) | 0.99.100 |
| 295 | Same question twice: same answer or different? | P12 | Answered (Ibiko 1: the same answer unless it learned in between; API models: the same substance, different wording) | 0.99.100 |
| 296 | Why is Ibiko responding like this (gateway errors; a robotic greeting next to GPT-5's) | P12 | Fixed (an unreachable API falls back to Ibiko 1; a warm greeting by name and time of day) | 0.99.100 |
| 297 | CodeAssist: ai.ibiko.ibiko1 cannot be resolved (again) | P12 | Fixed (Ibiko 1 in the app's own sources; tools/export_ibiko1.sh for the standalone library) | 0.99.101 |
| 298 | Ibiko learns the live chat, including the thought process, adaptable to it | P12 | Built (formulas/Thinking: the moves of the reasoning shown, followed in its own thinking) | 0.99.101 |
| 299 | Speak professionally on what it doesn't know; no fixed reply repeated | P12 | Built (Phrasing: varied, specific replies; a repeated question recognised) | 0.99.101 |
| 300 | A deep diagnosis plan format so audits make no untrue claims; a guiding map for excellent results | P12 | Built (agent/Diagnosis D1–D10, report format, claims checked against the files) | 0.99.102 |
| 301 | Give Ibiko's brain a surgical-precision formula for processing and analysing data | P12 | Built (formulas/Analyst A1–A10) | 0.99.102 |
| 302 | The voice pauses about 10 s at a full stop | P12 | Fixed (the next sentence is made while this one plays) | 0.99.102 |
| 303 | Replace the model-naming status lines; Ibiko is the first channel and takes accountability | P12 | Built (no model names in status lines; Ibiko's own short lines) | 0.99.102 |
| 304 | A voice for a child, to suit the cute bot | P12 | Built (Sky, played higher: "A child's voice" in Settings › Voice) | 0.99.103 |
| 305 | Make the speech while the message shows, keep it, clear it after a while, make it again when wanted | P12 | Built (voice/SpeechCache: one day, 60 MB; made again on Listen after that) | 0.99.103 |
| 306 | Models page: list every model; Use and Test for downloaded ones, Download for the rest | P12 | Built (always listed; Test on any downloaded model) | 0.99.103 |
| 307 | Test is faster than chat: use the same simple channel for chat; prepare the model ahead as the voice is | P12 | Built (everyday messages skip the extras; the model loads and reads its instructions while you type) | 0.99.103 |
| 308 | Several models in the model menu: add, switch when wanted; an Edit button beside the model in use | P12 | Built (every model kept for a provider listed; Add a model; Edit opens that provider) | 0.99.104 |
| 309 | A button to fetch the model list from the API | P12 | Built (model menu and AI providers; OpenAI, Anthropic, Gemini and compatible gateways) | 0.99.105 |
| 310 | Material, responsive icons in the app and Settings, the entire app | P12 | Built (every icon Material Symbols Rounded; Settings section icons; ripples and 44 dp targets) | 0.99.105 |
| 311 | Restyle every icon as the app's own, in the app colour | P12 | Built (two-tone Ibiko set, accent colour, both themes; docs/ibiko-icons.png) | 0.99.106 |
| 312 | Make all icon sizes match perfectly | P12 | Built (two sizes only: 24 dp on its own, 18 dp beside words; checked by IconSetTest) | 0.99.107 |
| 313 | One repo chosen in the GitHub tool, but the agent picked every repo on the account | P12 | Fixed (agent/RepoScope: only the chosen ones, and any named in the message) | 0.99.108 |
| 314 | Exiting a picker and other cancels send a reply | P12 | Fixed (cancels are silent) | 0.99.109 |
| 315 | Left the page and came back: the chat was empty, then came back | P12 | Fixed (a long job's steps no longer fill the drawing window) | 0.99.109 |
| 316 | Make the speech when the message lands, Listen dimmed until ready, keep 7 days | P12 | Built (latest reply; 7 days, 300 MB) | 0.99.109 |
| 317 | A hard limit on files and folders so one chat's files never reach another | P12 | Built (chat commands in the chat's own Linux with its own /root and /tmp) | 0.99.110 |
| 318 | The process icon in the same head shape as the app icon | P12 | Built (LiveFace measured from the icon art) | 0.99.110 |
| 319 | Every message has an id; its speech is attached to it, played in order; deleted with the chat | P12 | Built (voices/said/<chat>/<message>/) | 0.99.110 |
| 320 | Teach the AI to ship batch by batch to the git repository | P12 | Built (agent/Shipping S1–S9) | 0.99.111 |
| 321 | Teach it to work like Claude Opus: how it thinks and moves through tasks | P12 | Built (agent/WorkingMethod W1–W12) | 0.99.111 |
| 322 | The head more of a rectangle, not full size | P12 | Built (LiveFace: 74% of its square, height 80% of the width) | 0.99.112 |
| 323 | IDs on messages and sentences, tags registered for them; think by the codes and read only the words they pull | P12 | Built (learn/Codes; chat recall and learned answers by code) | 0.99.112 |
| 324 | Separate what goes into the plan file from questions and requests sent while the agent works | P12 | Built (agent/Intake I1–I6; only project changes reach the plan) | 0.99.113 |
| 325 | Multi-agents and workers on every task | P12 | Built (always on; one-part jobs get a checker) | 0.99.114 |
| 326 | A probability formula for thinking and deciding | P12 | Built (formulas/Probability P1–P10) | 0.99.114 |
| 327 | The whole Ibiko mind thinks in ids of texts and sentences instead of full words | P12 | Built (learn/Symbols; Facts, BrainBox, BuiltIn, Codes look up by id) | 0.99.115 |
| 328 | A word and sentence identifier: identify, attach, save the ids as the conversation | P12 | Built (learn/Identifier; memory/ids.tsv per chat) | 0.99.115 |
| 329 | Learn API answers, compare them with what is kept, mark the kept one false when other answers keep coming, learn the new | P12 | Built (learn/Corroborate V1–V7 in the brain box) | 0.99.116 |
| 330 | Id the whole English dictionary; the brain thinks in short codes; an interpreter turns codes to words and back | P12 | Built (learn/WordBook, learn/Shorthand) | 0.99.116 |
| 331 | The space between words gets a code too | P12 | Built (space, new line and tab have fixed codes; text comes back exactly) | 0.99.116 |
| 332 | A double space is a code of its own | P12 | Built (one code for two spaces; three = double + single) | 0.99.117 |
| 333 | New smaller characters never used before, in place of codes; numbers in the code | P12 | Built (learn/Glyphs on Unicode private-use characters; numbers by value) | 0.99.118 |
| 334 | Show the text in the image as the codes the AI sees | P12 | Done (sample given in chat) | 0.99.118 |
| 335 | Keep the recurring codes across answers as the meaning (football → game); form its own thoughts | P12 | Built (learn/Concepts N1–N6) | 0.99.118 |
| 336 | A thought process: codes re-evaluated, brain sections working together, shown on the chat screen | P12 | Built (learn/Mind M1–M5; steps shown while thinking) | 0.99.118 |
| 337 | Replace the old brain structure with the code brain | P12 | Partial (the brain's knowledge files are kept in codes: brain box, concepts, facts, knowledge, lexicon; small state files and the learning log stay plain) | 0.99.119 |
| 338 | Open to growth: new characters as it learns words, the same across users' engines | P12 | Built (ids from the word's own letters; unknown words spelled in packs; a pack names its dictionary) | 0.99.119 |
| 339 | Convert a 100 kB pool of words to codes and compare sizes | P12 | Done (50% packed, 33% packed and squeezed, for plain prose) | 0.99.119 |
| 340 | Compare (screenshot: "What have you learned?" lists junk; later short messages unanswered) | P12 | Partial (junk no longer kept and cleaned on read; every message answered in the JVM; the unanswered messages fit the main-thread stall fixed in 0.99.121, to be confirmed on the device) | 0.99.122 |
| 341 | Build pack format 2, and convert old stored replies and knowledge to the codes | P12 | Built (codes by use, fingerprint, checksum; stores and chat conversations converted at start, exact or kept aside) | 0.99.123 |
| 342 | The whole brain built around the new language, and its name | P12 | Built (IBK, Ibiko Code: every store and log in it; the few plain files named with the reason) | 0.99.124 |
| 343 | Decompile a llama engine and rebuild it with the shrinking formula (less RAM, smaller, faster) | P12 | Not built (llama.cpp is already in the app as source; IBK shrinks text, not a model's weights; the levers for RAM and speed are quantisation, the cache's type and the context length) | 0.99.124 |
| 344 | Turn on KV cache compression and add the 4-bit model; the speed percentages | P12 | Built (8-bit cache at every size; Qwen3 1.7B Fast, Q4_0; measured: Q4_0 writes 64% faster, the cache saves 4-14% memory and delays the first word about 17%) | 0.99.125 |
| 345 | Merge the Q4_0 model into Ibiko's own brain; is it trainable; make it evolvable | P12 | Built (Ibiko 1 asks the device's model for what it doesn't know; the frozen 4-bit weights evolve by trained LoRA adapters, exported data, install and take back; training itself is off the device, docs/TRAINING.md) | 0.99.126 |

## Still open, in the order that matters

1. Install 0.45.2 on a phone and confirm the chat screen stays open.
2. Run `gradle assembleDebug` and replace the 0.26.2 APK only if that build succeeds.
3. Do not build a mesh from random web photos. That stays not built until the cameras are known.
4. Google and logged-in social search stay not built until there is a real key and a real login. Do not fake them.
5. The backend stays deferred.

## How to read a row

- **Built** — the path is in the app and does the thing the row says, with the limit in the last column.
- **Partial** — some of the request is real. The last column says what is missing.
- **Not built** — do not demo it as if it works.
