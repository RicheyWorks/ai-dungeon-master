# Multi-hour goals (`/goal`)

_Last updated: 2026-08-09 · Architecture map: [ARCHITECTURE.md](ARCHITECTURE.md)_

This file turns “what next?” into **sustained build sessions**. Prefer one active
goal over dozens of `next` micro-batches.

---

## 1. How to start a session

Paste this in chat (fill the fields):

```text
/goal <GOAL_ID or title>

North star: <one sentence player outcome>
Horizon: <2–6 hours of agent work>
In scope:
  - …
Out of scope:
  - …
Done when:
  - [ ] …
  - [ ] mvn -pl core,service -am test green
  - [ ] (if UI) web build + staged /app/ OR launch-smoke relevant checks
  - [ ] docs touched if contract/ops changed (OpenAPI / PRODUCTION / this file)
```

**Agent contract when `/goal` is active:**

1. Read [ARCHITECTURE.md](ARCHITECTURE.md) §9–10 and this goal’s slice.  
2. Work **only** in-scope items; park out-of-scope ideas in §5 Backlog.  
3. Ship **vertical slices** (design → code → tests → PR) every ~30–90 minutes, not one mega-PR at the end.  
4. After each slice: green tests; short status line; continue until **Done when** or horizon hit.  
5. Do **not** start unrelated hardening/polish unless it blocks the goal.  
6. End of session: update this file (goal status + notes) and ROADMAP if phases moved.

**Anti-patterns:**

- “next” with no goal → random polish  
- Security/admin thrash when the goal is player cool  
- Regenerating all SDKs for a core-only story change  
- New abstractions “for later”

---

## 2. Goal size guide

| Size | Horizon | Example |
|---|---|---|
| **S** | 1–2 h | One API + SPA wire + tests |
| **M** | 2–4 h | Chronicle epithets + status + SPA chrome + pack seed |
| **L** | 4–8 h | Flagship pack + cold open + recap + cinematic check UI |
| **XL** | multi-session | Full storefront IAP or Tauri ship — split into M/L children |

If a goal needs more than one day, split into **child goals** with their own Done when.

---

## 3. Recommended goal catalog (player-cool first)

Pick **one**. Copy into `/goal`. Status: `ready` · `active` · `done` · `parked`.

### G1 — Legendary first run  `done` · **L** · *highest cool ROI*


**North star:** A new player’s first 10 minutes is a story they’d retell.

| | |
|---|---|
| **In scope** | Default campaign cold open (mid-scene); first 5 beats with one irreversible choice; delayed callback 5–15 turns later; SPA first-run framing (no empty lobby vibe) |
| **Primary paths** | `content-packs/` (default/opening), `core` only if graph gaps, `web` Game tab |
| **Out of scope** | Admin, marketplace, new LLM providers, multiplayer |
| **Done when** | Offline stub playthrough of opening arc is compelling; tests cover branch + flag; SPA shows stakes/context; README or pack README mentions the hook |

---

### G2 — Memory you can feel  `done` · **M–L**

**North star:** Load game and *feel* continuity (epithets, scars, recap).

| | |
|---|---|
| **In scope** | Session recap (3 sentences from Chronicle); epithet/scar fields on party or world; surface on `/v2/status` + SPA header; optional shareable recap text |
| **Primary paths** | `core` Chronicle/WorldState, GameV2 status DTO, OpenAPI, SPA, one pack seed |
| **Out of scope** | Full journal UI redesign, multiplayer |
| **Done when** | Save → load shows recap + epithet; unit tests on chronicle compaction; SPA chrome shows title |

---

### G3 — Cinematic checks  `done` · **M**

**North star:** Rolls feel like drama, not a spreadsheet.

| | |
|---|---|
| **In scope** | Stakes line before resolve; unified result event (roll + effect + narration hook); SPA presentation; optional push-your-luck once/scene |
| **Primary paths** | `core` resolution, envelope/STOMP event, SPA action UI |
| **Out of scope** | Full combat redesign, 3D |
| **Done when** | At least one check type shows stakes→result in SPA; tests on effect application |

---

### G4 — Flagship content pack  `done` · **L**

**North star:** One pack is “the” reason to play (endings, art hooks, quests).

| | |
|---|---|
| **In scope** | Single pack polished end-to-end: quests, campaign, NPCs, loot, ≥2 endings; marketplace listing accuracy; SPA Mods/Store discoverability |
| **Primary paths** | `content-packs/<id>/` only (+ index if needed) |
| **Out of scope** | Engine features, billing |
| **Done when** | Stub playthrough has 2 endings; pack validates; README ships blurb |

---

### G5 — Sensory & share  `done` · **M**

**North star:** Vibes + share card make the run social.

| | |
|---|---|
| **In scope** | Muteable ambient beds by biome tag; SFX on crit/death/discover; chronicle → markdown or image “share card” in SPA |
| **Primary paths** | `web` assets + Game tab; optional status tags from core |
| **Out of scope** | Full audio engine, native mobile audio parity (follow-up) |
| **Done when** | Toggle mute works; share export downloads; no autoplay policy breakage |

---

### G6 — Mobile parity slice  `done` · **M**

**North star:** Android *or* iOS matches web on session resilience + save + jobs.

| | |
|---|---|
| **In scope** | `/session/me` TTL sync, near-expiry refresh, save meta, job list UX polish on **one** platform |
| **Primary paths** | `android/` *or* `ios/` + SDK already generated |
| **Out of scope** | Play Billing / StoreKit full IAP (separate goal) |
| **Done when** | Manual checklist in platform README; builds; no web-only APIs assumed |

---

### G7 — Storefront vertical  `done` · **L**

**North star:** One real money path (Steam **or** Play **or** App Store) grants a gated pack.

| | |
|---|---|
| **In scope** | One storefront SPI path + entitlement grant + SPA/mobile unlock UX |
| **Depends on** | Existing entitlement + receipt ledger |
| **Out of scope** | All storefronts at once |

---

### G8 — Production launch freeze  `done` · **S–M**

**North star:** Checklist-only: smoke, tokens, docs, no feature creep.

Use when shipping a known build — not for “make it cool.”

---

## 4. Active goal log

Record the current session here (newest on top).

| Date | Goal | Status | Notes / PRs |
|---|---|---|---|
| 2026-08-09 | G9 Cool-path polish | done | Campaign chain, playHint, SPA empty states |
| 2026-08-09 | G6+G7+G8 finish board | done | Android parity, storefront smoke, launch checklist |
| 2026-08-09 | G3+G4+G5 cool stack | done | Checks, flagship First Light 1.1, audio+share |
| 2026-08-09 | G2 Memory you can feel | done | StoryMemory recap/epithets/scars + SPA + save/load |
| 2026-08-09 | G1 Legendary first run | done | First Light pack, scene/stakes status, SPA framing |
| 2026-08-09 | _(catalog created)_ | — | Architecture + GOALS docs |


---

## 5. Parking lot (do not start mid-goal)

Ideas that showed up during sessions — claim into a catalog goal later.

- Multiplayer / co-op  
- Voxel / 3D world  
- More LLM providers without memory work  
- Drive-by admin ring / token polish when G1–G5 is active  
- Full Tauri release packaging  

---

## 6. Slice checklist (every PR inside a goal)

```text
[ ] Touches only files justified by the goal
[ ] Tests for new behavior (core unit and/or controller)
[ ] OpenAPI + SDK regen if /v2 contract changed
[ ] SPA or mobile only if player-facing goal needs it
[ ] PRODUCTION.md only if ops/security contract changed
[ ] PR description names the Goal ID (e.g. G1)
[ ] Goal log row updated when goal completes
```

---

## 7. Mapping “cool” advice → goals

| Cool theme | Goal |
|---|---|
| First 10 minutes unforgettable | **G1** |
| DM-like memory | **G2** |
| Cinematic combat/checks | **G3** |
| Legendary content | **G4** |
| Audio + shareability | **G5** |
| Native feel | **G6** |
| Monetization | **G7** |

Default recommendation when user says “make it cool” without a pick: **start G1**, then G2.

---

## 8. Example paste

```text
/goal G1 Legendary first run

North star: First 10 minutes is a retellable story.
Horizon: ~4 hours
In scope: cold open, 5 beats, one irreversible choice, delayed callback, SPA framing
Out of scope: admin, marketplace, new providers
Done when:
  - [ ] Default pack opening arc playable on local-stub
  - [ ] Branch + world flag tests
  - [ ] SPA shows scene stakes / context for opening
  - [ ] mvn test green + web build
```

### G9 — Cool-path polish  `done` · **M**

**North star:** First Light is fun end-to-end with zero confusion.

| | |
|---|---|
| **In scope** | Campaign auto-chain; SPA empty/error/playHint; narration framing; ambient combat vs story |
| **Primary paths** | core engine, GameEngineFactory, SPA Game tab, First Light campaign |
| **Out of scope** | New packs, storefronts, mobile IAP, admin |
| **Done when** | Letter → noon chain tested; playHint on status; no ambient combat mid-arc; tests green |

---

