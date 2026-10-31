# DVINE prompt ledger

What was asked, what the phone app actually does, and what it still does not do.
Version on this commit: 0.44.0. Tests are JVM unit tests. Gradle and a phone were not run for 0.44.0.

This is one reading of the instructions sent over the build. It is not a claim that every line is finished.

## How a sentence is handled now

1. The words are read once.
2. `MachinePlan` turns a draw or a design into one operation: look up photographs.
3. A known object the user asked to **build** (a motorcycle, a house, a person, and the other named categories) stays a procedural model with parts. That path says what it is. It is not a photograph.
4. A world question can be checked online. Sources are Wikipedia, Wikidata, DuckDuckGo, and Reddit. There is no Google API key, so Google is not queried and is not pretended.
5. If two of those sources agree, the answer says two sources, which is not a proof. One source is not called verified. No network says it did not search.
6. Several web photographs of a bicycle are shown as photographs. They are not turned into a mesh. They are not one bicycle from known cameras.

## This batch (0.44.0), twenty changes

| # | Change | Honest limit |
|---|---|---|
| 1 | Draw and sketch requests no longer become a pile of boxes named as the object | The old planner is not called |
| 2 | Design requests look up photographs, including "design a red bicycle" | Needs the network |
| 3 | "Build a motorcycle" is still the editable procedural model | It is not a scan |
| 4 | "Form a dragon" and other unknown forms look up photographs | No invented mesh |
| 5 | Built-in globe, DNA, galaxy, tower, and the other stored 3D figures are not put on the board | Photograph lookup instead |
| 6 | Stored sketches of a house, car, tree, person, and similar objects are not drawn | A triangle diagram and "3 plus 4" still explain the idea |
| 7 | An unknown "build a pen" looks up a photograph even with no language model | Offline, it refuses rather than invent |
| 8 | Wikimedia Commons is searched for up to four real file URLs | Only `upload.wikimedia.org` is accepted |
| 9 | The first file that downloads is shown | If none download, nothing is invented |
| 10 | The photograph is cropped to the middle | That is not an object cut-out |
| 11 | The speech counts the files and says they are not a measured model | No COLMAP, no dense scan |
| 12 | Wikipedia's lead image is only a fallback | Still a photograph, still not a mesh |
| 13 | A saved shape file is not reopened onto the board as the object | The file can still exist on disk |
| 14 | DuckDuckGo is a fact-check source | It is not Google |
| 15 | Reddit public search is a fact-check source | It is post titles, not a private feed |
| 16 | Any agreeing pair can verify, not only the first two | Still not a proof |
| 17 | A number conflict is still a conflict | The answer is not called verified |
| 18 | Empty search no longer claims only Wikipedia was tried | Google is named as unavailable |
| 19 | Words for a drawing become `LOOK_UP_PHOTOS` before any geometry | One plan, then the fetch |
| 20 | "Show me a globe" from the offline brain is the same photograph command | It does not set a built-in object |

## Earlier instructions, and where they stand

| Asked | State |
|---|---|
| Offline Android app first, backend later | In progress. The phone app is the repo that is being pushed |
| Personas Obi, Ivie, and Ibiko | Built. Ibiko is a role for coding talk, not a second brain |
| One reading for text and voice | Built. `BrainCoordinator` |
| Do not invent news | Built. There is no news source |
| Fact check with two sources, strip hostile page text | Built. Sources are now four when they answer |
| Do not guess a world fact with no source and no model | Built. Online, a lookup is tried first |
| Show thinking instead of a silent screen | Built. A live line, seconds, and word count |
| Do not cut a live answer at 25 seconds | Built. Quiet for 12 seconds, or 90 seconds total |
| Keep a packed fact when the model is missing | Built |
| Yes or no only for a pending delete or server command | Built |
| Photogrammetry from the user's own photographs | Built as a sparse solver. One photo is not measured. Not COLMAP |
| Procedural motorcycle: detach, restore, undo, save, reopen | Built. "Draw a motorcycle" is now a photograph, not this model |
| Rigged person: one arm, both arms, wave, look up | Built. Procedural bones, not a scan |
| Part tree, undo, redo, catalogue | Built |
| Drawing a pen as spheres and boxes | Removed from the live path in 0.44.0 |
| Google image search and a measured bicycle from those pictures | Not built. No Google key. Unrelated web photos are not a calibrated scan, so no mesh is made from them |
| Social media beyond a public Reddit search | Not built. No logged-in X, Instagram, or TikTok |
| Dense reconstruction, COLMAP, a diffusion image model | Not built, and not claimed |
| Backend service | Not started. Deferred on purpose |
| `gradle assembleDebug` and a phone smoke test | Not run in this environment |

## What to say on a phone

- "Draw me a bicycle" while online: photographs, a middle crop, and a sentence that it is not a measured model.
- "Draw me a pen" while offline: a refusal. No shape called a pen.
- "Build a motorcycle": the procedural model, with parts you can detach.
- "Fact check the capital of France" while online: sources by name, or an honest miss.
