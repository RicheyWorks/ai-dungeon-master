# AI Dungeon Master

A single-player, AI-narrated dungeon crawler built as a portable game engine: a
pure-Java core, a Spring Boot server, a versioned REST + WebSocket API, an
offline-capable LLM narration layer, and a data-driven content-pack system.

> Java 17 · Spring Boot 3.2.5 · Maven multi-module · 91 tests green

## Highlights

- Typed, versioned REST + STOMP WebSocket API (`/v2/*`) with generated TypeScript, Kotlin, and Swift SDKs.
- Swappable LLM narration: offline `local-stub` by default, plus keyed OpenAI / Anthropic / xAI / local-llama backends.
- Eight plugin SPIs, all `ServiceLoader`-driven; code mods are signature-verified and bytecode-sandboxed before loading.
- Data-driven content packs (four themed packs ship) with a runtime enable/disable mod browser at `/mod-browser.html`.
- Story depth engine (ADR-001): branching scene graphs, flag-gated campaigns, structured narrative memory fed to the narrator, and NPC/faction dispositions — all authorable as pack JSON, all offline-capable.
- Optional JWT session identity (in-memory or file-persisted) and storefront receipt validation with per-session entitlements.

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
mvn clean test     # compile both modules and run the suite (91 tests)
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

Once it's up, open the mod browser at `http://localhost:8080/mod-browser.html`, or
smoke-test the API:

```bash
curl -s localhost:8080/v2/status      # typed game_status envelope
curl -s localhost:8080/v2/catalog     # installed packs + plugins
```

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
| `POST /v2/catalog/packs` | Upload + install a content-pack zip at runtime (multipart `file`, `?replace=true` to overwrite) |
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
  "recentHistory": ["..."],
  "quest": { "title": "The Weeping Tree", "completed": false, "failed": false, "progress": 0.33 },
  "recentEvents": ["Quest begun: The Weeping Tree", "Boss slain: Grave Warden (by Kael)"]
}
```

Send an `X-Request-Id` header to have it echoed back in the envelope for
request/response correlation.

### WebSocket (STOMP)

- Connect: `ws://localhost:8080/ws` (SockJS fallback available)
- Subscribe: `/topic/narrative` — the narration stream (typed `narrative_chunk`
  chunks followed by a final `narrative_update` envelope for v2 streaming)
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
- All eight plugin SPIs (SpellEffect, ItemEffect, EncounterTable, LootTable,
  QuestScript, LLMProvider, StorefrontIntegration, ContentPack) dispatch through
  registries and are discovered via Java `ServiceLoader` (`META-INF/services`);
  each ships a bundled default that packs or mods can override — no engine changes.
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
- **Story content is pack data too** (ADR-001). A pack may also ship:
  - `quests/*.json` — branching quest documents (scenes, per-choice `nextSceneId`
    targets, declarative `effects` like `SET_FLAG`/`START_QUEST`/`MODIFY_DISPOSITION`
    and `condition` gates like `FLAG`/`DISPOSITION`/`REPUTATION`), registered as
    `QuestScript`s and graph-linted at load;
  - `campaigns/*.json` — flag-gated quest chains the engine walks automatically
    (activate with `game.campaign.id`);
  - `npcs/*.json` and `factions/*.json` — characters with persona sheets for the
    narrator, plus disposition/reputation tracked in the persistent world state.
  `black-hollows` ships all four end-to-end: the 3-quest **Hollows Cycle** campaign
  (`game.campaign.id=the-hollows-cycle`), where burning or resting the weeping tree
  routes the arc to different quests, and Mother Brine, whose blessing unlocks only
  if you've earned her trust.
- A static **mod-browser page** ships at `/mod-browser.html` (served by the
  engine): it renders the `/v2/catalog` data — installed packs, plugins per SPI,
  and the narration provider — and can **enable/disable packs at runtime**, with
  no build step or dependencies.
- **Packs install at runtime** via `POST /v2/catalog/packs` (or the mod
  browser's upload button): zip a pack folder and upload it. Uploads are
  validated defensively — zip-slip guard, entry/size caps, manifest id checks —
  extracted under `game.content.packs.dir`, and registered through the same
  loader as the startup scan (quests and campaigns included). Data only; code
  mods still go through the signed + sandboxed plugin loader.

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
| `game.campaign.id` | _(none)_ | Story arc to run (from a pack's `campaigns/*.json`), e.g. `the-hollows-cycle` |
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

See [`docs/ROADMAP_STATUS.md`](docs/ROADMAP_STATUS.md) for the detailed,
code-grounded status. In brief: Phases 0–1 are complete (headless core, all eight
plugin SPIs dispatchable, signed and sandboxed mods), and Phase 2 is essentially
done — the typed envelope API, structured party state, the LLM provider stack with
guardrails, streaming, and keyed OpenAI/Anthropic/xAI/llama backends, validated
specs, generated TypeScript/Kotlin/Swift SDKs, JWT session identity with optional
persistence, and storefront receipt validation with entitlements. Four themed
content packs ship with a web mod browser. What's left is client-native: the
Android/iOS/Steam apps on the generated SDKs, and a richer in-game mod-browser UI.

## License

Not yet specified.
