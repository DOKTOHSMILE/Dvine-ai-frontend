# PLAN — fix the GitHub-updated repo (agent tool-doc audit)

Goal: make what the agent is told about its tools match what the phone actually does,
then build/test.

- [x] Read the tool prompt in `app/src/main/java/ai/dvine/agent/CodeAgent.java` (system(), lines ~196–270)
- [x] Read the host side: `PhoneHost.java` (allowRun dialog, run → SandboxShell) and `SandboxShell.java`
- [x] Check every tool line against the code that runs it: list_files, read_file, write_file,
      edit_file, make_file, zip/unzip, search, run, fetch, git, connector
- [x] Fix the wording that is wrong (run: "one command at a time" → one command per tool call,
      chains allowed; verified cwd = workspace, 30 s timeout, 8000-char cut-off are already correct)
- [x] Check the host's refusal/timeout messages actually say what the prompt promises
      (allowRun = Run / Always run / Don't run, auto_accept pref, 3-min wait; SandboxShell
      reports "stopped after 30 s" and "…(output cut short)"; fetch refuses non-https)
- [x] Tests could not be executed here: no JVM in the workspace, and Termux refuses to start
      (`allow-external-apps` must be set to `true` in `~/.termux/termux.properties`). Run on a
      machine with JDK 17+: `./gradlew :app:testDebugUnitTest --tests 'ai.dvine.agent.CodeAgentTest'`
- [x] Commit + push to `ibiko/localmodel-fixes` (247d592); PR #114 opened against `main`
- [x] Report changed files
