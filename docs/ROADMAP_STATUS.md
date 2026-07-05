# AI Dungeon Master — Roadmap Status

_Last updated: 2026-07-04 · Baseline: `mvn test` green (54 tests) · Reference: `AI_Dungeon_Master_Audit_and_Roadmap.docx` (May 2026)_

Grounded in the current code, not the May plan. Phase 0 is complete, Phase 1 is
now complete, and most of Phase 2 has been built (session identity landed).

## Snapshot

| Phase | Scope | Status |
|---|---|---|
| 0 — Hygiene | headless, packages, tests, listeners, sync | ✅ Done |
| 1 — Headless core + plugin SPI | core module, SPIs, registries, loaders, signing | ✅ Done |
| 2 — API v2 + LLM provider | envelope, PartyState, LLM stack, streaming, specs, SDK, auth | ◑ Largely done — keyed providers + client gen remain |
| 3 — First native client (Android) | Compose UI on the SDK | ⬜ Not started |
| 4 — Steam + iOS | Tauri, SwiftUI, storefronts | ⬜ Not started |
| 5 — Content packs & mods | 4 packs, mod browser, signing | ◐ Signing + first pack shipped |

## Phase 0 — Hygiene ✅

Every item verified in code:

- Headless by default; Swing gated behind `@ConditionalOnProperty(game.gui.enabled=true)` (`GuiLauncher`, `DungeonMasterApplication`).
- Packages all lowercase (`config/`, `util/`, `controller/`).
- `src/test` present with a real, growing suite (54 tests).
- `setUiUpdater` deprecated in favor of additive `addUiListener`, with a regression test asserting listeners aren't evicted.
- `currentQuest` is now `volatile`.

## Phase 1 — Headless core + plugin SPI ✅

**Done**

- `core` module is Spring/Swing-free (deps: Jackson databind+yaml, JUnit only).
- All 8 SPI interfaces defined: SpellEffect, ItemEffect, EncounterTable, LootTable, QuestScript, LLMProvider, StorefrontIntegration, ContentPack.
- **All 8 SPIs are now dispatchable.** SpellEffect/ItemEffect (Phase 1) and LLMProvider (Phase 2) were already wired; this pass added `EncounterTableRegistry`, `LootTableRegistry`, `QuestScriptRegistry`, and `StorefrontRegistry`, each mirroring the Spell/Item pattern (ServiceLoader discovery + explicit `register()` + a bundled built-in via `META-INF/services`). `PluginLoader` now routes all seven code-bearing plugin types (was: 3).
- **Generation runs through the SPIs.** `DungeonGenerator.generateEnemy`/`generateLoot` dispatch to the `"default"` biome tables (`DefaultEncounterTable`/`DefaultLootTable`), and the engine's opening quest comes from `QuestScriptRegistry` (`DefaultQuestScript`). Behavior is preserved when no pack is loaded; content packs can now override generation by registering biome-keyed tables.
- `ResourceLoader` scans `content-packs/`; `DefaultContentPack` loads `items.json`/`monsters.json`; `GameConfig` registers external packs on boot.
- **Plugin JAR signatures are verified.** `PluginLoader` computes SHA-256 over the JAR payload (all entries except `plugin.yaml`, name-sorted) and checks it against the manifest `signature` before any class is loaded, under a configurable `SignaturePolicy` (LENIENT default / REQUIRED / DISABLED, via `game.plugins.signature.policy`). Rejections are reported separately from failures. This closes the old trust-on-load gap.

**Gaps: none.** (Code sandboxing of loaded plugins — a SecurityManager or equivalent — is deferred to Phase 5+ and tracked there, not as a Phase 1 gap.)

## Phase 2 — API v2 + LLM provider ◑  (largely built)

**Shipped**

- Typed `Envelope<T>{type, version, payload, requestId}` and versioned `/v2/*` endpoints (`status`, `action`, `narrate`), alongside the untouched legacy `/api/game/*`.
- Structured `PartyState` / `MemberState` replacing the flat `partySummary` string.
- `X-Request-Id` correlation echoed on every v2 response.
- `LLMProvider` wired end-to-end: offline deterministic `local-stub`, `LLMProviderRegistry` (ServiceLoader discovery + always-available fallback), and `TokenBudgetProvider` + `ModerationProvider` guardrail decorators; engine `narrate()` / `narrateStreaming()`.
- Streaming narration over STOMP as typed `narrative_chunk` → `narrative_update` envelopes.
- Validated OpenAPI 3.0.3 + AsyncAPI 2.6.0 specs; a generated, `tsc`-clean TypeScript client.
- **Session identity + JWT auth.** `POST /v2/session` mints a guest session (UUID identity) and a self-contained HS256 JWT; `GET /v2/session/me` echoes it. `JwtAuthFilter` resolves the token on every request and, when `game.auth.enabled=true`, guards all `/v2/**` routes except the public login endpoint (401 error envelope otherwise). Enforcement is opt-in so existing clients keep working. `JwtService` is dependency-free (HMAC-SHA256, base64url), secret + TTL from config.

**Remaining**

- Keyed provider implementations (OpenAI/Anthropic/xAI/llama) — interface + registry are ready; need API keys.
- Kotlin + Swift client generation from the specs.
- Persistence/refresh for sessions (currently in-memory, single-process) and storefront receipt validation.

## Phases 3–5

Native clients (Android/iOS) and Steam/Tauri are untouched. Content-pack
**plumbing** is complete, and Phase 5 now has real groundwork:

- **First themed pack shipped:** `content-packs/black-hollows/` (gothic horror) —
  4 monsters (incl. a boss), 5 items on bundled effect keys, and localized
  strings. Loaded end-to-end by `ContentPackLoadTest`.
- **Signature verification** (see Phase 1) is the security half of the mod story.
- Still missing: the other launch packs (D&D-classic, Sci-Fi, Cozy), an in-game
  pack/mod browser, and code sandboxing for JAR mods.

## Remaining backlog

- Ship the remaining launch content packs and an in-game pack/mod browser.
- Sandbox code-bearing mods (SecurityManager successor) — signing is done; isolation is not.
- Keyed LLM providers + session persistence + storefront receipt validation.
- Generate and wire the Kotlin (Android) and Swift (iOS) clients.
