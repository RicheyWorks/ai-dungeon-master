package com.xai.dungeonmaster.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class RequestIdFilterTest {

    @Test
    void generatesWhenMissing() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v2/health");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        String id = res.getHeader(RequestIdFilter.HEADER);
        assertNotNull(id);
        assertFalse(id.isBlank());
        assertEquals(id, req.getAttribute(RequestIdFilter.ATTR));
    }

    @Test
    void echoesClientId() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v2/health");
        req.addHeader(RequestIdFilter.HEADER, "client-abc");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        assertEquals("client-abc", res.getHeader(RequestIdFilter.HEADER));
    }

    @Test
    void rejectsUnsafeClientId() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v2/health");
        req.addHeader(RequestIdFilter.HEADER, "evil\ninjection");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        String id = res.getHeader(RequestIdFilter.HEADER);
        assertNotNull(id);
        assertFalse(id.contains("\n"));
        assertNotEquals("evil\ninjection", id);
    }
}
