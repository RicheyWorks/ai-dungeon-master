package com.xai.dungeonmaster.entitlement;

import com.xai.dungeonmaster.plugin.StorefrontRegistry;
import com.xai.dungeonmaster.plugin.builtin.DevStorefront;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Goal G7 — end-to-end dev storefront: sign receipt → verify → grant product.
 */
class StorefrontVerticalTest {

    @BeforeEach
    @AfterEach
    void reset() {
        StorefrontRegistry.clearForTests();
    }

    @Test
    void devReceiptGrantsProduct() {
        DevStorefront dev = new DevStorefront();
        StorefrontRegistry.register(dev);

        String sku = "pack_the_hollows";
        String receipt = dev.signReceipt(sku);
        assertTrue(dev.verifyReceipt(receipt));

        EntitlementService service = new EntitlementService();
        EntitlementService.Grant grant = service.verifyAndGrant("sess-g7", "dev", sku, receipt);
        assertTrue(grant.granted(), grant.reason());
        assertEquals(sku, grant.productId());

        // Idempotent re-submit for same session
        EntitlementService.Grant again = service.verifyAndGrant("sess-g7", "dev", sku, receipt);
        assertTrue(again.granted(), again.reason());
    }
}
