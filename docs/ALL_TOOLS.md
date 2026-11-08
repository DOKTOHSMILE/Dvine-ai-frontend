# Every tool, checked against the source (0.96.1)

Companion to `LIMITATIONS.md`. That file lists what is broken; this one lists **every tool the
agent exposes**, says whether it actually works in this build, and points at the evidence.

Two notes on where the evidence lives:

- The app source (`app/src/main/...`) is **not in this folder** — only the docs are. So every
  citation below points at the numbered item in `LIMITATIONS.md`, which carries the file:line
  taken from the original code audit.
- Statuses: **works** (reaches its own result), **partial** (does something narrower than its
  name), **blocked** (always fails), **not wired** (call site exists, nothing is hooked up).

---

## 1. File and workspace

| Tool | Status | What actually happens | Evidence |
|---|---|---|---|
| `list_files` | works | Walks a directory under the workspace. | audit, no limitation reported |
| `read_file` | works | Reads a range of lines; a missing file returns an error result, not a crash. | audit, no limitation reported |
| `write_file` | works | Plain text only — whatever the extension says. | audit, no limitation reported |
| `edit_file` | works | Old text must match exactly, or it reports no match. | audit, no limitation reported |
| `make_file` | **P1 blocked** | Refuses any path outside the shared `documents/` folder, so a deliverable cannot land in the project. When it does save, it writes Markdown under every extension (`.pdf`, `.docx`, `.xlsx`, `.html`, `.csv` all get Markdown text). | `LIMITATIONS.md` items 1, 11 |
| `zip` / `unzip` | partial | Reachable only through the document/shell paths, both of which are limited (see below). | `LIMITATIONS.md` items 11, 13 |
| `search` | works | Scans source files a line at a time and reports `file:line:text`. | audit, no limitation reported |
| `run` | **P2 blocked** | The command runner returns `ToolResult.error("Termux is not available in this build")` unconditionally, so every shell command in the workspace fails. | `LIMITATIONS.md` item 13 |
| `termux` | **P2 blocked** | Same runner path — `TermuxSessionStore.start/finish` is never called, so the session store, `TermuxActivity` and the workload sheet all stay empty. | `LIMITATIONS.md` item 13 |

## 2. Documents and attachments

| Tool | Status | What actually happens | Evidence |
|---|---|---|---|
| Document build (`.pdf`, `.docx`, `.xlsx`, `.html`, `.csv`, `.md`) | **P2 partial** | The builder only ever writes Markdown; the extension is ignored. | `LIMITATIONS.md` item 11 |
| "Download" chip on a built document | **P1 blocked** | The handler re-resolves `saved.name` while the chip is built from `saved.path`, so `saveCopy` reports missing and the user gets "Couldn't save". | `LIMITATIONS.md` item 2 |
| Attachment read: `.pdf`, `.eml`, `.csv` | works | Real text comes back. | audit, no limitation reported |
| Attachment read: `.docx`, `.xlsx` | **P3 stub** | Both return a one-line placeholder instead of the file's words. | `LIMITATIONS.md` item 16 |

## 3. Shell, git, Termux

| Tool | Status | What actually happens | Evidence |
|---|---|---|---|
| Shell run in workspace (`run`) | **P2 blocked** | No backend: the command runner always errors. | `LIMITATIONS.md` item 13 |
| Git (clone/pull/push) | **P1 blocked** | `Credentials.githubToken()` returns null — no repository, no env var — so every push/pull/fetch is refused before it starts. | `LIMITATIONS.md` item 5 |
| Termux command execution | **P2 blocked** | Same runner path; sessions are never recorded. | `LIMITATIONS.md` item 13 |

## 4. Network: `fetch`, `connector`

| Tool | Status | What actually happens | Evidence |
|---|---|---|---|
| `fetch` (raw URL) | **P2 partial** | Sends no `user-agent`, so many sites answer 403 before content is read. | `LIMITATIONS.md` item 10 |
| `connector web.search` | **P2 blocked** | Goes through the network layer whose four openers all throw `UnsupportedOperationException("No networking backend is wired up yet.")` — every request returns `FAILED` with no file. | `LIMITATIONS.md` item 9 |
| `connector calendar.week` | **P2 blocked** | Same network path. | `LIMITATIONS.md` item 9 |
| `connector github.*` | **P1 blocked** | Needs both the network layer and a token; the token is null, so it can never authenticate. | `LIMITATIONS.md` items 5, 9 |
| `connector social.*` | **P2 blocked** | Same network path, plus a signed-in session store. | `LIMITATIONS.md` item 9 |
| Model download over the network | **P2 blocked** | Same network path — no model file can be fetched. | `LIMITATIONS.md` item 9 |

## 5. Local model and native layer

| Tool | Status | What actually happens | Evidence |
|---|---|---|---|
| Local GGUF load | **P2 blocked** | `LLamaModel.load()` / `LLamaChatModel` call `llama_init_from_file`, which is not defined in `llama_jni.cpp` → `UnsatisfiedLinkError` at runtime. | `LIMITATIONS.md` item 7 |
| Model picker in the UI | **P2 blocked** | `Models.kt` never fills its list and `Models.default` returns null, so there is no "current model" to report. | `LIMITATIONS.md` item 8 |

## 6. Timeouts, credentials, progress

| Tool | Status | What actually happens | Evidence |
|---|---|---|---|
| Any tool with `timeoutMs` | **P2 partial** | The work runs on the calling thread, so the timeout only fires after the work returns; a hung call cannot be cancelled. | `LIMITATIONS.md` item 14 |
| Credential storage behind API tools | **P3 risk** | Session token, GitHub PAT and API key are written as plain key/value lines. | `LIMITATIONS.md` item 15 |
| `Scribe` progress | **P3 partial** | Reports file size and read/not-read, never the line where reading stopped — no "line 400 of 900". | `LIMITATIONS.md` item 17 |

## 7. Build and test entry points

| Entry point | Status | What actually happens | Evidence |
|---|---|---|---|
| Gradle build | **P4 red** | `--add-exports java.base/java.lang=ALL-UNNAMED` fails with "not allowed with target 8". | `LIMITATIONS.md` items 4, 18 |
| `run_crew_tests.sh` | **P1 blocked** | Reports `done` while skipping all five suites — `agent_tests` never matches a `.java` file and `run()` exits before the first suite. | `LIMITATIONS.md` item 3 |
| The 0.96.0 record-keeping test | not yet compiled | Written, not yet compiled or run; runs at the next build. | `PLAN.md` |

---

## Summary

- **Works as advertised:** `list_files`, `read_file`, `write_file`, `edit_file`, `search`, and the
  `.pdf`/`.eml`/`.csv` attachment readers.
- **P1 — nothing ships until these move:** `make_file` into the project, the Download chip, the
  test script, the build flag, the GitHub token, the dead session runner call.
- **P2 — the app cannot talk to the world or to a model:** no networking backend, no model list,
  no `llama_init_from_file`, no shell runner, timeouts on the wrong thread, Markdown-only
  documents, missing user-agent.
- **P3/P4:** plain-text credentials, two stub readers, Scribe's coarse progress, the one-line build
  break.

The order of work is at the bottom of `LIMITATIONS.md`; this file is the map of what each fix
unblocks.