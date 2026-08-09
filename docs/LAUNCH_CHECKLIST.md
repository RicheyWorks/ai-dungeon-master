# Production launch freeze checklist (Goal G8)

> Next builder onboarding: [HANDOFF.md](HANDOFF.md)

Use this **only** when shipping a known build — no feature work mid-freeze.

_Last updated: 2026-08-09 · Goals G1–G7 complete on `main`_

## 1. Code gates

| Gate | Command / check | Pass? |
|---|---|---|
| Unit + controller tests | `mvn -pl core,service -am test` | ☐ |
| Web production build | `cd web && npm run build` | ☐ |
| Stage SPA into jar static | `./scripts/build-web.sh` | ☐ |
| Typecheck (web) | `cd web && npx tsc --noEmit` | ☐ |

## 2. Runtime smoke (jar up on :8080)

| Gate | Command | Pass? |
|---|---|---|
| Launch smoke | `BASE_URL=http://127.0.0.1:8080 ./scripts/launch-smoke.sh` | ☐ |
| Storefront vertical | `./scripts/storefront-smoke.sh` | ☐ |
| Public health lean | `curl -sf localhost:8080/health` | ☐ |
| Metrics scrape token | see [PRODUCTION.md](PRODUCTION.md) | ☐ |

## 3. Security / multi-tenant

| Gate | Notes | Pass? |
|---|---|---|
| Auth on | `game.auth.enabled=true` in prod | ☐ |
| Admin dual tokens | `GAME_ADMIN_TOKEN` + `PREVIOUS` rotation | ☐ |
| Metrics scrape token | not public without token | ☐ |
| Catalog upload admin-gated | `game.catalog.upload.require-admin=true` | ☐ |
| STOMP ACL | session topic only when authed | ☐ |
| Marketplace job ownership | foreign job → 404 | ☐ |
| No secrets in git | scan `.env`, properties | ☐ |

## 4. Product cool path (manual 10 min)

| Gate | Pass? |
|---|---|
| First Light cold open loads | ☐ |
| Irreversible letter choice + epithet | ☐ |
| Cinematic check UI on Attack | ☐ |
| Share card / recap export (web) | ☐ |
| Dev storefront unlocks `pack_the_hollows` | ☐ |

## 5. Docs / tags

| Gate | Pass? |
|---|---|
| README version blurb current | ☐ |
| [PRODUCTION.md](PRODUCTION.md) matches tokens | ☐ |
| [GOALS.md](GOALS.md) status accurate | ☐ |
| GitHub release notes drafted | ☐ |

## Freeze rule

During freeze: **bugfixes only**. Park new goals in GOALS.md parking lot.
After ship: un-freeze and open G-next (or mobile Play Billing).

## Sign-off

| Role | Name | Date |
|---|---|---|
| Eng | | |
| Ops | | |
