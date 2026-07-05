# AI Dungeon Master

A single-player, AI-narrated dungeon crawler built as a portable game engine: a
pure-Java core, a Spring Boot server, a versioned REST + WebSocket API, an
offline-capable LLM narration layer, and a data-driven content-pack system.

> Java 17 · Spring Boot 3.2.5 · Maven multi-module · 69 tests green

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
mvn clean test     # compile both modules and run the suite (69 tests)
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
| `POST /v2/session` | Create a guest session; returns a JWT + session id |
| `GET /v2/session/me` | Echo the caller's session (requires a Bearer token) |
| `GET /v2/catalog` | Installed content packs + registered plugins (mod browser) |
| `POST /v2/catalog/packs/{id}/enable` · `/disable` | Toggle a content pack on/off at runtime |
| `POST /v2/entitlements/verify` | Validate a purchase receipt and grant the entitlement |
| `GET /v2/entitlements` | List the caller's owned products |
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

Generated, type-checked client SDKs live in [`clients/`](clients/):
`typescript/`, `kotlin/` (Android/JVM), and `swift/` (iOS/macOS) — all from the
same spec via openapi-generator 7.7.0. See [`clients/README.md`](clients/README.md).

## AI narration (LLM providers)

Narration flows through a swappable
[`LLMProvider`](core/src/main/java/com/xai/dungeonmaster/plugin/LLMProvider.java)
SPI, so the backend can change per deployment or per turn without touching game
code:

- **Offline by default, keyed when you want it.** The bundled `local-stub`
  provider is deterministic and needs no API key or network — ideal for dev, CI,
  and a free/privacy tier. Keyed backends for **OpenAI, Anthropic, xAI, and local
  llama** ship too: set `game.narration.provider` and the matching `*_API_KEY` env
  var. A provider with no key reports DOWN and the registry falls back to the stub.
- **Guardrails.** `TokenBudgetProvider` enforces a per-session token ceiling and
  `ModerationProvider` filters output; they compose as decorators and are wired
  in `GameConfig`.
- **Streaming.** Streaming-capable providers push partial narration chunks over
  the WebSocket as typed envelopes.

Config:

```properties
game.narration.provider=local-stub    # local-stub | openai | anthropic | xai | llama
game.narration.token.ceiling=4000     # per-session cost guardrail
# Keyed providers read env / system props: OPENAI_API_KEY, ANTHROPIC_API_KEY,
# XAI_API_KEY, LLAMA_BASE_URL (+ optional *_MODEL / *_BASE_URL overrides).
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
- All eight plugin SPIs are dispatchable through registries — SpellEffect,
  ItemEffect, EncounterTable, LootTable, QuestScript, LLMProvider,
  StorefrontIntegration, ContentPack — each with a bundled default that
  content packs or mods can override.
- Plugin JARs are signature-checked before any class loads: `PluginLoader`
  hashes the payload (SHA-256, all entries except `plugin.yaml`) and compares
  it to the manifest `signature` under a configurable
  `game.plugins.signature.policy` (LENIENT / REQUIRED / DISABLED).
- Plugin bytecode is sandboxed before instantiation: `SandboxedClassLoader`
  rejects classes referencing blocked APIs (process exec, reflection, raw net/fs,
  JDK internals) under `game.plugins.sandbox.enabled` (default on).
- Four themed packs ship under `content-packs/`: `black-hollows` (horror),
  `dnd-classic`, `sci-fi`, and `cozy-hearthwood` — monsters, items, and localized
  strings, each loaded end-to-end by tests.
- A static **mod-browser page** ships at `/mod-browser.html` (served by the
  engine): it renders the `/v2/catalog` data — installed packs, plugins per SPI,
  and the narration provider — and can **enable/disable packs at runtime**, with
  no build step or dependencies.

## Project layout

```
core/      pure-Java engine + plugin SPI (no Spring/Swing)
service/   Spring Boot: REST, WebSocket, GUI, CLI, wiring
docs/
  api/                OpenAPI + AsyncAPI specs and SDK-generation notes
  ROADMAP_STATUS.md   current state vs the 5-phase roadmap
clients/
  typescript/  kotlin/  swift/   generated REST client SDKs (openapi-generator)
content-packs/   themed data packs: black-hollows, dnd-classic, sci-fi, cozy-hearthwood
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
| `game.auth.enabled` | `false` | Enforce JWT auth on `/v2/**` (opt-in) |
| `game.auth.jwt.secret` | _(insecure dev secret)_ | HMAC-SHA256 token signing secret |
| `game.auth.jwt.ttl-seconds` | `86400` | Session token lifetime (seconds) |
| `game.auth.session.store` | `memory` | Session store: `memory` or `file` (survives restart) |
| `game.auth.session.file` | `sessions.json` | JSON file for the file session store |
| `game.plugins.signature.policy` | `LENIENT` | Plugin signature policy: LENIENT / REQUIRED / DISABLED |
| `game.plugins.sandbox.enabled` | `true` | Sandbox-scan plugin bytecode before loading |

## Roadmap

See [`docs/ROADMAP_STATUS.md`](docs/ROADMAP_STATUS.md) for a detailed,
code-grounded status. In brief: Phase 0 (hygiene) is complete, Phase 1 (headless
core + plugin SPI) is complete — all eight SPIs are dispatchable and plugin JARs
are signature-verified — and Phase 2 (v2 API + LLM provider) is largely built:
typed envelope, structured party state, the provider stack with guardrails,
streaming, and keyed OpenAI/Anthropic/xAI/llama backends, validated specs,
generated TypeScript/Kotlin/Swift clients, and session identity + JWT auth. Plugin
mods are signed and sandboxed, and four themed content packs ship. The native
client apps (Steam / Android / iOS) and storefront integrations are future phases.

## License

Not yet specified.
