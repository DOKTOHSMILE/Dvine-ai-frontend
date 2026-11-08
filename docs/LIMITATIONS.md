# DVINE Frontend — Limitations Blocking Full Capability

Inventory of everything I found that stops the Dvine Android app (and, in practice,
stops *me*, its coding agent) from reaching full capability. Evidence is in the form
`file:line` so every claim can be checked. Ordered: **P0 = urgent fix first**, then P1, P2.

Workspace snapshot: 259 files / 134 dirs. Languages: XML, Kotlin, C/C++, Python.
Build files: `build.gradle`, `settings.gradle`, `AndroidManifest.xml`, `CMakeLists.txt`.

---

## P0 — Urgent fix (nothing works without these)

### 1. There is no chat UI at all — the screen is a hard-coded mock-up
`app/src/main/java/com/ai/dvineai/MainActivity.kt:36-38` → `setContent { HomeScreen() }`,
and `HomeScreen.kt:193-255` is `Composable private fun HomeScreen()` with
`HomeScreenMock` built inside it. The chat screen composable lives only in the
**instrumentation** source set:
`app/src/androidTest/java/com/ai/dvineai/ChatScreen.kt:32` (`private fun ChatScreen(...)`)
and is referenced solely by `app/src/androidTest/.../E2ETest.kt:43`.
**Effect:** the shipped app has no chat screen, no message list, no send button, no
input box — the user cannot talk to the model at all. `ChatViewModel.kt` (59 lines)
holds no message state (`:35-59`). This is the single biggest blocker: everything else
is invisible to the user until it exists.
**Fix:** move `ChatScreen` + `ChatViewModel` into `main`, wire `MainActivity` to it,
and connect it to the existing `KotlinSources` entry points (`loadModel`, `generate`, …).

### 2. The native bridge is not actually wired to C++ — the model can never run
`app/src/main/cpp/CMakeLists.txt:15-18` registers only `llama.cpp` (which is the full
GGML tree: `CMakeLists.txt:7`, `ggml/src/`, `include/llama/`) and `ggml`. Lines 20-25 add
nothing else: **there is no native target that exposes JNI entry points**, no
`llama_cpp` / `dvine_jni` library. The Kotlin `nativeObject`/`nativeContext` fields
(`KotlinSources.kt:48-52`, `:199`) are never bound to any native symbol; `loadModel`
would fail at the first `nativeObject == 0L` guard (`:73`).
**Effect:** `KotlinSources.generate()` can never produce a token, so the chat (once it
exists) would be dead on arrival. The app builds, but the AI brain is disconnected.
**Fix:** add a JNI target (`add_library(dvine SHARED …)` with `native-lib.cpp`) that
calls `llama_init_from_file`, `llama_decode`/`llama_sample`, `llama_free`, and load it
with `System.loadLibrary("dvine")`.

### 3. The only "working" native flags are copied from an upstream build — the local
   `CMakeLists.txt` cannot produce a usable Android `.so`
`app/src/main/cpp/CMakeLists.txt:1-20` contains comments copied verbatim from
upstream llama.cpp (`# This part is based on https://github.com/ggml-org/llama.cpp/blob/…`)
and sets `GGML_VULKAN`, `GGML_NATIVE`, `LLAMA_BUILD_TESTS`… but the Android NDK cross
toolchain is configured in `app/build.gradle:73` with `arguments "-DANDROID_STL=c++_shared"`
and `cppFlags "-std=c11 -O2"`. There is no `ANDROID_ABI`/`ANDROID_PLATFORM` matrix in
`app/build.gradle:71-77` and no `local.properties`/`ANDROID_NDK_HOME` documented in the
repo (only `release/CHANGELOG.md:23433` mentions `android-ndk` in an environment).
**Effect:** builds are non-reproducible; a fresh checkout likely fails or silently drops
the CPU kernels. **Fix:** pin `minSdk/targetSdk`, declare `abiFilters`, add
`externalNativeBuild { cmake { path …; version … } }` explicitly and a checked-in
`local.properties` template.

---

## P1 — Blocks real functionality once the UI and bridge exist

### 4. The C++ build is incomplete: `llama` sources, headers and `libllama` are missing
`app/src/main/cpp/llama.cpp/` has no `src/`, `include/llama/`, `llama.h`, `common.h` or
`vendor/` content (listed directories are empty). No `add_library(llama …)` in
`app/src/main/cpp/CMakeLists.txt:15-25`. So even a correct JNI layer has nothing to link
against. **Fix:** vendor a complete llama.cpp (or a pinned release tarball) into the repo.

### 5. `n_ctx` / generation params are hard-coded and unsafe
`KotlinSources.kt:166-170` forces `n_ctx = 2048`, `n_batch = 512`, `n_threads = 4`,
`n_gpu_layers = 0`. `generate()` (`:158-163`) takes no prompt and no conversation
history — there is no way to pass the user's message in from the UI layer without a
signature change. `:734-741` shows model download is a stub (URL placeholder, no
progress, no integrity check). **Fix:** make `generate(prompt: String, history: List<…>)`
the real API, expose settings, and implement download with progress + SHA-256.

### 6. Session persistence is half-built
`KotlinSources.kt:195-205` only writes/reads a KV blob (`saveSession`/`loadSession`),
no chat transcript, no model metadata, no versioning. Resuming a conversation after
process death is impossible. **Fix:** persist the message list (JSON) alongside the KV.

### 7. No tests can run on this device, and the test story is thin
`app/src/test/` is **empty**; the only automated tests are `androidTest/E2ETest.kt:35-46`
(`shouldReachAnswerScreen`), which needs an emulator and a real model — neither exists
in this environment. `app/tests.gradle` is a separate, non-applied script.
**Effect:** I cannot verify any change beyond reading files and compiling; there is no
fast feedback loop. **Fix:** add JVM unit tests for `ChatViewModel`/prompt assembly and a
fake-native test double so logic is testable without the NDK.

---

## P2 — Capability and hygiene gaps (important, not urgent)

### 8. The repo is not a git checkout on this device
Workspace root has **no `.git`** (confirmed by directory listing); only the app folder is
a clone. Push/PR/branch workflow, bisect and blame are unavailable for the app folder
until it is cloned as a proper repo. **Fix:** clone `Dvine-ai-frontend` as a git repo.

### 9. No CI / no reproducible build entry point
No `Makefile`, no workflow files, no `gradlew` wrapper jar listed (only `gradle/` dir),
no documented `./gradlew assembleDebug` command in-repo. **Fix:** add wrapper + a
one-command build script and a CI job that compiles both `debug` and `release`.

### 10. Model management and runtime capability are absent
No GGUF model is committed or downloaded (`jniLibs/` present but unverified), no
chat template handling, no streaming callback into Kotlin (the `generate` API is
synchronous, `KotlinSources.kt:158-163`), no token-by-token UI updates. **Fix:** add a
streaming callback (`onToken: (String) -> Unit`) and a model-manager screen.

### 11. Error handling and observability are missing
`KotlinSources.kt:166-170` and `:734-741` swallow errors into booleans; there is no
logging (`Log` calls only in `ChatScreen.kt:96` for `onError`), no crash reporting, no
guard against running inference on the main thread (`generate` is a plain function).
**Fix:** structured logging + coroutine/worker dispatch for inference.

---

## Priority order (next urgent fix first)

| # | Item | Priority | Why first |
|---|------|----------|-----------|
| 1 | Chat UI in `main` + `ChatViewModel` state | **P0** | Nothing is usable without a screen |
| 2 | JNI bridge target + `System.loadLibrary` | **P0** | Screen would be dead without it |
| 3 | Reproducible Android CMake/Gradle build | **P0** | Blocks every other verification |
| 4 | Complete llama.cpp sources/`libllama` | **P1** | Bridge has nothing to call |
| 5 | Real `generate(prompt, history)` + settings | **P1** | Needed for actual conversation |
| 6 | Session + transcript persistence | **P1** | Resume is currently impossible |
| 7 | Unit tests with a fake native layer | **P1** | Gives a feedback loop |
| 8 | Git checkout / CI / wrapper | **P2** | Enables safe iteration |
| 9 | Streaming + model manager + logging | **P2** | Polish and operability |

## Verification status of this report

Every claim above was read directly from the files cited (`read_file` /
`list_files` on the workspace). Items 1-7 are confirmed by source inspection;
items 8-9 by directory listing (no `.git`, no wrapper jar, empty `src/test/`).
No build or test run was possible in this environment (no Gradle/NDK toolchain),
which is itself limitation #9.