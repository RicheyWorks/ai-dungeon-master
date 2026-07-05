package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.plugin.ContentRegistry;
import com.xai.dungeonmaster.plugin.DefaultContentPack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Standalone MockMvc test for the catalog endpoint (mirrors the other controller
 * tests). Registers the bundled content pack, then asserts /v2/catalog surfaces
 * it plus the ServiceLoader-discovered plugins and active narration provider.
 */
class CatalogControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ContentRegistry.clearForTests();
        ContentRegistry.register(new DefaultContentPack()); // items.json + monsters.json
        mvc = standaloneSetup(new CatalogController()).build();
    }

    @AfterEach
    void reset() {
        ContentRegistry.clearForTests();
    }

    @Test
    void catalogListsPacksPluginsAndNarration() throws Exception {
        mvc.perform(get("/v2/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("catalog"))
                .andExpect(jsonPath("$.version").value(1))
                // content packs
                .andExpect(jsonPath("$.payload.contentPacks").isArray())
                .andExpect(jsonPath("$.payload.contentPacks[?(@.id=='builtin')]").exists())
                .andExpect(jsonPath("$.payload.contentPacks[?(@.id=='builtin')].monsters").isNotEmpty())
                .andExpect(jsonPath("$.payload.contentPacks[?(@.id=='builtin')].items").isNotEmpty())
                // plugins per SPI (bundled built-ins)
                .andExpect(jsonPath("$.payload.plugins.spellEffects").value(org.hamcrest.Matchers.hasItem("VOID_BOLT")))
                .andExpect(jsonPath("$.payload.plugins.itemEffects").value(org.hamcrest.Matchers.hasItem("HEAL")))
                .andExpect(jsonPath("$.payload.plugins.encounterBiomes").value(org.hamcrest.Matchers.hasItem("default")))
                .andExpect(jsonPath("$.payload.plugins.lootBiomes").value(org.hamcrest.Matchers.hasItem("default")))
                .andExpect(jsonPath("$.payload.plugins.questScripts").value(org.hamcrest.Matchers.hasItem("default")))
                .andExpect(jsonPath("$.payload.plugins.storefronts").value(org.hamcrest.Matchers.hasItem("none")))
                .andExpect(jsonPath("$.payload.plugins.llmProviders").value(org.hamcrest.Matchers.hasItem("local-stub")))
                .andExpect(jsonPath("$.payload.plugins.llmProviders").value(org.hamcrest.Matchers.hasItem("openai")))
                // narration
                .andExpect(jsonPath("$.payload.narration.active").isNotEmpty())
                .andExpect(jsonPath("$.payload.narration.available").isArray());
    }
}
