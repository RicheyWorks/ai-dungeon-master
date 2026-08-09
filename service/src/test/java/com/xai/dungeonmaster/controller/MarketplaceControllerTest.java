package com.xai.dungeonmaster.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xai.dungeonmaster.auth.JwtAuthFilter;
import com.xai.dungeonmaster.auth.JwtService;
import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.plugin.ContentRegistry;
import com.xai.dungeonmaster.service.MarketplaceService;
import com.xai.dungeonmaster.service.PackUploadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class MarketplaceControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir Path tmp;
    private MockMvc mvc;
    private SessionService sessions;

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
        sessions = new SessionService(new JwtService("mkt-ctrl-test-secret-abcdefghijkl", 3600));
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

    @Test
    void asyncInstallJobIsOwnerOnly() throws Exception {
        SessionService.Session owner = sessions.createSession("Owner").session();
        SessionService.Session other = sessions.createSession("Other").session();

        MvcResult started = mvc.perform(post("/v2/marketplace/demo-pack/install")
                        .param("async", "true")
                        .requestAttr(JwtAuthFilter.SESSION_ATTR, owner))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.type", equalTo("marketplace_install_job")))
                .andExpect(jsonPath("$.payload.jobId").isNotEmpty())
                .andReturn();

        JsonNode root = MAPPER.readTree(started.getResponse().getContentAsString());
        String jobId = root.path("payload").path("jobId").asText();

        mvc.perform(get("/v2/marketplace/jobs/" + jobId)
                        .requestAttr(JwtAuthFilter.SESSION_ATTR, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.jobId", equalTo(jobId)));

        mvc.perform(get("/v2/marketplace/jobs/" + jobId)
                        .requestAttr(JwtAuthFilter.SESSION_ATTR, other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type", equalTo("error")));

        mvc.perform(delete("/v2/marketplace/jobs/" + jobId)
                        .requestAttr(JwtAuthFilter.SESSION_ATTR, other))
                .andExpect(status().isForbidden());

        // unauthenticated also forbidden when job has an owner
        mvc.perform(get("/v2/marketplace/jobs/" + jobId))
                .andExpect(status().isForbidden());
    }

    @Test
    void installAsyncPathReturns202Job() throws Exception {
        SessionService.Session owner = sessions.createSession("AsyncOwner").session();
        mvc.perform(post("/v2/marketplace/demo-pack/install-async")
                        .requestAttr(JwtAuthFilter.SESSION_ATTR, owner))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.type", equalTo("marketplace_install_job")))
                .andExpect(jsonPath("$.payload.jobId").isNotEmpty())
                .andExpect(jsonPath("$.payload.packId", equalTo("demo-pack")));
    }

    @Test
    void listJobsReturnsOnlyCallerOwned() throws Exception {
        SessionService.Session owner = sessions.createSession("Lister").session();
        SessionService.Session other = sessions.createSession("Stranger").session();

        mvc.perform(get("/v2/marketplace/jobs").requestAttr(JwtAuthFilter.SESSION_ATTR, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", equalTo("marketplace_install_jobs")))
                .andExpect(jsonPath("$.payload.count", equalTo(0)))
                .andExpect(jsonPath("$.payload.jobs").isArray());

        mvc.perform(post("/v2/marketplace/demo-pack/install-async")
                        .requestAttr(JwtAuthFilter.SESSION_ATTR, owner))
                .andExpect(status().isAccepted());

        mvc.perform(get("/v2/marketplace/jobs").requestAttr(JwtAuthFilter.SESSION_ATTR, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.count", equalTo(1)))
                .andExpect(jsonPath("$.payload.jobs[0].packId", equalTo("demo-pack")));

        mvc.perform(get("/v2/marketplace/jobs").requestAttr(JwtAuthFilter.SESSION_ATTR, other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.count", equalTo(0)));

        mvc.perform(get("/v2/marketplace/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.count", equalTo(0)));
    }
}
