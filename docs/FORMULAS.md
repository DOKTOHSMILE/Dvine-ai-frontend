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
| `Analyst.java` | Data handled with surgical precision, run by Ibiko 1 on data in a message and given to every brain as its method | A1 objective · A2 inventory · A3 clean, said, nothing dropped silently · A4 exact count, sum, mean, median, min, max, range, sample SD · A5 outliers by Tukey's fences, flagged not deleted · A6 change first to last and the least-squares direction (a trend from 4 values) · A7 every total checked a second way · A8 limits said · A9 figures, meaning, what would sharpen it · A10 units kept |
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


## Memory codes (learn/Codes, 0.99.112)

- C1 Every sentence kept gets an id: `<message id>#<n>`.
- C2 Its tags are its content words (lower case, small words dropped, plural and tense endings taken off).
- C3 A tag points to the ids filed under it.
- C4 A question becomes tags; the tags pull ids; each id scores the sum over shared tags of log(1 + all / filed under the tag), so a rare tag counts more.
- C5 At least two shared tags (one when nothing has two), at most a few ids, best first.
- C6 Only those sentences are read back, with their codes, in the order they were said.


## Intake: a message during a running job (agent/Intake, 0.99.113)

- I1 CHAT (thanks, okay, hello): answered in a word; nothing reaches the job.
- I2 QUESTION (a question mark, or what/how/why/is/can…): answered beside the job; the plan is not touched.
- I3 REQUEST (an errand not about the job: no shared subject word, no project word): handled on its own, beside the job.
- I4 STEER (stop, skip, focus on, first do, faster, don't push…): a note to the job; no re-planning.
- I5 PLAN_CHANGE (add/also/include/change/use/instead…, "it should…", "what if we…", "can you also…?" about the job): into PLAN.md as new steps.
- I6 Unsure: shares the job's subject → a note (I4); otherwise its own errand (I3).


## Probability: thinking in chances (ibiko1/formulas/Probability, 0.99.114)

- P1 Possibilities: at least two, "something else" included.
- P2 Base rate: how often each is the case in general; start there, not at 50/50.
- P3 Evidence: odds after = odds before × likelihood ratio (how much more likely the clue is under A than B).
- P4 Independence: clues from one source count once.
- P5 Expected value: Σ probability × gain or cost; pick the best, the cheaper-to-undo between near-equals.
- P6 Cost of being wrong: act when p × gain − (1 − p) × loss > 0; otherwise check first.
- P7 Value of a check: check first when its result could flip the decision.
- P8 Calibration: "almost certainly" ≥ 95%, "very likely" ≥ 85%, "likely" ≥ 65%, "a toss-up" 45–55%, "unlikely" ≤ 35%.
- P9 Chains: a plan is as likely as the product of its steps (0.9 × 0.9 × 0.9 = 0.73).
- P10 Update with new evidence; don't swing on one weak clue.


## Ids: the mind works by numbers (learn/Symbols, learn/Identifier, 0.99.115)

- D1 Every word gets one id, the same for good (files/brain/words.tsv, only added to).
- D2 Every sentence gets an id: `<message id>#<n>`; a sentence is the list of its word ids, which reads back to the exact words.
- D3 Every conversation is saved as ids in its chat's folder (memory/ids.tsv): role, sentence id, word ids.
- D4 Stores are filed by id (facts by the first word of subject and object; examples and tags by word; names by first word). A question's words become ids once; the ids pull only what could answer; nothing else is read.


## Corroborate: learning answers without keeping a wrong one (learn/Corroborate, 0.99.116)

- V1 Never learned: a connection failure, a status line, a stand-in reply while the API was away, a refusal.
- V2 A new answer starts unconfirmed.
- V3 The same answer again (same words, or ≥ 60% of the content-word ids shared) confirms it.
- V4 A different answer counts once against each kept answer it differs from, and is kept as the challenger.
- V5 Marked false when the answers against are ≥ 2 and ≥ its confirmations.
- V6 A false answer is never given; ties go to the answer kept first; a challenger takes over once confirmed more.
- V7 The pilot's check or cancel overrules all of it.

## Word ids and short codes (learn/WordBook, learn/Shorthand, 0.99.116)

- Small words and punctuation: ids 1–999 (fixed list). Dictionary words: 1000 + place in lexicon.tsv (152,137). Others: 1,000,000 up, kept on the device.
- A code is the id in base 36 ("telephone" = 2x9m); ^ marks a capital, ! all capitals. The interpreter turns codes back into the same words.
- Spaces have codes too: a space, a double space (one code of its own, 0.99.117), a new line and a tab each have their own fixed id, so every character of a text is in a code and it comes back exactly as written ("Abuja is the capital of Nigeria." = 12 codes: 6 words, 5 spaces, the stop).


## Ibiko's script and numbers (learn/Glyphs, 0.99.118)

- One character a word: Unicode private-use characters (U+E000–U+F76F, U+F0000–U+FFFFD, U+100000–U+10FFFD), 137,068 letters. Small words, spaces and marks keep their ids; a single dictionary word is 1000 + its place among the 84,706 single words; own words from letter 90,000. A capital U+F8FD, all capitals U+F8FC before the letter.
- A number's id is 1,500,000,000 + its value; its code # and the value in base 36 (1969 = #1ip); in the script its digits. Nothing stored.

## Concepts: meaning from what recurs (learn/Concepts, 0.99.118)

- N1 A topic is a content word of the question.
- N2 Each answer about it counts each of its codes once.
- N3 A code in ≥ 2 answers and ≥ 50% of them is part of the meaning; the rest are weaker links.
- N4 Every new answer re-weighs the links; one that stops recurring drops out.
- N5 A link's strength is its share of the answers.
- N6 A sentence of its own: the strongest link that can be a noun says what the topic is; adjectives how it is described. At least 3 answers.

## Mind: the thought process (learn/Mind, 0.99.118)

- M1 Reader: words to codes, the topics. M2 Memory: what recurred about the topic. M3 Weigher: (count + 1) / (answers + 2), said in matching words. M4 Critic: too few answers or nothing recurring: no view. M5 Speaker: the sentence. Each section's line is shown as a thinking step.


## Pack: the codes as bytes (learn/Pack, learn/CodeFile, 0.99.119)

- One number a token, 7 bits a byte: (id << 2 | case) for a small or dictionary word (case 0 none, 1 capital, 2 all capitals); 3 = extended: a number (its value), a spelled word (length and UTF-8), or a dictionary word and an ending (s, es, ed, d, ing, ly, er, est).
- The single space between two words is not written.
- Header: IBK1 and the dictionary's word count; a pack from another dictionary is refused.
- CodeFile: IBKZ and the pack deflated. Measured on 100 kB of plain prose: 50% packed, 33% packed and deflated.
- An own word's id is a hash of its letters into 1,000,000…1,499,999,999, the same on every device.
