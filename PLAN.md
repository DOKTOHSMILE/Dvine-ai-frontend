## Mini agents on every build

- [x] `Tuning.WORKERS` (`adv_workers`, 1–8, default 4) in Settings › Advanced.
- [x] `MainActivity` passes that count to `Crew.run` on the phone and on an API. More than one worker uses the non-streaming brain so helpers don't share one live buffer. The phone model still answers one step at a time (`taskNow` is synchronized).
- [x] `Crew.plan` / `planPrompt` let 8 workers get 8 parts, not the old cap of 5. Pool size stays `min(workers, parts)`.
- [x] `CrewTest`: 5 tests, OK (including `eightWorkersGetEightPartsNotTheOldCapOfFive`).
- [ ] Full `javac` of `app/src/main/java` against AndroidX jars: not run here (no Android SDK in this workspace). `play(List<TurnLine>, Runnable)` is present on `MainActivity` in this tree; the Termux log's missing `PlayStep` wiring is not this checkout.