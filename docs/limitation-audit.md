# Limitation audit

## 0.95.0 — RAM offline, tokens online

Read in the source and changed. Security limits (sandbox, confirmations, key storage) stay.

| Limit | Where | What was wrong | What 0.95.0 does |
|---|---|---|---|
| Every API call reserved at least 1,024 output tokens, and a short reasoning call reserved 8,000 | `RemoteBrain.body` | A one-line order ("hi", 48 tokens) was allowed to spend a long answer. A user cap of 4,000 on a reasoning model was raised to 12,000. | A plain model gets the allowance it was asked for. A short reasoning call keeps 2,048 tokens of room to think, not 8,000. A cap set in Settings is never raised. A one-line order that fills its cap is not sent again. |
| Simple questions resent 12,000 characters of old chat | `PromptFit.remoteTurns` | A 128k window was trimmed, but a greeting still paid for the long history. | Fast-tier calls keep 4,000 characters of older turns. The latest turn is still sent whole. Real work keeps 12,000. |
| Agent steps resent up to 48,000 characters | `MainActivity` work budget | A large API window billed about 12,000 tokens of the work log on every tool step. | Capped at 24,000 characters. Small windows are unchanged (`window × 3`). |
| Unknown network counted as online | `MainActivity.applyProvider` | `connectivity == null` tried every API, failed, then fell back. That spends tokens and time. | Unknown is offline. The on-device model answers. A real validated network still uses the API. |
| Session token used the wall clock | `SessionClient` | Moving the phone clock kept a dead token or dropped a live one until the next 401. | Expiry is five minutes early on `elapsedRealtime`. |
| On-device weights stayed resident | `dvine_llm.cpp`, `DvineApp` | The loader could mlock the GGUF. A 512-wide compute batch was allocated even for a 2,048-token window. 4,096 tokens of cache stayed 16-bit. A low-memory warning did not unload an idle model. | Weights are mmap, not locked. Batch is 128 / 256 / 512 with the window. KV cache is 8-bit from 4,096 tokens up (16-bit if that fails). Android `onTrimMemory` frees an idle model. A GGUF with no chat template still answers (ChatML). |

Not changed, on purpose: the sandbox, confirm before shell / push / pull request, the stop button, prompt fitting when the model says the input is too long, and Android's refusal to keep a background process forever.

## 0.94.0

Each limit below was read in the source and classed. Only class A items that were safe to widen were changed. Security and Android limits stay.

| Limit | Where | Class | What 0.94.0 does |
|---|---|---|---|
| Reply idle 60s, write cap 180s, read 10 min | `LocalModel` / `ReplyWatch` | A for long jobs, D for a stalled model | Run mode overrides them. Balanced keeps 180s. Deep is 10 min, Autonomous is 60 min. A token still resets the idle timer. |
| Termux wait 180s | `TermuxRunner` | A | Balanced stays 180s. Deep 10 min, Autonomous 60 min. A killed process is not resumed mid-instruction. |
| Shell 30s | `PhoneHost` | A | Follows the run mode (20s quick, 30s balanced, up to 5 min autonomous). |
| Agent round of 8 steps | `MainActivity.ROUND_STEPS` | A | Balanced stays 8. Deep 20, Extended 40, Autonomous 80, still capped by Settings › Agent steps. |
| Job wall clock 8 hours | `MAX_WORK_MS` | C (process lifetime) plus A | Not shortened. Extended and Autonomous may run the loop for 24 hours while the process lives. |
| Clone file size 1 MB | `GitHubGit.MAX_FILE_BYTES` | B (memory) | Balanced stays 1 MB. Deep 4 MB, Autonomous 8 MB. Larger blobs are skipped, not loaded. |
| Clone file count | `Tuning.CLONE_FILES` | B (storage) | Unchanged. Already a setting, up to 50,000. |
| GitHub repo list | `Connectors.github.repos` | E | Already paged at 100 (0.93.9). |
| File read window | `SourceRead.WINDOW` | B (context) | Unchanged. Reads are by line range, not a silent clip. |
| Context window | model / `PromptFit` | F | Not raised blindly. Offline context stays the Settings knob. |
| Background execution | `ReplyService` foreground service | C | Unchanged. Android can still kill the process. Scribe's record is what resumes. |
| Credential handling | tokens in chat and logs | B | Scribe redacts `ghp_` and `sk-` before writing the record. |

Classes: A artificial, B security or resource safeguard, C Android, D device, E network or provider, F model architecture.

## What was not removed

- The sandbox (paths may not climb out of the workspace).
- Confirmation before shell, push, and pull request unless auto-accept is on.
- The stop button and cooperative cancel.
- Prompt fitting when the model says the input is too long.
- Android's refusal to keep an unrestricted background process.

## Run mode

Settings › Advanced › How long a job may run. 0 quick, 1 balanced, 2 deep reasoning, 3 extended project, 4 autonomous development.
