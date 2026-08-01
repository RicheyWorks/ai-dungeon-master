package com.xai.dungeonmaster.controller;

import com.xai.dungeonmaster.DungeonMasterEngine;
import com.xai.dungeonmaster.auth.JwtService;
import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.service.AuthDependencyProbe;
import com.xai.dungeonmaster.service.GameInstanceService;
import com.xai.dungeonmaster.store.MemoryRedisOps;
import com.xai.dungeonmaster.store.UnusedDataSource;
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
        AuthDependencyProbe probe = new AuthDependencyProbe(
                new UnusedDataSource(), new MemoryRedisOps(), "memory", "memory");
        mvc = standaloneSetup(new HealthController(sessions, instances, probe)).build();
    }

    @Test
    void livenessUp() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("UP")))
                .andExpect(jsonPath("$.probe", equalTo("liveness")));
    }

    @Test
    void readinessUpWithDependencyMap() throws Exception {
        mvc.perform(get("/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("UP")))
                .andExpect(jsonPath("$.probe", equalTo("readiness")))
                .andExpect(jsonPath("$.engines", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.dependencies.jdbc.status", equalTo("NOT_CONFIGURED")))
                .andExpect(jsonPath("$.dependencies.redis.status", equalTo("NOT_CONFIGURED")));
    }

    @Test
    void readiness503WhenDependencyDown() throws Exception {
        SessionService sessions = new SessionService(new JwtService("health-test-secret-abcdefghijklmn", 3600));
        DungeonMasterEngine engine = new DungeonMasterEngine(4, 4, new String[]{"Kael"}, new String[]{"Warrior"});
        GameInstanceService instances = GameInstanceService.singleton(engine);
        AuthDependencyProbe deadRedis = new AuthDependencyProbe(
                new UnusedDataSource(),
                new com.xai.dungeonmaster.store.RedisOps() {
                    @Override public void hset(String key, java.util.Map<String, String> fields) {}
                    @Override public java.util.Map<String, String> hgetAll(String key) { return java.util.Map.of(); }
                    @Override public void sadd(String key, String... members) {}
                    @Override public void srem(String key, String... members) {}
                    @Override public java.util.Set<String> smembers(String key) { return java.util.Set.of(); }
                    @Override public void del(String key) {}
                    @Override public boolean ping() { return false; }
                    @Override public boolean isNetworked() { return true; }
                    @Override public void close() {}
                },
                "redis",
                "redis");
        MockMvc downMvc = standaloneSetup(new HealthController(sessions, instances, deadRedis)).build();
        downMvc.perform(get("/health/ready"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status", equalTo("DOWN")))
                .andExpect(jsonPath("$.dependencies.redis.status", equalTo("DOWN")));
    }

    @Test
    void v2HealthEnvelope() throws Exception {
        mvc.perform(get("/v2/health").header("X-Request-Id", "h1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", equalTo("health")))
                .andExpect(jsonPath("$.payload.status", equalTo("UP")))
                .andExpect(jsonPath("$.payload.uptimeSeconds", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.payload.dependencies", notNullValue()))
                .andExpect(jsonPath("$.payload.memory", notNullValue()))
                .andExpect(jsonPath("$.requestId", equalTo("h1")));
    }
}
