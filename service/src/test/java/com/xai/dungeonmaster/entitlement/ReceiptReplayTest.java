package com.xai.dungeonmaster.entitlement;

import com.xai.dungeonmaster.plugin.StorefrontRegistry;
import com.xai.dungeonmaster.plugin.builtin.DevStorefront;
import com.xai.dungeonmaster.store.MemoryRedisOps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReceiptReplayTest {

    @BeforeEach
    void setUp() {
        StorefrontRegistry.clearForTests();
        StorefrontRegistry.register(new DevStorefront());
    }

    @AfterEach
    void tearDown() {
        StorefrontRegistry.clearForTests();
    }

    @Test
    void secondSessionCannotReuseReceipt() {
        EntitlementService svc = new EntitlementService();
        String receipt = new DevStorefront().signReceipt("sku_gold");

        var g1 = svc.verifyAndGrant("alice", "dev", "sku_gold", receipt);
        assertTrue(g1.granted(), g1.reason());

        var g2 = svc.verifyAndGrant("bob", "dev", "sku_gold", receipt);
        assertFalse(g2.granted());
        assertTrue(g2.reason().contains("already redeemed"), g2.reason());
        assertFalse(svc.isEntitled("bob", "sku_gold"));
    }

    @Test
    void sameSessionIsIdempotent() {
        EntitlementService svc = new EntitlementService();
        String receipt = new DevStorefront().signReceipt("sku_gold");

        assertTrue(svc.verifyAndGrant("alice", "dev", "sku_gold", receipt).granted());
        var again = svc.verifyAndGrant("alice", "dev", "sku_gold", receipt);
        assertTrue(again.granted(), again.reason());
        assertTrue(again.reason().contains("idempotent") || again.reason().equals("granted"), again.reason());
    }

    @Test
    void redisLedgerSharedAcrossNodes() {
        MemoryRedisOps redis = new MemoryRedisOps();
        ReceiptLedger ledger = new RedisReceiptLedger(redis, "dm", 3600);
        EntitlementService a = new EntitlementService(new InMemoryEntitlementStore(), ledger, true);
        EntitlementService b = new EntitlementService(new InMemoryEntitlementStore(), ledger, true);

        String receipt = new DevStorefront().signReceipt("sku_pass");
        assertTrue(a.verifyAndGrant("s1", "dev", "sku_pass", receipt).granted());
        var replay = b.verifyAndGrant("s2", "dev", "sku_pass", receipt);
        assertFalse(replay.granted());
        assertTrue(replay.reason().contains("already redeemed"), replay.reason());
    }

    @Test
    void fingerprintStable() {
        String a = ReceiptLedger.fingerprint("dev", "sku", "r1");
        String b = ReceiptLedger.fingerprint("DEV", "sku", "r1");
        assertEquals(a, b);
        assertNotEquals(a, ReceiptLedger.fingerprint("dev", "sku", "r2"));
    }
}
