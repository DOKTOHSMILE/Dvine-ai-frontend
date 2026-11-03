# Deep analysis: model connection in Dvine LLM frontend

Goal: a written analysis of every path a prompt takes from the app to a model and back,
with the failure handling, security, and tests. No code changes — this is an analysis repo task.

Steps:

- [x] Map the repo: where the LLM code lives (app/src/main/java/ai/dvine/llm, chat/ui).
- [x] Read RemoteBrain.java end to end (URL check, headers, body/forms, streaming readers, errors, transport).
- [x] Read ProviderStore.java (key encryption, address/model/advanced settings, provider order, CA sockets, keySource session).
- [x] Read Providers.java — protocols, default addresses/models, capability hints.
- [x] Trace the session token: MainActivity:1338 / session.token(), renewal, expiry handling.
- [x] Read the call site that drives requests (MainActivity.applyProvider -> LocalModel.useRemotes -> askRemote:
  up to 3 slots, fast-then-strong per slot, restUntil cooldown, one-shot switchNote, cancel via epoch).
- [x] Read LocalBrain + NativeLlm (the on-phone path) for contrast in the same connection story.
- [x] Read the wiring: Connectivity/online supplier, and how DVINE_SERVER's address is derived (serverUrl).
- [x] Read tests: ProviderOrderTest and any RemoteBrain/connection tests; run them if a JVM test task exists
  (no javac/gradle on this shell — verified by reading, not by running).
- [x] Write the final analysis report: architecture, request lifecycle, negotiation/fallback ladder, security, weaknesses with file:line references.
      Written to docs/model-connection-analysis.md (architecture, lifecycle, ladder, offline path, security table,
      7 weaknesses with file:line, tests, sources). Verified by reading the referenced lines — no javac/gradle here.

## Fixes (issue round: fix what the audit found, then commit and push)

- [x] LocalModel.java: orphaned Javadoc for `forget()` sitting above `warm()` — move/relabel so the doc matches its method.
      (Removed the stray block that sat above `remoteTask` with no declaration under it; `warm()`, `forget()` and
      `remoteTask` now each carry their own correct doc.)
- [x] LocalModel.java: `withTimeout(...)` watchdog — Javadoc says "12 seconds / 90 seconds" but constants are
      60s idle / 180s write / 600s read; and the watchdog writes `problem` before `cancel()`, racing the
      reply thread which can overwrite it — fix the doc and make the timeout problem stick.
      (Doc rewritten to IDLE_MS 60s / 180s writing cap / READ_MS 600s; the timeout reason now survives `cancel()`
      instead of relying on the fragile `startsWith("the reply")` prefix check.)
- [x] LocalModel.java: failover `problem = apiProblem == null ? local : …` at the two fallback sites —
      make sure a stale API problem isn't reported as the reason the phone's model failed, and that
      "stopped" is never overwritten by an engine message. (Guarded at both `replyNow` and `taskNow`.)
- [x] Re-read each change for correctness (no compiler on this machine) and note it here.
      Re-read every edited hunk by hand: no orphaned Javadoc left, the watchdog doc matches the constants,
      and both failover sites preserve a stopped/timeout reason. Verified by reading only — no javac/gradle here.
- [x] git: review `git status`, commit, push on a branch, open a PR.
      Committed as `6fb84ae` on branch `ibiko/localmodel-fixes`, PR #91:
      https://github.com/DOKTOHSMILE/Dvine-ai-frontend/pull/91