# DVINE Android

Native Java client. One face fills the screen. Obi (cyan `#00E5FF`) and Ivie (amber `#FFB300`) do not appear together.

The face changes only when that persona has a line, or when you call the other one.

- Say **Obi switch** or **Ivie switch** to change who is on screen. Speech may hear those as "Ubi switch" and "Eva switch".
- Ask a question and they switch by themselves as each one speaks. A swipe does not change the face.
- Tap the face and the one on screen speaks.
- Mic runs the same turn rules as the desk: a short line stays with one voice. A real question lets Obi and Ivie each argue once from their own side, then one of them closes. A rush gets one rebuttal. A consequential ask is refused out loud.
- Type → Obi or Ivie speaks your words. Ask both runs the turn rules.

Motion is sprung, not snapped: blink, glance, brow, jaw, and a slow breathe. Ivie rests happier. Obi rests content. Lips and brows are rigged, not a flat zoom.

## Open

Android Studio: File → Open this directory. It will create the Gradle wrapper from `gradle/wrapper/gradle-wrapper.properties` (Gradle 8.9, Android Gradle Plugin 8.7.3, Java 17, minSdk 26).

No provider key belongs in this project. `api_base` in `strings.xml` stays empty until a private desk URL is set, and even then the session token is not stored in source.

## Status

Phase 4 is in progress. The turn order is covered by `LocalTurn` and matched on the server. It has not been run on a device in this commit.
