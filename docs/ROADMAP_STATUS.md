# AI Dungeon Master — Roadmap Status

_Audit date: 2026-07-03 · Baseline: `mvn test` green (14 tests) · Reference: `AI_Dungeon_Master_Audit_and_Roadmap.docx` (May 2026)_

Grounded in the current code, not the May plan. The project has advanced well past
the original audit: Phase 0 is complete and Phase 1 is nearly complete.

## Snapshot

| Phase | Scope | Status |
|---|---|---|
| 0 — Hygiene | headless, packages, tests, listeners, sync | ✅ Done |
| 1 — Headless core + plugin SPI | core module, SPIs, registries, loaders | ◐ Mostly done — 3 gaps |
| 2 — API v2 + LLM provider | envelope, PartyState, identity, LLM, specs | ⬜ Not started |
| 3 — First native client (Android) | Compose UI on the SDK | ⬜ Not started |
| 4 — Steam + iOS | Tauri, SwiftUI, storefronts | ⬜ Not started |
| 5 — Content packs & mods | 4 packs, mod browser, signing | ◐ Plumbing only |

## Phase 0 — Hygiene ✅

Every item verified in code:

- Headless by default; Swing gated behind `@ConditionalOnProperty(game.gui.enabled=true)` (`GuiLauncher`, `DungeonMasterApplication`).
- Packages all lowercase (`config/`, `util/`, `controller/`).
- `src/test` present — 14 tests across 3 classes, all green.
- `setUiUpdater` deprecated in favor of additive `addUiListener`, with a regression test asserting listeners aren't evicted.
- `currentQuest` is now `volatile`.

## Phase 1 — Headless core + plugin SPI ◐

**Done**

- `core` module is Spring/Swing-free (deps: Jackson databind+yaml, JUnit only).
- All 8 SPI interfaces defined: SpellEffect, ItemEffect, EncounterTable, LootTable, QuestScript, LLMProvider, StorefrontIntegration, ContentPack.
- `Spell.cast` / `Item.use` dispatch through `SpellEffectRegistry` / `ItemEffectRegistry`, ServiceLoader-wired via `META-INF/services`.
- `ResourceLoader` scans `content-packs/`; `DefaultContentPack` loads `items.json`/`monsters.json`; `GameConfig` registers external packs on boot.
- `PluginLoader` reads `plugin.yaml` from a JAR root and loads via a scoped `URLClassLoader`.

**Gaps**

1. **DungeonGenerator ignores loaded data.** Procedural generation still uses hardcoded inline `String[]` (loot names, party roles, monster behaviors) instead of the content loaded into `ContentRegistry`. The data pipeline exists but generation doesn't consume it.
2. **Only 2 of 8 SPIs are dispatchable.** EncounterTable, LootTable, QuestScript, LLMProvider, StorefrontIntegration have interfaces but no registry / ServiceLoader wiring (`PluginLoader` marks them "Future").
3. **JAR signatures unverified.** `plugin.yaml` `signature` is parsed but never checked — code-bearing mods are trust-on-load.

## Phase 2 — API v2 + LLM provider ⬜  (the real next work)

Nothing here is started:

- API is unversioned `/api/game/*`; no `/v2/`.
- No typed envelope `{type, version, payload, requestId}`.
- `partySummary` is still a flat `String` (the audit's "API leaks server strings"); no structured `PartyState`.
- No session IDs, request IDs, or player identity in any DTO.
- `LLMProvider` interface exists but has **zero implementations** and is wired nowhere — narration is hardcoded `broadcast("…")` strings in the engine.
- No moderation decorator, no token-budget guardrails.
- No OpenAPI/AsyncAPI specs, no generated SDKs.

## Phases 3–5 ⬜

Native clients (Android/iOS), Steam/Tauri, and storefront SDKs are untouched. Content-pack **plumbing** exists (ContentPack SPI, filesystem scanner, DefaultContentPack) but no packs ship and there's no mod browser or signature verification — so Phase 5 is groundwork only.

---

## Recommended first Phase 2 slice

**"API v2 read path: typed envelope + structured PartyState"** — additive `/v2` endpoints alongside the existing API.

Why this first: no external services or API keys, fully unit-testable today, and it establishes the JSON contract every later piece (LLM streaming, native clients, generated SDKs) plugs into. It also directly closes the audit's "API leaks internal strings" red flag.

**Scope**

- `core`: add `PartyState` / `MemberState` value types (name, hp/maxHp, mana, statuses, alive) plus `engine.getPartyState()`. Keep `getPartySummary()` as a thin formatter over it so the GUI/CLI keep working.
- `service`: add a typed `Envelope<T>{type, version, payload, requestId}`; expose `GET /v2/status` and `POST /v2/action` returning envelopes; leave `/api/game/*` untouched.
- Replace loose strings with structured payloads (`PartyStatePayload`, `NarrativePayload`) on the v2 path only.
- Tests: MockMvc coverage asserting envelope shape + structured party fields; a `PartyState → payload` mapping unit test.

**Acceptance:** `GET /v2/status` returns `{type:"game_status", version:1, payload:{party:[…structured…]}, requestId}`; `/api/game/*` and all 14 current tests stay green.

**Natural second slice:** wire `LLMProvider` end-to-end with an offline, no-key **local/stub provider** (deterministic narrator) via a new `LLMProviderRegistry`; route engine narration through it; add the moderation-decorator + token-budget wrapper. Tackles the "biggest design gap" without secrets and streams into the slice-1 envelope.

## Low-risk quick wins (optional, ~an afternoon)

- Point `DungeonGenerator` loot/monster generation at `ContentRegistry` (closes Phase 1 gap 1).
- Add `LootTable` / `EncounterTable` registries mirroring the Spell/Item pattern (chips at gap 2).
