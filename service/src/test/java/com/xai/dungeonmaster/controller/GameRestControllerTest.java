package com.xai.dungeonmaster.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.dto.ActionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * REST API smoke test for GameRestController.
 *
 * Uses MockMvc in standalone mode — no Spring context, no embedded servlet
 * container, no WebSocket wiring. We construct the engine directly the same
 * way GameConfig would, then hand-build the controller around it. This keeps
 * the test fast and free of any Swing/WebSocket dependencies.
 */
class GameRestControllerTest {

    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        DungeonMasterEngine engine = new DungeonMasterEngine(
                3, 3,
                new String[] { "Kael", "Lira" },
                new String[] { "Warrior", "Mage" });
        mvc = standaloneSetup(new GameRestController(engine)).build();
    }

    @Test
    void statusEndpointReturnsParty() throws Exception {
        mvc.perform(get("/api/game/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partySummary").isNotEmpty())
                .andExpect(jsonPath("$.chaosLevel").isNumber())
                .andExpect(jsonPath("$.availableChoices").isArray());
    }

    @Test
    void choicesEndpointReturnsArray() throws Exception {
        mvc.perform(get("/api/game/choices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void actionWithoutLabelIsRejected() throws Exception {
        ActionRequest req = new ActionRequest(" ");
        mvc.perform(post("/api/game/action")
                        .contentType("application/json")
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownChoiceIsRejected() throws Exception {
        ActionRequest req = new ActionRequest("Pirouette");
        mvc.perform(post("/api/game/action")
                        .contentType("application/json")
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void startReturnsBanner() throws Exception {
        mvc.perform(post("/api/game/start"))
                .andExpect(status().isOk());
    }
}
