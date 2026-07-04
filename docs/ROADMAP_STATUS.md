# AI Dungeon Master — Roadmap Status

_Last updated: 2026-07-03 · Baseline: `mvn test` green (33 tests) · Reference: `AI_Dungeon_Master_Audit_and_Roadmap.docx` (May 2026)_

Grounded in the current code, not the May plan. Phase 0 is complete, Phase 1 is
essentially complete, and most of Phase 2 has now been built.

## Snapshot

| Phase | Scope | Status |
|---|---|---|
| 0 — Hygiene | headless, packages, tests, listeners, sync | ✅ Done |
| 1 — Headless core + plugin SPI | core module, SPIs, registries, loaders | ◐ Nearly done — 2 gaps |
| 2 — API v2 + LLM provider | envelope, PartyState, LLM stack, streaming, specs, SDK | ◑ Largely done — identity remaining |
| 3 — First native client (Android) | Compose UI on the SDK | ⬜ Not started |
| 4 — Steam + iOS | Tauri, SwiftUI, storefronts | ⬜ Not started |
| 5 — Content packs & mods | 4 packs, mod browser, signing | ◐ Plumbing only |

## Phase 0 — Hygiene ✅

Every item verified in code:

- Headless by default; Swing gated behind `@ConditionalOnProperty(game.gui.enabled=true)` (`GuiLauncher`, `DungeonMasterApplication`).
- Packages all lowercase (`config/`, `util/`, `controller/`).
- `src/test` present with a real, growing suite (33 tests).
- `setUiUpdater` deprecated in favor of additive `addUiListener`, with a regression test asserting listeners aren't evicted.
- `currentQuest` is now `volatile`.

## Phase 1 — Headless core + plugin SPI ◐

**Done**

- `core` module is Spring/Swing-free (deps: Jackson databind+yaml, JUnit only).
- All 8 SPI interfaces defined: SpellEffect, ItemEffect, EncounterTable, LootTable, QuestScript, LLMProvider, StorefrontIntegration, ContentPack.
- `Spell.cast` / `Item.use` dispatch through `SpellEffectRegistry` / `ItemEffectRegistry`, ServiceLoader-wired via `META-INF/services`.
- `ResourceLoader` scans `content-packs/`; `DefaultContentPack` loads `items.json`/`monsters.json`; `GameConfig` registers external packs on boot.
- `PluginLoader` reads `plugin.yaml` from a JAR root and loads via a scoped `URLClassLoader`.
- **Content-pack monster loading fixed.** `monsters.json` never actually loaded — entries lacked the `entityType` discriminator `Entity`'s `@JsonTypeInfo` requires, and `baseHp`/`baseAc`/`levelRequirement`/`isBoss` didn't match `Enemy`'s Jackson fields. Fixed with the discriminator + `@JsonAlias` + an `isBoss` setter; `DungeonGenerator.generateEnemy` now scales from the loaded base stats.

**Gaps (2)**

1. **Only 2 of 8 SPIs are dispatchable.** EncounterTable, LootTable, QuestScript, StorefrontIntegration have interfaces but no registry / ServiceLoader wiring (`PluginLoader` marks them "Future"). LLMProvider is now wired (see Phase 2).
2. **JAR signatures unverified.** `plugin.yaml` `signature` is parsed but never checked — code-bearing mods are trust-on-load.

## Phase 2 — API v2 + LLM provider ◑  (largely built)

**Shipped**

- Typed `Envelope<T>{type, version, payload, requestId}` and versioned `/v2/*` endpoints (`status`, `action`, `narrate`), alongside the untouched legacy `/api/game/*`.
- Structured `PartyState` / `MemberState` replacing the flat `partySummary` string.
- `X-Request-Id` correlation echoed on every v2 response.
- `LLMProvider` wired end-to-end: offline deterministic `local-stub`, `LLMProviderRegistry` (ServiceLoader discovery + always-available fallback), and `TokenBudgetProvider` + `ModerationProvider` guardrail decorators; engine `narrate()` / `narrateStreaming()`.
- Streaming narration over STOMP as typed `narrative_chunk` → `narrative_update` envelopes.
- Validated OpenAPI 3.0.3 + AsyncAPI 2.6.0 specs; a generated, `tsc`-clean TypeScript client.

**Remaining**

- Session IDs / player identity / auth (JWT) — not started.
- Keyed provider implementations (OpenAI/Anthropic/xAI/llama) — interface + registry are ready; need API keys.
- Kotlin + Swift client generation from the specs.

## Phases 3–5 ⬜

Native clients (Android/iOS), Steam/Tauri, and storefront SDKs are untouched.
Content-pack **plumbing** works now (SPI, filesystem scanner, DefaultContentPack,
JSON loading), but no themed packs ship and there's no mod browser or signature
verification — so Phase 5 is groundwork only.

## Remaining backlog

- Wire the other SPI registries (EncounterTable, LootTable, QuestScript, StorefrontIntegration) mirroring the Spell/Item pattern.
- Verify plugin JAR signatures before loading (close the trust-on-load gap).
- Session/player identity + storefront receipt validation.
- Ship the launch content packs (D&D-classic, Sci-Fi, Horror, Cozy) and an in-game pack/mod browser.
- Generate and wire the Kotlin (Android) and Swift (iOS) clients.
