# DVINE Android

Native Java client. No faces: the screen is a pod floating in space, and the persona speaking (**O-bìì** / Obi, **Ēèvié** / Ivie, Ibiko) is a voice and a tint on it. Anything you ask it to draw ("draw a cat", "build me a car") appears in 3D above the pod: turn it with a finger, pinch to zoom and move it, change it part by part by voice ("take out the tyres", "make the ears pink", "add a spoiler", "undo"), and say "export it" to save it as a .glb for a game engine. While it thinks, its inner voice writes each step at the top of the screen.

The build plan is [`docs/DVINE_MASTER_BUILD_HANDBOOK.md`](docs/DVINE_MASTER_BUILD_HANDBOOK.md). Progress and evidence live in [`docs/BUILD_STATUS.md`](docs/BUILD_STATUS.md).

## What works without internet

The app ships a basic knowledge file (`app/src/main/assets/knowledge/base_knowledge.json`). With no connection it can:

- talk (greetings, who are you, jokes, riddles, help)
- do maths: "what is 12 times 7", "15 percent of 200", "square root of 81"
- convert units: "100 km to miles", "30 celsius to fahrenheit"
- tell the time and date
- define words: "what does stewardship mean"
- answer basic facts: "capital of Nigeria"
- comfort on sad news and share scripture or wisdom with its reference
- learn: "remember that my brother's name is Tobi" → later "what is my brother's name"

When it doesn't know, it says: *"I'm unable to connect to my database to get more information. Please connect to the internet and ask me again."*

What it learns stays on the phone. Say **"share what you learned"** to allow upload when online; **"forget that"** / **"forget everything"** to delete. Passwords, PINs, codes and card numbers are never kept.

## Controls (there is no screen UI)

- Hold anywhere to speak; let go to send.
- Double-tap: a text field to type in, with SEND.
- One finger on a drawing: turn it. Two fingers: pinch to zoom, move to place it.
- Tap the pod: the one speaking talks.
- Say **switch to Ivie** / **Obi** / **Ibiko** to change who speaks. **Debate X** has Obi and Ivie argue it out.

On first open Obi asks your name and remembers it ("call me …" changes it).

Actions are asked, then confirmed by voice:
- "Post this on WhatsApp: Good morning" → "Shall I open WhatsApp with this post…?" → "yes" → WhatsApp opens on the post screen with the text; **you** press send.
- "Copy that" → "Shall I copy…?" → "yes" → "Copied. It's on your clipboard."

They speak slowly in Nigerian English voices where the phone has them, over a soft synthesised pad ("music off" / "music on", "hide subtitles").

## Build

Android Studio or CodeAssist: open this directory. Gradle 8.9, AGP 8.7.3, Java 17, compileSdk 35, minSdk 34.

```
gradle testDebugUnitTest assembleDebug
```

Regenerate assets: `gradle :app:generateHumanAssets` (3D humans), `python3 tools/make_logo.py` (logo + launcher icons), `python3 tools/pack_source.py` (knowledge pack).

No provider key belongs in this project. `api_base` in `strings.xml` stays empty until a private desk URL is set; sync does nothing until then.
