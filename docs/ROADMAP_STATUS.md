# AI Dungeon Master — Roadmap Status

_Last updated: 2026-07-04 · Baseline: `mvn test` green (69 tests) · Reference: `AI_Dungeon_Master_Audit_and_Roadmap.docx` (May 2026)_

Grounded in the current code, not the May plan. Phases 0–1 are complete, Phase 2
is essentially complete, and Phase 5 (content packs & mods) now has substantial
groundwork shipped.

## Snapshot

| Phase | Scope | Status |
|---|---|---|
| 0 — Hygiene | headless, packages, tests, listeners, sync | ✅ Done |
| 1 — Headless core + plugin SPI | core module, SPIs, registries, loaders, signing, sandbox | ✅ Done |
| 2 — API v2 + LLM provider | envelope, PartyState, LLM stack + keyed providers, streaming, specs, SDKs, auth, sessions | ✅ Nearly done — storefront receipts remain |
| 3 — First native client (Android) | Compose UI on the generated Kotlin SDK | ◐ SDK generated; UI not started |
| 4 — Steam + iOS | Tauri, SwiftUI on the generated Swift SDK, storefronts | ◐ Swift SDK generated; apps not started |
| 5 — Content packs & mods | packs, mod browser, signing, sandboxing | ◐ 4 packs + signing + sandbox + catalog API; browser UI remains |

## Phase 1 — Headless core + plugin SPI ✅

- `core` is Spring/Swing-free (Jackson + JUnit only) with all 8 SPIs **dispatchable**
  through registries (SpellEffect, ItemEffect, EncounterTable, LootTable, QuestScript,
  LLMProvider, StorefrontIntegration, ContentPack), each with a bundled default and
  ServiceLoader wiring. Generation (enemies/loot/opening quest) routes through them.
- **Plugin JAR signatures verified** (SHA-256 payload hash vs manifest `signature`)
  before load, under a configurable `SignaturePolicy`.
- **Plugin bytecode sandboxed.** `SandboxedClassLoader` scans each plugin-defined
  class's constant pool via `SandboxVerifier` and refuses any that reference blocked
  APIs (process execution, reflection, raw networking, filesystem, JDK internals)
  before instantiation, under `SandboxPolicy` (`game.plugins.sandbox.enabled`,
  default on). Signing (integrity) + sandbox (capability) are the two load-time gates.

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
  Sessions persist through a pluggable `SessionStore` — in-memory (default) or
  file-backed (survives restart), selected by `game.auth.session.store`.
- **Content/mod catalog.** `GET /v2/catalog` lists installed content packs and
  every registered plugin across the SPIs plus the active narration provider —
  the read model behind an in-game mod browser.
- Validated OpenAPI 3.0.3 + AsyncAPI 2.6.0 specs, and **generated client SDKs for
  TypeScript, Kotlin, and Swift** (`clients/`, openapi-generator 7.7.0).

**Remaining**

- Storefront receipt validation. (Session persistence is done — see the
  `SessionStore` note above.)

## Phases 3–5

- **Phase 3/4 inputs are ready:** the Kotlin (`clients/kotlin`) and Swift
  (`clients/swift`) SDKs are generated from the specs. The native UIs (Jetpack
  Compose, SwiftUI/Tauri) and storefront integrations are the remaining work.
- **Phase 5 groundwork shipped:** four themed content packs live under
  `content-packs/` — `black-hollows` (horror), `dnd-classic`, `sci-fi`, and
  `cozy-hearthwood` — each loaded end-to-end by tests. Signing + sandboxing (Phase 1)
  are the security half of the mod story. The read model for a browser is now live
  (`GET /v2/catalog` lists installed packs and every registered plugin); the
  in-game browser UI and install flow remain.

## Remaining backlog

- In-game content pack / mod browser UI + install flow (the `/v2/catalog` read API is done).
- Storefront receipt validation; keyed-provider live smoke tests.
- Native client apps (Android Compose, iOS SwiftUI, Steam/Tauri) on the generated SDKs.
- Deeper mod isolation (dedicated process / OS sandbox) beyond the static bytecode scan.
