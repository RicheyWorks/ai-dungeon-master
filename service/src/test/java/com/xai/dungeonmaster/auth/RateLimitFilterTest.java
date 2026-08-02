package com.xai.dungeonmaster.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitFilterTest {

    private static RateLimitFilter filter(RateLimitProperties.Builder b) {
        return new RateLimitFilter(b.build());
    }

    @Test
    void allowsUnderLimitThen429() throws Exception {
        RateLimitFilter filter = filter(RateLimitProperties.builder().sessionPerMinute(3));
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v2/session");
        req.setRemoteAddr("10.0.0.5");

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertEquals(200, res.getStatus(), "request " + (i + 1));
            assertNotNull(res.getHeader("X-RateLimit-Limit"));
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(req, blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus());
        assertEquals("0", blocked.getHeader("X-RateLimit-Remaining"));
        assertNotNull(blocked.getHeader("Retry-After"));
        assertTrue(blocked.getContentAsString().contains("Rate limit exceeded"));
    }

    @Test
    void differentIpsHaveSeparateBuckets() throws Exception {
        RateLimitFilter filter = filter(RateLimitProperties.builder().sessionPerMinute(1));

        MockHttpServletRequest a = new MockHttpServletRequest("POST", "/v2/session");
        a.setRemoteAddr("1.1.1.1");
        MockHttpServletResponse ra = new MockHttpServletResponse();
        filter.doFilter(a, ra, new MockFilterChain());
        assertEquals(200, ra.getStatus());

        MockHttpServletRequest b = new MockHttpServletRequest("POST", "/v2/session");
        b.setRemoteAddr("2.2.2.2");
        MockHttpServletResponse rb = new MockHttpServletResponse();
        filter.doFilter(b, rb, new MockFilterChain());
        assertEquals(200, rb.getStatus());

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(a, blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus());
    }

    @Test
    void usesXForwardedFor() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
        req.setRemoteAddr("10.0.0.1");
        assertEquals("203.0.113.9", RateLimitFilter.clientIp(req));
    }

    @Test
    void disabledSkips() throws Exception {
        RateLimitFilter filter = filter(RateLimitProperties.builder().enabled(false).allBuckets(1));
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v2/session");
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertEquals(200, res.getStatus());
        }
    }

    @Test
    void metricsPathLimited() throws Exception {
        RateLimitFilter filter = filter(RateLimitProperties.builder().metricsPerMinute(2));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/metrics");
        req.setRemoteAddr("9.9.9.9");
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(req, blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus());
    }

    @Test
    void logoutBucketIndependentOfSession() throws Exception {
        RateLimitFilter filter = filter(RateLimitProperties.builder()
                .sessionPerMinute(100).logoutPerMinute(2));
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("DELETE", "/v2/session");
            req.setRemoteAddr("198.51.100.10");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertEquals(200, res.getStatus());
        }
        MockHttpServletRequest blockedReq = new MockHttpServletRequest("DELETE", "/v2/session");
        blockedReq.setRemoteAddr("198.51.100.10");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(blockedReq, blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus());
        assertNotNull(blocked.getHeader("Retry-After"));

        MockHttpServletRequest post = new MockHttpServletRequest("POST", "/v2/session");
        post.setRemoteAddr("198.51.100.10");
        MockHttpServletResponse postRes = new MockHttpServletResponse();
        filter.doFilter(post, postRes, new MockFilterChain());
        assertEquals(200, postRes.getStatus());
    }

    @Test
    void adminBucketLimitsBruteForce() throws Exception {
        RateLimitFilter filter = filter(RateLimitProperties.builder().adminPerMinute(2));
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v2/admin/receipts");
            req.setRemoteAddr("203.0.113.50");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertEquals(200, res.getStatus());
        }
        MockHttpServletRequest blockedReq = new MockHttpServletRequest("GET", "/v2/admin/session-packs");
        blockedReq.setRemoteAddr("203.0.113.50");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(blockedReq, blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus());
    }

    @Test
    void installBucketCoversMarketplaceAndUpload() throws Exception {
        RateLimitFilter filter = filter(RateLimitProperties.builder().installPerMinute(2));
        MockHttpServletRequest m1 = new MockHttpServletRequest("POST", "/v2/marketplace/cool-pack/install");
        m1.setRemoteAddr("198.51.100.77");
        MockHttpServletResponse r1 = new MockHttpServletResponse();
        filter.doFilter(m1, r1, new MockFilterChain());
        assertEquals(200, r1.getStatus());

        MockHttpServletRequest m2 = new MockHttpServletRequest("POST", "/v2/catalog/packs");
        m2.setRemoteAddr("198.51.100.77");
        MockHttpServletResponse r2 = new MockHttpServletResponse();
        filter.doFilter(m2, r2, new MockFilterChain());
        assertEquals(200, r2.getStatus());

        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/v2/marketplace/other/install");
        blockedReq.setRemoteAddr("198.51.100.77");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(blockedReq, blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus());
    }

    @Test
    void narrateBucketLimitsHttpNarration() throws Exception {
        RateLimitFilter filter = filter(RateLimitProperties.builder().narratePerMinute(2));
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v2/narrate");
            req.setRemoteAddr("203.0.113.88");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertEquals(200, res.getStatus());
        }
        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/v2/narrate");
        blockedReq.setRemoteAddr("203.0.113.88");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(blockedReq, blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus());
    }

    @Test
    void saveBucketCoversSaveLoadReset() throws Exception {
        RateLimitFilter filter = filter(RateLimitProperties.builder().savePerMinute(2));
        for (String path : new String[]{"/v2/save", "/v2/load"}) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
            req.setRemoteAddr("203.0.113.70");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertEquals(200, res.getStatus(), path);
        }
        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/v2/reset");
        blockedReq.setRemoteAddr("203.0.113.70");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(blockedReq, blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus());

        MockHttpServletRequest legacy = new MockHttpServletRequest("POST", "/api/game/save");
        legacy.setRemoteAddr("203.0.113.70");
        MockHttpServletResponse legacyRes = new MockHttpServletResponse();
        filter.doFilter(legacy, legacyRes, new MockFilterChain());
        assertEquals(429, legacyRes.getStatus());
    }
}
