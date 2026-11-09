# DVINE as a suit

DVINE is a suit, like Iron Man's. The **pilot** is the user. The **brain** is whatever model is plugged in: an API
model (Claude, GPT, Gemini, DeepSeek, a custom gateway) or a model on the device. The **suit** is the app: the
functions, the senses, the memory, the controls. The suit never decides what the pilot meant: the brain does. The
suit carries out what the brain orders, shows the pilot everything, and keeps the pilot's own controls (buttons)
in the pilot's hands.

```
            PILOT (the user)
   words ──────────┐          ┌──── buttons: Stop, Send, persona toggle, tools, approve / reject
                   ▼          ▼
 ┌────────────────────────────────────────────────────────────────────────────┐
 │ SUIT (DVINE)                                                               │
 │                                                                            │
 │  Cortex (always on, in microseconds, never acts)                           │
 │    Comprehension: act, negation, numbers, names, repos, files, links, topic│
 │    Arithmetic: exact values for any sum in the message                     │
 │    Brain box: what earlier models did and answered (learns from every one) │
 │          │ the reading goes to the brain with the message                  │
 │          ▼                                                                 │
 │  BRAIN (the selected model; one call per message)                          │
 │    answers, and when it decides something should be done writes one       │
 │    machine code on its first line: §SR weather in Lagos                    │
 │          │                                                                 │
 │          ▼                                                                 │
 │  Function codes (suit/FunctionCode) ──► the suit's functions               │
 │    §SR search  §SH shell (approved first)  §TL tool  §CA calc  §PH photo   │
 │    §BD 3D build  §SO social  §MF make file  §TM time  §NM name  §UZ §FZ zip │
 │    §NC new chat  §SW switch persona  §PM plan mode  §AO §AF auto-accept    │
 │    §RT retry  §OS settings                                                 │
 │          │ results (output, files, pages) go back to the brain to explain  │
 │          ▼                                                                 │
 │  Deep thinking (suit/DeepThink), when the chat asks for it:                │
 │    plan ─► draft ─► critique ─► revise (up to 3 rounds; stops when sound)  │
 └────────────────────────────────────────────────────────────────────────────┘
```

## Rules the suit keeps

1. **Words never run anything.** The pilot's message is never parsed for command words or codes. "Stop", "new
   chat", "post this" or a typed `§NC` do nothing by themselves. Only a code on the first line of the *brain's*
   reply runs a function (`FunctionCode.parse`). Stop is the Stop button.
2. **One read per message.** The brain gets the message once, with the persona, the codes it may use and the
   Cortex's reading. No classifier call, no routing call (see `docs/PERFORMANCE_ANALYSIS.md`).
3. **The brain is replaceable.** Every brain gets the same contract: the same codes, the same reading, the same
   results. Switching from an API to the device's model changes who thinks, not how the suit works.
4. **Facts beat guesses.** Arithmetic is computed by the suit (`Arithmetic`, decimal, exact) and handed to the brain
   as a value; the brain is told to use `§CA` instead of working sums out.
5. **The pilot approves what touches the world.** A shell command, a push, a post or a deletion waits for the pilot's
   button (or the auto-approve setting the pilot chose).
6. **The suit learns from every brain.** The brain box keeps how messages became functions and what was answered;
   offline it acts on what it learned and answers when no brain is plugged in; with an API it only learns.

## The Cortex (DVINE's own light brain)

| Part | Does | Never |
|---|---|---|
| `Comprehension` | reads the act (question, request, greeting, thanks, correction, feeling, statement), negation, numbers, names, repos, files, links and topic words | decides or runs a function |
| `Arithmetic` | exact decimal arithmetic from symbols or words (`15% of 2,400`, `twelve times seven`, `square root of 81`, `7 mod 3`) | guesses |
| `BrainBox` | learns orders and answers from every model; acts offline on close learned orders; forgets a lesson on "no, that's not what I meant" | acts while an API is selected |
| `Cortex` | gives the brain the reading; with no brain answers sums, greetings, thanks and what it learned | makes anything up |
| `DeepThink` | makes any brain think in passes: plan, draft, critique, revise | runs unless the chat asked for it (each pass is a call) |

## DVINE 1 · 0.1 in the brain picker

The model picker (tap the model under the title) lists **DVINE 1 · 0.1** first: DVINE's own intelligence, the Cortex and
the brain box with no model loaded. It answers sums exactly, greetings and thanks, and what it learned from earlier
brains; it is not a language model and says so when it doesn't know. Picking it asks no API and frees the offline
model's memory. The pick is kept as `brain_pick` (`dvine`, `api`, `device`).

### How DVINE 1 learns

1. Exact sums, greetings and thanks: the Cortex.
2. Confirmed answers (`learn/Knowledge`): found by intent (what kind of answer, the meaning words with synonyms, endings
   and small typos folded), so a question asked another way gets the same answer, without asking again.
3. Answers learned from earlier models (`BrainBox` talk): given, with "Is it accurate?" and Correct / Wrong.
4. Otherwise it researches the web and answers from the sources' own sentences (`suit/ResearchAnswer`), naming them,
   and asks Correct / Wrong. Correct saves it; Wrong marks that answer wrong for that intent.

5. In its learning sessions (the hours the pilot chose, or Learn now) it learns only from usage: what it missed or got wrong, what the
   pilot asked about, and the pilot's interests; those answers are offered with the check and cancel until confirmed.

### Language formulas (`suit/Grammar`, `learn/Facts`, `learn/Lexicon`)

| Formula | Reads | Example |
|---|---|---|
| Intent | greeting, thanks, question, command, statement | "Tell me about volcanoes" → command, about volcanoes |
| Wants | person, thing, time, place, reason, way, number, yes/no | "What year did the war end?" → time |
| F1–F4 focus | what a question is about, its action and object | "When was Microsoft founded?" → Microsoft, found, passive |
| T1–T5 facts | any text into (subject, relation, object) | "Bell invented the telephone in 1876." → (telephone, invent by, Bell), (telephone, invent in, 1876) |
| Say | a fact back as a sentence of its own | "Alexander Graham Bell invented the telephone." |
| Words | "what does X mean", "define X" | from its dictionary, learned from Wiktionary |

It does not reason the way a language model does: it retrieves, checks and remembers. Reasoning it can't do yet is
the selected model's, and what that model answers is learned too.

## Files

* `suit/FunctionCode.java`: the codes, their parser and the brain's instructions.
* `suit/Arithmetic.java`, `suit/Comprehension.java`, `suit/Cortex.java`, `suit/DeepThink.java`.
* `learn/BrainBox.java`: the learning memory.
* `llm/BrainOrder.java`: code → order (`fromCode`, `leading`); `MainActivity.carryOut` runs the order.
* Tests: `suit/SuitTest`, `llm/OneCallOrderTest`, `learn/BrainBoxTest`.

## What comes next

* ~~Tools per chat~~ and ~~deep thinking per chat~~: built in 0.99.1 (`suit/ChatTools`, the tools button).
* **Parallel brains**: one model answers one call at a time (`LocalModel.reply` holds a lock), so a second page's
  message waits for the first. A lock per provider would let an API serve two pages at once.
* ~~Templates in the brain box~~: built in 0.99.6 (shapes: a learned `§SR weather in {x}` works for new words; never shell, posting or scheduling).
* ~~A pilot profile~~: built in 0.99.6 (`learn/PilotProfile`, told to every brain, shown in What DVINE remembers).
