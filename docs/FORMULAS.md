# DVINE 1 formulas

Every rule DVINE 1 uses to read, answer and learn lives in one folder:
`app/src/main/java/ai/dvine/dvine1/formulas/`. Each file opens with its rules, numbered, so a behaviour can be traced
to one line. Tests: `app/src/test/java/ai/dvine/dvine1/` and `app/src/test/java/ai/dvine/learn/IndependentLearnerTest.java`.

| File | What it decides | Rules |
|---|---|---|
| `Grammar.java` | What a message is (question, command, greeting, thanks, statement), what it asks for (person, thing, time, place), and the facts (subject, relation, object) in a sentence | INTENT, FOCUS F1–F4, and the fact formulas, listed at the top of the file |
| `PartsOfSpeech.java` | Each word's part of speech: noun, proper noun, pronoun, verb, auxiliary, adjective, adverb, preposition, conjunction, determiner, interjection, numeral; the subject, verb and object of a clause | 1 closed words · 2 numbers · 3 learned · 4 endings · 5 neighbours · 6 otherwise noun |
| `Commands.java` | A clear command turned into one of the app's function codes | one code per command |
| `Confidence.java` | How sure DVINE is of a fact (strength 0–5) | C1 first sight · C2 seen again · C3 contradicted · C4 accepted · C5 corrected · C6 30-day fade · C7 answers at ≥ 2 · C8 tie asks |
| `Shapes.java` | New ways a fact is written, learned from reading | S1 PII mask · S2 value · S3 shape · S4 caps · S5 promote · S6 prune |
| `Score.java` | The learning score | A accuracy · K knowledge · R reading rules · U understanding |
| `Foresight.java` | Premeditation: what the pilot is likely to need next after a request like this one, and the check run before answering or acting | P1 transition · P2 forgetting (0.95) · P3 likelihood (Laplace) · P4 offered at mass ≥ 2.5 and P ≥ 0.40 · P5 look before (what is asked, what for, what could go wrong, what comes next) · P6 offer, never act unasked · P7 a 6-hour gap is a new session |

The sentence formula (how an answer is said: "Bell invented the telephone.") is in `dvine1/Facts.java`, next to the
facts it reads.

## Where the learner learns from

`dvine1/learner/Learner.java`:

| Source | What it teaches |
|---|---|
| The pilot's messages, to any brain | grammar, sentence shapes, the pilot's check or correction of DVINE 1's last answer |
| Every model's answer | grammar and sentence shapes only (a model's claim is not a fact) |
| Research DVINE 1 reads | facts at strength 2, grammar, sentence shapes |
| Social posts and pages | grammar only (people's words, never facts) |
| Questions it couldn't answer | kept until a strong fact answers them; after 3 asks, logged as unknown |

Premeditation (`Foresight.java`) learns from the pilot's own messages only: which kind of request follows which,
kept in `brain/foresight.tsv`, with names, emails, numbers and links masked. Every brain gets the look-before check with
the request, and the likely next step when the habit is sure enough; the brain may offer it, never do it unasked.

Files, all in the app's `brain/` folder: `foresight.tsv`, `facts.tsv`, `learner-answers.jsonl`, `learner-open.json`,
`learner-rules.json`, `learner-words.tsv`, `learner-state.json`, `learner-journal.txt`. Memory → "What DVINE 1 learned
by itself" shows them and forgets them.
