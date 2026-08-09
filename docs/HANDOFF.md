# Handoff — next builder

_Last updated: 2026-08-09 · `main` @ merge of G1–G9 · repo: [RicheyWorks/ai-dungeon-master](https://github.com/RicheyWorks/ai-dungeon-master)_

This is the **single onboarding page** if you are picking up the project cold.
Read this first, then open the linked docs only as needed.

---

## 1. Where things stand (one screen)

| Area | State |
|---|---|
| **Git** | `main` is the only ship branch; **no open PRs** as of handoff. Pull `origin/main` before any work. |
| **Product** | Playable single-player AI dungeon engine: core + Spring API + SPA + Android/iOS shells + content packs. |
| **Default story** | Free pack **First Light** (`first-light-arc`): letter cold-open → **Noon in the Square** (3 endings). |
| **Goals G1–G9** | **All done** — see [GOALS.md](GOALS.md). Do not re-open unless regressions. |
| **Launch** | Freeze checklist ready: [LAUNCH_CHECKLIST.md](LAUNCH_CHECKLIST.md). Not a “shipped to prod” claim — **gates to run before you call it live**. |
| **Tests** | `mvn -pl core,service -am test` should be green on `main`. Web: `cd web && npm run build` + `./scripts/build-web.sh` stages `/app/`. |

**North star that still matters:** a new player’s first 10–20 minutes is a **retellable** story on local-stub, with no empty lobby and no mid-scene combat hijack during the campaign.

---

## 2. First 30 minutes (do this in order)

```bash
git clone https://github.com/RicheyWorks/ai-dungeon-master.git
cd ai-dungeon-master
git checkout main && git pull --ff-only

# Core + service tests (authoritative)
export PATH="/opt/maven/bin:$PATH"   # or your Maven
mvn -pl core,service -am test

# Package + run engine
mvn -pl service -am package -DskipTests
java -jar service/target/ai-dungeon-master-service-1.0-SNAPSHOT.jar
# → http://localhost:8080  (health, API)
# → http://localhost:8080/app/  after SPA stage

# Stage SPA into jar static (if /app looks stale)
./scripts/build-web.sh
# restart jar if needed
```

**Play path (manual, 15 min):**

1. Open `/app/` → **Start session / Sync**.  
2. Confirm campaign chip **First Light**, scene prose, **What to do** hint.  
3. Play letter choices (try **burn** once for **Ash-Handed** epithet).  
4. Finish dawn reckoning → confirm **Noon in the Square** auto-starts.  
5. Optional: combat path → **cinematic check** card + **Push Your Luck**.  
6. **Sound on** (gesture) → stings; **Share card PNG** / Export .md.  
7. Store sandbox buy `pack_the_hollows` if testing unlock.  

**Smoke (with jar up):**

```bash
BASE_URL=http://127.0.0.1:8080 ./scripts/launch-smoke.sh
BASE_URL=http://127.0.0.1:8080 ./scripts/storefront-smoke.sh
```

---

## 3. Repo map (where to edit)

| Path | Own this when… |
|---|---|
| `core/` | Rules, quests, campaign chain, combat checks, Chronicle, StoryMemory |
| `service/` | REST/STOMP, auth, multi-tenant jobs, admin, config, engine factory |
| `content-packs/first-light/` | Default story — **prefer JSON here over engine code** |
| `web/` | SPA (Vite React); stage with `./scripts/build-web.sh` |
| `clients/{typescript,kotlin,swift}` | Generated OpenAPI SDKs — regen only if contract changed |
| `android/` · `ios/` | Mobile shells; parity notes in `android/PARITY.md` |
| `docs/` | Architecture, goals, production, handoff (this file) |
| `docs/api/openapi.yaml` | **Contract source of truth** for `/v2` |
| `scripts/` | launch-smoke, storefront-smoke, build-web, prod verify |

Design map: [ARCHITECTURE.md](ARCHITECTURE.md) · multi-hour work: [GOALS.md](GOALS.md) · prod ops: [PRODUCTION.md](PRODUCTION.md).

---

## 4. Invariants (do not break)

1. **Story state in the engine; prose is decoration** (ADR-001). local-stub and LLMs must play the same game.  
2. **Per-session engines** when JWT auth is on — no shared mutable world between players.  
3. **STOMP:** subscribe to `/topic/narrative/{sessionId}` when authed (not global only).  
4. **Marketplace jobs** are session-owned; foreign job id → 404.  
5. **Campaign ambient combat is off** while a campaign is active (G9) — combat only via `TRIGGER_COMBAT` / combat state.  
6. **Opening script id** for First Light must stay `first-light-cold-open` so campaign grants/chain work.  
7. **Default campaign** `game.campaign.id=first-light-arc` (application.properties); factory also falls back if blank.  
8. Prefer **pack JSON** for story; engine only for systemic gaps.  
9. Regen **all** SDKs only when OpenAPI changed; core-only story PRs should not force full SDK churn.  
10. Production secrets never committed — see PRODUCTION.md + `scripts/gen-secrets.sh`.

---

## 5. What G1–G9 already shipped (do not redo)

| Goal | Outcome | PR (approx) |
|---|---|---|
| G1 Legendary first run | First Light cold open, stakes SPA | #108 |
| G2 Memory | Epithets, scars, recap on status/SPA | #109 |
| G3 Cinematic checks | d20 CheckResult, Push Your Luck, SPA card | #110 |
| G4 Flagship pack | Noon quest, 3 endings, boss/items/NPCs | #110 |
| G5 Sensory & share | Web Audio mute, share MD/PNG | #110 |
| G6 Mobile parity | Android me TTL, story/check UI, PARITY.md; iOS STOMP/me | #111 |
| G7 Storefront vertical | dev receipt → grant; `storefront-smoke.sh` | #111 |
| G8 Launch freeze | [LAUNCH_CHECKLIST.md](LAUNCH_CHECKLIST.md) | #111 |
| G9 Cool-path polish | Campaign chain, playHint, no ambient combat mid-arc | #112 |

Status log also in [GOALS.md](GOALS.md) §4.

---

## 6. Known gaps / honest debt

| Gap | Severity | Notes |
|---|---|---|
| **Human playtest backlog** | High for “cool” | Automated cool path is green; a real 30-min play will still find copy/feel bugs. Prefer fix-only PRs. |
| **Live IAP** | Medium | Dev storefront works; Play Billing / StoreKit full purchase not production-certified. |
| **iOS story/check UI** | Low–med | Android got G2/G3 chrome; iOS has session/save/jobs parity more than story cards. |
| **Deployed environment** | Medium | Checklist exists; no claim that a public staging is always up. |
| **local-stub quality** | Low | Deterministic; keyed LLMs optional. Narration facts include scene/quest/campaign. |
| **Chaos outside campaign** | By design | Ambient combat still rolls when **no** campaign is set. |
| **SDK lag** | Occasional | If status fields 404 in a client, check OpenAPI → regen that language only. |

---

## 7. Recommended next work (pick one)

Use `/goal` from [GOALS.md](GOALS.md). Suggested order:

1. **G10 Playtest fix-only** — run First Light yourself; fix confusion/bugs only; no new systems.  
2. **Staging deploy** — run LAUNCH_CHECKLIST against a real host; auth on; tokens rotated.  
3. **iOS story chrome** — partyTitle / recap / lastCheck parity with Android.  
4. **One new content beat** — pack JSON only (e.g. post-noon epilogue), not engine features.  
5. **Live storefront (XL)** — split into children; do not mix with story polish.

**Anti-patterns for the next builder:**

- Another multi-day “hardening only” spiral without player-visible wins  
- Regenerating all three SDKs for a quest JSON change  
- New admin endpoints while First Light still has feel bugs  
- Multiplayer / realtime co-op (out of product shape)

---

## 8. PR hygiene

```text
Branch: feat/<short-goal-slug> off main
Tests:  mvn -pl core,service -am test
UI:     cd web && npm run build && ./scripts/build-web.sh
Docs:   GOALS status + HANDOFF/ROADMAP if direction changed; OpenAPI if contract changed
PR:     one user-visible sentence in title; Done when from /goal in body
Merge:  squash or merge to main; delete branch
```

Labels historically used: `type/feature`, `area/backend`, `area/frontend`, `area/docs`, `area/android`, `status/ready`.

---

## 9. Config cheatsheet

| Knob | Default / note |
|---|---|
| `game.campaign.id` | `first-light-arc` |
| `game.narration.provider` | `local-stub` |
| `game.auth.enabled` | `false` in dev; **true** in prod |
| `game.production` | fails boot on weak secrets when true |
| `STOREFRONT_DEV_SECRET` | dev receipt HMAC; change outside local |
| Content packs root | scanned at boot; First Light free |

Details: [PRODUCTION.md](PRODUCTION.md), [STOREFRONTS.md](STOREFRONTS.md).

---

## 10. “Am I unblocked?” checklist

- [ ] `git pull` on `main` — clean working tree  
- [ ] `mvn -pl core,service -am test` green  
- [ ] Jar runs; `/health` 200; `/app/` shows SPA  
- [ ] First Light loads with campaign chip + playHint  
- [ ] Read [ARCHITECTURE.md](ARCHITECTURE.md) §1–3 and §9–10 if touching engine  
- [ ] Active work is a **single** `/goal` or an explicit fix-only PR  

If all boxes checked, you are caught up. Build something a player can feel.

---

## 11. Contact surface

- Issues / PRs: GitHub `RicheyWorks/ai-dungeon-master`  
- Architecture debates: ADR under `docs/adr/`  
- Goal protocol: paste `/goal …` in chat with the agent (see GOALS.md)

*End of handoff. Update this file when `main` moves in a way that would strand a new builder.*
