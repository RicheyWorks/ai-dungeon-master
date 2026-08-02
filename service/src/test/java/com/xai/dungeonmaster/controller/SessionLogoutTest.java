package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.auth.InMemorySessionStore;
import com.xai.dungeonmaster.auth.JwtAuthFilter;
import com.xai.dungeonmaster.auth.JwtService;
import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.content.MemorySessionPackStore;
import com.xai.dungeonmaster.content.SessionPackService;
import com.xai.dungeonmaster.plugin.ContentPack;
import com.xai.dungeonmaster.plugin.ContentRegistry;
import com.xai.dungeonmaster.service.SessionLogoutService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SessionLogoutTest {

    private SessionService sessions;
    private SessionPackService packs;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ContentRegistry.clearForTests();
        ContentRegistry.register(new Pack("dlc"));
        ContentRegistry.setEnabled("dlc", false);
        sessions = new SessionService(
                new JwtService("test-secret-at-least-32-characters!!", 3600L),
                new InMemorySessionStore());
        packs = new SessionPackService(new MemorySessionPackStore(), true);
        SessionLogoutService logout = new SessionLogoutService(sessions, packs, null);
        mvc = MockMvcBuilders.standaloneSetup(new SessionController(sessions, logout)).build();
    }

    @AfterEach
    void tearDown() {
        ContentRegistry.clearForTests();
    }

    @Test
    void logoutRequiresAuth() throws Exception {
        mvc.perform(delete("/v2/session")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsSessionAndPacks() throws Exception {
        SessionService.Issued issued = sessions.createSession("Hero");
        String id = issued.session().id();
        packs.setEnabled(id, "dlc", true);
        assertTrue(packs.isEnabled(id, "dlc"));

        mvc.perform(delete("/v2/session")
                        .requestAttr(JwtAuthFilter.SESSION_ATTR, issued.session())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("session.logout"))
                .andExpect(jsonPath("$.payload.loggedOut").value(true));

        assertTrue(sessions.find(id).isEmpty());
        assertFalse(packs.isEnabled(id, "dlc"));
    }

    private static final class Pack implements ContentPack {
        private final String id;
        Pack(String id) { this.id = id; }
        @Override public String id() { return id; }
        @Override public String displayName() { return id; }
        @Override public String version() { return "1"; }
        @Override public Map<String, com.xai.dungeonmaster.Item> items() { return Collections.emptyMap(); }
        @Override public Map<String, com.xai.dungeonmaster.Enemy> monsters() { return Collections.emptyMap(); }
    }
}
