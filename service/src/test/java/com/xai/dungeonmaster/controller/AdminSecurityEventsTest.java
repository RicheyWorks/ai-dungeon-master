package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.auth.SecurityAudit;
import com.xai.dungeonmaster.entitlement.MemoryReceiptLedger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminSecurityEventsTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SecurityAudit.clearForTests();
        mvc = MockMvcBuilders.standaloneSetup(
                new AdminController(new MemoryReceiptLedger(), "ops-sec-secret")).build();
    }

    @AfterEach
    void tearDown() {
        SecurityAudit.clearForTests();
    }

    @Test
    void listRequiresToken() throws Exception {
        mvc.perform(get("/v2/admin/security-events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsRecentEvents() throws Exception {
        SecurityAudit.log("forbidden", "/v2/marketplace/jobs/x", "10.0.0.1", "rid-1", "owner_mismatch");
        SecurityAudit.log("rate_limited", "/v2/action", "10.0.0.2", "rid-2", "bucket=action");

        mvc.perform(get("/v2/admin/security-events")
                        .param("limit", "10")
                        .header("X-Admin-Token", "ops-sec-secret")
                        .header("X-Request-Id", "admin-sec-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", equalTo("admin.security_events")))
                .andExpect(jsonPath("$.payload.count", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.payload.events[0].outcome", equalTo("rate_limited")))
                .andExpect(jsonPath("$.payload.events[0].path", equalTo("/v2/action")))
                .andExpect(jsonPath("$.requestId", equalTo("admin-sec-1")));
    }
}
