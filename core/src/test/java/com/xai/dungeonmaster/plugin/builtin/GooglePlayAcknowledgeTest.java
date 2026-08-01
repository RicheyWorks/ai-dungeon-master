package com.xai.dungeonmaster.plugin.builtin;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class GooglePlayAcknowledgeTest {

    @Test
    void afterGrantNoOpWhenNotLive() {
        GooglePlayStorefront s = new GooglePlayStorefront(
                "", "", "sec".getBytes(StandardCharsets.UTF_8), HttpClient.newHttpClient());
        assertFalse(s.isLive());
        s.afterGrant("sku_gold",
                "{\"packageName\":\"com.x\",\"productId\":\"sku_gold\",\"purchaseToken\":\"tok\"}");
    }

    @Test
    void sandboxJsonStillVerifies() {
        GooglePlayStorefront s = new GooglePlayStorefront(
                "com.x", "", "google-play-sandbox-insecure-secret".getBytes(StandardCharsets.UTF_8),
                HttpClient.newHttpClient());
        String token = s.signSandboxReceipt("sku_gold");
        String json = "{\"packageName\":\"com.x\",\"productId\":\"sku_gold\",\"purchaseToken\":\""
                + token + "\"}";
        assertTrue(s.verifyReceipt(json));
    }
}
