package com.xai.dungeonmaster.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class RequestSizeFilterTest {

    @Test
    void rejectsOversizedContentLength() throws Exception {
        RequestSizeFilter filter = new RequestSizeFilter(100, true);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v2/narrate");
        req.setContentType("application/json");
        req.setContent(new byte[0]);
        req.addHeader("Content-Length", "500");
        // MockHttpServletRequest uses content length from content array; set via setContent
        req.setContent(new byte[500]);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        assertEquals(413, res.getStatus());
        assertTrue(res.getContentAsString().contains("too large"));
    }

    @Test
    void allowsUnderLimit() throws Exception {
        RequestSizeFilter filter = new RequestSizeFilter(10_000, true);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v2/action");
        req.setContentType("application/json");
        req.setContent("{\"choiceLabel\":\"Attack\"}".getBytes());
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        assertEquals(200, res.getStatus());
    }

    @Test
    void skipsMultipart() throws Exception {
        RequestSizeFilter filter = new RequestSizeFilter(10, true);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v2/catalog/packs");
        req.setContentType("multipart/form-data; boundary=x");
        req.setContent(new byte[1000]);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        assertEquals(200, res.getStatus());
    }
}
