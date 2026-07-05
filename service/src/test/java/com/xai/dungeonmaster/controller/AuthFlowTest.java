package com.xai.dungeonmaster.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.auth.JwtAuthFilter;
import com.xai.dungeonmaster.auth.JwtService;
import com.xai.dungeonmaster.auth.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * End-to-end auth flow over MockMvc in standalone mode (mirroring the other
 * controller tests): the real JwtAuthFilter guards real controllers with
 * enforcement enabled.
 */
class AuthFlowTest {

    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        JwtService jwt = new JwtService("auth-flow-test-secret-abcdefghijklmnop", 3600);
        SessionService sessions = new SessionService(jwt);
        DungeonMasterEngine engine = new DungeonMasterEngine(
                3, 3, new String[] { "Kael" }, new String[] { "Warrior" });
        mvc = standaloneSetup(new SessionController(sessions), new GameV2Controller(engine))
                .addFilters(new JwtAuthFilter(jwt, sessions, true)) // enforcement ON
                .build();
    }

    @Test
    void protectedEndpointRejectsMissingToken() throws Exception {
        mvc.perform(get("/v2/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("error"));
    }

    @Test
    void garbageTokenIsRejected() throws Exception {
        mvc.perform(get("/v2/status").header("Authorization", "Bearer not.a.real.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginIsPublicAndReturnsToken() throws Exception {
        mvc.perform(post("/v2/session").contentType(APPLICATION_JSON).content("{\"displayName\":\"Kael\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("session"))
                .andExpect(jsonPath("$.payload.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.payload.token").isNotEmpty())
                .andExpect(jsonPath("$.payload.displayName").value("Kael"));
    }

    @Test
    void tokenFromLoginUnlocksProtectedEndpoints() throws Exception {
        String body = mvc.perform(post("/v2/session").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(body).path("payload").path("token").asText();
        assertFalse(token.isBlank(), "login should return a non-blank token");

        mvc.perform(get("/v2/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("game_status"));

        mvc.perform(get("/v2/session/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("session"))
                .andExpect(jsonPath("$.payload.displayName").value("Adventurer"));
    }
}
