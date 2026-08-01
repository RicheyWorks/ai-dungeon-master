package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.auth.JwtService;
import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.service.GameInstanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class HealthControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SessionService sessions = new SessionService(new JwtService("health-test-secret-abcdefghijklmn", 3600));
        DungeonMasterEngine engine = new DungeonMasterEngine(4, 4, new String[]{"Kael"}, new String[]{"Warrior"});
        GameInstanceService instances = GameInstanceService.singleton(engine);
        mvc = standaloneSetup(new HealthController(sessions, instances)).build();
    }

    @Test
    void livenessUp() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("UP")))
                .andExpect(jsonPath("$.probe", equalTo("liveness")));
    }

    @Test
    void readinessUp() throws Exception {
        mvc.perform(get("/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("UP")))
                .andExpect(jsonPath("$.probe", equalTo("readiness")))
                .andExpect(jsonPath("$.engines", greaterThanOrEqualTo(0)));
    }

    @Test
    void v2HealthEnvelope() throws Exception {
        mvc.perform(get("/v2/health").header("X-Request-Id", "h1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", equalTo("health")))
                .andExpect(jsonPath("$.payload.status", equalTo("UP")))
                .andExpect(jsonPath("$.payload.uptimeSeconds", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.payload.memory", notNullValue()))
                .andExpect(jsonPath("$.requestId", equalTo("h1")));
    }
}
