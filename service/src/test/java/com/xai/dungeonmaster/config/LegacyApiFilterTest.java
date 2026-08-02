package com.xai.dungeonmaster.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class LegacyApiFilterTest {

    @Test
    void enabledPassesThrough() throws Exception {
        LegacyApiFilter f = new LegacyApiFilter(true);
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(new MockHttpServletRequest("GET", "/api/game/status"), res, new MockFilterChain());
        assertEquals(200, res.getStatus());
    }

    @Test
    void disabledReturns410() throws Exception {
        LegacyApiFilter f = new LegacyApiFilter(false);
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(new MockHttpServletRequest("GET", "/api/game/status"), res, new MockFilterChain());
        assertEquals(410, res.getStatus());
        assertTrue(res.getContentAsString().contains("Legacy"));
    }

    @Test
    void disabledDoesNotAffectV2() throws Exception {
        LegacyApiFilter f = new LegacyApiFilter(false);
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(new MockHttpServletRequest("GET", "/v2/status"), res, new MockFilterChain());
        assertEquals(200, res.getStatus());
    }
}
