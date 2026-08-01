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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class MetricsControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SessionService sessions = new SessionService(new JwtService("metrics-test-secret-abcdefghij", 3600));
        DungeonMasterEngine engine = new DungeonMasterEngine(4, 4, new String[]{"Kael"}, new String[]{"Warrior"});
        GameInstanceService instances = GameInstanceService.singleton(engine);
        AuthDependencyProbe probe = new AuthDependencyProbe(
                new UnusedDataSource(), new MemoryRedisOps(), "memory", "memory");
        mvc = standaloneSetup(new MetricsController(sessions, instances, probe)).build();
    }

    @Test
    void prometheusTextExposition() throws Exception {
        mvc.perform(get("/metrics").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("# TYPE dm_up gauge")))
                .andExpect(content().string(containsString("dm_up 1")))
                .andExpect(content().string(containsString("dm_ready 1")))
                .andExpect(content().string(containsString("dm_sessions_active")))
                .andExpect(content().string(containsString("dm_engines_active")))
                .andExpect(content().string(containsString("jvm_memory_bytes{area=\"heap\"")))
                .andExpect(content().string(containsString("dm_uptime_seconds")));
    }

    @Test
    void dependencyGaugeWhenRedisConfiguredDown() throws Exception {
        SessionService sessions = new SessionService(new JwtService("metrics-test-secret-abcdefghij", 3600));
        DungeonMasterEngine engine = new DungeonMasterEngine(4, 4, new String[]{"Kael"}, new String[]{"Warrior"});
        GameInstanceService instances = GameInstanceService.singleton(engine);
        AuthDependencyProbe dead = new AuthDependencyProbe(
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
        MockMvc down = standaloneSetup(new MetricsController(sessions, instances, dead)).build();
        down.perform(get("/metrics"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("dm_ready 0")))
                .andExpect(content().string(containsString("dm_dependency_up{name=\"redis\"} 0")));
    }
}
