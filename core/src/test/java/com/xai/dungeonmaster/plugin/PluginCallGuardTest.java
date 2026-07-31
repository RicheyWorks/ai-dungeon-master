package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.Adventurer;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runtime plugin call timeout: a hung ItemEffect must not freeze dispatch —
 * {@link PluginCallGuard} returns a fizzle message after the wall timeout.
 */
class PluginCallGuardTest {

    @AfterEach
    void cleanup() {
        System.clearProperty(PluginCallGuard.PROP_TIMEOUT_MS);
        System.clearProperty(PluginCallGuard.PROP_ENABLED);
        ItemEffectRegistry.clearForTests();
    }

    @Test
    void fastCallReturnsResult() {
        String r = PluginCallGuard.run("FAST", () -> "ok");
        assertEquals("ok", r);
    }

    @Test
    void timeoutReturnsFizzleMessage() {
        System.setProperty(PluginCallGuard.PROP_TIMEOUT_MS, "80");
        long start = System.currentTimeMillis();
        String r = PluginCallGuard.run("SLOW", () -> {
            Thread.sleep(5_000);
            return "should-not-return";
        });
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(r.contains("timed out"), "expected timeout fizzle, got: " + r);
        assertTrue(elapsed < 2_000, "guard should return near the timeout, elapsed=" + elapsed);
    }

    @Test
    void thrownExceptionIsCaught() {
        String r = PluginCallGuard.run("BOOM", () -> {
            throw new IllegalStateException("boom");
        });
        assertTrue(r.contains("IllegalStateException"), r);
    }

    @Test
    void itemDispatchUsesGuardAgainstHang() {
        System.setProperty(PluginCallGuard.PROP_TIMEOUT_MS, "80");
        ItemEffectRegistry.register(new ItemEffect() {
            @Override public String id() { return "HANG_POTION"; }
            @Override
            public String execute(DungeonMasterEngine engine, Adventurer user, Item item) {
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "never";
            }
        });
        Item potion = new Item("Hang Potion", "A cursed flask.", Item.ItemType.CONSUMABLE,
                Item.Rarity.COMMON, "HANG_POTION", 0);
        DungeonMasterEngine engine = new DungeonMasterEngine(
                3, 3, new String[]{"Kael"}, new String[]{"Warrior"});
        Adventurer user = new Adventurer("Kael", 12, 12, 12, 12, 12, 12, 100, "Warrior");
        String result = ItemEffectRegistry.dispatch(engine, user, potion);
        assertTrue(result.contains("timed out") || result.contains("fizzles"),
                "hung item effect must be cut short: " + result);
    }

    @Test
    void guardCanBeDisabled() {
        System.setProperty(PluginCallGuard.PROP_ENABLED, "false");
        String r = PluginCallGuard.run("X", () -> "direct");
        assertEquals("direct", r);
    }
}
