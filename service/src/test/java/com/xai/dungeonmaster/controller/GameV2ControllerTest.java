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
 * MockMvc smoke test for the v2 API. Standalone setup (no Spring context, no
 * servlet container, no WebSocket wiring), mirroring GameRestControllerTest.
 */
class GameV2ControllerTest {

    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        DungeonMasterEngine engine = new DungeonMasterEngine(
                3, 3,
                new String[] { "Kael", "Lira" },
                new String[] { "Warrior", "Mage" });
        mvc = standaloneSetup(new GameV2Controller(engine)).build();
    }

    @Test
    void statusIsWrappedInTypedEnvelopeWithStructuredParty() throws Exception {
        mvc.perform(get("/v2/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("game_status"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.payload.party").isArray())
                .andExpect(jsonPath("$.payload.party[0].name").isNotEmpty())
                .andExpect(jsonPath("$.payload.party[0].hp").isNumber())
                .andExpect(jsonPath("$.payload.party[0].maxHp").isNumber())
                .andExpect(jsonPath("$.payload.party[0].statuses").isArray())
                .andExpect(jsonPath("$.payload.chaosLevel").isNumber())
                .andExpect(jsonPath("$.payload.availableChoices").isArray());
    }

    @Test
    void statusEchoesSuppliedRequestId() throws Exception {
        mvc.perform(get("/v2/status").header("X-Request-Id", "abc-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("abc-123"));
    }

    @Test
    void actionWithoutLabelReturnsErrorEnvelope() throws Exception {
        ActionRequest req = new ActionRequest(" ");
        mvc.perform(post("/v2/action")
                        .contentType("application/json")
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("error"))
                .andExpect(jsonPath("$.payload.message").isNotEmpty());
    }

    @Test
    void unknownChoiceReturnsErrorEnvelope() throws Exception {
        ActionRequest req = new ActionRequest("Pirouette");
        mvc.perform(post("/v2/action")
                        .contentType("application/json")
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("error"));
    }
}
