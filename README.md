# AI Dungeon Master

A single-player, AI-narrated dungeon crawler built as a portable game engine: a
pure-Java core, a Spring Boot server, a versioned REST + WebSocket API, an
offline-capable LLM narration layer, and a data-driven content-pack system.

> Java 17 · Spring Boot 3.2.5 · Maven multi-module · 33 tests green

## Overview

The project follows a **hybrid architecture** — a pure-Java engine holds all the
rules and plugin interfaces, a Spring Boot layer exposes it over HTTP + STOMP
WebSocket, and thin native clients (generated from the API specs) consume it.
Scope for v1 is single-player.

Two Maven modules:

- **`core`** — the game engine and plugin SPI. No Spring, no Swing; depends only
  on Jackson + JUnit. Domain model (party, combat, quests, items, spells),
  procedural generation, the `LLMProvider` narration abstraction, and the
  content-pack registries live here.
- **`service`** — the Spring Boot adapter: REST (`/v2/*` plus legacy
  `/api/game/*`), STOMP WebSocket narration, an optional Swing desktop GUI, an
  optional terminal CLI, plugin/content-pack wiring, and the narration provider
  stack.

## Requirements

- JDK 17+
- Maven 3.9+

## Build & test

```bash
mvn clean test     # compile both modules and run the suite (33 tests)
mvn package        # build the runnable Spring Boot jar
```

The runnable artifact is `service/target/ai-dungeon-master-service-1.0-SNAPSHOT.jar`.

## Run

Headless server (REST + WebSocket only):

```bash
java -jar service/target/ai-dungeon-master-service-1.0-SNAPSHOT.jar
```

The server listens on `http://localhost:8080`. Optional modes (pass as Spring
`--property=value` args or set in `application.properties`):

```bash
--game.gui.enabled=true    # launch the Swing desktop window
--game.cli.enabled=true    # run the interactive terminal CLI
```

The JVM stays headless by default; the GUI flag flips `java.awt.headless` before
Spring boots so AWT can initialise.

## API

Every **v2** response is wrapped in a typed, versioned envelope, so clients get a
stable, self-describing shape:

```json
{ "type": "game_status", "version": 1, "payload": { ... }, "requestId": "..." }
```

### REST

| Method & path | Description |
|---|---|
| `GET /v2/status` | Full snapshot — structured party, chaos, choices, recent log |
| `POST /v2/action` | Apply a choice (`{ "choiceLabel": "Attack" }`); returns updated status |
| `POST /v2/narrate` | Generate DM narration via the active LLM provider |
| `GET/POST /api/game/*` | Original unversioned API (kept for existing clients) |

Example `GET /v2/status` payload:

```json
{
  "party": [
    { "name": "Kael", "role": "Warrior", "hp": 100, "maxHp": 100,
      "armorClass": 12, "alive": true, "statuses": [] }
  ],
  "chaosLevel": 4,
  "combatActive": false,
  "availableChoices": ["Scavenge for parts", "Push deeper into the rift"],
  "recentHistory": ["..."]
}
```

Send an `X-Request-Id` header to have it echoed back in the envelope for
request/response correlation.

### WebSocket (STOMP)

- Connect: `ws://localhost:8080/ws` (SockJS fallback available)
- Subscribe: `/topic/narrative` — the narration stream (plain text today; typed
  `narrative_chunk` then `narrative_update` envelopes for v2 streaming)
- Send: `/app/action` (a choice) or `/app/narrate` (a prompt to stream narration)

### Specs & client SDKs

The full contract is documented and validated under [`docs/api/`](docs/api/):

- [`openapi.yaml`](docs/api/openapi.yaml) — OpenAPI 3.0.3 (REST)
- [`asyncapi.yaml`](docs/api/asyncapi.yaml) — AsyncAPI 2.6.0 (WebSocket)

A generated, type-checked TypeScript client lives in
[`clients/typescript`](clients/); Kotlin and Swift come from the same spec — see
[`clients/README.md`](clients/README.md).

## AI narration (LLM providers)

Narration flows through a swappable
[`LLMProvider`](core/src/main/java/com/xai/dungeonmaster/plugin/LLMProvider.java)
SPI, so the backend can change per deployment or per turn without touching game
code:

- **Offline by default.** The bundled `local-stub` provider is deterministic and
  needs no API key or network — ideal for dev, CI, and a free/privacy tier. Real
  keyed backends (OpenAI, Anthropic, xAI, local llama.cpp) implement the same
  interface and slot in behind `LLMProviderRegistry`.
- **Guardrails.** `TokenBudgetProvider` enforces a per-session token ceiling and
  `ModerationProvider` filters output; they compose as decorators and are wired
  in `GameConfig`.
- **Streaming.** Streaming-capable providers push partial narration chunks over
  the WebSocket as typed envelopes.

Config:

```properties
game.narration.provider=local-stub    # active provider id
game.narration.token.ceiling=4000     # per-session cost guardrail
```

## Content packs & plugins

Game content is data-driven, not hardcoded:

- `core/src/main/resources/items.json` and `monsters.json` form the bundled pack,
  merged into a process-wide `ContentRegistry` at startup; `DungeonGenerator`
  draws loot and enemies from it (with legacy fallbacks when nothing is loaded).
- Additional packs are scanned from a `content-packs/` directory
  (`game.content.packs.dir`).
- Code-bearing plugins ship as JARs with a `plugin.yaml` manifest, loaded via a
  scoped classloader (`game.plugins.dir`).
- Spell and item effects register via Java `ServiceLoader` (`META-INF/services`),
  so new effects need no engine changes.

## Project layout

```
core/      pure-Java engine + plugin SPI (no Spring/Swing)
service/   Spring Boot: REST, WebSocket, GUI, CLI, wiring
docs/
  api/                OpenAPI + AsyncAPI specs and SDK-generation notes
  ROADMAP_STATUS.md   current state vs the 5-phase roadmap
clients/
  typescript/         generated, type-checked REST client
```

## Configuration reference

| Property | Default | Purpose |
|---|---|---|
| `server.port` | `8080` | HTTP/WebSocket port |
| `game.difficulty` | `4` | Encounter difficulty |
| `game.chaos` | `4` | Chaos level (affects generation) |
| `game.party.names` / `game.party.roles` | `Kael,Lira` / `Warrior,Mage` | Starting party |
| `game.gui.enabled` | `true` | Launch the Swing GUI |
| `game.cli.enabled` | `false` | Run the terminal CLI |
| `game.narration.provider` | `local-stub` | Active LLM provider id |
| `game.narration.token.ceiling` | `4000` | Per-session narration token cap |

## Roadmap

See [`docs/ROADMAP_STATUS.md`](docs/ROADMAP_STATUS.md) for a detailed,
code-grounded status. In brief: Phase 0 (hygiene) is complete, Phase 1 (headless
core + plugin SPI) is essentially complete, and Phase 2 (v2 API + LLM provider)
is largely built — typed envelope, structured party state, the provider stack
with guardrails and streaming, validated specs, and a generated client. Native
clients (Steam / Android / iOS) and storefront integrations are future phases.

## License

Not yet specified.
