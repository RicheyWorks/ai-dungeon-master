package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.plugin.ContentRegistry;
import com.xai.dungeonmaster.plugin.DefaultContentPack;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Standalone MockMvc test for the catalog endpoint: it lists packs (with enabled
 * state), plugins, and narration, and lets a pack be toggled on/off.
 */
class CatalogControllerTest {

    private MockMvc mvc;

    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path packsDir;

    @BeforeEach
    void setUp() {
        ContentRegistry.clearForTests();
        ContentRegistry.register(new DefaultContentPack()); // "builtin"
        mvc = standaloneSetup(new CatalogController(
                new com.xai.dungeonmaster.service.PackUploadService(packsDir.toString()))).build();
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
                .andExpect(jsonPath("$.payload.contentPacks[?(@.id=='builtin')]").exists())
                .andExpect(jsonPath("$.payload.contentPacks[?(@.id=='builtin')].monsters").isNotEmpty())
                .andExpect(jsonPath("$.payload.contentPacks[?(@.id=='builtin')].enabled").value(Matchers.hasItem(true)))
                .andExpect(jsonPath("$.payload.plugins.itemEffects").value(Matchers.hasItem("HEAL")))
                .andExpect(jsonPath("$.payload.plugins.storefronts").value(Matchers.hasItem("dev")))
                .andExpect(jsonPath("$.payload.plugins.llmProviders").value(Matchers.hasItem("local-stub")))
                .andExpect(jsonPath("$.payload.narration.active").isNotEmpty());
    }

    @Test
    void disableThenEnableTogglesPackState() throws Exception {
        mvc.perform(post("/v2/catalog/packs/builtin/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("catalog"))
                .andExpect(jsonPath("$.payload.contentPacks[?(@.id=='builtin')].enabled").value(Matchers.hasItem(false)));

        mvc.perform(post("/v2/catalog/packs/builtin/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.contentPacks[?(@.id=='builtin')].enabled").value(Matchers.hasItem(true)));
    }

    @Test
    void togglingUnknownPackReturns404() throws Exception {
        mvc.perform(post("/v2/catalog/packs/does-not-exist/disable"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("error"));
    }

    @Test
    void uploadingPackZipInstallsAndReturnsCatalog() throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(out)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("pack.yaml"));
            zos.write("id: \"api-pack\"\ndisplayName: \"API Pack\"\nversion: \"1.0.0\"\n"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "api-pack.zip", "application/zip", out.toByteArray());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/v2/catalog/packs").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("catalog"))
                .andExpect(jsonPath("$.payload.contentPacks[?(@.id=='api-pack')]").exists());

        // Same id again without replace: conflict.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/v2/catalog/packs").file(file))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("error"));
    }

    @Test
    void uploadingGarbageReturns400() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "junk.zip", "application/zip", "not a zip".getBytes());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/v2/catalog/packs").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("error"));
    }
}
