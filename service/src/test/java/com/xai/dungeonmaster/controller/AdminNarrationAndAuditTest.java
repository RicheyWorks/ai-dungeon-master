package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.auth.AdminAudit;
import com.xai.dungeonmaster.entitlement.MemoryReceiptLedger;
import com.xai.dungeonmaster.plugin.LLMProviderRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminNarrationAndAuditTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        AdminAudit.clearForTests();
        LLMProviderRegistry.clearForTests();
        mvc = MockMvcBuilders.standaloneSetup(
                new AdminController(new MemoryReceiptLedger(), "ops-narr-secret")).build();
    }

    @AfterEach
    void tearDown() {
        AdminAudit.clearForTests();
        LLMProviderRegistry.clearForTests();
    }

    @Test
    void narrationRequiresToken() throws Exception {
        mvc.perform(get("/v2/admin/narration"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAndSetNarrationProvider() throws Exception {
        mvc.perform(get("/v2/admin/narration").header("X-Admin-Token", "ops-narr-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", equalTo("admin.narration")))
                .andExpect(jsonPath("$.payload.active").isNotEmpty())
                .andExpect(jsonPath("$.payload.available", hasItem("local-stub")));

        mvc.perform(post("/v2/admin/narration/provider")
                        .param("id", "local-stub")
                        .header("X-Admin-Token", "ops-narr-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.active", equalTo("local-stub")));

        mvc.perform(post("/v2/admin/narration/provider")
                        .param("id", "no-such-provider-xyz")
                        .header("X-Admin-Token", "ops-narr-secret"))
                .andExpect(status().isNotFound());
    }

    @Test
    void auditEventsList() throws Exception {
        AdminAudit.log("ok", "/v2/admin/sessions", "1.1.1.1", "r1", "count=1");
        mvc.perform(get("/v2/admin/audit-events")
                        .param("limit", "10")
                        .header("X-Admin-Token", "ops-narr-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", equalTo("admin.audit_events")))
                .andExpect(jsonPath("$.payload.count", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.payload.events[0].path", equalTo("/v2/admin/sessions")));
    }
}
