package com.xai.dungeonmaster.auth;

import com.xai.dungeonmaster.store.MemoryRedisOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RedisRateLimitStoreTest {

    @Test
    void sharedCounterAcrossStoreInstances() {
        MemoryRedisOps redis = new MemoryRedisOps();
        RateLimitStore a = new RedisRateLimitStore(redis, "dm");
        RateLimitStore b = new RedisRateLimitStore(redis, "dm");

        assertEquals(1, a.hit("session|1.2.3.4").count());
        assertEquals(2, b.hit("session|1.2.3.4").count());
        assertEquals(3, a.hit("session|1.2.3.4").count());
        // different key
        assertEquals(1, b.hit("session|9.9.9.9").count());
    }

    @Test
    void filterUsesInjectedStore() throws Exception {
        MemoryRedisOps redis = new MemoryRedisOps();
        RateLimitStore store = new RedisRateLimitStore(redis, "t");
        RateLimitFilter filter = new RateLimitFilter(store, new RateLimitMetrics(), true, 2, 20, 30, 15, 20, 100, 100, 100);

        org.springframework.mock.web.MockHttpServletRequest req =
                new org.springframework.mock.web.MockHttpServletRequest("POST", "/v2/session");
        req.setRemoteAddr("10.0.0.1");

        filter.doFilter(req, new org.springframework.mock.web.MockHttpServletResponse(),
                new org.springframework.mock.web.MockFilterChain());
        filter.doFilter(req, new org.springframework.mock.web.MockHttpServletResponse(),
                new org.springframework.mock.web.MockFilterChain());
        org.springframework.mock.web.MockHttpServletResponse blocked =
                new org.springframework.mock.web.MockHttpServletResponse();
        filter.doFilter(req, blocked, new org.springframework.mock.web.MockFilterChain());
        assertEquals(429, blocked.getStatus());
    }
}
