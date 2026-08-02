package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.entitlement.EntitlementService;
import com.xai.dungeonmaster.entitlement.MemoryReceiptLedger;
import com.xai.dungeonmaster.entitlement.ReceiptLedger;
import com.xai.dungeonmaster.plugin.StorefrontRegistry;
import com.xai.dungeonmaster.plugin.builtin.DevStorefront;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminReceiptsTest {

    private MockMvc mvc;
    private ReceiptLedger ledger;

    @BeforeEach
    void setUp() {
        StorefrontRegistry.clearForTests();
        StorefrontRegistry.register(new DevStorefront());
        ledger = new MemoryReceiptLedger();
        mvc = MockMvcBuilders.standaloneSetup(new AdminController(ledger, "ops-secret")).build();
    }

    @AfterEach
    void tearDown() {
        StorefrontRegistry.clearForTests();
    }

    @Test
    void disabledWithoutTokenConfig() throws Exception {
        MockMvc off = MockMvcBuilders.standaloneSetup(new AdminController(ledger, "")).build();
        off.perform(get("/v2/admin/receipts")).andExpect(status().isNotFound());
    }

    @Test
    void rejectsBadToken() throws Exception {
        mvc.perform(get("/v2/admin/receipts").header("X-Admin-Token", "nope"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listsRecentRedeems() throws Exception {
        EntitlementService svc = new EntitlementService(
                new com.xai.dungeonmaster.entitlement.InMemoryEntitlementStore(), ledger, true);
        String receipt = new DevStorefront().signReceipt("sku_gold");
        assert svc.verifyAndGrant("alice", "dev", "sku_gold", receipt).granted();

        mvc.perform(get("/v2/admin/receipts?limit=10")
                        .header("X-Admin-Token", "ops-secret")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("admin.receipts"))
                .andExpect(jsonPath("$.payload.count").value(1))
                .andExpect(jsonPath("$.payload.receipts", hasSize(1)))
                .andExpect(jsonPath("$.payload.receipts[0].sessionId").value("alice"))
                .andExpect(jsonPath("$.payload.receipts[0].productId").value("sku_gold"))
                .andExpect(jsonPath("$.payload.receipts[0].fingerprint").isString());
    }
}
