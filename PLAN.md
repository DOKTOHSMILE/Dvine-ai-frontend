# Task: break up MainActivity (the god class)

Goal: `app/src/main/java/ai/dvine/MainActivity.java` is 5271 lines / ~196 top-level members with no
section markers. Extract its responsibilities into focused classes under `ai.dvine.main.*`
so the Activity only wires lifecycle, views and the extracted helpers. Behaviour must not
change; every extraction is a move, not a rewrite.

## Toolchain: use Termux directly (do not forget)

This sandbox shell only has toybox — but **Termux on this device has the real toolchain**.
Anything needing `javac`, `java`, `git`, `python3`, `gradle` or `npm` must be run through
the `termux` tool (project folder is copied in, command runs inside it), not through `run`.
Verified present in Termux: `git`, `java`, `javac` at `/data/data/com.termux/files/usr/bin`,
plus a `~/.gradle` cache, so a real compile check **is** possible.

```sh
# compile check (from the repo root inside Termux)
javac -d /tmp/out $(find app/src/main/java -name '*.java')
# or the gradle route if the wrapper needs the network:
./gradlew :app:compileDebugJavaWithJavac

# git baseline / commit / push / PR
git status && git diff --stat
```

Rules:
- Compile verification happens in Termux (step 6), never as "impossible".
- git steps (status, commit, push, PR) happen in Termux too (step 7).
- Files changed with `write_file`/`edit_file` in the workspace are copied into Termux on
  each call, and any files Termux creates stay in Termux — so change in the workspace,
  verify in Termux, then push from the workspace-side git.

## Rules of the extraction

- One cluster per step; read every method in the cluster before moving it, keep bodies verbatim.
- The helper takes the `MainActivity` (or a narrow interface it implements) and uses its
  package-private fields/methods — same package, so no visibility hacks where avoidable.
- Fields the cluster owns move with it; fields shared by several clusters stay in the Activity.
- After each move: read the new file back, grep for leftover references, then `git status`.
- Test: compile in Termux (javac); also grep every moved symbol for stale references.

## Method clusters — CORRECTED against the live index (old names in PLAN.md did not exist)

Indexed with `grep -nE '^    [A-Za-z][^(]*[(]' MainActivity.java` → 196 members. The original
cluster lists were written against an older file (`sendGuardShow`, `isSending`, `speakingNow`,
`rebuildPersonaTabs`, `applyTypeBarStyle` … do not exist); these are the real symbols:

1. Send + waiting queue (was "send guard"): `SEND_GUARD_MS`/`lastSendAt` (L781), `sendComposer`
   (L785), `waiting`/`waitingWords`/`waitingSince`/`idleBeats` (L821), `busy` (L827), `hold` (L831),
   `releaseBeat` (L847), `unhold` (L889).
2. Crash banner/reporter: `installCrashReporter` (L475), `takeCrashReport` (L479),
   `bindCrashBanner` (L1046), `freshCrash` (L1082), `clearCrash` (L1104).
3. Read-aloud / speech: `readAloud` (L1226), `speakReply` (L1231), `readingSentences` (L1682),
   `toggleReading` (L1691), `readNext` (L1713), `stopGeneration`/`setGenerating`/`refreshSend`
   (L1734–1755) stay put (shared with send).
4. Persona tabs: `selectPersona` (L536/L540), `updatePersonaTabs` (L593), `setPersonaTab` (L599).
5. Type-bar/chat fitting: `fitTypeBar` (L511), `fitChatPane` (L527).
6. Brain/model gates: `applyProvider` (L3946), `brainLabel` (L3973), `askForBrainIfNone` (L3983),
   `showModelSwitch` (L3993), `switchOfflineModel` (L4021), `loadModel` (L4074),
   `modelCommand` (L4145), `startModelDownload` (L4313), `modelOn` (L1967).
7. Actions (staged card, connectors, shell, documents, creation): `showStaged` (L3244),
   `closeStaged` (L3265), `doAction` (L3273), `connectorTools` (L2740), `useConnector` (L2748),
   `runShell` (L2795), `makeDocument` (L3159), `create` (L4340), `runCreation` (L4349),
   `applyCreation` (L4400).
8. Voice input: `onMic` (L4527), `onMicRelease` (L4539), `sendHeld` (L4554),
   `startListening` (L4572), `onType` (L4595), `heldWords` (L4522), `dictating`/`toggleDictation`
   (L1023/L1027).
9. Play/turn stepping: `play` (L4677), `step` (L4693), `stopPlay` (L4780), `spokenLine` (L4685),
   `selfSheet` (L4760), `lastSaid` (L4770).
10. File picking: `openContentPicker` (L4795), `openReconstructionPicker` (L4822),
    `onActivityResult` (L4845), `pickedUris` (L4937), `deliverReconstruction` (L4954),
    `deliverFile` (L5010), `showSelected` (L5074), `readRaster` (L4980).
11. Agent work loop: `runAgent` (L2154), `markRunning` (L2361), `offerToCarryOn` (L2377),
    `backupPartial` (L2420), `clearPartial` (L2430), `agentWork`/`workKey`/`agentFile`/
    `saveAgentWork` (L2653–2672), `carriesOn` (L2642).
12. Social: `social` (L3040).
13. Meaning/command: `meanings` (L3295), `understand` (L3308), `act` (L3360), `command` (L3374),
    `retryLast` (L3423), `clearWorkspace` (L3452), `deleteTree` (L3470), `stagedAction` (L2992),
    `looksLikeAction` (L2979), `stager` (L2972).

## Steps

Baseline (step 0): 5271 lines / 196 top-level members in MainActivity, indexed as above.
- [ ] 1. Cluster 1 (sendComposer + waiting queue) → `ai/dvine/main/SendQueue.java`.
- [ ] 2. Cluster 2 (crash banner/reporter) → `main/CrashReport.java`.
- [ ] 3. Cluster 3 (read-aloud) → `main/ReadAloud.java`.
- [ ] 4. Cluster 4 (persona tabs) → `main/PersonaTabs.java`.
- [ ] 5. Cluster 5 (type-bar fitting) → `main/TypeBarFitter.java`.
- [ ] 6. Re-check: MainActivity line count drop, no stale refs (`grep`), compile in Termux.
- [ ] 7. git: status, commit, push on a new branch, open a PR.