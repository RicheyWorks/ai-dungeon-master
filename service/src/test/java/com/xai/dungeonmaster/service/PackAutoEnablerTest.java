package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.entitlement.EntitlementService;
import com.xai.dungeonmaster.entitlement.InMemoryEntitlementStore;
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

class PackAutoEnablerTest {

    @BeforeEach
    void setUp() {
        ContentRegistry.clearForTests();
        StorefrontRegistry.clearForTests();
        StorefrontRegistry.register(new DevStorefront());
        ContentRegistry.register(new Gated("black-hollows", List.of("pack_the_hollows")));
        ContentRegistry.setEnabled("black-hollows", false);
    }

    @AfterEach
    void tearDown() {
        ContentRegistry.clearForTests();
        StorefrontRegistry.clearForTests();
    }

    @Test
    void grantEnablesMatchingPack() {
        InMemoryEntitlementStore store = new InMemoryEntitlementStore();
        PackAutoEnabler enabler = new PackAutoEnabler(store);
        EntitlementService ents = new EntitlementService(store, new com.xai.dungeonmaster.entitlement.MemoryReceiptLedger(), enabler);

        assertFalse(ContentRegistry.isEnabled("black-hollows"));
        String receipt = new DevStorefront().signReceipt("pack_the_hollows");
        var g = ents.verifyAndGrant("sess", "dev", "pack_the_hollows", receipt);
        assertTrue(g.granted(), g.reason());
        assertEquals(List.of("black-hollows"), g.enabledPacks());
        assertTrue(ContentRegistry.isEnabled("black-hollows"));
    }

    @Test
    void disabledFlagSkipsEnable() {
        InMemoryEntitlementStore store = new InMemoryEntitlementStore();
        PackAutoEnabler enabler = new PackAutoEnabler(store, false);
        EntitlementService ents = new EntitlementService(store, new com.xai.dungeonmaster.entitlement.MemoryReceiptLedger(), enabler);
        String receipt = new DevStorefront().signReceipt("pack_the_hollows");
        var g = ents.verifyAndGrant("sess", "dev", "pack_the_hollows", receipt);
        assertTrue(g.granted());
        assertTrue(g.enabledPacks().isEmpty());
        assertFalse(ContentRegistry.isEnabled("black-hollows"));
    }

    private static final class Gated implements ContentPack {
        private final String id;
        private final List<String> required;
        Gated(String id, List<String> required) {
            this.id = id;
            this.required = required;
        }
        @Override public String id() { return id; }
        @Override public String displayName() { return id; }
        @Override public String version() { return "1.0.0"; }
        @Override public List<String> requiredProductIds() { return required; }
        @Override public Map<String, com.xai.dungeonmaster.Item> items() { return Collections.emptyMap(); }
        @Override public Map<String, com.xai.dungeonmaster.Enemy> monsters() { return Collections.emptyMap(); }
    }
}
