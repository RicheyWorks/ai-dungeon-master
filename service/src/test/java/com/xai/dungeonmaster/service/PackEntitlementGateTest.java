package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.entitlement.EntitlementService;
import com.xai.dungeonmaster.plugin.ContentPack;
import com.xai.dungeonmaster.plugin.ContentRegistry;
import com.xai.dungeonmaster.plugin.StorefrontRegistry;
import com.xai.dungeonmaster.plugin.builtin.DevStorefront;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PackEntitlementGateTest {

    @BeforeEach
    void setUp() {
        ContentRegistry.clearForTests();
        StorefrontRegistry.clearForTests();
        StorefrontRegistry.register(new DevStorefront());
        ContentRegistry.register(new GatedPack("paid-pack", List.of("pack_the_hollows"), false));
        ContentRegistry.register(new GatedPack("free-pack", List.of(), false));
    }

    @AfterEach
    void tearDown() {
        ContentRegistry.clearForTests();
        StorefrontRegistry.clearForTests();
    }

    @Test
    void freePackAlwaysAllowed() {
        PackEntitlementGate gate = new PackEntitlementGate(new EntitlementService());
        assertNull(gate.denyReason(null, "free-pack"));
        assertTrue(gate.isEntitled(null, "free-pack"));
    }

    @Test
    void gatedPackRequiresAuthAndSku() {
        EntitlementService ents = new EntitlementService();
        PackEntitlementGate gate = new PackEntitlementGate(ents);

        assertTrue(gate.denyReason(null, "paid-pack").startsWith("Authentication"));
        assertTrue(gate.denyReason("sess", "paid-pack").contains("Requires one of"));

        // grant via dev storefront
        String receipt = new DevStorefront().signReceipt("pack_the_hollows");
        var g = ents.verifyAndGrant("sess", "dev", "pack_the_hollows", receipt);
        assertTrue(g.granted(), g.reason());
        assertNull(gate.denyReason("sess", "paid-pack"));
    }

    @Test
    void gatesCanBeDisabled() {
        PackEntitlementGate gate = new PackEntitlementGate(new EntitlementService(), false);
        assertFalse(gate.isGated("paid-pack"));
        assertNull(gate.denyReason(null, "paid-pack"));
    }

    private static final class GatedPack implements ContentPack {
        private final String id;
        private final List<String> required;
        private final boolean all;

        GatedPack(String id, List<String> required, boolean all) {
            this.id = id;
            this.required = required;
            this.all = all;
        }

        @Override public String id() { return id; }
        @Override public String displayName() { return id; }
        @Override public String version() { return "1.0.0"; }
        @Override public List<String> requiredProductIds() { return required; }
        @Override public boolean requireAllProducts() { return all; }
        @Override public Map<String, com.xai.dungeonmaster.Item> items() { return Collections.emptyMap(); }
        @Override public Map<String, com.xai.dungeonmaster.Enemy> monsters() { return Collections.emptyMap(); }
    }
}
