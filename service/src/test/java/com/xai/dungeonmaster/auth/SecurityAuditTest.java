package com.xai.dungeonmaster.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SecurityAuditTest {

    @Test
    void logDoesNotThrow() {
        assertDoesNotThrow(() ->
                SecurityAudit.log("forbidden", "/v2/marketplace/jobs/x", "127.0.0.1", "rid", "caller=a owner=b"));
        assertDoesNotThrow(() ->
                SecurityAudit.log("unauthorized", "/metrics", null, null, null));
    }
}
