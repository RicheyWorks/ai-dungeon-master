# AI Dungeon Master

Single-player, AI-narrated dungeon crawler as a **portable game engine**: pure-Java
core, Spring Boot HTTP + STOMP API, offline-capable LLM narration, data-driven
content packs, and typed clients for web, Android, and iOS.

| | |
|---|---|
| **Stack** | Java 17 · Spring Boot 3.2 · Maven multi-module |
| **API** | Versioned REST `/v2/*` + STOMP WebSocket · OpenAPI / AsyncAPI |
| **Clients** | Web SPA (`/app/`) · Android (Compose) · iOS (SwiftUI) · generated TS / Kotlin / Swift SDKs |
| **Tests** | `mvn clean test` · `scripts/launch-smoke.sh` (health + play + STOMP + metrics) |
| **Status** | Phase 0–2 + story depth + production multi-tenant gates on `main` · clients ◐ |

---

## Table of contents

1. [Highlights](#highlights)
2. [Quick start](#quick-start)
3. [Architecture](#architecture)
4. [API surface](#api-surface)
5. [SPA & clients](#spa--clients)
6. [AI narration](#ai-narration)
7. [Content packs & plugins](#content-packs--plugins)
8. [Project layout](#project-layout)
9. [Documentation](#documentation)
10. [Configuration](#configuration-common)
11. [Deploy](#deploy)
12. [Roadmap](#roadmap)
13. [Contributing](#contributing)
14. [License](#license)

---

## Highlights

- **Typed API + SDKs** — every `/v2` response is an envelope
  `{ type, version, payload, requestId }`. OpenAPI → TypeScript, Kotlin, Swift.
- **Narration you can run offline** — `local-stub` by default; optional OpenAI /
  Anthropic / xAI / local-llama when keys are present.
- **Plugin SPIs** — eight `ServiceLoader` SPIs; code mods are signature-checked
  and bytecode-sandboxed before load.
- **Content packs** — four themed packs ship; marketplace browse + async install
  with **session-owned job list**; runtime enable/disable and zip upload
  (admin-gated in multi-tenant prod).
- **Story depth (ADR-001)** — branching quest graphs, campaigns, narrative memory,
  NPC/faction dispositions — pack JSON, offline-capable.
- **Session resilience** — JWT mint / refresh / rename, SPA auto-renew near expiry,
  offline banner, STOMP auto-reconnect, per-request `X-Request-Id`.
- **Save lifecycle** — session-scoped save/load/reset plus **GET/DELETE save meta**.
- **Production hardening** — JWT isolation, rate limits, STOMP subscription ACL,
  marketplace job ownership, dual admin + metrics token rotation, lean public health,
  Prometheus scrape token. See [`docs/PRODUCTION.md`](docs/PRODUCTION.md).

---

## Quick start

```bash
# Build & test
mvn clean test
mvn package

# Run headless engine (REST + WebSocket on :8080)
java -jar service/target/ai-dungeon-master-service-1.0-SNAPSHOT.jar

# Optional modes
java -jar service/target/ai-dungeon-master-service-1.0-SNAPSHOT.jar \
  --game.gui.enabled=true    # Swing desktop
  # --game.cli.enabled=true  # terminal CLI
```

Smoke the API:

```bash
curl -s localhost:8080/health          # liveness (LB-safe, lean)
curl -s localhost:8080/health/ready    # readiness
curl -s localhost:8080/v2/status       # game_status envelope (session may be required)
curl -s localhost:8080/v2/marketplace  # pack discovery
curl -s localhost:8080/v2/catalog      # installed packs + plugins
```

**Play in the browser:** stage the SPA and open `/app/`:

```bash
./scripts/build-web.sh
# with engine running → http://localhost:8080/app/
```

Or hot-reload the web client against a local engine:

```bash
# terminal 1 — engine
mvn -pl service -am spring-boot:run

# terminal 2 — Vite (proxies /v2 + /ws-stomp → :8080)
cd web && npm install && npm run dev
# → http://localhost:5173
```

End-to-end gate (HTTP play path + STOMP ACL + metrics):

```bash
BASE_URL=http://127.0.0.1:8080 ./scripts/launch-smoke.sh
```

---

## Architecture

```
┌─────────────┐     REST / STOMP      ┌──────────────────┐
│  Web / mobile│ ◄──────────────────► │  service (Boot)  │
│  + SDKs      │                      │  JWT · rate limit │
└─────────────┘                      │  marketplace jobs │
                                     └────────┬─────────┘
                                              │
                                     ┌────────▼─────────┐
                                     │  core (pure Java) │
                                     │  engine · packs   │
                                     │  plugins · LLM    │
                                     └──────────────────┘
```

| Module | Role |
|---|---|
| **`core`** | Engine + plugin SPI. No Spring. Domain model, generation, `LLMProvider`, content registries. |
| **`service`** | Spring adapter: REST `/v2/*`, STOMP, optional Swing/CLI, production gates, static `/app` SPA. |

---

## API surface

Full contract: [`docs/api/openapi.yaml`](docs/api/openapi.yaml) ·
[`docs/api/asyncapi.yaml`](docs/api/asyncapi.yaml).

### REST (selected)

| Method & path | Description |
|---|---|
| `POST /v2/session` | Guest session → JWT + session id |
| `POST /v2/session/refresh` | Re-issue JWT (same session id) |
| `PATCH /v2/session` | Rename display name + re-issue JWT |
| `GET /v2/session/me` | Echo caller session |
| `DELETE /v2/session` | Logout (drop identity + engine) |
| `GET /v2/status` | Party, quest, choices, location, events |
| `POST /v2/action` | Apply a choice |
| `POST /v2/narrate` | DM narration (active LLM) |
| `POST /v2/save` · `/load` · `/reset` | Session-scoped save lifecycle |
| `GET /v2/save` · `DELETE /v2/save` | Save presence / clear slot |
| `GET /v2/marketplace` | Discover local + remote packs |
| `POST /v2/marketplace/{id}/install` | Install (`?async=true` → job + poll) |
| `POST /v2/marketplace/{id}/install-async` | Typed async install (always 202) |
| `GET /v2/marketplace/jobs` | List install jobs for this session |
| `GET/DELETE /v2/marketplace/jobs/{jobId}` | Progress / cancel (**owner session only**) |
| `GET /v2/catalog` | Installed packs + plugins |
| `POST /v2/catalog/packs` | Upload zip (admin-gated when required) |
| `POST /v2/catalog/packs/{id}/enable` · `/disable` | Toggle pack |
| `POST /v2/entitlements/verify` · `GET /v2/entitlements` | Store receipts |
| `GET /v2/admin/*` | Ops: sessions, receipts, packs, purge, security events (admin token) |
| `GET /health` · `/health/ready` · `/v2/health` | Probes (detail token-gated in prod) |
| `GET /metrics` | Prometheus (scrape token in prod; dual rotation supported) |

Envelope shape:

```json
{ "type": "game_status", "version": 1, "payload": { "...": "..." }, "requestId": "..." }
```

Send `X-Request-Id` to correlate request/response (clients generate one per call).

### STOMP WebSocket

- Connect: `ws://localhost:8080/ws-stomp` (SockJS also available)
- CONNECT with `Authorization: Bearer <jwt>`
- Subscribe: `/topic/narrative/{sessionId}` (foreign session ids are denied)
- Send: `/app/action`, `/app/narrate`
- SPA client auto-reconnects with exponential backoff; STOMP heartbeats every 10s
  (server: `game.ws.heartbeat.server-ms` / `client-ms`)


---

## SPA & clients

Generated SDKs live under [`clients/`](clients/) — regenerate from OpenAPI
(see [`clients/README.md`](clients/README.md)); do not hand-edit.

| Path | Platform | Notes |
|---|---|---|
| [`web/`](web/) | Vite + React (TypeScript SDK) | Also staged at engine `/app/` |
| [`android/`](android/) | Jetpack Compose (Kotlin SDK) | Session + STOMP + marketplace + save meta/jobs + store |
| [`ios/`](ios/) | SwiftUI (Swift SDK) | Parity with Android shell (save meta, jobs list) |
| [`desktop/`](desktop/) | Launcher + Tauri scaffold | `./desktop/launch.sh` |

**Web SPA (`/app/`)** includes Game / Mods / Store / System tabs, keyboard shortcuts,
drag-drop pack upload, live narrate stream, session TTL + renew/rename, save meta,
marketplace job history, and admin ops when tokens are set.

---

## AI narration

| Provider id | Notes |
|---|---|
| `local-stub` | Default · deterministic · no network |
| `openai` · `anthropic` · `xai` · `llama` | Keyed backends; missing key → DOWN, fall back to stub |

```properties
game.narration.provider=local-stub
game.narration.token.ceiling=4000
# OPENAI_API_KEY / ANTHROPIC_API_KEY / XAI_API_KEY / LLAMA_BASE_URL
```

Decorators: token budget + moderation. Streaming providers push
`narrative_chunk` / `narrative_update` over STOMP.

---

## Content packs & plugins

- Bundled + scanned packs under `content-packs/` (`game.content.packs.dir`)
- Ships: `black-hollows`, `dnd-classic`, `sci-fi`, `cozy-hearthwood`
- Story data: `quests/`, `campaigns/`, `npcs/`, `factions/` (see [ADR-001](docs/adr/ADR-001-story-depth.md))
- Code plugins: JARs + `plugin.yaml`, signature policy
  (`LENIENT` / `REQUIRED` / `DISABLED`) + bytecode sandbox
- Marketplace: remote index, checksums, HMAC, async install with job ownership + list
- Static mod browser: `/mod-browser.html` · full SPA: `/app/`

---

## Project layout

```
core/            pure-Java engine + SPI
service/         Spring Boot REST, STOMP, production gates, static SPA
web/             Vite + React client (TypeScript SDK)
android/ · ios/  native shells
clients/         generated openapi-generator SDKs
content-packs/   themed data packs
docs/            production, multi-node, storefronts, roadmap, ADRs, OpenAPI
deploy/          docker compose (app ×2 + Postgres + nginx + metrics)
scripts/         build-web, launch-smoke, marketplace helpers
desktop/         launcher scripts + Tauri scaffold
```

---

## Documentation

| Doc | Purpose |
|---|---|
| [`docs/PRODUCTION.md`](docs/PRODUCTION.md) | Multi-tenant ops, tokens, rate limits, smoke gates |
| [`docs/MULTI_NODE.md`](docs/MULTI_NODE.md) | Redis/JDBC session stores, sticky LB |
| [`docs/STOREFRONTS.md`](docs/STOREFRONTS.md) | Receipt plugins & storefronts |
| [`docs/ROADMAP_STATUS.md`](docs/ROADMAP_STATUS.md) | Phase checklist vs code |
| [`docs/adr/`](docs/adr/) | Architecture decision records |
| [`docs/api/openapi.yaml`](docs/api/openapi.yaml) | REST contract |
| [`docs/api/asyncapi.yaml`](docs/api/asyncapi.yaml) | STOMP / WebSocket contract |
| [`clients/README.md`](clients/README.md) | SDK regeneration |

---

## Configuration (common)

| Property | Default | Purpose |
|---|---|---|
| `server.port` | `8080` | HTTP / WebSocket |
| `game.difficulty` / `game.chaos` | `4` / `4` | Encounter tuning |
| `game.campaign.id` | _(none)_ | Pack campaign, e.g. `the-hollows-cycle` |
| `game.narration.provider` | `local-stub` | Active LLM |
| `game.auth.enabled` | `false` | Enforce JWT on `/v2/**` |
| `game.auth.jwt.secret` | _(dev only)_ | HMAC secret — set in prod |
| `game.auth.session.store` | `memory` | `memory` · `file` · `redis` · `jdbc` |
| `game.admin.token` / `.previous` | empty | Ops dual-token rotation |
| `game.metrics.scrape-token` / `.previous` | empty | Prometheus dual-token rotation |
| `game.plugins.signature.policy` | `LENIENT` | Prod: `REQUIRED` |
| `game.plugins.sandbox.enabled` | `true` | Bytecode sandbox |
| `game.saves.dir` | `saves` | Per-session saves |
| `game.instances.max` | `100` | Concurrent session engines |

Full production matrix: [`docs/PRODUCTION.md`](docs/PRODUCTION.md). Multi-node stores:
[`docs/MULTI_NODE.md`](docs/MULTI_NODE.md).

---

## Deploy

```bash
# Multi-node local stack (2 app nodes + Postgres + sticky nginx)
docker compose -f deploy/docker-compose.yml up --build
# → http://localhost:8080/app/

# Prometheus overlay
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.metrics.yml up --build
```

Desktop helper: `./desktop/launch.sh` (Windows: `.\desktop\launch.ps1`).

Launch gate: `scripts/launch-smoke.sh` (health + play + STOMP ACL + metrics auth).

---

## Roadmap

See [`docs/ROADMAP_STATUS.md`](docs/ROADMAP_STATUS.md).

| Area | State |
|---|---|
| Core engine + plugins + v2 API + LLM | ✅ |
| Story depth (ADR-001) | ✅ |
| Production multi-tenant gates | ✅ |
| Web SPA | ✅ (ongoing polish) |
| Android / iOS | ◐ shells on generated SDKs |
| Steam / Tauri packaging | ◐ scaffold |
| Play Billing / App Store live IAP | open |

---

## Contributing

1. Branch from `main` (`feat/…`, `fix/…`, `docs/…`, `chore/…`).
2. Keep changes focused; regenerate SDKs when OpenAPI changes.
3. Run `mvn -pl service -am test` and `cd web && npm run build` before PR.
4. Stage SPA with `./scripts/build-web.sh` when shipping web changes.
5. Prefer product-facing summaries in PRs; link `docs/PRODUCTION.md` for ops.

Labels (areas): `area/backend`, `area/frontend`, `area/android`, `area/ios`,
`area/docs`, `area/security`, `area/ops` · types: `type/feature`, `type/fix`,
`type/chore`, `type/docs`.

---

## License

Not yet specified — treat as private/proprietary until a `LICENSE` file lands.
