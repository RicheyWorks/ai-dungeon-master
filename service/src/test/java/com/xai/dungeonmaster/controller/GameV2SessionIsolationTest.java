package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.auth.JwtAuthFilter;
import com.xai.dungeonmaster.auth.JwtService;
import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.service.GameEngineFactory;
import com.xai.dungeonmaster.service.GameInstanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Authenticated v2 clients must not share world state through the REST API.
 */
class GameV2SessionIsolationTest {

    private MockMvc mvc;
    private SessionService.Session alice;
    private SessionService.Session bob;
    private GameInstanceService games;

    @BeforeEach
    void setUp(@TempDir Path tmp) {
        GameEngineFactory factory = new GameEngineFactory(
                3, 3,
                new String[]{"Kael"}, new String[]{"Warrior"},
                "", "local-stub", 4000, null);
        games = new GameInstanceService(factory, factory.createDefault(), tmp);
        mvc = standaloneSetup(new GameV2Controller(games)).build();

        SessionService sessions = new SessionService(new JwtService("isolation-secret-abcdefghij", 3600));
        alice = sessions.createSession("Alice").session();
        bob = sessions.createSession("Bob").session();
    }

    @Test
    void twoSessionsHaveIndependentStatus() throws Exception {
        // Touch both engines so they exist.
        mvc.perform(get("/v2/status").requestAttr(JwtAuthFilter.SESSION_ATTR, alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("game_status"));
        mvc.perform(get("/v2/status").requestAttr(JwtAuthFilter.SESSION_ATTR, bob))
                .andExpect(status().isOk());

        assertEquals(2, games.sessionCount());
    }

    @Test
    void saveAndLoadEndpointsWorkForSession(@TempDir Path tmp) throws Exception {
        // Re-bind saves to temp (already set in setUp).
        mvc.perform(post("/v2/save").requestAttr(JwtAuthFilter.SESSION_ATTR, alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("game_save"))
                .andExpect(jsonPath("$.payload.saved").value(true))
                .andExpect(jsonPath("$.payload.sessionScoped").value(true));

        mvc.perform(post("/v2/load").requestAttr(JwtAuthFilter.SESSION_ATTR, alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("game_status"));
    }

    @Test
    void resetReturnsFreshStatus() throws Exception {
        mvc.perform(post("/v2/reset").requestAttr(JwtAuthFilter.SESSION_ATTR, alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("game_status"))
                .andExpect(jsonPath("$.payload.party").isArray());
    }

    @Test
    void loadWithoutSaveReturnsError() throws Exception {
        // Bob never saved.
        mvc.perform(post("/v2/load").requestAttr(JwtAuthFilter.SESSION_ATTR, bob))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("error"));
    }

    private static void assertEquals(int expected, int actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
