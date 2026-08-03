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
        assertDoesNotThrow(() ->
                SecurityAudit.log("unauthorized", "/v2/health", "10.0.0.1", "h1", "bad_metrics_token"));
        assertDoesNotThrow(() ->
                SecurityAudit.log("rate_limited", "/v2/session", "10.0.0.2", "r2",
                        "bucket=session count=31 limit=30 retryAfterSec=12"));
    }
}
