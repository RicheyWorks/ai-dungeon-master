package com.xai.dungeonmaster.web;

import com.xai.dungeonmaster.config.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class ApiExceptionHandlerTest {

    @Test
    void badJsonReturnsEnvelope() {
        ApiExceptionHandler h = new ApiExceptionHandler();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(RequestIdFilter.ATTR, "rid-1");
        ResponseEntity<?> res = h.badJson(
                new HttpMessageNotReadableException("bad"), req);
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertNotNull(res.getBody());
        assertTrue(res.getBody().toString().contains("error")
                || res.getBody().getClass().getSimpleName().contains("Envelope"));
    }
}
