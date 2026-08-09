# Architecture — AI Dungeon Master

_Last updated: 2026-08-09 · Companion: [GOALS.md](GOALS.md) (multi-hour `/goal` protocol) · [ADR-001](adr/ADR-001-story-depth.md) · [PRODUCTION.md](PRODUCTION.md)_

This document is the **stable design map**. Use it to orient long build sessions.
Use [GOALS.md](GOALS.md) to pick a multi-hour objective and stay on it.

---

## 1. Product shape

**Single-player, AI-narrated dungeon crawler as a portable engine:**

| Layer | Role |
|---|---|
| **core** | Pure Java game rules + story state (no Spring, no network) |
| **service** | Spring Boot HTTP `/v2/*` + STOMP, auth, multi-tenant isolation, ops |
| **content-packs** | Data-only story/content (YAML/JSON); hybrid authoring (ADR-001) |
| **clients** | Web SPA, Android, iOS shells on generated SDKs |

**Canonical rule (ADR-001):** *story state lives in the engine; prose is decoration.*  
Offline `local-stub` and keyed LLMs must play the **same** game; only writing quality differs.

---

## 2. System diagram

```text
┌──────────────────────────────────────────────────────────────────┐
│  Clients                                                         │
│  web/ (Vite React) · android/ · ios/ · desktop/                  │
│  clients/{typescript,kotlin,swift}  ← openapi-generator          │
└───────────────┬───────────────────────────┬──────────────────────┘
                │ REST /v2/*                │ STOMP /ws-stomp
                │ Bearer JWT                │ /topic/narrative/{sessionId}
                ▼                           ▼
┌──────────────────────────────────────────────────────────────────┐
│  service  (Spring Boot)                                          │
│  JwtAuthFilter · RateLimitFilter · RequestSizeFilter · CSP       │
│  SessionController · GameV2Controller · Catalog · Marketplace    │
│  AdminController · Health · Metrics · Entitlements               │
│  GameInstanceService ──per session──► DungeonMasterEngine        │
│  SessionPackService · MarketplaceJobStore · ReceiptLedger        │
└───────────────┬──────────────────────────────────────────────────┘
                │ in-process calls
                ▼
┌──────────────────────────────────────────────────────────────────┐
│  core                                                            │
│  DungeonMasterEngine · PartyState · Quest/Campaign graph         │
│  Chronicle · WorldState · NPC/Faction · Combat                   │
│  SPI registries (8) + ContentRegistry + pack loaders             │
│  LLMProvider (local-stub | openai | xai | anthropic | llama)     │
└──────────────────────────────────────────────────────────────────┘
                ▲
                │ load at boot / install
┌───────────────┴──────────────────────────────────────────────────┐
│  content-packs/*   pack.yaml + items/monsters/quests/campaigns   │
│  plugins/*.jar     signed + sandboxed ServiceLoader SPIs         │
└──────────────────────────────────────────────────────────────────┘
```

---

## 3. Module boundaries (do not blur)

| Module | May depend on | Must not |
|---|---|---|
| **core** | Jackson, JUnit only | Spring, servlet, Redis, HTTP clients at engine core (LLM transports are isolated) |
| **service** | core + Spring + stores | Business story rules that belong in core (keep controllers thin) |
| **web** | TypeScript SDK + thin `api.ts` mappers | Re-implementing game rules |
| **android / ios** | Kotlin / Swift SDK | Forked envelope shapes |
| **clients/** | Generated only | Hand-edits that regen will wipe — change OpenAPI first |
| **content-packs** | Data format only | Java code (use plugins for code mods) |

**Extension preference (in order):**

1. Content pack JSON/YAML  
2. Declarative choice effects / flags (ADR-001)  
3. SPI plugin (signed + sandboxed)  
4. core engine change (last resort; needs tests)

---

## 4. Request / session lifecycle

```text
POST /v2/session  →  SessionService.create  →  JWT (sub=sessionId)
        │
        ▼
Authorization: Bearer … on /v2/**
        │
        ├─ JwtAuthFilter → request attr SESSION
        ├─ RateLimitFilter (bucket by path)
        └─ controller → GameInstanceService.getOrCreate(sessionId)
                              │
                              ▼
                     DungeonMasterEngine (isolated)
                              │
              save/load  →  saves/{sessionId}.json
              narrate    →  LLMProvider → STOMP /topic/narrative/{sessionId}
```

**Isolation invariants (production):**

- One engine instance per authenticated session (`GameInstanceService`)
- Marketplace install jobs owned by `ownerSessionId` (fail-closed; foreign → 404)
- STOMP: session topic only when auth on; no dual-subscribe to shared `/topic/narrative`
- Admin / metrics tokens: dual rotation + `SecretEquals` (digest compare)
- Public health is lean; detail requires ops token

---

## 5. Story stack (core)

See [ADR-001](adr/ADR-001-story-depth.md) for full decision history. Runtime pieces:

| Concept | Responsibility |
|---|---|
| **Quest / Scene graph** | Branching choices via `nextSceneId` |
| **Campaign** | Quest DAG gated by world flags |
| **WorldState** | Flags + counters = narrative truth |
| **Chronicle** | Typed story events → compact prompt facts |
| **ChoiceEffect** | Declarative verbs (not hard-coded switches) |
| **NPC / Faction** | Disposition + reputation via same flag machinery |
| **NarrativePrompt** | Facts + fallback prose; LLM decorates |

**Cool-game work belongs here and in packs** — not in more admin endpoints.

---

## 6. API surface (stable contracts)

| Surface | Contract source |
|---|---|
| REST | [`docs/api/openapi.yaml`](api/openapi.yaml) |
| STOMP | [`docs/api/asyncapi.yaml`](api/asyncapi.yaml) |
| Envelope | `{ type, version, payload, requestId }` |
| Errors | `type: error` + message; never leak other tenants’ existence |

**Change protocol:** OpenAPI first → implement service → regenerate SDKs → wire SPA/mobile → smoke.

---

## 7. Client architecture

| Client | Path | Notes |
|---|---|---|
| Web SPA | `web/` → staged to `service/.../static/app/` | Primary UX; System tab = ops |
| Android | `android/` | Compose + Kotlin SDK |
| iOS | `ios/` | SwiftUI + Swift SDK |
| Desktop | `desktop/` | launch scripts + Tauri scaffold |

SPA patterns to preserve:

- `ensureSession` single-flight + near-expiry refresh + `/v2/session/me` TTL sync  
- STOMP reconnect + heartbeats  
- Busy bar, offline banner, `X-Request-Id`  

---

## 8. Ops & deploy

| Concern | Doc |
|---|---|
| Tokens, rate limits, purge, CSP | [PRODUCTION.md](PRODUCTION.md) |
| Redis/JDBC multi-node | [MULTI_NODE.md](MULTI_NODE.md) |
| Storefront receipts | [STOREFRONTS.md](STOREFRONTS.md) |
| Compose / LB | `deploy/` |

**Verify before merge:**

```bash
mvn -pl core,service -am test
./scripts/launch-smoke.sh          # when jar is up
cd web && npm run build && ../scripts/build-web.sh
```

---

## 9. Design principles (non-negotiable)

1. **Engine is source of truth** — LLM never owns story continuity alone.  
2. **Offline parity** — structural depth works with `local-stub`.  
3. **Session isolation** — no cross-tenant engines, jobs, saves, or STOMP.  
4. **Envelope + OpenAPI** — no ad-hoc JSON on `/v2`.  
5. **Thin controllers** — logic in core/services; DTO mapping only at edge.  
6. **Packs over forks** — new content prefers data packs.  
7. **Test what you assert** — story depth via state tests; security via ACL tests.  
8. **Ship in multi-hour goals** — see [GOALS.md](GOALS.md); avoid drive-by micro-refactors.

---

## 10. Where new work should land

| If the goal is… | Primary touch points |
|---|---|
| Cooler first session / drama | `core` quest/campaign + `content-packs/*` + SPA Game tab |
| Better memory / epithets | `Chronicle`, `WorldState`, status payload, SPA chrome |
| Cinematic checks | `core` resolution + SPA action UI + STOMP events |
| New world / pack | `content-packs/<id>/` only (then marketplace index) |
| Store / IAP | entitlements + storefront SPI + mobile shells |
| Ops / security | `service` filters, admin, PRODUCTION.md |
| New client surface | OpenAPI → SDK → one client; don’t triple-edit envelopes |

---

## 11. Related docs

| Doc | Use when |
|---|---|
| [GOALS.md](GOALS.md) | Starting a multi-hour `/goal` session |
| [ROADMAP_STATUS.md](ROADMAP_STATUS.md) | Phase checklist vs shipped code |
| [ADR-001](adr/ADR-001-story-depth.md) | Story system design detail |
| [PRODUCTION.md](PRODUCTION.md) | Launch / multi-tenant gates |
| [README.md](../README.md) | Quick start + API table |
