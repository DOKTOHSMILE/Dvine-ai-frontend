# Ibiko 1 formulas

Every rule Ibiko 1 uses to read, answer and learn lives in one folder:
`app/src/main/java/ai/ibiko/ibiko1/formulas/` (Ibiko 1's folder; `tools/export_ibiko1.sh` makes it a standalone library). Each file opens with its rules, numbered, so a behaviour can be traced
to one line. Tests: `app/src/test/java/ai/ibiko/ibiko1/` and `app/src/test/java/ai/ibiko/learn/IndependentLearnerTest.java`.

| File | What it decides | Rules |
|---|---|---|
| `Grammar.java` | What a message is (question, command, greeting, thanks, statement), what it asks for (person, thing, time, place), and the facts (subject, relation, object) in a sentence | INTENT, FOCUS F1–F4, and the fact formulas, listed at the top of the file |
| `PartsOfSpeech.java` | Each word's part of speech: noun, proper noun, pronoun, verb, auxiliary, adjective, adverb, preposition, conjunction, determiner, interjection, numeral; the subject, verb and object of a clause | 1 closed words · 2 numbers · 3 learned · 4 endings · 5 neighbours · 6 otherwise noun |
| `Commands.java` | A clear command turned into one of the app's function codes | one code per command |
| `CodeTasks.java` | A code task turned into one shell command for the built-in Linux, approved before it runs: a typed command, pasted code with "run this", files, folders, clone, install | one line; pasted code written with printf, then run |
| `Confidence.java` | How sure Ibiko is of a fact (strength 0–5) | C1 first sight · C2 seen again · C3 contradicted · C4 accepted · C5 corrected · C6 30-day fade · C7 answers at ≥ 2 · C8 tie asks |
| `Shapes.java` | New ways a fact is written, learned from reading | S1 PII mask · S2 value · S3 shape · S4 caps · S5 promote · S6 prune |
| `Score.java` | The learning score | A accuracy · K knowledge · R reading rules · U understanding |
| `Truth.java` | The foundation every answer stands on: all that is known about a request, from every store, settled by how trustworthy each source is, with how sure it is | G1 ranks (you told me › you confirmed › built-in › read today › learned, not checked › a model's memory) · G2 two different sources agreeing make it firm · G3 the higher rank stands; equal ranks that differ are said to differ · G4 the newer dated source wins at equal rank · G5 say how sure, never invent a fact or a source, posts are opinions · G6 a claim against a firm one is held back · G7 every answer built on this, in order, before any reasoning · G8 question everything: each sentence going in (research, what it learns, what the pilot teaches) and coming out (every reply from any brain) is asked "true or false?" against the most trusted source that knows; FALSE is not learned and is corrected after the reply with its source; UNKNOWN is never stated as certain; denials, hedges, questions and the past are not judged · G9 the truth ledger (`TruthLedger.java`): every TRUE and FALSE judgment is kept, both sides, with its source, correction, where (read, reply, the pilot), when and how often; a sentence judged before is judged the same again; the known mistakes and checked truths about a request go to every brain, mistakes first; at most 3,000, the least recently seen forgotten first |
| `Reflection.java` | Learning from its own replies: how each went, judged by the pilot's next message, and the lessons handed to the next reply of the same kind | R1 outcome (corrected −2, retried −1, restyled, accepted +1) · R2 record (score and the last 3 lessons) · R3 style (shorter/longer, list/prose, simpler) · R4 trust (score ≤ −2: slow down and check) · R5 tick +2 / cross −3 · R6 forgetting (×0.9) · R7 apply to every brain |
| `Reasoner.java` (in `ibiko1/`) | Reasoning: a question one fact can't answer is broken into steps (the operators of compositional question answering, as in KQA Pro's KoPL) and run on the built-in facts | O1 find the things named (whole words, longest first) · O2 filter a set (continent, region, currency, language, border, coast; a country is a UN member) · O3 relate (neighbours; the country of a capital, currency or code) · O4 read a property · O5 compare (more or less, how many times, the difference) · O6 rank (most, least; ties said; the next two given) · O7 count and list · O8 compute (people per square kilometre, a sum "combined", a continent's total) · R1 every answer says the values it rests on · R2 a yes-or-no or "why" claim is checked and a false premise corrected ("It isn't.") · R3 a step that can't run says what is missing; nothing is invented · R4 a question one fact answers is left to the lookup |
| `Critical.java` | Critical reasoning, deep thinking that questions before it answers: Ibiko 1 runs it on its own answers, and its checks for each request go to every other brain | K1 kind (fact, cause, comparison, judgement, prediction, definition, how-to: each needs its own proof) · K2 premise checked before it is explained · K3 absolutes (one exception disproves) · K4 not only two options · K5 cause is not correlation (mechanism, order in time, other causes) · K6 who says is not evidence · K7 a judgement names its criteria · K8 a forecast is not a fact · K9 how sure matches how it was reached (certain, sure, very likely, likely, not checked) · K10 the other side's best case · K11 a second route; disagreement lowers certainty · K12 what would change the answer |
| `Thinking.java` | Learning how the other brains think from the live chat (Deep Thinking's plan and review, the working steps, the reasoning written into replies), so Ibiko 1's own thinking follows it | T1 moves (understand, recall, plan, look up, compare, consider, compute, check, conclude) · T2 pattern in order, at most 6 · T3 kept per kind of request, at most 8 · T4 followed once seen twice · T5 fade 0.9 · T6 shown as Ibiko 1's steps, its own last steps kept |
| `Foresight.java` | Premeditation: what the pilot is likely to need next after a request like this one, and the check run before answering or acting | P1 transition · P2 forgetting (0.95) · P3 likelihood (Laplace) · P4 offered at mass ≥ 2.5 and P ≥ 0.40 · P5 look before (what is asked, what for, what could go wrong, what comes next) · P6 offer, never act unasked · P7 a 6-hour gap is a new session |

The sentence formula (how an answer is said: "Bell invented the telephone.") is in `ibiko1/Facts.java`, next to the
facts it reads.

## Where the learner learns from

`ibiko1/learner/Learner.java`:

| Source | What it teaches |
|---|---|
| The pilot's messages, to any brain | grammar, sentence shapes, the pilot's check or correction of Ibiko 1's last answer |
| Every model's answer | grammar and sentence shapes only (a model's claim is not a fact) |
| Research Ibiko 1 reads | facts at strength 2, grammar, sentence shapes |
| Social posts and pages | grammar only (people's words, never facts) |
| Questions it couldn't answer | kept until a strong fact answers them; after 3 asks, logged as unknown |

Premeditation (`Foresight.java`) learns from the pilot's own messages only: which kind of request follows which,
kept in `brain/foresight.tsv`, with names, emails, numbers and links masked. Every brain gets the look-before check with
the request, and the likely next step when the habit is sure enough; the brain may offer it, never do it unasked.

The foundation (`Truth.java`) is put together for every request, before Foresight and Reflection: what the pilot said
about themselves, what they confirmed, the built-in reference, what was read, what was learned and not checked, each
marked with its source and how sure it is, and the rules for when they disagree. Ibiko 1's own answers follow the same
ranks: a built-in fact outranks a lesson that was never confirmed.

Question everything (G8): `BuiltIn.check` and `Facts.check` judge one sentence each (true, false or unknown), the built-in
reference first, then facts held at strength 3 or more. Research is learned only from its true and unknown sentences;
an answer found false is not learned at all. Every reply is checked on the device after it is shown, and a false
sentence is followed by "A correction to what I just said: …" with where the correction comes from. When the pilot
teaches something the reference contradicts, it is kept (the pilot outranks it) and the difference is said.

Reflection (`Reflection.java`) judges every reply, from any brain, by what the pilot does next: a correction or the same
request asked again counts against it, thanks or moving on counts for it, and "shorter" or "in a list" teaches the
pilot's taste. The lessons for that kind of request and the taste go with the next request to any brain. Kept in
`brain/reflection.tsv`, private details masked.

The truth ledger (G9) keeps both sides of every check in `brain/truth.tsv`: what was found true is knowledge
confirmed, what was found false is a known mistake. Memory → "Checked true or false" lists them with the time and
where each came from, and forgets one or all.

Files, all in the app's `brain/` folder: `truth.tsv`, `reflection.tsv`, `foresight.tsv`, `facts.tsv`, `learner-answers.jsonl`, `learner-open.json`,
`learner-rules.json`, `learner-words.tsv`, `learner-state.json`, `learner-journal.txt`. Memory → "What Ibiko 1 learned
by itself" shows them and forgets them.
