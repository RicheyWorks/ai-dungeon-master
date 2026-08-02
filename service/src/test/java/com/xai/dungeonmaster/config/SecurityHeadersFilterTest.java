package com.xai.dungeonmaster.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class SecurityHeadersFilterTest {

    @Test
    void addsBaselineHeaders() throws Exception {
        SecurityHeadersFilter filter = new SecurityHeadersFilter(true, false, 0, "DENY", "no-referrer");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/v2/health"), res, new MockFilterChain());
        assertEquals("nosniff", res.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", res.getHeader("X-Frame-Options"));
        assertEquals("no-referrer", res.getHeader("Referrer-Policy"));
        assertNotNull(res.getHeader("Content-Security-Policy"));
        assertNull(res.getHeader("Strict-Transport-Security"));
    }

    @Test
    void hstsWhenSecureAndEnabled() throws Exception {
        SecurityHeadersFilter filter = new SecurityHeadersFilter(true, true, 3600, "DENY", "no-referrer");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        req.addHeader("X-Forwarded-Proto", "https");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        assertEquals("max-age=3600; includeSubDomains", res.getHeader("Strict-Transport-Security"));
    }

    @Test
    void disabledSkips() throws Exception {
        SecurityHeadersFilter filter = new SecurityHeadersFilter(false, true, 3600, "DENY", "no-referrer");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/"), res, new MockFilterChain());
        assertNull(res.getHeader("X-Content-Type-Options"));
    }
}
