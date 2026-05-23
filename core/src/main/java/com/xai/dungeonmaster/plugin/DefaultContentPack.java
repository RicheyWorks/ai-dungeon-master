package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.Enemy;
import com.xai.dungeonmaster.Item;
import com.xai.dungeonmaster.util.ResourceLoader;

import java.util.Map;

/**
 * The built-in content pack. Wraps items.json and monsters.json from the
 * core jar's classpath so the engine ships with a playable starter set
 * even without external packs installed.
 *
 * This is also a worked example of the ContentPack contract: an external
 * pack that wants to live as a Java class (rather than pure data) just
 * implements ContentPack the same way.
 */
public final class DefaultContentPack implements ContentPack {

    private final Map<String, Item> items;
    private final Map<String, Enemy> monsters;

    public DefaultContentPack() {
        this.items = ResourceLoader.loadItems();
        this.monsters = ResourceLoader.loadMonsters();
    }

    @Override public String id() { return "builtin"; }
    @Override public String displayName() { return "Bundled Content"; }
    @Override public String version() { return "1.0.0"; }
    @Override public Map<String, Item> items() { return items; }
    @Override public Map<String, Enemy> monsters() { return monsters; }
}
