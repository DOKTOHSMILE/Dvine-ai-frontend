# Model connection in the Ibiko LLM frontend — a full-path analysis

Every path a prompt takes from the app to a model and back, with the failure handling, the
security, and the tests. Analysis only — no code was changed for this report.

## 1. Architecture

```
MainActivity.applyProvider()            (app/src/main/java/ai/ibiko/MainActivity.java:3667)
  └─ builds brains/fast models/windows from ProviderStore
  └─ model.useRemotes(brains, fasts, names, windows, online)   (MainActivity.java:3685)
       └─ LocalModel (llm/LocalModel.java)
            ├─ on-phone path: NativeLlm → llama.cpp (app/src/main/cpp/ibiko_llm.cpp)
            └─ remote path:  askRemote() → RemoteBrain.ask() → HttpURLConnection
                                 ▲
                                 │ per-provider settings: address, model, key, CA, session
                            ProviderStore (llm/ProviderStore.java) + Providers enum
```

Layers, bottom up:

- **Providers** (`llm/Providers.java:7`) — an enum of eight brains: `ON_PHONE`, `ANTHROPIC`,
  `OPENAI`, `GEMINI`, `DEEPSEEK`, `OPENROUTER`, `CUSTOM`, `IBIKO_SERVER`. Each carries a
  `RemoteBrain.Protocol`, a default address, a strong model, a fast model, a context window
  and a human "about" string. Everything except the enum name is user-overridable in
  `ProviderStore`.
- **ProviderStore** (`llm/ProviderStore.java`) — the phone-local settings: address, model,
  fast model, window, last test result, and the key. Keys are encrypted at rest (§5).
- **RemoteBrain** (`llm/RemoteBrain.java`) — one HTTP conversation: URL check, headers, body
  or form, streaming reader, error mapping, retries.
- **LocalModel** (`llm/LocalModel.java`) — the scheduler: it owns the request, the watchdog,
  the failover ladder and cancellation (epoch counter).
- **MainActivity** — the call site: which providers are active, which order, the cooldown
  (`restUntil`), the one-shot `switchNote`, and whether the network is up.

## 2. Request lifecycle

1. **Entry** — `MainActivity.applyProvider()` (`MainActivity.java:3667`) collects the chosen
   brains into parallel arrays and hands them to `model.useRemotes(...)` together with an
   `online` supplier: `() -> connectivity == null || connectivity.isOnline()`
   (`MainActivity.java:3685`).
2. **Connectivity** — `net/Connectivity.java:23-31` only reports *validated* internet
   (`NET_CAPABILITY_INTERNET` **and** `NET_CAPABILITY_VALIDATED`); `onLost` sets it false.
   `isOnline()` is volatile, read from the request thread.
3. **Selection** — up to 3 slots, fast-then-strong per slot: the fast model (if set) is asked
   first and the strong model only when the fast one can't carry the turn; each slot gets its
   own `restUntil` cooldown after a failure, and a failure switches the note once
   (`switchNote`, one-shot).
4. **URL** — `RemoteBrain` requires `https://` (except `http://localhost…` for a local
   gateway), `RemoteBrain.java:223-231`; `ProviderStore.address()`
   (`ProviderStore.java:140-146`) resolves `IBIKO_SERVER` to
   `serverRoot() + "/api/v1/llm"` where `serverRoot()` reads the app's existing `ibiko.server`
   pref or falls back to `R.string.api_base` (`ProviderStore.java:148-153`).
5. **Auth** — `RemoteBrain.java:425-433`: Anthropic sends `x-api-key`, everything else
   `Authorization: Bearer <key>`. `IBIKO_SERVER` sends no key: `ProviderStore.keySource()`
   (`ProviderStore.java:292-296`) swaps in `SessionClient.token()` from `serverSession`.
6. **Body** — JSON by default; `RemoteBrain.java:370-404` is the fallback ladder that falls
   back to form-encoding (and to a reduced payload) when a gateway rejects the JSON shape.
7. **Streaming** — SSE lines read from the socket; a reader that dies mid-stream ends the
   reply with what was already received rather than throwing the partial text away.
8. **Errors** — non-2xx bodies are parsed for a message (`error.message`, `message`, …), a
   401/403 is a key problem, 429 is rate-limit, 5xx is server — surfaced as `problem` strings.
9. **Failover** — `LocalModel` falls back phone-ward: remote fails → the on-phone model
   answers, and the reported reason must not be overwritten (`LocalModel.java`, both
   `replyNow` and `taskNow` sites; fixed in commit `6fb84ae`).

## 3. Negotiation / fallback ladder

Order actually attempted (first success wins):

1. **Fast remote model** of the highest-priority provider.
2. **Strong remote model** of the same provider (when the fast one is unset, flagged or failed).
3. **Next provider slot** (3 slots), each with its own cooldown so a dead provider isn't
   hammered while a healthy one keeps answering.
4. **Transport retries** inside one call: `RemoteBrain.java:279-324` — 1.5 s then 4 s, twice,
   and only *before the first byte* of the body (a partial stream is never replayed, to avoid
   duplicated tokens).
5. **Payload retries**: JSON → form-encoded → reduced payload (`RemoteBrain.java:370-404`).
6. **On-phone model** as the last resort, with the true reason preserved as `problem`.

Session (IBIKO_SERVER only): `net/SessionClient.java:37-44` keeps the token until less than
5 minutes remain, `:67` parses `expiresAt` from the sign-in body, `:74-77` `invalidate()`
zeroes it so the next `token()` signs in again — the token is fetched at each request
(`ProviderStore.java:228`), never cached across expiry.

## 4. Offline path

`NativeLlm` (`llm/NativeLlm.java`) loads `libibiko_llm` on first use and, if that fails (no
64-bit ARM, or a build without the native part), records *why* (`available()`/`why()`) and the
personas carry on with the offline brain. `Connectivity.isOnline() == false` short-circuits
the remote ladder at the call sites (`MainActivity.java:1400, 2873, 4035`) and the on-phone
model answers. `LocalModel`'s watchdog bounds the whole thing: 60 s idle / 180 s writing /
600 s reading, and a timeout reason now survives `cancel()` (commit `6fb84ae`).

## 5. Security

| Area | Where | Verdict |
|---|---|---|
| Keys at rest | `ProviderStore.java:370-381` | AES-256-GCM key in `AndroidKeyStore` (`KEY_ALIAS`), `AES/GCM/NoPadding`, 128-bit IV, Base64 without wrap (`:390, :405-406`). Correct. |
| Transport | `RemoteBrain.java:223-231` | `https://` enforced (localhost excepted). No cleartext key paths. |
| Custom CA | `ProviderStore.java:309-351` | `sockets(pem)` adds a pasted CA to the phone's own trust store; both managers are still consulted and the full chain check runs (`:336`). Not a trust-all. |
| Session | `net/SessionClient.java` | Token renewed 5 min before expiry, `invalidate()` on a server refusal; fetched per request, so a rotated server token is picked up immediately. |
| Key in flight | `RemoteBrain.java:425-433` | `Bearer` / `x-api-key` headers only — keys never appear in the URL. |
| Storage | `ProviderStore.java:28` | `SharedPreferences("ibiko_providers", MODE_PRIVATE)` — app-private; values are the ciphertext from §1, not plaintext keys. |

## 6. Weaknesses (with file:line)

1. **`MainActivity.java:3685`** — the online supplier is
   `connectivity == null || connectivity.isOnline()`; a null `Connectivity` (constructed
   before `start()`, or a failed system service) reads as *online*, so the ladder attempts
   remote calls that will all fail before falling back. Safer inverted: unknown ≠ online.
2. **`ProviderStore.java:142-145`** — `address()` for `IBIKO_SERVER` falls back to
   `serverRoot()`, which reads a pref or a *build-time* string (`R.string.api_base`). If the
   pref is cleared the app silently targets whatever host the build shipped with; there is no
   validation that `serverRoot()` is a real host at all (empty ⇒ `"/api/v1/llm"`, which then
   fails late, inside `RemoteBrain`'s URL check, as an opaque problem).
3. **`RemoteBrain.java:279-324`** — retries on 1.5 s / 4 s are fixed, not backed off, and are
   gated only on "no first byte yet". A 429 with `Retry-After` is retried on the same schedule
   rather than honoring the header.
4. **`SessionClient.java:39`** — the 5-minute renewal margin uses `System.currentTimeMillis()`
   (wall clock): moving the phone's clock past `expiresAt` while a token is cached yields a
   stale token until `invalidate()`; a clock jumped backwards extends it. `elapsedRealtime()`
   would be immune. Low impact because `invalidate()` recovers on the first 401.
5. **`ProviderStore.java:324-346`** — with a pasted CA, `both` accepts a chain valid under
   *either* store. Correct, but a private CA plus a compromised public CA would pass; worth
   documenting for users who paste a corporate CA.
6. **`Connectivity.java:47-52`** — `start()` seeds `online` from the active network only
   once; if `start()` runs before the network is validated (cold boot), the badge stays false
   until the next `onCapabilitiesChanged`, i.e. there is a window where requests are skipped
   though the network is fine.
7. **No end-to-end test of a live conversation** — the tests assert request shape and
   fallback decisions, never a real stream round-trip (understandable: no network in CI).

## 7. Tests

`app/tests.gradle` wires the JVM test task; 27 test classes under
`app/src/test/java/ai/ibiko/llm/`:

- `RemoteBrainTest` — URL/header/body construction, error mapping.
- `ProviderOrderTest` — the fast-then-strong ordering, provider priority and cooldowns.
- `LocalModelTest`, `ReplyLeaseTest`, `ReplyWatchTest`, `ThinkPaceTest` — the scheduler,
  leases, the watchdog, pacing.
- `OfflineBridgeBenchTest`, `OfflineBridgeTest`, `OfflineInterpreterTest`, `OfflineIbikoTest`,
  `OfflineCouncilTest`, `OfflineFactCheckTest` — the on-phone ladder and its fallbacks.
- `DownloadStatusTest`, `DownloadNotificationTest`, `LocalModelTest` — model download.
- `PromptFitTest`, `UsageLogTest`, `ProviderOrderTest` — context budgeting and accounting.

**How this report was verified:** this shell has no `javac`/`gradle`, so every claim above was
verified by *reading* the referenced lines, not by running the suite. Run locally with
`./gradlew test` (see `app/tests.gradle`).

## 8. Sources

- `app/src/main/java/ai/ibiko/MainActivity.java:3667-3685` (call site, online supplier)
- `app/src/main/java/ai/ibiko/llm/ProviderStore.java:140-153, 228-296, 309-406`
- `app/src/main/java/ai/ibiko/llm/RemoteBrain.java:223-231, 279-324, 370-433`
- `app/src/main/java/ai/ibiko/llm/Providers.java:7-33`
- `app/src/main/java/ai/ibiko/llm/NativeLlm.java:19-45`
- `app/src/main/java/ai/ibiko/net/Connectivity.java:23-56`
- `app/src/main/java/ai/ibiko/net/SessionClient.java:37-77`
- `PLAN.md` (audit record, commits `6fb84ae` / PR #91)
</result>
