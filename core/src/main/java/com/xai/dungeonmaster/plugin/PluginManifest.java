package com.xai.dungeonmaster.plugin;

import java.util.Collections;
import java.util.List;

/**
 * Mirrors plugin.yaml — the manifest that ships at the root of every
 * code-bearing plugin JAR.
 *
 * <pre>
 * # plugin.yaml
 * id: "house-rules-mod"
 * displayName: "Richmond's House Rules"
 * version: "1.2.0"
 * minEngineVersion: "1.0.0"
 * entryClasses:
 *   - "com.example.houseRules.NetherStrikeEffect"
 *   - "com.example.houseRules.GoldDoublerItemEffect"
 * dependencies: []
 * signature: "..."   # optional, hex-encoded SHA-256
 * </pre>
 *
 * Each entry class must implement one of the Plugin sub-interfaces
 * (SpellEffect, ItemEffect, ContentPack, LLMProvider, etc.). The loader
 * instantiates each via its no-arg constructor and registers it.
 *
 * Public fields so Jackson can populate via reflection — this isn't a
 * value object users construct by hand.
 */
public final class PluginManifest {
    public String id;
    public String displayName;
    public String version;
    public String minEngineVersion;
    public List<String> entryClasses;
    public List<String> dependencies;
    public String signature;

    public List<String> entryClassesOrEmpty() {
        return entryClasses != null ? entryClasses : Collections.emptyList();
    }

    public List<String> dependenciesOrEmpty() {
        return dependencies != null ? dependencies : Collections.emptyList();
    }
}
