# ADR-001: Story depth — branching, campaign arc, narrative memory, NPCs

**Status:** Accepted — Phases 1–4 + follow-ups implemented 2026-07-16 (suite: 91 → 135 tests)
**Date:** 2026-07-16
**Deciders:** Richmond
**Scope:** `core` engine + content-pack format; `service` changes are surface-level (envelope fields)

## Context

The engine's narrative layer is structurally shallow, and the shallowness is architectural, not content-related:

- **Choices don't branch.** `DungeonMasterEngine.processExplorationAction()` executes the chosen `Choice`, then calls `quest.advance(this)` unconditionally. `Quest` is a linear `List<Scene>` walked by `currentSceneIndex++`. Every choice leads to the same next scene; picking "Search for traps" vs "Charge forward" changes one line of flavor text.
- **No arc.** When a quest completes, nothing follows. The opening quest comes from `QuestScriptRegistry` (the `default` script → `DungeonGenerator.generateCustomRift`), and that's the whole game. `WorldMap` (locations, discovered rifts) exists but is never wired into the engine.
- **The narrator is amnesiac.** `narrate()` builds `NarrativePrompt(userPrompt, scene, 256)` where `scene` is just the quest title. The LLM knows nothing about party deaths, prior quests, defeated bosses, or past choices. `turnHistoryLog` holds raw broadcast strings — unstructured, unbounded, unusable as context.
- **Choice effects are hardcoded.** `Choice.processEngineAction()` is a 5-case switch (`TRIGGER_COMBAT`, `GIVE_LOOT`, `REST_PARTY`, `INCREASE_CHAOS`, default). Two of the five only log text without doing anything. Packs can't add verbs.
- **Conditional choices break persistence.** `Choice.requirement` is a `@JsonIgnore Predicate<Adventurer>` — any gated choice silently loses its gate on save/load.
- **No NPCs.** No social entity exists outside combat; `ContentRegistry` merges items, monsters, and strings only.

### Requirements (from decision elicitation)

1. All four depth dimensions: branching choices, campaign arc, narrative memory, NPCs/factions.
2. **Hybrid authoring**: humans (or pack authors) write the structural skeleton; the LLM fleshes out prose at runtime.
3. **Offline constraint**: everything must work with the deterministic `local-stub` provider — depth must live in engine state, not in LLM improvisation.
4. **Save-file compatibility**: existing `GameStateData` JSON saves must still load.

## Decision

Adopt a **"canonical state in the engine, prose from the narrator"** architecture, delivered in four phases:

1. **Phase 1 — Branching scene graph** (foundation): scenes become a graph keyed by id; each choice names its destination scene. Quests become data files in content packs.
2. **Phase 2 — Campaign arc + world state**: a `Campaign` chains quests through a flag-gated DAG; a persistent `WorldState` (flags + counters) is the single source of narrative truth; choice effects become a declarative, extensible effect list.
3. **Phase 3 — Narrative memory**: a structured `Chronicle` of typed story events, compacted into the `NarrativePrompt` so every provider (including the stub) narrates with continuity.
4. **Phase 4 — NPCs & factions**: data-driven NPC/faction registries with disposition and reputation driven by the same flag/effect machinery, plus dialogue scenes.

Each phase is independently shippable and each later phase consumes only the machinery of earlier ones.

## Options considered

### Option A: LLM-driven depth ("let the model improvise the story")

Feed the full turn history to the LLM and let it invent branches, NPCs, and continuity; the engine just tracks combat.

| Dimension | Assessment |
|-----------|------------|
| Complexity | Low (engine barely changes) |
| Cost | High per-session token cost; `game.narration.token.ceiling=4000` would need a big raise |
| Scalability | Poor — context grows per turn; no compaction story |
| Team familiarity | High (prompt engineering only) |
| Offline story | **Fails** — `local-stub` is deterministic and can't improvise; free tier gets no depth |

**Pros:** fastest to demo; infinitely varied prose.
**Cons:** violates the offline constraint outright; story state lives in a context window, so save/load can't restore it; untestable (91-test suite can't assert on improvisation); mod authors can't script anything.

### Option B: Engine-authored graph only (no LLM role change)

Full branching/campaign/NPC machinery in `core`, all prose hand-authored in pack JSON; the LLM stays a cosmetic per-request narrator.

| Dimension | Assessment |
|-----------|------------|
| Complexity | Medium |
| Cost | Zero runtime token cost for story |
| Scalability | Content-bound — depth requires hand-writing every branch's prose |
| Team familiarity | High (plain Java + JSON, mirrors existing pack format) |
| Offline story | Full parity |

**Pros:** deterministic, testable, save-friendly, offline-complete.
**Cons:** wastes the existing LLM stack; prose volume explodes combinatorially with branching; "AI Dungeon Master" without AI narration depth.

### Option C: Hybrid — canonical engine state, LLM decoration (chosen)

Option B's machinery, plus: every structural element (scene, event, NPC) carries authored *fallback prose* and structured *facts*. The narration layer renders facts → prose. Keyed providers get facts as prompt context and generate rich prose; `local-stub` renders the authored fallback. Same story either way; only the writing quality differs.

| Dimension | Assessment |
|-----------|------------|
| Complexity | Medium-high (B + prompt-context plumbing) |
| Cost | Bounded — facts are compact; existing `TokenBudgetProvider` ceiling still applies |
| Scalability | Good — authors write skeletons, not every prose variant |
| Team familiarity | High — extends existing SPI/registry/pack patterns |
| Offline story | Full structural parity; plainer prose |

**Pros:** honors all four constraints; depth is testable (assert on state, not prose); packs ship campaigns the same way they ship monsters.
**Cons:** most moving parts; two prose paths to keep coherent; `NarrativePrompt` needs a compatible extension.

## Trade-off analysis

The offline constraint is the forcing function: any design where the *story itself* lives in the LLM (Option A) makes the free/dev/CI tier a different, shallower game and makes saves unable to restore narrative position. So canonical state must live in the engine — the real question is how much the LLM adds on top. Option B pays for determinism with combinatorial authoring cost; Option C caps that cost by letting authored content be *facts + one fallback line* while the LLM absorbs prose variety. The extra complexity of C is mostly additive plumbing along seams that already exist (`QuestScript` SPI, `ContentRegistry`, `NarrativePrompt`), not new architecture.

## Design by phase

### Phase 1 — Branching scene graph

- `Choice` gains `String nextSceneId` (nullable → "stay/advance linearly" for compat).
- `Quest` gains `Map<String, Scene> sceneIndex` built from the existing list and a `String currentSceneId`; `advance(engine)` becomes `advance(engine, Choice taken)` — resolve `taken.getNextSceneId()`, else fall back to index order. Old serialized saves (index-based) still resolve via the retained `currentSceneIndex`.
- New pack directory `quests/*.json`: a `Quest` document (scenes, choices, `nextSceneId` links, `isFinalScene`). A bundled `DataQuestScript implements QuestScript` loads these through the existing `QuestScriptRegistry` — packs ship campaigns with **zero code**, mirroring how `pack.yaml` merges items/monsters today.
- Multiple final scenes = multiple endings, recorded as an `outcome` string on completion.

*Save compat:* new fields are additive with Jackson defaults; `Quest`'s `@JsonCreator` already tolerates missing properties.

### Phase 2 — Campaign arc + world state

- `WorldState`: persisted `Map<String, Integer>` flags/counters + quest outcome log. Added to `GameStateData` (additive field; old saves load with an empty world).
- `Campaign`: list of `QuestNode {questId, requiresFlags, grantsFlags}` — a flag-gated DAG. On quest completion the engine consults the campaign for the next eligible node and dispatches through `QuestScriptRegistry`; no eligible node → procedural rift fallback (today's behavior).
- Replace `Choice.processEngineAction()`'s switch with a `List<ChoiceEffect> {type, arg}` executed by an effect interpreter: existing verbs plus `SET_FLAG`, `ADD_FLAG`, `START_QUEST`, and (Phase 4) `MODIFY_DISPOSITION`. Legacy `actionKey` maps to a one-element effect list.
- Replace the non-serializable `Predicate` requirement with a declarative `ChoiceCondition {type, key, op, value}` (flag checks, stat checks). Fixes the save/load gate-loss bug as a side effect.
- Wire `WorldMap` in as the campaign's location model (currently dead code).

### Phase 3 — Narrative memory

- `Chronicle`: bounded, structured event list — `StoryEvent {type, turn, subject, detail}` for quest outcomes, boss kills, deaths, flag milestones, (later) NPC interactions. Engine emits events at the same points it already broadcasts strings. Persisted in `GameStateData` (additive).
- `NarrativePrompt` gains an optional `List<String> contextFacts` (compact renderings of recent + pinned events). Additive field on the existing final class, with the old constructor delegating — existing providers compile unchanged.
- Keyed providers interpolate facts into their system prompt. `local-stub` renders a deterministic recap line from the same facts — offline players see continuity too.
- Compaction: keep last N events verbatim + roll older ones into per-type counters, so context stays within the token ceiling. No LLM summarization required (offline constraint); keyed deployments may layer it later.

### Phase 4 — NPCs & factions

- Pack data `npcs/*.json`, `factions/*.json` merged into `ContentRegistry` like items/monsters. `Npc {id, name, role, factionId, persona, disposition}`; `Faction {id, name, reputation}`.
- Disposition/reputation live in `WorldState` (Phase 2 machinery) and are mutated by `ChoiceEffect`s; `ChoiceCondition`s gate choices on them.
- Dialogue = scenes tagged with `npcId`; choices carry disposition effects. For keyed providers, the NPC's persona sheet + relevant Chronicle events feed `contextFacts`, so the LLM voices the character; the stub uses authored lines.
- Faction standing feeds `EncounterTable` context (hostile/neutral spawns) via the existing SPI.

## Consequences

**Easier:** packs ship full campaigns (data-only, like `black-hollows` ships monsters); story regressions are unit-testable as state assertions; endings/outcomes become queryable for the v2 API; the choice-gate serialization bug disappears; offline tier gets real depth.

**Harder:** quest JSON authoring needs validation (dangling `nextSceneId` = broken game — add a load-time graph lint); two prose paths (authored fallback + LLM) must stay tonally coherent; `GameStateData` grows — a `saveVersion` field should be added now to ease future migrations.

**Revisit later:** LLM-proposed branches (model suggests, engine validates against the graph) once Phase 3 context plumbing exists; multiplayer implications for `WorldState` (v1 is single-player); moving `Scene`'s static flavor arrays into pack strings.

## Action items

1. [x] Phase 1: `nextSceneId` on `Choice`, graph-aware `Quest.advance`, `quests/*.json` pack loading via `DataQuestScript`; graph lint at load; tests for branch resolution + legacy-save load.
2. [x] Phase 2: `WorldState`, `Campaign` DAG, `ChoiceEffect`/`ChoiceCondition` interpreter (retire the switch and the `Predicate`); `saveVersion` in `GameStateData`; 3-quest Hollows Cycle demo campaign in `black-hollows` (`game.campaign.id=the-hollows-cycle`).
3. [x] Phase 3: `Chronicle` + compaction, `NarrativePrompt.contextFacts`, stub recap rendering, keyed-provider prompt interpolation; fact-budget test bounded well under the 4000 ceiling.
4. [x] Phase 4: NPC/faction pack schema merged into `ContentRegistry` (additive `ContentPack` defaults), `MODIFY_DISPOSITION`/`MODIFY_REPUTATION` effects + `DISPOSITION`/`REPUTATION` conditions on WorldState flag namespaces, `Scene.npcId` dialogue scenes with first-meeting Chronicle events and persona narration facts; Mother Brine shipped end-to-end in `black-hollows`.
5. [x] Faction-aware `EncounterTable` context: additive default overload `roll(random, difficulty, chaos, isBoss, WorldState)` + world-aware registry dispatch; engine passes its WorldState through combat generation, so tables can shape spawns from faction reputation and story flags. Legacy 4-arg tables unaffected.
6. [x] `docs/ROADMAP_STATUS.md`, README, and OpenAPI spec updated (`quest` outcome + `recentEvents` in `/v2/status`).
7. [x] TypeScript/Kotlin/Swift SDKs regenerated from the updated spec (openapi-generator 7.7.0, per `clients/README.md`) — `QuestInfo` + `recentEvents` in all three clients.
