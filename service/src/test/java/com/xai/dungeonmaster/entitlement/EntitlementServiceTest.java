package com.xai.dungeonmaster.entitlement;

import com.xai.dungeonmaster.plugin.builtin.DevStorefront;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies receipt validation routes through the storefront plugin and grants (or denies) accordingly. */
class EntitlementServiceTest {

    @Test
    void validReceiptGrantsProduct() {
        EntitlementService svc = new EntitlementService();
        String receipt = new DevStorefront().signReceipt("sku_gold"); // same dev secret as the registered store
        EntitlementService.Grant g = svc.verifyAndGrant("sess-1", "dev", "sku_gold", receipt);
        assertTrue(g.granted(), "valid dev receipt should grant; reason=" + g.reason());
        assertTrue(svc.isEntitled("sess-1", "sku_gold"));
        assertTrue(svc.entitlements("sess-1").contains("sku_gold"));
    }

    @Test
    void forgedReceiptDenied() {
        EntitlementService svc = new EntitlementService();
        EntitlementService.Grant g = svc.verifyAndGrant("sess-2", "dev", "sku_gold", "forged-receipt");
        assertFalse(g.granted());
        assertFalse(svc.isEntitled("sess-2", "sku_gold"));
        assertTrue(g.reason().contains("verification"), g.reason());
    }

    @Test
    void unknownStorefrontDenied() {
        EntitlementService svc = new EntitlementService();
        EntitlementService.Grant g = svc.verifyAndGrant("sess-3", "nintendo-eshop", "sku", "x");
        assertFalse(g.granted());
        assertTrue(g.reason().contains("unknown storefront"), g.reason());
    }

    @Test
    void entitlementsAreIsolatedPerSession() {
        EntitlementService svc = new EntitlementService();
        String receipt = new DevStorefront().signReceipt("sku_a");
        svc.verifyAndGrant("alice", "dev", "sku_a", receipt);
        assertTrue(svc.isEntitled("alice", "sku_a"));
        assertFalse(svc.isEntitled("bob", "sku_a"), "bob must not inherit alice's purchase");
    }
}
