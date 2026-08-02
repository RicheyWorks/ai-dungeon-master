package com.xai.dungeonmaster.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitMetricsTest {

    @Test
    void filterIncrementsRejectedAndAllowed() throws Exception {
        RateLimitMetrics metrics = new RateLimitMetrics();
        RateLimitFilter filter = new RateLimitFilter(
                new MemoryRateLimitStore(), metrics, true,
                2, 20, 30, 15, 20, 60, 30, 100, 100, 100);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v2/session");
        req.setRemoteAddr("10.0.0.9");
        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertEquals(200, res.getStatus());
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(req, blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus());

        assertEquals(2L, metrics.allowedSnapshot().get("session"));
        assertEquals(1L, metrics.rejectedSnapshot().get("session"));
    }

    @Test
    void stompNarrateUsesNarrateStompBucket() {
        RateLimitMetrics metrics = new RateLimitMetrics();
        NarrationRateGuard guard = new NarrationRateGuard(new MemoryRateLimitStore(), metrics, true, 1);
        assertTrue(guard.check("s").allowed());
        assertFalse(guard.check("s").allowed());
        assertEquals(1L, metrics.allowedSnapshot().get("narrate_stomp"));
        assertEquals(1L, metrics.rejectedSnapshot().get("narrate_stomp"));
    }
}
