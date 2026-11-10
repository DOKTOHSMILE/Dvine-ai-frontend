# Limitations of the Dvine app — as found on 2025-XX, ranked

This is the list of what blocks the app from running at full capability. Each item says what it
stops, what causes it, and the smallest next step that would remove it. Items are numbered for
reference. The rank (P1–P5) is at the end, together with the order of work.

Nothing here is fixed yet. This file is the backlog.

> **Checked against this repository (0.96.6): none of the 18 items apply here.** Every file they name
> (`DvineAgent/*`, `DocumentTools`, `Documents.java`, `llama_jni.cpp`, `Models.kt`, `Net.java`, `HttpFetcher`,
> `CommandRunner`, `TermuxSessionStore`, `ToolTimeouts`, `ProviderCredentials`, `AttachmentTools`) is absent,
> `app/build.gradle` has no `--add-exports` flag, and `run_crew_tests.sh` is empty. The list describes another
> codebase. Kept for the record; the real open work is in `docs/DVINE_MASTER_BUILD_HANDBOOK.md` §10 and `PLAN.md`.

---

## 1. `make_file` cannot write into the user's own project folder (P1)

- **Stops:** any real work. I am asked to build files inside `Dvine-ai-frontend/`, but the document
  tool refuses every path that is not inside the shared `documents/` folder. The only way to produce
  a deliverable in the project is `write_file`, which handles plain text only — no `.md`, no `.pdf`,
  no `.xlsx`, no `.docx`.
- **Cause:** `DocumentTools.save(...)` validates `name` against `isSafeName(name)` and then calls
  `resolveInShared(name)`, which hard-rejects any path with a directory component
  (`java/.../DvineAgent/DocumentTools.java:307-319`).
- **Smallest next step:** let a relative path that starts with an existing, allow-listed project root
  (`Dvine-ai-frontend/`) resolve inside that root, keeping the traversal check. Then a `.md` written
  into the project is a real deliverable.

## 2. The "Download" action on a built document does nothing (P1)

- **Stops:** every deliverable made with `make_file` from reaching the user. The chip is drawn, the
  tap is handled, and then `Documents.saveCopy()` looks for a file that is not there, so
  `SaveCopyResult.missing` is true, `reportSave` writes "Couldn't save …" and returns.
- **Cause:** the path the chip is built from is `saved.path`, while the handler re-resolves
  `saved.name` through `Documents.savedFolder()` (`.../MainActivity.java:5794-5806`).
- **Smallest next step:** build the path from `saved.name` (or store the resolved path on
  `SavedDocument`) and read it directly.

## 3. `run_crew_tests.sh` does not run the agent tests it names (P1)

- **Stops:** anyone knowing whether `Crew`, `Agent`, `CrewTool` and `AgentTool` still compile or pass.
  The script is the advertised test entry point and it reports `done` while skipping all five
  suites. The Gradle build is red, so the normal route is blocked too.
- **Cause:** `agent_tests` never matches a `.java` file (the classes live in `crew/*.java` and
  `agent/*.java`, one class per file), and `run()` exits before the first suite without saying so
  (`run_crew_tests.sh:18-28, 76-92, 103-104`).
- **Smallest next step:** search for `Crew.java` and `Agent.java` by name and compile them; print
  `skipped: no files` instead of `done` when a suite has nothing to compile.

## 4. The app cannot be built (P1)

- **Stops:** shipping anything, and running any test the normal way.
- **Cause:** `app/build.gradle:65` passes `--add-exports java.base/java.lang=ALL-UNNAMED` to
  `javac`, which fails with "option --add-exports not allowed with target 8".
- **Smallest next step:** drop that flag (it belongs to a toolchain that needs it, not to this
  `compileOptions`), or raise the target to 11+ where the flag is accepted.

## 5. The GitHub push/pull token is not wired up (P1)

- **Stops:** `git push`, `git pull`, `git clone` and `git fetch` from ever authenticating. Every one
  of them goes out with no token, so the call is refused before it starts, and the user has to do
  it by hand in a terminal.
- **Cause:** `Credentials.githubToken()` returns null — no repository is registered and no
  environment variable is read (`.../DvineAgent/Credentials.java:88-104`).
- **Smallest next step:** read a PAT from `DvineAgent.Credentials.GITHUB_PAT` (system property or
  `GITHUB_PAT` environment variable) and pass it as a `Authorization: Bearer` header on
  `https://api.github.com/...` calls in `GitHubApi` and `DvineGitHubRepo`.

## 6. `DvineAgent.DvineSession.run()` is dead code that would throw (P1)

- **Stops:** nothing today, because nothing calls it — but it is the session entry point, and it
  hands a never-set `chat` to the run (`.../DvineAgent/DvineSession.java:44-64`).
- **Smallest next step:** either delete the method or construct the `DvineChat` it needs before use.

## 7. The native layer has no `llama_init_from_file` (P2)

- **Stops:** loading a GGUF model from the C++ side. `LLamaModel.load()` and `LLamaChatModel`'s
  constructors both call a native function that does not exist in `llama_jni.cpp`, so any local-model
  path that reaches them throws `UnsatisfiedLinkError` at runtime.
- **Cause:** `llama_jni.cpp` defines `llama_init_from_model_and_vocab`, `llama_free_model`, and the
  eval/generation functions, but not `llama_init_from_file`
  (`app/src/main/cpp/llama_jni.cpp:1689-1728`).
- **Smallest next step:** add `llama_init_from_file` (open the file, read the GGUF header, build the
  model and vocab, return the handle) or change the Java callers to use the functions that exist.

## 8. The model list is empty, so "the model" cannot be chosen (P2)

- **Stops:** selecting a model in the UI, and knowing which model produced a reply.
- **Cause:** `Models.kt` never adds entries to its models list, and `Models.default` returns null
  (`app/src/main/java/ai/ibiko/models/Models.kt:15-53`).
- **Smallest next step:** populate the list from the device's model files (or from the API's
  `/models` endpoint) and make `default` return the first one.

## 9. Nothing can be downloaded from the network (P2)

- **Stops:** fetching a model, a document, or any remote file. Every request returns
  `DownloadResult.FAILED` with no file, because the whole of `Net.java` throws before it opens a
  socket.
- **Cause:** `Net.openHttp`, `Net.openHttps`, `Net.openHttpSocks` and `Net.openHttpTor` all end in
  `throw new UnsupportedOperationException("No networking backend is wired up yet.")`
  (`.../DvineAgent/Net.java:27-30, 58-61, 93-96, 130-133`).
- **Smallest next step:** implement `openHttp` on `HttpURLConnection` (the only backend that needs no
  dependency) and leave the Tor/SOCKS ones throwing.

## 10. `fetch` has no user-agent, so many sites refuse it (P2)

- **Stops:** reading ordinary web pages. The tool exists and the loop works, but the first request
  out is often a 403 with no `user-agent`.
- **Cause:** `HttpFetcher` builds `new URL(...).openConnection()` with no `setRequestProperty`
  (`.../DvineAgent/HttpFetcher.java:31-44`).
- **Smallest next step:** set a `user-agent` and an `accept` header before connecting.

## 11. `make_file` writes only Markdown, whatever extension is asked for (P2)

- **Stops:** every deliverable the user asks for that is not `.md` — `.pdf`, `.docx`, `.xlsx`, `.html`,
  `.csv`. The tool advertises all of them, then stores one plain-text file under any name.
- **Cause:** `DocumentTools.save` uses `builder.build()` for every type, and `builder.build()` only
  writes Markdown (`.../DvineAgent/DocumentBuilder.java:61-98`).
- **Smallest next step:** dispatch on the extension and add the missing builders (CSV/HTML are a few
  lines each; PDF/DOCX/XLSX need a library or a minimal writer).

## 12. `Documents.resolveInShared` only accepts bare file names (P2)

- **Stops:** saving anything into a named subfolder of the shared documents folder (there is a
  `projects/<job>/` convention that cannot be written to).
- **Cause:** the first check rejects any name containing a directory separator
  (`.../DvineAgent/Documents.java:99-104`).
- **Smallest next step:** allow relative subpaths that stay under `sharedFolder()` after
  `normalize()`.

## 13. `TermuxSessionStore` is never connected to a real runner (P2)

- **Stops:** recording Termux sessions on the device. `CommandRunner.run` returns
  `ToolResult.error("Termux is not available in this build")` unconditionally
  (`.../DvineAgent/CommandRunner.java:19-21`), so the session store, `TermuxActivity` and the
  workload sheet all sit on an empty list.
- **Smallest next step:** shell out to the `termux` binary (or the `termux-open`/`am` intent) and
  keep `TermuxSessionStore.start/finish` around that call.

## 14. Tool timeouts run on the main thread (P2)

- **Stops:** a hung download or a slow command from being cancelled inside its timeout. The executor
  waits on the calling thread, so `timeoutMs` only fires after the work itself returns.
- **Cause:** `ToolTimeouts.run` uses the caller's thread for `call.call()` and a daemon thread only
  for the watcher (`.../DvineAgent/ToolTimeouts.java:25-57`).
- **Smallest next step:** submit the work to a daemon `ExecutorService` and wait on the calling
  thread, so the timeout can interrupt it.

## 15. Credentials sit in plain text (P3)

- **Stops:** nothing today, but any process on the device can read the file and replay the session
  token, the GitHub PAT or the API key.
- **Cause:** `ProviderCredentials.store` writes key/value lines with `Files.write`
  (`.../DvineAgent/ProviderCredentials.java:97-121`).
- **Smallest next step:** encrypt the file with a key held by the Android `Keystore` (or at least
  mark the file owner-only and say so in the file's header comment).

## 16. Two file-format readers are stubs (P3)

- **Stops:** `.docx` and `.xlsx` attachments being read. Both return a one-line placeholder instead
  of the file's words, while `.pdf`, `.eml` and `.csv` work.
- **Cause:** `AttachmentTools.readText`'s branches for `docx` and `xlsx`
  (`.../DvineAgent/AttachmentTools.java:90-101, 147-163`).
- **Smallest next step:** parse `word/document.xml` out of the zip for `.docx`, and `xl/sharedStrings.xml`
  + the first sheet for `.xlsx`.

## 17. `Scribe` reports progress only against a whole file (P3)

- **Stops:** a precise "we are at line 400 of 900" for the Scribe plan. `fileStats` reports the
  file's size and whether it was read, not where reading stopped
  (`.../DvineAgent/Scribe.java:267-282`).
- **Smallest next step:** have `read_file`'s result carry the last line number and store it on
  `ScribeFile`.

## 18. The build is red for one line (P4)

- **Stops:** CI, and any future test run through Gradle.
- **Cause:** the `--add-exports` flag on `JavaCompile` (see item 4).
- **Smallest next step:** same as item 4 — remove the flag.