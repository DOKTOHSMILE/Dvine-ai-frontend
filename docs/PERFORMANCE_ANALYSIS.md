# Why messages were read twice, and what 0.98 changed

Checked in the source on 2026-11-09 (0.98.0). "A read" is one full model call: the system prompt, the chat
history and the message sent to an API, or prefilled into the device model's memory (the slow, RAM-heavy part
on a phone).

## What one ordinary message cost before 0.98

| Step | Where | API selected | Offline model |
|---|---|---|---|
| 1. "Read the meaning": a classifying call (new chat? switch persona? stop?) | `DirectCommands.read` → `Meaning.prompt` | 1 call (with a 6 s race) | skipped |
| 2. "The model decides": a routing call, 48 tokens out | `routeWithModel` → `BrainOrder.prompt` | 1 call | 1 prefill of the router prompt + message |
| 3. The answer | `askLocal` → `PersonaPrompt` | 1 call | 1 prefill of the persona prompt + message |
| 4. Fact-check after a factual answer (default on) | `factCheck` → `FactCheck.run` | 1–2 calls + web | – |
| 5. "Think together" on a weighty question (default on) | `Council.run` | 3 persona calls + 1 merge | same, one after another |
| 6. Pidgin / Yoruba / French | `Interpreter` | 2 calls (in and out) | 2 calls |
| Start-up | `warmModel` | – | 2 prefills (router and persona prompts), both kept in the KV cache |
| Start-up, API primary | `loadModel` | the whole offline model loaded into RAM anyway | – |

So an ordinary question cost 3 API calls (the history sent three times) or 2 device prefills; a "weighty" one
5–7. Each prefill on a phone is seconds to minutes and holds the KV cache in RAM.

Also found:

* Rule shortcuts acted on the words before any model saw them ("a known order", the brain box, "who am I",
  hand-to-Ibiko, new post plans from wording, Ibiko's shortcut list), so with an API selected the app and the API
  could disagree on what a message meant.
* `DO SEARCH` was not a code the parser accepted from a model (only the wording rules produced it).
* Every offline message carried up to 1,400 characters (~400 tokens) of "similar answers learned before".

## What 0.98 does

* **One call per message.** The persona prompt gains a short list of the orders the model may give
  (`BrainOrder.actions`). The model answers; only when it decides the user wants something done does it put
  `DO …` alone on the first line, which Ibiko hides while streaming and carries out (`BrainOrder.leading`). The
  classifying call and the routing call are gone. An action whose result needs explaining (a tool, a command) is
  the order call plus one call to read the result: two, never three.
* **With an API selected, the API decides.** `apiDirect()`: the meaning reader, rule orders, the brain box,
  "who am I", hand-to-Ibiko, wording-based post plans and Ibiko's shortcut list stand aside. Replies to something
  already on screen (yes/no to a staged post, retry, measurements Ibiko asked for) still work: they answer the
  app, they don't start a function.
* **Offline**, the same single call replaces routing + answer (one prefill instead of two); the warm-up reads the
  one prompt every message uses, not two. The brain box still acts offline (it is the fast path the device model
  needs), and corrects itself: "no, that's not what I meant" right after it acted makes it forget that lesson and
  ask the model.
* **Fact-check and automatic think-together default to off** (one switch each in Settings; "think together" in a
  message still asks for it).
* **The offline model is not loaded while an API answers**, and choosing an API frees it (0.97.1).
* Learned examples sent to the device model: one, at most 700 characters.

| | API selected | Offline model |
|---|---|---|
| Ordinary message | **1 call** (was 3) | **1 prefill** (was 2) |
| Message that acts and explains | 2 calls | 2 prefills |
| Start-up | nothing loaded | 1 prompt read ahead |

## The engine (native)

`app/src/main/cpp/ibiko_llm.cpp` keeps up to 3 conversations' tokens side by side (`SLOTS`) and reuses the
longest matching prefix (`pick_slot`, `read_into`), so a prompt that starts the same is not read again. What still
costs a re-read, and what to do about it:

1. **The last exchange is read again each message.** The chat history keeps the user's words without the context
   that was sent with them (notes, taught facts, web results), so the cached tokens stop matching at the previous
   user turn. Fix next: keep the sent text of the latest turn only, or send the context as a system message
   after the history so the history itself never changes.
2. **The KV cache is 16-bit below 4,096 tokens.** 8-bit (q8_0) halves its RAM with little loss; it is already
   used from 4,096 up.
3. **Batch 64/128 and two threads** (0.96.2) keep the device quiet but make prefill slower. A prefill-only burst on
   the big cores, then two threads for writing, would read prompts faster without the hum.
4. **The shipped engine was from 0.85.0** until 0.96.4 refreshed it; refresh it after every C++ change
   (`gradle --no-configuration-cache :app:refreshNativeEngine`), or the APK keeps the old engine.

## The brain box

`learn/BrainBox` learns from every model: each order a model gives for the user's words, and each answer (not
ones about the moment: time, today, news, prices). Offline it acts on a close learned order (TF-IDF cosine ≥ 0.86,
an order that carries none of the user's words, or the same words for any order) and, with no model at all,
answers from a close learned conversation, said as "From what I learned earlier". With an API selected it only
learns. Next steps that would make it smarter without a heavy model: weight examples by how often they were
confirmed, keep corrections as negative examples, and learn order templates ("search X" → `DO SEARCH {X}`) so a
learned search works for new words.
