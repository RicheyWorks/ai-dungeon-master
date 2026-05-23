package com.xai.dungeonmaster.plugin;

/**
 * Marker interface for every Dungeon Master plugin.
 *
 * Every concrete plugin (SpellEffect, ItemEffect, LLMProvider, etc.) extends
 * one of the sub-interfaces in this package, and all of those extend Plugin.
 * Code that needs to enumerate plugins generically (e.g., the admin/debug
 * "/plugins" REST endpoint) can ask for {@code List<Plugin>}.
 *
 * The {@link #id()} string is the canonical key used to register and look up
 * plugins, and is also what content-pack JSON / save files reference. It must
 * be stable across versions of the plugin — never rename an id, version it
 * instead.
 */
public interface Plugin {

    /**
     * Stable identifier (e.g., "VOID_BOLT", "openai", "steam"). Conventionally
     * uppercase snake-case for in-game effects, lowercase kebab-case for
     * external integrations.
     */
    String id();

    /**
     * Human-readable name shown in mod browsers, debug panels, and logs.
     * Falls back to {@link #id()} if not overridden.
     */
    default String displayName() {
        return id();
    }
}
