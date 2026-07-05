package com.xai.dungeonmaster.dto;

import java.util.List;

/**
 * Registered plugins grouped by SPI, as surfaced by {@code /v2/catalog}. Each
 * list holds the stable ids (or biome tags) currently registered — the bundled
 * built-ins plus anything a content pack or mod added.
 */
public record PluginSummary(
        List<String> spellEffects,
        List<String> itemEffects,
        List<String> encounterBiomes,
        List<String> lootBiomes,
        List<String> questScripts,
        List<String> storefronts,
        List<String> llmProviders) {}
