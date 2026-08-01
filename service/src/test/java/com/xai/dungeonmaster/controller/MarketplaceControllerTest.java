package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.plugin.ContentRegistry;
import com.xai.dungeonmaster.service.MarketplaceService;
import com.xai.dungeonmaster.service.PackUploadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class MarketplaceControllerTest {

    @TempDir Path tmp;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        ContentRegistry.clearForTests();
        Path packs = tmp.resolve("packs");
        Path pack = packs.resolve("demo-pack");
        Files.createDirectories(pack);
        Files.writeString(pack.resolve("pack.yaml"), """
                id: "demo-pack"
                displayName: "Demo Pack"
                version: "1.2.0"
                minEngineVersion: "1.0.0"
                description: "Controller test pack"
                """);
        Files.createDirectories(pack.resolve("items"));
        PackUploadService uploads = new PackUploadService(packs.toString());
        MarketplaceService svc = new MarketplaceService(packs, "", 0, uploads);
        mvc = standaloneSetup(new MarketplaceController(svc)).build();
    }

    @AfterEach
    void tearDown() {
        ContentRegistry.clearForTests();
    }

    @Test
    void listMarketplace() throws Exception {
        mvc.perform(get("/v2/marketplace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", equalTo("marketplace")))
                .andExpect(jsonPath("$.payload.available", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.payload.packs[0].id", equalTo("demo-pack")))
                .andExpect(jsonPath("$.payload.packs[0].source", equalTo("local")));
    }

    @Test
    void getAndInstall() throws Exception {
        mvc.perform(get("/v2/marketplace/demo-pack"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", equalTo("marketplace_pack")))
                .andExpect(jsonPath("$.payload.displayName", equalTo("Demo Pack")));

        mvc.perform(post("/v2/marketplace/demo-pack/install"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type", equalTo("marketplace_install")))
                .andExpect(jsonPath("$.payload.packId", equalTo("demo-pack")));
    }

    @Test
    void unknownPack404() throws Exception {
        mvc.perform(get("/v2/marketplace/nope"))
                .andExpect(status().isNotFound());
    }
}
