package com.xai.dungeonmaster.plugin.builtin;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class GooglePlayStorefrontLiveFlagTest {

    @Test
    void sandboxWithoutToken() {
        GooglePlayStorefront s = new GooglePlayStorefront(
                "com.example.app", "", "secret".getBytes(StandardCharsets.UTF_8),
                HttpClient.newHttpClient());
        assertFalse(s.isLive());
        assertTrue(s.displayName().contains("sandbox"));
    }

    @Test
    void liveWithPackageAndToken() {
        GooglePlayStorefront s = new GooglePlayStorefront(
                "com.example.app", "ya29.token", "secret".getBytes(StandardCharsets.UTF_8),
                HttpClient.newHttpClient());
        assertTrue(s.isLive());
        assertTrue(s.displayName().contains("live"));
    }
}
