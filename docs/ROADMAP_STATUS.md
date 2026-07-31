# AI Dungeon Master — Roadmap Status

_Last updated: 2026-07-31 · Baseline: `mvn test` green (153 tests) · Reference: `AI_Dungeon_Master_Audit_and_Roadmap.docx` (May 2026)_

Grounded in the current code, not the May plan. Phases 0–1 are complete, Phase 2
is essentially complete, and Phase 5 (content packs & mods) now has substantial
groundwork shipped. **Story-depth architecture
([ADR-001](adr/ADR-001-story-depth.md)) is complete**, including the Phase 4
follow-ups: production faction-aware encounters (default table weights monsters
by reputation via optional `factionId` on pack data) and WorldMap wiring
(location + discovered rifts persist at saveVersion 4 and surface on
`/v2/status`). SDKs (TypeScript / Kotlin / Swift) expose `location` and
`discoveredRifts` on `GameStatusV2`.

## Snapshot

| Phase | Scope | Status |
|---|---|---|
| 0 — Hygiene | headless, packages, tests, listeners, sync | ✅ Done |
| 1 — Headless core + plugin SPI | core module, SPIs, registries, loaders, signing, sandbox | ✅ Done |
| 2 — API v2 + LLM provider | envelope, PartyState, LLM stack + keyed providers, streaming, specs, SDKs, auth, sessions, entitlements | ✅ Done |
| 3 — First native client (Android) | Compose UI on the generated Kotlin SDK | ◐ v1 under `android/` — session+Bearer, STOMP live narrate, Mods (upload), Store (entitlements); polish + Play Billing remain |
| 4 — Steam + iOS | Tauri, SwiftUI on the generated Swift SDK, storefronts | ◐ SwiftUI + web SPA on main; engine hosts `/app/`; `desktop/launch.sh` one-click play; Tauri scaffold under `desktop/tauri/` (full bundle later) |
| 5 — Content packs & mods | packs, mod browser, signing, sandboxing | ✅ 4 packs + signing + sandbox + catalog + web mod-browser w/ enable-disable + runtime pack upload (`POST /v2/catalog/packs`) |

## Phase 1 — Headless core + plugin SPI ✅

- `core` is Spring/Swing-free (Jackson + JUnit only) with all 8 SPIs **dispatchable**
  through registries (SpellEffect, ItemEffect, EncounterTable, LootTable, QuestScript,
  LLMProvider, StorefrontIntegration, ContentPack), each with a bundled default and
  ServiceLoader wiring. Generation (enemies/loot/opening quest) routes through them.
- **Plugin JAR signatures verified** (SHA-256 payload hash vs manifest `signature`)
  before load, under a configurable `SignaturePolicy`.
- **Plugin bytecode sandboxed.** `SandboxedClassLoader` scans each plugin-defined
  class's constant pool via `SandboxVerifier` and refuses any that reference blocked
  APIs (process execution, reflection, classloaders, raw networking, filesystem,
  RMI/JMX, JDK internals) or declare `native` methods, before instantiation, under
  `SandboxPolicy` (`game.plugins.sandbox.enabled`, default on). SPI dispatches run
  under `PluginCallGuard` (wall timeout, default 2s via `game.plugins.call.timeout-ms`).
  Signing (integrity) + sandbox (capability) + call guard (runaway) are the three gates.


## Phase 2 — API v2 + LLM provider ✅ (nearly)

**Shipped**

- Typed `Envelope<T>` + versioned `/v2/*` endpoints, structured `PartyState`,
  `X-Request-Id` correlation, alongside the untouched legacy `/api/game/*`.
- Full LLM stack: offline `local-stub`, `LLMProviderRegistry` with always-available
  fallback, `TokenBudgetProvider` + `ModerationProvider` guardrails, STOMP streaming.
- **Keyed providers implemented:** `openai`, `xai`, `anthropic`, and local `llama`
  (OpenAI-compatible + Anthropic Messages), behind an injectable `HttpTransport`
  so they're unit-tested with no network. Keys/models come from env; a provider with
  no key reports DOWN and the registry falls back to the offline stub. Select via
  `game.narration.provider`.
- **Session identity + JWT auth.** `POST /v2/session` mints a guest session + HS256
  JWT; `JwtAuthFilter` guards `/v2/**` when `game.auth.enabled=true` (opt-in).
  Sessions and entitlements persist through pluggable stores — in-memory (default)
  or file-backed with cross-process locks (`game.auth.session.store` /
  `game.auth.entitlement.store` = `file`), so multi-node deployments that share a
  volume share identity and purchases.
  **Per-session game isolation:** authenticated v2 callers each get their own
  `DungeonMasterEngine` via `GameInstanceService` (lazy); unauthenticated calls
  and legacy `/api/game/*` still share the process-default engine. Saves are
  session-scoped under `game.saves.dir` (`POST /v2/save|load|reset`). STOMP
  connections that CONNECT with a Bearer JWT bind to the same session and
  stream on `/topic/narrative/{sessionId}` (`/app/action`, `/app/narrate`).

- **Content/mod catalog.** `GET /v2/catalog` lists installed content packs and
  every registered plugin across the SPIs plus the active narration provider —
  the read model behind an in-game mod browser.
- **Storefront receipt validation.** `POST /v2/entitlements/verify` routes a
  purchase receipt to the matching storefront plugin (bundled `dev` store signs
  and verifies HMAC receipts, the storefront analogue of `local-stub`) and grants
  the product to the session; `GET /v2/entitlements` lists owned products.
- Validated OpenAPI 3.0.3 + AsyncAPI 2.6.0 specs, and **generated client SDKs for
  TypeScript, Kotlin, and Swift** (`clients/`, openapi-generator 7.7.0).
- **World map on status.** `/v2/status` exposes `location` + `discoveredRifts`
  from the engine WorldMap (saveVersion 4).

**Remaining**

- Keyed-provider live smoke is opt-in (`LLM_LIVE_SMOKE=true` + provider keys;
  see `KeyedLlmLiveSmokeTest`). Fake-transport unit coverage for all keyed
  providers is complete. Shared file-backed session/entitlement stores cover
  multi-node when nodes share a volume; a networked DB remains optional.
  Nothing else blocks Phase 2.


## ADR-001 follow-ups ✅

- Faction-aware encounter production path: optional `factionId` on monsters.json;
  `DefaultEncounterTable` weights spawns by effective reputation
  (pack base + WorldState delta). Black Hollows drowned mobs are tagged.
- WorldMap wired into the engine (quest start → location, quest complete → discover
  rift, save/load restore). No longer dead code.
- Client SDKs updated for `location` / `discoveredRifts`.

## Phases 3–5

- **Phase 3/4 inputs are ready:** the Kotlin (`clients/kotlin`) and Swift
  (`clients/swift`) SDKs are generated from the specs. The native UIs (Jetpack
  Compose, SwiftUI/Tauri) and storefront integrations are the remaining work.
- **Phase 5 groundwork shipped:** four themed content packs live under
  `content-packs/` — `black-hollows` (horror), `dnd-classic`, `sci-fi`, and
  `cozy-hearthwood` — each loaded end-to-end by tests. Signing + sandboxing (Phase 1)
  are the security half of the mod story. The read model for a browser is now live
  (`GET /v2/catalog` lists installed packs and every registered plugin) and a
  static web mod-browser page ships at `/mod-browser.html` that also enables and
  disables packs at runtime (`POST /v2/catalog/packs/{id}/enable|disable`, backed
  by a provenance-aware ContentRegistry). Pack upload is live
  (`POST /v2/catalog/packs`).

## Remaining backlog

- Networked multi-node session/entitlement store: **Redis** via `game.auth.*.store=redis` (see `docs/MULTI_NODE.md`). Postgres still optional.

- Native client apps (Android Compose polish; iOS SwiftUI; Steam/Tauri) on the generated SDKs.
- Optional OS-level / dedicated-process plugin isolation beyond the in-JVM sandbox + call guard.
- Richer in-game mod-browser UI (the `/mod-browser.html` page, `/v2/catalog`, enable/disable, and pack upload are done).
