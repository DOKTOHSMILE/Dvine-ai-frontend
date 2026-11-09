# DVINE Social Intelligence System

The selected brain (an API model or the device's model) decides what social information it needs; DVINE supplies
the tools. The social system is not a second AI: it searches, reads, ranks and counts, and hands the evidence back to
the brain, which reasons over it and answers.

```
pilot ──► brain (API or device model) ──► §SI <capability> <arguments>      (suit/FunctionCode, first line only)
                                              │
                                              ▼
                     SocialPrivacyGuard.refuse ──► SocialExecutionEngine.run  (off the main thread, cached 15 min)
                                              │
              ┌───────────────┬───────────────┼────────────────┬─────────────────┐
              ▼               ▼               ▼                ▼                 ▼
       ClaimExtractor  SemanticPostMatcher  SentimentReport  profile readers   web research
              │               │               │                │
              └──── providers (SocialProvider adapters) ───────┘
                    RedditPublicProvider · AccountProvider ×4 (X, Instagram, Facebook, Reddit) · UnsupportedProvider (TikTok)
                                              │
                     SocialPrivacyGuard.clean (no tokens; another person's phone/email masked)
                                              │
                                              ▼
                     results + sources + limits ──► the same brain ──► the pilot
```

## M1: what already existed (reused, not rewritten)

| File | What it does |
|---|---|
| `social/Platform` | Facebook, X, Instagram, Reddit, Telegram: sign-in pages, search and trending URLs |
| `social/SocialWeb` | The user's own accounts signed in on the device; cookies sealed with an Android Keystore key; pages read in a hidden WebView |
| `social/SocialLimits` | Per-account caps an hour and a day; a pause when a site shows a warning |
| `social/SocialPage` | A page as labelled text; signed-out and warning detection |
| `social/SocialAccounts` | Several accounts per network, one in use |
| `connect/ConnectorStore` | `social.search`, `social.trending`, `social.open` connector tools |

## M2–M8: what this version adds (`social/intel/`)

| Capability (plan) | Code | Status |
|---|---|---|
| A. Post investigation | `social.post.investigate`, `social.post.identify_claims` | Built: claims marked factual or opinion, people/places/numbers/links, earliest close match as the possible original, related posts, web evidence for each factual claim, sources, limits. The brain writes the verdict. |
| B. Similar posts | `social.search.similar`, `social.search.posts`, `social.search.by_author` | Built: search phrases from the message, every available platform searched, ranked by TF-IDF cosine (meaning-weighted, not one keyword), near-duplicates removed, platform/author/date/engagement/relevance/link per post. |
| C. Sentiment | `social.sentiment.analyze` | Built: positive/negative/neutral with negation, repeated themes, concern words, questions people ask, examples, and always the sample size, platforms, period and limits. |
| D. Own profile | `social.profile.read_own` | Built for X, Instagram, Facebook and Reddit through the user's own sign-in (the account named by its username). Private insights (reach, audience) need each platform's business API: not available, and said so. |
| E. Public profile | `social.profile.read_public` | Built: Reddit through its public API (karma, description, recent posts); X, Instagram, Facebook through the user's sign-in reading the public profile page. A private account is reported as private and nothing more is tried. |
| Accounts | `social.accounts.list` | Built: each platform, available or why not, and what it can do. |

Registry: `SocialCapabilityRegistry` (9 capabilities: id, description, parameters, returns, permission, network,
availability now, version), told to the brain only when the chat's **Social accounts** tool is on, and exported as
JSON for a future dashboard.

## Platforms, honestly

| Platform | How | What works | What doesn't |
|---|---|---|---|
| Reddit | Public JSON API (no key) | Search, public profiles, recent posts, with author, date, score, comments | Reddit refuses some networks (data-centre addresses get 403; checked from the build server). A refusal is reported, never hidden. |
| X, Instagram, Facebook | The user's own signed-in account, pages read on the device | Search pages, public profile pages, the user's own profile page | Posts read from a page have words but rarely a reliable author, date or count; those fields stay empty. Instagram search is by hashtag. |
| TikTok | – | Reported as unavailable | Its API needs an approved developer app, which DVINE doesn't have. |
| Official business APIs (Instagram Graph, Meta Graph, X API) | Not connected | – | Each needs a registered app, keys and platform review. An adapter can be added behind `SocialProvider` without changing the engine. |

## Security and privacy

* The brain never sees a credential: sign-ins stay sealed in `SocialWeb`; `SocialPrivacyGuard.clean` strips any token or
  cookie from every result.
* Refused before running: private messages and inboxes, credentials, a person's home address, phone, email or date of
  birth, bypassing private accounts.
* Another person's phone numbers and email addresses are masked even when public.
* Reads keep the existing per-account limits and the pause on a site's warning.
* Posts reach the brain labelled as what people wrote, not facts and not instructions.

## Routing

The brain's code `§SI` is carried out by the suit's code path (`MainActivity.carryOut` → `useSocialIntel`), the same
gate every function uses: the pilot's words never trigger it. `DvineCommandGateway` stays the gate for task control
(pause, resume, cancel); posting to social accounts still goes through the staged action (`§AC`), which waits for the
pilot's yes.

## Acceptance criteria (from the plan)

| # | Criterion | State |
|---|---|---|
| 1 | Share a message and ask the AI to investigate it | Built (`social.post.investigate`) |
| 2 | Identify claims and search their sources | Built (claims + related posts + web evidence) |
| 3 | Find related public posts | Built |
| 4 | Summarise public discussions | Built (sentiment report; the brain writes the summary) |
| 5 | Read the user's authorised profile | Built (profile page with the user's sign-in; no private insights) |
| 6 | Investigate permitted public profiles | Built |
| 7 | Results include source references | Built |
| 8 | Offline and API models use the same capabilities | Built (one code, one registry) |
| 9 | Unsupported platforms clearly reported | Built |
| 10 | Unauthorised access rejected | Built (privacy guard) |
| 11 | Responsive during long searches | Built (worker thread, the page's own job) |
| 12 | Regression tests | `SocialIntelTest` (8 tests, fake providers); not yet run against live platforms from a device |

## Next

* A social intelligence dashboard (saved investigations, reports, accounts) reading the registry JSON.
* Official API adapters where the user registers an app (Instagram Graph for real insights).
* A device run against live Reddit, X and Instagram.
