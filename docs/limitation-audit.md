# Limitation audit (0.94.0)

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
