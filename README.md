# Ibiko (Android)

<img src="docs/brand/ibiko_icon_512.png" width="128" alt="Ibiko icon: a black rounded face with two glowing cyan eyes and circuit traces, on the app's purple">

Ibiko is one AI assistant on your Android device. There are no personas to pick and no other names: you talk to Ibiko, and Ibiko talks, researches, writes, designs in 3D, and writes and runs code on the device.

The app's package is `ai.ibiko` (since 0.99.68; before that `ai.ibiko`, so it installs as a new app beside an older build, and the model is downloaded again). The repository keeps its old name, `Dvine-ai-frontend`. Everything a person sees says Ibiko.

- Build plan: [`docs/IBIKO_MASTER_BUILD_HANDBOOK.md`](docs/IBIKO_MASTER_BUILD_HANDBOOK.md)
- What was built in each version, with the tests that prove it: [`docs/BUILD_STATUS.md`](docs/BUILD_STATUS.md)
- Every request made so far and whether it is built: [`docs/IBIKO_ALL_PROMPTS_STATUS.md`](docs/IBIKO_ALL_PROMPTS_STATUS.md)
- The functions a model can use: [`docs/APP_FUNCTIONS.md`](docs/APP_FUNCTIONS.md)
- What is planned, newest first: [`PLAN.md`](PLAN.md)

## How it works

**One chat.** Every message goes to Ibiko. Over the message box, quick chips open Tools, Code (files and the shell), Media (attach), Explore (social accounts, connectors) and More (settings, chats, memory, workload). With a model in use (one of your API providers, or a model downloaded to the device), the model reads what you mean and Ibiko acts on it through function codes (`§SR` search, `§WK` start the agents, `§MF` make a file…). Your own words never run a function by themselves, and the codes are never shown in the chat.

**Brains.** Pick one from the header:
- an API model (OpenAI-compatible or Anthropic), with a backup in order;
- a model downloaded to the device (Qwen3, Llama, Gemma, Phi GGUF files), working offline;
- Ibiko 1, the app's own learning brain, which needs no model.

**Voice.** Ibiko speaks in one voice, a man's or a woman's, as chosen in Settings › Voice; there are no personas. The voice is human-sounding and runs on the device with no internet (`voice/NeuralVoice`, sherpa-onnx 1.13.8 in `src/main/jniLibs`, its Java API in `com/k2fsa/sherpa/onnx`, Apache 2.0):
- Kokoro (82M, Apache 2.0): 11 voices, men and women, American and British, downloaded on the first start; the voice within it is chosen in Settings;
- Piper Ryan and Piper Amy: one fast voice each (a man's, a woman's), downloaded from Settings with a progress bar, for slower devices;
- the device's own voice, with any speech engine installed on the device (another voice app) chosen in Settings.

While the human voice loads (a few seconds after Ibiko starts), what is to be read waits for it; only if it can't start does the device's voice read, and Settings › Voice says why. There is no pitch shifting: each voice speaks at its own pitch, a short pause after each sentence. The packs come from the sherpa-onnx model releases as `.tar.bz2`; `voice/Bzip2InputStream` and `voice/TarUnpack` unpack them in pure Java after the checksum. Ibiko reads a reply aloud by itself only while the chat is on screen and only a reply to something you asked in the last three minutes (then the "Read replies aloud" setting decides); a morning report, a job that ends in the background, a note or a message that waited is shown, not spoken. The play button under a reply always reads it.

**Ibiko 1 runs code and the app.** With no model at all, Ibiko 1 (`ibiko1/formulas/Commands` and `CodeTasks`) turns a clear request into one of the app's function codes and carries it out through the same gate every model uses:
- code tasks become one shell command in the built-in Linux, shown for your yes before it runs:
  - a command typed as it is (`git status`, `ls -la`, `python3 app.py`, or anything in backticks or after `$ `);
  - code pasted in a code block with "run this" (Python, JavaScript, C, C++, shell), written to `/work` and run, with the language installed first when missing;
  - "list my files", "read the file notes.txt", "clone owner/repo", "install nodejs", "make a folder called demo", "is python installed", "how much space is left";
- bigger code work ("fix the login bug in my app", "build a website") goes to the agents (`§WK`), which need a model;
- every app control: a new chat, settings, retry, search, photos, files, sharing, scheduled posts, a 3D model, a long piece of writing, thinking together, reading the workspace, carrying on a task, yes and cancel, plan mode, auto mode on and off, calculating, unzipping and finding zip files.

A tool switched off for the chat is never used.

**Agents for real work.** Code, files, repositories and commands go to the agent organisation (`agent/Org`):
- ARCH plans and CPGC coordinates (the lead), ROLX keeps time, the Scribe records every step;
- specialists take parts: UCIX (interface), UCUX (what each control does), CORX (engine), DATX (data), QATX (tests), SECX (security), REVX (review), RSRX (research), DOCX (docs), RELX (release);
- each part has a time limit; each agent can start up to four mini workers on pieces it designs;
- on an API they run at the same time.

**Long jobs run in the background.** While an agent job runs, you can keep talking: Ibiko answers your new message beside the job. If the message is meant for the job, it is passed to it (`§ST`) and the job reads it on its next step.

**Nothing is read twice.**
- The Scribe keeps one record file per project (`memory/projects/<project>/RECORD.md`): the state, the last step and its time, the last change and its file, what is done and what is pending. A new round starts from that point.
- A codebase survey keeps every part it has read (`.blueprint/<project>/pieces/`) and its progress (`SURVEY.md`). After a broken connection or "continue", only the parts not yet read are read. The map (`MAP.md`) is sorted by file and line, with the time each part was read.

**One folder per repository.** A repository is cloned into `memory/projects/<name>` (the folder the workspace screen shows), and the agent's PLAN.md names it under `## Repository` ("owner/name -> memory/projects/<name>"): the first step pulls it there, and every read, edit, command and push uses that folder. A cloned repository there is code any task may read; only another project's record files (RECORD.md, RECOVERY.md) stay private.

**Ibiko's face.** While Ibiko works, the chat shows its face (the icon's face, `stage/LiveFace`) drawn live: it blinks at random, its eyes glide, it breathes, and its look follows the work: thinking, reading, writing, running commands, replying. Nothing loops, so it looks alive.

**Always running.** Ibiko keeps running in the background with a quiet "Ibiko is running" notification, until you close it: the notification's Close Ibiko, or swiping it away from the recent apps (`main/KeepAlive`; Settings › Coding › Keep Ibiko running in the background). Back never quits: it sends Ibiko to the background like Home. Ibiko asks once to be left out of battery saving so the system doesn't freeze it.

**Settings.** Settings opens as a list of sections (Profile, Chat, Voice, Models, Coding, Advanced and the rest); each opens on its own page and only that page's controls are built, so Settings opens at once and keeps little in memory.

**Listening.** While a reply is read aloud, a floating player sits over the chat: play or pause, back and forward 15 seconds, speed (1x to 2x), the time and close. One sound plays at a time anywhere in Ibiko (`voice/AudioGate`); other apps pause while Ibiko speaks, and a call pauses Ibiko. The voice is loaded once as Ibiko starts and kept ready.

**Pictures and designs.** Ask for an icon, a logo, a badge, a diagram or a chart and Ibiko draws it itself: the brain in use writes the design as SVG and Ibiko's design board (`design/SvgDraw`) draws it on the device and shows it in the chat, keeping the .svg to edit later. No image service is needed. For a photo-like picture Ibiko uses an image model on your own provider (OpenAI or Google Gemini, or a gateway once you name its image model); with none, the board draws it (`llm/ImageMaker`, the §IM function every brain can use). Pictures are made in the background: a card stands where the picture will go and the chat carries on. The brain only imagines the picture; the generator makes it. There is no 3D.

**Each chat its own files.** A chat's folder (`files/ibiko/chats/<chat>/`) holds `repos/` (what it pulled), `memory/` (what Ibiko keeps about that chat) and `images/`. A chat sees and changes only its own folder, the workspace screen shows only it, and deleting the chat deletes it.

**Ibiko 1 on its own.** Ibiko's own brain is one folder, `app/src/main/java/ai/ibiko/ibiko1/`, with an entry point like any model (`Ibiko1Model`). `sh tools/export_ibiko1.sh` makes it a standalone library, with its knowledge, for another app (see its README). It reasons over what it knows: it compares, ranks, counts, lists, follows a chain ("the currency of the country whose capital is Accra") and works numbers out. Each answer says the values it used, and a question built on a false premise is corrected.

**Asking first.** Ibiko asks before a shell command, a push, a pull request and any connector call that changes something (creating, deleting, posting, merging, sending); reading, searching and editing files in the workspace go ahead. "Allow for this task" lets the rest of one task go ahead; Settings › Coding › Run commands without asking turns asking off, and the steps then say so. Every plan step names its files and its check, and is ticked only after the check passed.

**Plans grow.** A plan file (`PLAN.md`) is only added to. New work goes after the existing steps, or right after the last finished step when you call it a priority. A finished step is ticked, never deleted. A write that would drop a step is refused.

**Built-in knowledge.** `app/src/main/assets/knowledge/` ships what Ibiko knows on install, with no internet:
- `world_facts.tsv` (2,765 facts): the 194 countries (capital, region, currency, official languages, area, population with its year, people, neighbours, coastline) and the 118 elements;
- `country_more.tsv` (989): each country's calling code, internet domain, region, ISO codes and UN membership;
- `elements_more.tsv` (1,027): each element's atomic mass, category, state at room temperature, discovery, melting and boiling points, density, electron configuration, group and period;
- `local_facts.tsv` (106): Nigeria's 36 states and the FCT with their capitals; the eight planets (order from the Sun, diameter, year, kind);
- `base_knowledge.json`: everyday talk, maths, units, wisdom;
- `shards/dictionary_en.json`: the English dictionary (WordNet);
- `lexicon.tsv` (198,273 senses of 152,137 words, Open English WordNet 2024, CC BY 4.0) and `concepts.tsv` (316,294 common-sense links between 133,754 things, ConceptNet 5.7, CC BY-SA 4.0): the comprehension layer's files, unpacked once and read by binary search, never loaded whole into memory (`ibiko1/comprehend`, licences in `COMPREHENSION_LICENSE.txt`). `concepts.tsv` is not inside the app: see "First start" below.

**First start.** On the first start Ibiko downloads the app files it needs before it opens: the common-sense table (`downloads/knowledge/concepts-<version>.tsv.gz` in this repository, 2.6 MB) and the human voice (Kokoro, 103 MB, see "Voice"). Models (llama and the others) are not among them: they are chosen and downloaded in Settings. The screen and the notification only say "Downloading app files", with one progress bar over everything (percent and MB), never a file's name; while it waits, what Ibiko can do, short quotes and facts run across the screen, and facts about Ibiko roll slowly underneath. The file is checked against its checksum (`main/AppFiles`) and unpacked to `brain/knowledge/concepts.tsv`. A download the network breaks carries on where it stopped (the part that arrived is kept and only the rest is asked for, by itself a few times and again on Try again), for the app files, the voices, Linux and the models. If the download still fails or the file is wrong, the app does not open: it says so and offers Try again or Close. After that the app opens with no internet. A rebuilt table (`tools/build_concepts.py`) gets a new version name and checksum in `AppFiles`.

**Comprehension.** Before any brain reads a message, `ibiko1/comprehend/Comprehension` works out what it means: the intent (ten kinds), each action with its target, attributes and position ("take the red tyre off the car": remove, tyre, red, from car, and whether a car has tyres), the word sense in context (a river bank or a money bank), and what "it" and "its" refer to in the conversation. The reading goes into the foundation as "Understood: …", and dictionary and common-sense questions ("what does X mean", "what is a knife used for", "parts of a car") are answered offline.

**Learning.** Ibiko 1 learns from every reply, from any brain, without waiting for a tap:
- facts and sentence shapes;
- the method that reached an answer (search, read, edit, test…), which grows stronger when it is used again or marked right, and weaker when marked wrong.

The check and cross under a reply teach it faster.

**Thinking ahead.** Before it answers or acts, Ibiko weighs what you are really asking, what it is for, what could go wrong and what you will need next (`ibiko1/formulas/Foresight`). It also learns your habits, such as pushing after a fix. On the device's own model, Ibiko reads short instructions and does not reason in hiding, so the first word comes in seconds; the steps show how long it read. Everything it says stands on one foundation of truth (`ibiko1/formulas/Truth`): what you told it about yourself first, then what you confirmed, the built-in reference, what it read today, what it learned and has not checked, and a model's own memory last; when these disagree the more trusted one stands, and it says how sure it is. It questions everything, going in and coming out: each sentence it reads or learns, and each sentence of every reply, is asked "true or false?" against what it knows for certain. A false one is not learned, and a false one in a reply is followed by the correction and where it comes from. Both sides are kept in a truth ledger: what was found true is confirmed knowledge, what was found false is a known mistake it will not repeat (Memory → "Checked true or false"). It learns from its own replies too (`ibiko1/formulas/Reflection`): what you do next tells it how each reply went, such as a correction, the same question again, "shorter" or "thanks", and it keeps the lesson for the next reply of that kind. When it is sure, it offers the likely next step in one line, and never does it unasked.

## Built in

- **A Linux environment**: Alpine with a real terminal, inside the app. No Termux.
- **Social accounts**: post with your approval, or auto-post if you switch it on.
- **3D design and export**: `.glb`, `.obj`, `.stl`, `.ply`.
- **Share › Ibiko** from other apps.
- **Voices**: African English first where the device has them.

## Build

Android Studio or CodeAssist: open this directory. Gradle 8.9, AGP 8.7.3, Java 17, compileSdk 35, minSdk 34. `app/build.gradle` and `app/module.toml` carry the same version.

```
gradle testDebugUnitTest assembleDebug
```

Regenerate assets:
- `python3 tools/build_world_facts.py`: built-in facts from Wikidata;
- `python3 tools/build_more_facts.py`: the further facts (countries' codes, the elements in full, Nigeria's states, the planets);
- `python3 tools/make_icon.py app/src/main/res`: the app icon from the pilot's art (the face on the app's purple, every density, round, store image); the face in the chat is drawn in code (`stage/LiveFace`);
- `python3 tools/make_logo.py`: logo and launcher icons;
- `python3 tools/pack_source.py`: knowledge pack;
- `python3 tools/build_lexicon.py` and `python3 tools/build_concepts.py`: the comprehension files from Open English WordNet and ConceptNet (the second writes the first-start download and prints its checksum).

No provider key belongs in this project. Keys are entered in the app and stay on the device.

## Licences of shipped data

- `world_facts.tsv`: countries from mledoze/countries (ODbL) and population from datasets/population (PDDL); see `WORLD_FACTS_LICENSE.txt`.
- `dictionary_en.json`: WordNet; see `WORDNET_LICENSE.txt`.
