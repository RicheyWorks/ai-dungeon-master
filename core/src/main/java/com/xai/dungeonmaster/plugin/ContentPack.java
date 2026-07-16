package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.Enemy;
import com.xai.dungeonmaster.Item;

import java.util.Collections;
import java.util.Map;

/**
 * A bundle of game content — monsters, items, spell defs, biomes,
 * localized strings — loaded from disk at startup.
 *
 * Content packs are pure data (no executable code). They ship as a folder
 * or zip with a pack.yaml manifest and JSON / properties files:
 *
 *   /content-packs/dnd-classic/
 *     pack.yaml
 *     monsters/*.json
 *     items/*.json
 *     spells/*.json
 *     biomes/*.json
 *     strings/en.properties
 *     art/portraits/*.png
 *
 * ResourceLoader scans the content-packs directory at startup, parses each
 * pack.yaml, and constructs one ContentPack per directory. The engine then
 * asks each pack for its monsters/items/etc. and merges them into the
 * global registries.
 *
 * This interface is also the contract a code-bearing mod fulfills when it
 * wants to ship pure data — implement ContentPack instead of dropping JSON
 * files, get the same merge behavior.
 */
public interface ContentPack extends Plugin {

    /** Pack version string (e.g., "1.0.0"). Used for save-game compatibility. */
    String version();

    /** Minimum engine version this pack is compatible with. */
    default String minEngineVersion() {
        return "1.0.0";
    }

    /** Items keyed by their id. */
    default Map<String, Item> items() {
        return Collections.emptyMap();
    }

    /** Monsters keyed by their id. */
    default Map<String, Enemy> monsters() {
        return Collections.emptyMap();
    }

    /**
     * Localized strings keyed by string id. Multiple language variants may
     * be present in the pack folder; the implementation picks the right one
     * based on the current locale.
     */
    default Map<String, String> strings() {
        return Collections.emptyMap();
    }

    /** NPCs keyed by their id (ADR-001 Phase 4). Additive: packs predating NPCs return empty. */
    default Map<String, com.xai.dungeonmaster.Npc> npcs() {
        return Collections.emptyMap();
    }

    /** Factions keyed by their id (ADR-001 Phase 4). Additive: packs predating factions return empty. */
    default Map<String, com.xai.dungeonmaster.Faction> factions() {
        return Collections.emptyMap();
    }
}
