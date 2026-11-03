# DVINE — every prompt, built or not

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
| 42 | `gradle assembleDebug` and a phone smoke test | P4, P8 | Partial | 0.47.8: `gradle assembleDebug` and the 226 JVM tests pass in a cloud session with the Android SDK; the APK is `releases/dvine-0.47.8-debug.apk`. No phone smoke test from here |
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

| 54 | Ibiko as the Claude Code of DVINE, with that flexibility | P12 | Built (JVM) | 0.51.0: a step-by-step agent with files, search, shell commands (asked first, or Auto-accept), web pages and connectors, in its own folder |
| 55 | A cheaper model for simple questions, a stronger one for big tasks, switched automatically | P12 | Built (JVM) | 0.51.0: a fast and a strong model per API; real work gets the strong one and a full answer |
| 56 | Fix the attach icon | P12 | Built (rendered) | 0.51.0: a paperclip matching the mic |
| 57 | Remove the word "brain" from the interface | P12 | Built | 0.52.0: screens, notifications and spoken lines say "model" |
| 58 | DVINE learns from every interaction with the custom APIs to build its own | P12 | Built (JVM) | 0.52.0: requests, replies, models and Ibiko's steps kept on the phone; used as examples for the phone's model; exported as a fine-tuning file. Hidden reasoning isn't returned by providers, so it can't be kept |
| 59 | Connect to GitHub: push, pull, commit | P12 | Built (JVM) | 0.52.0: Ibiko clones, shows status, pulls, commits and pushes, makes branches and opens pull requests, with the GitHub connector's token |
| 60 | Fix the voice lag (streaming listening with quick end of speech, the model's words to the voice a sentence at a time) | P12 | Built (JVM) | 0.53.0: on-device recognizer, turn closed 0.85 s after the last word, no routing call for real work, the reply shown and spoken sentence by sentence as it is written. Not measured on a phone |
| 61 | Fix the keyboard covering the fields | P12 | Built (Robolectric) | 0.53.0: the providers sheet and the other settings screens move up above the keyboard |
| 62 | The message box loses focus after Send and while the model works | P12 | Built (JVM) | 0.54.0: the chat list no longer takes the focus when it scrolls |
| 63 | Show what DVINE is doing step by step, not just "thinking" | P12 | Built (rendered) | 0.54.0: a live steps panel that folds into "Worked for Ns" |
| 64 | Voice replies automatic | P12 | Built | 0.54.0: read aloud by default; Always / After the mic / Never in Settings |
| 65 | The mic puts the words in the box to edit, not send | P12 | Built | 0.54.0: dictation into the message box |
| 66 | Not Claude's exact colours | P12 | Built (rendered) | 0.54.0: DVINE's own palette, violet accent |
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
| 94 | Server as an online model; offline memory 8,192 customizable with slowness advice; GPU speed | P12 | Partial | 0.64.0 + backend: /api/v1/llm relays to llama-server, DVINE server provider signs in with the session; memory setting and advice built. GPU (Vulkan) not built: building upstream code was refused in the cloud session |
| 95 | Screenshot: "too long for the model's memory", a progress line as the answer, "My model is ready" inside a reply | P12 | Built (JVM) | 0.64.1: the offline take-over fits its memory and keeps the API's reason; a failed request after "are you done?" says so; download-finished is a note |
| 96 | Interpret every message by meaning, not words: a rephrased "are you done?" must still be understood, converted to the command and sent on | P12 | Built (Robolectric) | 0.65.0: turn/Meaning reads short messages into the commands DVINE runs, acts on their wording, remembers each phrasing |
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
| 109 | Fix this (Ibiko stuck 209 s with an 'ibiko:' step); make the typewriter faster like ChatGPT; memory review from the first build files | P12 | Built (Robolectric + desktop model) | 0.70.0: What DVINE remembers; small-model answers land at once; ~500 chars/s word typewriter |

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
