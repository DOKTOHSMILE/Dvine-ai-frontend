# DVINE Android

Native Java client. One face fills the screen: **O-bìì** (Obi, cyan `#00E5FF`) or **Ēèvié** (Ivie, gold `#FFB300`). They switch as each one speaks.

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

## Controls

- Swipe across the screen: microphone on/off.
- Tap the face: the one on screen speaks. Drag the face: turn the head.
- Tap the input row: type.
- Say **Obi switch** or **Ivie switch** to change who is on screen.

## Build

Android Studio or CodeAssist: open this directory. Gradle 8.9, AGP 8.7.3, Java 17, compileSdk 35, minSdk 34.

```
gradle testDebugUnitTest assembleDebug
```

Regenerate assets: `python3 tools/make_logo.py` (logo + launcher icons), `python3 tools/pack_source.py` (knowledge pack).

No provider key belongs in this project. `api_base` in `strings.xml` stays empty until a private desk URL is set; sync does nothing until then.
