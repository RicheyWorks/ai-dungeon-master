package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.plugin.StorefrontRegistry;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AppStoreStorefrontTest {

    @Test
    void sandboxHmacReceiptVerifies() {
        byte[] secret = "test-apple-secret".getBytes(StandardCharsets.UTF_8);
        AppStoreStorefront store = new AppStoreStorefront("", "com.example.dm", secret, HttpClient.newHttpClient());
        assertFalse(store.isLive());
        String receipt = store.signSandboxReceipt("sku_gold");
        assertTrue(store.verifyReceipt(receipt));
        assertFalse(store.verifyReceipt("not-a-receipt"));
    }

    @Test
    void sandboxJsonReceiptDataField() {
        byte[] secret = "test-apple-secret".getBytes(StandardCharsets.UTF_8);
        AppStoreStorefront store = new AppStoreStorefront("", "", secret, HttpClient.newHttpClient());
        String token = store.signSandboxReceipt("sku_pass");
        String json = "{\"receiptData\":\"" + token + "\",\"productId\":\"sku_pass\"}";
        assertTrue(store.verifyReceipt(json));
    }

    @Test
    void serviceLoaderRegistersAppStore() {
        StorefrontRegistry.clearForTests();
        assertTrue(StorefrontRegistry.isRegistered("app_store"));
        assertTrue(StorefrontRegistry.isRegistered("dev"));
        assertTrue(StorefrontRegistry.isRegistered("google_play"));
        StorefrontRegistry.clearForTests();
    }
}
