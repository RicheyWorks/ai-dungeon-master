# AI Dungeon Master

Single-player, AI-narrated dungeon crawler as a **portable game engine**: pure-Java
core, Spring Boot HTTP + STOMP API, offline-capable LLM narration, data-driven
content packs, and typed clients for web, Android, and iOS.

| | |
|---|---|
| **Stack** | Java 17 · Spring Boot 3.2 · Maven multi-module |
| **API** | Versioned REST `/v2/*` + STOMP WebSocket · OpenAPI / AsyncAPI |
| **Clients** | Web SPA · Android (Compose) · iOS (SwiftUI) · generated TS / Kotlin / Swift SDKs |
| **Tests** | `mvn clean test` (core + service suites) |

---

## Highlights

- **Typed API + SDKs** — every `/v2` response is an envelope
  `{ type, version, payload, requestId }`. OpenAPI → TypeScript, Kotlin, Swift.
- **Narration you can run offline** — `local-stub` by default; optional OpenAI /
  Anthropic / xAI / local-llama when keys are present.
- **Plugin SPIs** — eight `ServiceLoader` SPIs; code mods are signature-checked
  and bytecode-sandboxed before load.
- **Content packs** — four themed packs ship; marketplace browse + async install;
  runtime enable/disable and zip upload (admin-gated in multi-tenant prod).
- **Story depth (ADR-001)** — branching quest graphs, campaigns, narrative memory,
  NPC/faction dispositions — pack JSON, offline-capable.
- **Production hardening** — JWT sessions, rate limits, STOMP subscription ACL,
  marketplace job ownership, dual admin-token rotation, lean public health,
  metrics scrape token. See [`docs/PRODUCTION.md`](docs/PRODUCTION.md).

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
| `GET /v2/status` | Party, quest, choices, location, events |
| `POST /v2/action` | Apply a choice |
| `POST /v2/narrate` | DM narration (active LLM) |
| `POST /v2/save` · `/load` · `/reset` | Session-scoped save lifecycle |
| `GET /v2/save` · `DELETE /v2/save` | Save presence / clear slot |
| `GET /v2/marketplace` | Discover local + remote packs |
| `POST /v2/marketplace/{id}/install` | Install (`?async=true` → job + poll) |
| `GET /v2/marketplace/jobs` | List install jobs for this session |
| `GET/DELETE /v2/marketplace/jobs/{jobId}` | Progress / cancel (**owner session only**) |
| `GET /v2/catalog` | Installed packs + plugins |
| `POST /v2/catalog/packs` | Upload zip (admin-gated when required) |
| `POST /v2/catalog/packs/{id}/enable` · `/disable` | Toggle pack |
| `POST /v2/entitlements/verify` · `GET /v2/entitlements` | Store receipts |
| `GET /health` · `/health/ready` · `/v2/health` | Probes (detail token-gated in prod) |
| `GET /metrics` | Prometheus (scrape token in prod) |

Envelope shape:

```json
{ "type": "game_status", "version": 1, "payload": { "...": "..." }, "requestId": "..." }
```

Send `X-Request-Id` to correlate request/response.

### STOMP WebSocket

- Connect: `ws://localhost:8080/ws-stomp` (SockJS also available)
- CONNECT with `Authorization: Bearer <jwt>`
- Subscribe: `/topic/narrative/{sessionId}` (foreign session ids are denied)
- Send: `/app/action`, `/app/narrate`

### Client SDKs

Generated under [`clients/`](clients/) — do not hand-edit; regenerate from OpenAPI
(see [`clients/README.md`](clients/README.md)).

| Path | Platform |
|---|---|
| [`web/`](web/) | Vite + React SPA (TypeScript SDK) · also served at `/app/` |
| [`android/`](android/) | Jetpack Compose (Kotlin SDK) |
| [`ios/`](ios/) | SwiftUI (Swift SDK) |

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
- Story data: `quests/`, `campaigns/`, `npcs/`, `factions/` (see ADR-001)
- Code plugins: JARs + `plugin.yaml`, signature policy
  (`LENIENT` / `REQUIRED` / `DISABLED`) + bytecode sandbox
- Marketplace: remote index, checksums, HMAC, async install with job ownership
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
docs/
  api/           OpenAPI + AsyncAPI
  PRODUCTION.md  multi-tenant ops, tokens, smoke gates
  MULTI_NODE.md  redis/jdbc session stores, sticky LB
  STOREFRONTS.md receipt plugins
  ROADMAP_STATUS.md
deploy/          docker compose (app ×2 + Postgres + nginx)
scripts/         build-web, launch-smoke, marketplace helpers
```

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
| `game.plugins.signature.policy` | `LENIENT` | Prod: `REQUIRED` |
| `game.plugins.sandbox.enabled` | `true` | Bytecode sandbox |
| `game.saves.dir` | `saves` | Per-session saves |
| `game.instances.max` | `100` | Concurrent session engines |

Production env, admin/metrics tokens, marketplace gates, and launch smoke:
[`docs/PRODUCTION.md`](docs/PRODUCTION.md). Multi-node stores:
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

Launch gate (health + play + STOMP ACL + metrics): `scripts/launch-smoke.sh`.

---

## Roadmap

See [`docs/ROADMAP_STATUS.md`](docs/ROADMAP_STATUS.md). Core engine, plugins, v2 API,
LLM stack, story depth, and production multi-tenant gates are in place. Ongoing
work is client polish, storefront packaging, and live keyed-provider ops.

---

## License

Not yet specified.
