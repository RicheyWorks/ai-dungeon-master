package com.xai.dungeonmaster.entitlement;

import com.xai.dungeonmaster.plugin.StorefrontIntegration;
import com.xai.dungeonmaster.plugin.StorefrontRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EntitlementAfterGrantTest {

    @BeforeEach
    void setUp() {
        StorefrontRegistry.clearForTests();
    }

    @AfterEach
    void tearDown() {
        StorefrontRegistry.clearForTests();
    }

    @Test
    void afterGrantCalledOnSuccess() {
        AtomicInteger calls = new AtomicInteger();
        StorefrontRegistry.register(new StorefrontIntegration() {
            @Override public String id() { return "test_sf"; }
            @Override public String displayName() { return "test"; }
            @Override public boolean verifyReceipt(String receipt) { return "ok".equals(receipt); }
            @Override public void afterGrant(String productId, String receipt) {
                calls.incrementAndGet();
            }
            @Override public Identity currentIdentity() { return new Identity(null, "t", false); }
            @Override public void unlockAchievement(String achievementId) {}
            @Override public void submitLeaderboard(String boardId, long score) {}
            @Override public CloudSaveHandle openCloudSave(String slot) {
                return new CloudSaveHandle() {
                    @Override public byte[] read() { return new byte[0]; }
                    @Override public void write(byte[] data) {}
                    @Override public boolean isAvailable() { return false; }
                };
            }
            @Override public PurchaseFlow startPurchase(String productId) {
                return new PurchaseFlow() {
                    @Override public boolean isComplete() { return true; }
                    @Override public boolean wasSuccessful() { return false; }
                    @Override public String receipt() { return null; }
                };
            }
        });

        EntitlementService svc = new EntitlementService();
        EntitlementService.Grant g = svc.verifyAndGrant("sess-1", "test_sf", "sku_x", "ok");
        assertTrue(g.granted());
        assertEquals(1, calls.get());

        EntitlementService.Grant bad = svc.verifyAndGrant("sess-1", "test_sf", "sku_y", "nope");
        assertFalse(bad.granted());
        assertEquals(1, calls.get());
    }
}
