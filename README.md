# Ibiko (Android)

<img src="docs/brand/ibiko_icon_512.png" width="128" alt="Ibiko icon: two glowing cyan eyes on black, with circuit traces at the sides">

Ibiko is one AI assistant on your Android device. There are no personas to pick and no other names: you talk to Ibiko, and Ibiko talks, researches, writes, designs in 3D, and writes and runs code on the device.

The app's package is `ai.ibiko` (since 0.99.68; before that `ai.dvine`, so it installs as a new app beside an older build, and the model is downloaded again). The repository keeps its old name, `Dvine-ai-frontend`. Everything a person sees says Ibiko.

- Build plan: [`docs/DVINE_MASTER_BUILD_HANDBOOK.md`](docs/DVINE_MASTER_BUILD_HANDBOOK.md)
- What was built in each version, with the tests that prove it: [`docs/BUILD_STATUS.md`](docs/BUILD_STATUS.md)
- Every request made so far and whether it is built: [`docs/DVINE_ALL_PROMPTS_STATUS.md`](docs/DVINE_ALL_PROMPTS_STATUS.md)
- The functions a model can use: [`docs/APP_FUNCTIONS.md`](docs/APP_FUNCTIONS.md)
- What is planned, newest first: [`PLAN.md`](PLAN.md)

## How it works

**One chat.** Every message goes to Ibiko. Over the message box, quick chips open Tools, Code (files and the shell), Media (attach), Explore (social accounts, connectors) and More (settings, chats, memory, workload). With a model in use (one of your API providers, or a model downloaded to the device), the model reads what you mean and Ibiko acts on it through function codes (`§SR` search, `§WK` start the agents, `§MF` make a file…). Your own words never run a function by themselves, and the codes are never shown in the chat.

**Brains.** Pick one from the header:
- an API model (OpenAI-compatible or Anthropic), with a backup in order;
- a model downloaded to the device (Qwen3, Llama, Gemma, Phi GGUF files), working offline;
- Ibiko 1, the app's own learning brain, which needs no model.

**Agents for real work.** Code, files, repositories and commands go to the agent organisation (`agent/Org`):
- ARCH plans and CPGC coordinates (the lead), ROLX keeps time, the Scribe records every step;
- specialists take parts: UCIX (interface), UCUX (what each control does), CORX (engine), DATX (data), QATX (tests), SECX (security), REVX (review), RSRX (research), DOCX (docs), RELX (release);
- each part has a time limit; each agent can start up to four mini workers on pieces it designs;
- on an API they run at the same time.

**Long jobs run in the background.** While an agent job runs, you can keep talking: Ibiko answers your new message beside the job. If the message is meant for the job, it is passed to it (`§ST`) and the job reads it on its next step.

**Nothing is read twice.**
- The Scribe keeps one record file per project (`memory/projects/<project>/RECORD.md`): the state, the last step and its time, the last change and its file, what is done and what is pending. A new round starts from that point.
- A codebase survey keeps every part it has read (`.blueprint/<project>/pieces/`) and its progress (`SURVEY.md`). After a broken connection or "continue", only the parts not yet read are read. The map (`MAP.md`) is sorted by file and line, with the time each part was read.

**Plans grow.** A plan file (`PLAN.md`) is only added to. New work goes after the existing steps, or right after the last finished step when you call it a priority. A finished step is ticked, never deleted. A write that would drop a step is refused.

**Built-in knowledge.** `app/src/main/assets/knowledge/` ships what Ibiko knows on install, with no internet:
- `world_facts.tsv` (2,765 facts): the 194 countries (capital, region, currency, official languages, area, population with its year, people, neighbours, coastline) and the 118 elements;
- `country_more.tsv` (989): each country's calling code, internet domain, region, ISO codes and UN membership;
- `elements_more.tsv` (1,027): each element's atomic mass, category, state at room temperature, discovery, melting and boiling points, density, electron configuration, group and period;
- `local_facts.tsv` (106): Nigeria's 36 states and the FCT with their capitals; the eight planets (order from the Sun, diameter, year, kind);
- `base_knowledge.json`: everyday talk, maths, units, wisdom;
- `shards/dictionary_en.json`: the English dictionary (WordNet).

**Learning.** Ibiko 1 learns from every reply, from any brain, without waiting for a tap:
- facts and sentence shapes;
- the method that reached an answer (search, read, edit, test…), which grows stronger when it is used again or marked right, and weaker when marked wrong.

The check and cross under a reply teach it faster.

**Thinking ahead.** Before it answers or acts, Ibiko weighs what you are really asking, what it is for, what could go wrong and what you will need next (`dvine1/formulas/Foresight`). It also learns your habits, such as pushing after a fix. On the device's own model, Ibiko reads short instructions and does not reason in hiding, so the first word comes in seconds; the steps show how long it read. Everything it says stands on one foundation of truth (`dvine1/formulas/Truth`): what you told it about yourself first, then what you confirmed, the built-in reference, what it read today, what it learned and has not checked, and a model's own memory last; when these disagree the more trusted one stands, and it says how sure it is. It questions everything, going in and coming out: each sentence it reads or learns, and each sentence of every reply, is asked "true or false?" against what it knows for certain. A false one is not learned, and a false one in a reply is followed by the correction and where it comes from. Both sides are kept in a truth ledger: what was found true is confirmed knowledge, what was found false is a known mistake it will not repeat (Memory → "Checked true or false"). It learns from its own replies too (`dvine1/formulas/Reflection`): what you do next tells it how each reply went, such as a correction, the same question again, "shorter" or "thanks", and it keeps the lesson for the next reply of that kind. When it is sure, it offers the likely next step in one line, and never does it unasked.

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
- `python3 tools/make_icon.py app/src/main/res <thinking.gif>`: the icon and thinking animation from the pilot's art;
- `python3 tools/make_logo.py`: logo and launcher icons;
- `python3 tools/pack_source.py`: knowledge pack.

No provider key belongs in this project. Keys are entered in the app and stay on the device.

## Licences of shipped data

- `world_facts.tsv`: countries from mledoze/countries (ODbL) and population from datasets/population (PDDL); see `WORLD_FACTS_LICENSE.txt`.
- `dictionary_en.json`: WordNet; see `WORDNET_LICENSE.txt`.
