# Ibiko (Android)

Ibiko is one AI assistant on your Android device. There are no personas to pick and no other names: you talk to Ibiko, and Ibiko talks, researches, writes, designs in 3D, and writes and runs code on the device.

The repository and the Java package keep their old names (`Dvine-ai-frontend`, `ai.dvine`) so that existing installs, saved chats and settings carry over. Everything a person sees says Ibiko.

- Build plan: [`docs/DVINE_MASTER_BUILD_HANDBOOK.md`](docs/DVINE_MASTER_BUILD_HANDBOOK.md)
- What was built in each version, with the tests that prove it: [`docs/BUILD_STATUS.md`](docs/BUILD_STATUS.md)
- Every request made so far and whether it is built: [`docs/DVINE_ALL_PROMPTS_STATUS.md`](docs/DVINE_ALL_PROMPTS_STATUS.md)
- The functions a model can use: [`docs/APP_FUNCTIONS.md`](docs/APP_FUNCTIONS.md)
- What is planned, newest first: [`PLAN.md`](PLAN.md)

## How it works

**One chat.** Every message goes to Ibiko. With a model in use (one of your API providers, or a model downloaded to the device), the model reads what you mean and Ibiko acts on it through function codes (`§SR` search, `§WK` start the agents, `§MF` make a file…). Your own words never run a function by themselves, and the codes are never shown in the chat.

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
- `world_facts.tsv` (2,379 facts): the 194 countries (capital, region, currency, official languages, area, people, neighbours, coastline) and the 118 elements;
- `base_knowledge.json`: everyday talk, maths, units, wisdom;
- `shards/dictionary_en.json`: the English dictionary (WordNet).

**Learning.** Ibiko 1 learns from every reply, from any brain, without waiting for a tap:
- facts and sentence shapes;
- the method that reached an answer (search, read, edit, test…), which grows stronger when it is used again or marked right, and weaker when marked wrong.

The check and cross under a reply teach it faster.

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
- `python3 tools/make_logo.py`: logo and launcher icons;
- `python3 tools/pack_source.py`: knowledge pack.

No provider key belongs in this project. Keys are entered in the app and stay on the device.

## Licences of shipped data

- `world_facts.tsv` (countries): from mledoze/countries, ODbL; see `WORLD_FACTS_LICENSE.txt`.
- `dictionary_en.json`: WordNet; see `WORDNET_LICENSE.txt`.
