package com.xai.dungeonmaster.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MarketplaceIntegrityTest {

    @Test
    void sha256Stable() {
        byte[] data = "hello-pack".getBytes(StandardCharsets.UTF_8);
        String hex = MarketplaceIntegrity.sha256Hex(data);
        assertEquals(64, hex.length());
        assertTrue(MarketplaceIntegrity.sha256Matches(data, hex));
        assertTrue(MarketplaceIntegrity.sha256Matches(data, "sha256:" + hex));
        assertFalse(MarketplaceIntegrity.sha256Matches(data, "0".repeat(64)));
    }

    @Test
    void hmacRoundTrip() {
        byte[] body = "{\"version\":1,\"packs\":[]}".getBytes(StandardCharsets.UTF_8);
        String secret = "test-secret";
        String sig = MarketplaceIntegrity.hmacSha256Hex(body, secret);
        assertTrue(MarketplaceIntegrity.hmacMatches(body, secret, sig));
        assertTrue(MarketplaceIntegrity.hmacMatches(body, secret, "sha256=" + sig));
        assertFalse(MarketplaceIntegrity.hmacMatches(body, secret, "deadbeef"));
    }
}
