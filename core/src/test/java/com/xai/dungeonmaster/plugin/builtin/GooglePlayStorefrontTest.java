package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.plugin.StorefrontRegistry;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class GooglePlayStorefrontTest {

    @Test
    void sandboxHmacReceiptVerifies() {
        byte[] secret = "test-google-secret".getBytes(StandardCharsets.UTF_8);
        GooglePlayStorefront store = new GooglePlayStorefront("", "", secret, HttpClient.newHttpClient());
        assertFalse(store.isLive());
        String receipt = store.signSandboxReceipt("sku_gold");
        assertTrue(store.verifyReceipt(receipt));
        assertFalse(store.verifyReceipt(receipt + "x"));
    }

    @Test
    void sandboxJsonReceiptUsesPurchaseToken() {
        byte[] secret = "test-google-secret".getBytes(StandardCharsets.UTF_8);
        GooglePlayStorefront store = new GooglePlayStorefront("com.example.dm", "", secret, HttpClient.newHttpClient());
        String token = store.signSandboxReceipt("sku_gold");
        String json = "{\"packageName\":\"com.example.dm\",\"productId\":\"sku_gold\",\"purchaseToken\":\""
                + token + "\"}";
        assertTrue(store.verifyReceipt(json));

        String wrongSku = "{\"packageName\":\"com.example.dm\",\"productId\":\"other\",\"purchaseToken\":\""
                + token + "\"}";
        assertFalse(store.verifyReceipt(wrongSku));
    }

    @Test
    void serviceLoaderRegistersGooglePlay() {
        StorefrontRegistry.clearForTests();
        assertTrue(StorefrontRegistry.isRegistered("google_play"));
        StorefrontRegistry.clearForTests();
    }
}
