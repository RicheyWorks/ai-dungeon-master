package com.xai.dungeonmaster.entitlement;

import com.xai.dungeonmaster.plugin.builtin.DevStorefront;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Entitlement store persistence + multi-instance visibility. The file store
 * must share grants across two store instances on the same path (simulating
 * multi-process / shared-volume multi-node).
 */
class EntitlementStoreTest {

    @Test
    void inMemoryIsIsolatedPerInstance() {
        InMemoryEntitlementStore a = new InMemoryEntitlementStore();
        InMemoryEntitlementStore b = new InMemoryEntitlementStore();
        a.grant("sess", "sku_a");
        assertTrue(a.owns("sess", "sku_a"));
        assertFalse(b.owns("sess", "sku_a"), "in-memory stores must not share state");
    }

    @Test
    void fileStorePersistsAcrossReopen(@TempDir Path tmp) {
        Path file = tmp.resolve("entitlements.json");
        FileEntitlementStore store = new FileEntitlementStore(file);
        store.grant("alice", "sku_gold");
        store.grant("alice", "sku_skin");

        FileEntitlementStore reopened = new FileEntitlementStore(file);
        Set<String> owned = reopened.products("alice");
        assertTrue(owned.contains("sku_gold"));
        assertTrue(owned.contains("sku_skin"));
        assertEquals(2, owned.size());
    }

    @Test
    void twoInstancesShareGrantsOnSameFile(@TempDir Path tmp) {
        Path file = tmp.resolve("entitlements.json");
        FileEntitlementStore nodeA = new FileEntitlementStore(file);
        FileEntitlementStore nodeB = new FileEntitlementStore(file);

        nodeA.grant("sess-1", "sku_gold");
        // nodeB must see nodeA's grant without being reconstructed.
        assertTrue(nodeB.owns("sess-1", "sku_gold"),
                "shared file store must be visible across instances (multi-node)");

        nodeB.grant("sess-1", "sku_skin");
        assertTrue(nodeA.owns("sess-1", "sku_skin"));
        assertEquals(Set.of("sku_gold", "sku_skin"), nodeA.products("sess-1"));
    }

    @Test
    void serviceWithFileStoreSurvivesRestart(@TempDir Path tmp) {
        Path file = tmp.resolve("entitlements.json");
        String receipt = new DevStorefront().signReceipt("sku_gold");

        EntitlementService before = new EntitlementService(new FileEntitlementStore(file));
        EntitlementService.Grant g = before.verifyAndGrant("sess-x", "dev", "sku_gold", receipt);
        assertTrue(g.granted(), g.reason());

        EntitlementService after = new EntitlementService(new FileEntitlementStore(file));
        assertTrue(after.isEntitled("sess-x", "sku_gold"),
                "granted product must survive service restart with file store");
    }

    @Test
    void grantIsIdempotent(@TempDir Path tmp) {
        FileEntitlementStore store = new FileEntitlementStore(tmp.resolve("e.json"));
        store.grant("s", "sku");
        store.grant("s", "sku");
        assertEquals(1, store.products("s").size());
    }
}
