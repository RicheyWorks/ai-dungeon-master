package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.Enemy;
import com.xai.dungeonmaster.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Enabling/disabling a pack adds/removes exactly its content from the active pools. */
class ContentRegistryToggleTest {

    @AfterEach
    void reset() {
        ContentRegistry.clearForTests();
    }

    private static ContentPack pack(String id, String itemId, String monsterId) {
        return new ContentPack() {
            @Override public String id() { return id; }
            @Override public String version() { return "1.0.0"; }
            @Override public Map<String, Item> items() {
                return Collections.singletonMap(itemId,
                        new Item(itemId, "d", Item.ItemType.CONSUMABLE, Item.Rarity.COMMON, "HEAL", 10));
            }
            @Override public Map<String, Enemy> monsters() {
                return Collections.singletonMap(monsterId, new Enemy(monsterId, 30, 12, 3, 1));
            }
        };
    }

    @Test
    void disablingRemovesOnlyThatPacksContent() {
        ContentRegistry.clearForTests();
        ContentRegistry.register(pack("base", "base_potion", "base_goblin"));
        ContentRegistry.register(pack("horror", "horror_candle", "horror_wretch"));

        assertTrue(ContentRegistry.items().containsKey("base_potion"));
        assertTrue(ContentRegistry.items().containsKey("horror_candle"));
        assertTrue(ContentRegistry.isEnabled("horror"));

        assertTrue(ContentRegistry.setEnabled("horror", false));
        assertFalse(ContentRegistry.isEnabled("horror"));
        assertTrue(ContentRegistry.items().containsKey("base_potion"), "other pack unaffected");
        assertFalse(ContentRegistry.items().containsKey("horror_candle"), "disabled pack removed");
        assertFalse(ContentRegistry.monsters().containsKey("horror_wretch"));
        // pack still listed (known), just disabled
        assertTrue(ContentRegistry.packs().containsKey("horror"));
        assertTrue(ContentRegistry.disabledPackIds().contains("horror"));

        // re-enable restores it
        assertTrue(ContentRegistry.setEnabled("horror", true));
        assertTrue(ContentRegistry.items().containsKey("horror_candle"));
        assertTrue(ContentRegistry.monsters().containsKey("horror_wretch"));
    }

    @Test
    void togglingUnknownPackIsNoOp() {
        ContentRegistry.clearForTests();
        assertFalse(ContentRegistry.setEnabled("does-not-exist", false));
        assertFalse(ContentRegistry.isEnabled("does-not-exist"));
        assertFalse(ContentRegistry.isKnown("does-not-exist"));
    }
}
