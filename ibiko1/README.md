# Ibiko 1

Ibiko 1 is Ibiko's own language brain. It runs inside any Android app, with no network and no other model needed. It:

- answers from its built-in knowledge: world facts, the elements, local facts, and an English dictionary (Open English WordNet);
- remembers what it is taught, and learns from "right" and "wrong";
- researches the web only when the app gives it a way to (`Plug.web`);
- writes a function line (`§XX …`) when a message asks the app to do something.

Everything it needs is in this folder:

| Path | What it holds |
|---|---|
| `src/main/java/ai/ibiko/dvine1/` | The model: `Dvine1` (the brain), `Ibiko1Model` (the entry point), `formulas/` (grammar, commands, truth, foresight, …), `comprehend/` (dictionary and concepts), `learner/` |
| `src/main/assets/knowledge/` | Its built-in knowledge and their licences |
| `src/main/AndroidManifest.xml` | Its night-learning job |
| `build.gradle` | An Android library (minSdk 34, Java 17, no dependencies) |

## Use it in an app

1. Copy this folder next to your app module.
2. In `settings.gradle`, add `include ":ibiko1"`.
3. In the app's `build.gradle`, add `implementation project(':ibiko1')`.

```java
Ibiko1Model brain = new Ibiko1Model(context);   // its memory goes to context.getFilesDir()/brain
Ibiko1Model.Reply r = brain.reply("What is the capital of Ghana?", 15000);
r.text;    // "The capital of Ghana is Accra."
r.steps;   // how it worked it out
r.code;    // a function line for your app, when the message asked for an action
brain.feedback("What is the capital of Ghana?", r.text, true);   // it learns
brain.close();
```

Its id is `dvine-1-0.1` and its address is `dvine://dvine1/v1/chat` (`Ibiko1Model.ID` and `Ibiko1Model.ADDRESS`), the same kind of name other models have.

### Optional plugs (`Plug`)

Set these once, at app start:

- `Plug.asset`: where its files are. Set by `Ibiko1Model` from the context's assets.
- `Plug.web`: the web, for research.
- `Plug.workers`: a board of who is working on what.
- `Plug.interests`: what the user is into, for night learning.
- `Plug.icon`: its notification icon.

### On a chat screen

To show working steps and right or wrong buttons, implement `Dvine1.Host` and create a `Dvine1` directly. That is how the Ibiko app uses it (`main/Dvine1Screen` in the app).

## Tests

Its tests are in the app for now (`app/src/test/java/ai/ibiko/dvine1/`), including `Ibiko1ModelTest` for the entry point.
