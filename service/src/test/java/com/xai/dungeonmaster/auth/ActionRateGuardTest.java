package com.xai.dungeonmaster.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class ActionRateGuardTest {

    @Test
    void stompBudgetPerSession() {
        ActionRateGuard guard = new ActionRateGuard(new MemoryRateLimitStore(), true, 2);
        assertTrue(guard.check("a").allowed());
        assertTrue(guard.check("a").allowed());
        assertFalse(guard.check("a").allowed());
        assertTrue(guard.check("b").allowed());
    }

    @Test
    void httpActionBucket() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(true, 100, 100, 100, 100, 100, 2, 100, 100, 100);
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v2/action");
            req.setRemoteAddr("198.51.100.40");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertEquals(200, res.getStatus());
        }
        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/api/game/action");
        blockedReq.setRemoteAddr("198.51.100.40");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(blockedReq, blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus());
    }
}
