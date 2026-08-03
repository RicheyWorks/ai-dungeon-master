package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.auth.JwtService;
import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.entitlement.MemoryReceiptLedger;
import com.xai.dungeonmaster.service.GameInstanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminSessionsTest {

    private MockMvc mvc;
    private SessionService sessions;

    @BeforeEach
    void setUp() {
        sessions = new SessionService(new JwtService("admin-sessions-test-secret-abcdefgh", 3600));
        DungeonMasterEngine engine = new DungeonMasterEngine(4, 4, new String[]{"Kael"}, new String[]{"Warrior"});
        GameInstanceService instances = GameInstanceService.singleton(engine);
        mvc = MockMvcBuilders.standaloneSetup(new AdminController(
                new MemoryReceiptLedger(), null, sessions, instances, "ops-sessions-secret")).build();
    }

    @Test
    void listAndRevoke() throws Exception {
        SessionService.Session a = sessions.createSession("Alice").session();
        SessionService.Session b = sessions.createSession("Bob").session();

        mvc.perform(get("/v2/admin/sessions").header("X-Admin-Token", "ops-sessions-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", equalTo("admin.sessions")))
                .andExpect(jsonPath("$.payload.total", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.payload.count", greaterThanOrEqualTo(2)));

        mvc.perform(delete("/v2/admin/sessions/" + a.id())
                        .header("X-Admin-Token", "ops-sessions-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", equalTo("admin.session.revoked")))
                .andExpect(jsonPath("$.payload.revoked", equalTo(true)))
                .andExpect(jsonPath("$.payload.existed", equalTo(true)))
                .andExpect(jsonPath("$.payload.sessionId", equalTo(a.id())));

        org.junit.jupiter.api.Assertions.assertTrue(sessions.find(a.id()).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(sessions.find(b.id()).isPresent());
    }

    @Test
    void revokeRequiresToken() throws Exception {
        SessionService.Session a = sessions.createSession("X").session();
        mvc.perform(delete("/v2/admin/sessions/" + a.id()))
                .andExpect(status().isUnauthorized());
    }
}
