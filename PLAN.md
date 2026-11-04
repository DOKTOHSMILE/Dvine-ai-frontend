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

## Round 2 (audit: behaviour that doesn't match what the app says it does)

- [x] MainActivity.offerTermux(): gate the message on actually opening the Ibiko tab (`fromTap`), and fix the
      wording — it claimed "I won't open another app", which is false (apps open after a spoken yes), and it never
      named the tools that do exist here. Now: "My file tools live here: ask me to find a zip, unzip it, or run a
      command in this chat. If a task needs another app I'll ask you first." Read back the edited hunk.
- [x] run_terminal_cmd tool doc vs what the shell actually does (working directory, limits, denial wording).
      Checked line by line against `SandboxShell.run`, `PhoneHost` and `CodeAgent`: the doc says "one shell command at a
      time, run in the workspace folder, as the app", "up to 30 seconds, longer runs are stopped", "output beyond 8000
      characters is cut short", "asks you first (Run / Always run / Don't run) unless auto-accept is on, and a refused
      command comes back as not allowed" — each matches: `SandboxShell` runs `sh -c` with `directory(dir)` and
      HOME/TMPDIR set to the folder, kills at 30 s and appends `…(output cut short)` past 8000 chars (`maxChars` from
      `PhoneHost.run`), `PhoneHost.allowRun` shows exactly that three-button dialog behind `auto_accept`, and a refusal
      returns "The user didn't allow this command." Also matches for `termux` (3 minutes, `path` copied there) and the
      three `allowRun` git gates. No change needed.
- [ ] Interpretation.java `command()` bridge: does the classifier's promise match ActionPlanner's behaviour
      (yes/no confirmation, last-said fallback, app list).
- [ ] SocialAsk: network patterns vs the social.* connector calls actually implemented.
- [ ] Re-read each change, then commit + push on a branch and open a PR.

## Round 3 (user issue: voice → text auto-stops; voice sounds sharp/robotic)

- [x] Dictation keeps recording until the record button is pressed again (no auto-stop on silence/end of speech).
      `ai/dvine/voice/Dictation.java` rewritten as a hold-to-continue session; MainActivity only stops it from
      `toggle()` (read back; the hold-to-talk `startListening(hold)` path is separate and untouched).
- [x] Voice pitch: soften the sharp, bright, robotic edge so it sounds more human.
      - Read `VoiceTuning.java` (pitch/rate defaults, `warm`, `pitchFor`, thought variant) and `TalkBridge.java`
        say/queue.
      - The sharp edge was `TalkBridge.say()`'s `bright = robot && !ivie ? 1.25f : 1f` on top of the persona
        pitch with `quick = 1.06f` on rate, and defaults of Obi 0.84 / Ivie 1.05.
      - Now: `ROBOT_BRIGHT = 1.10f` / `ROBOT_QUICK = 0.95f` (brighter but slower, not hurried),
        defaults Obi 0.80 / Ivie 0.97, thought 0.94/0.96, THOUGHT_VOLUME 0.6f, and an anti-sharp
        `VoiceTuning.warm()` — floor 0.78 (no buzzy bottom), ease anything above neutral toward a 1.08
        ceiling via WARM_LIFT/WARM_FADE so it never reaches the thin/whiny zone. Every call site in
        `TalkBridge` (`say()` line 267, `queue()` line 308) passes `pitchFor(...) * bright` through
        `warm(...)` exactly once, so the user's VOICE_HIGHER/VOICE_LOWER commands still move the voice
        but never past the ceiling or floor.
- [x] Test: any JVM/unit tests for voice exist? run them; otherwise verify by reading back and note here.
      `app/src/test/java/ai/dvine/voice/VoiceTuningTest.java` exists (146 lines, 8 tests): warm never
      exceeds 1.08 nor drops below neutral and is monotonic; warm passes 0.80/0.85 through and lifts
      0.60/0.40/0.78 to the floor; defaults are gendered and inside the band; the 0.85x/1.15x gender
      shifts stay in 0.78–1.08 after warm; thought is softer/slower with THOUGHT_VOLUME 0.6; spoken
      higher/lower/slower/faster commands move the value and stay warm (junk and null return false);
      ROBOT_BRIGHT>1, ROBOT_QUICK<1, every robot-lit combination inside the band, Obi-lit 0.88 < Ivie
      0.97, and the robot rate stays in the 0.6–1.8 usable band. **Every assertion checked by hand
      against the constants — this shell has no java/javac/gradle/JVM anywhere (verified: only toybox),
      so the suite cannot be executed here; reading is the test.** All 8 pass on paper.
- [ ] Re-read each change, then commit + push on a branch and open a PR.

## Merged from ibiko/localmodel-fixes

- [x] Run tool prompt now says one command per tool call, and a chain like `a && b` is one command
      (`CodeAgent.system()`, test `aRunToolCallIsOneShellCommandWhateverItContains`).
- [x] Voice settings stay on the current `voice;pitch;rate` form. The older pipe format was not merged,
      so saved voices are not wiped.
