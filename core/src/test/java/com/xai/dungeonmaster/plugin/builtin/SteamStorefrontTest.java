package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.plugin.StorefrontRegistry;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SteamStorefrontTest {

    @Test
    void sandboxHmacReceiptVerifies() {
        byte[] secret = "test-steam-secret".getBytes(StandardCharsets.UTF_8);
        SteamStorefront store = new SteamStorefront("", "", false, secret, HttpClient.newHttpClient());
        assertFalse(store.isLive());
        String receipt = store.signSandboxReceipt("sku_gold");
        assertTrue(store.verifyReceipt(receipt));
        assertFalse(store.verifyReceipt(receipt + "x"));
    }

    @Test
    void sandboxJsonOrderIdEnvelope() {
        byte[] secret = "test-steam-secret".getBytes(StandardCharsets.UTF_8);
        SteamStorefront store = new SteamStorefront("", "", false, secret, HttpClient.newHttpClient());
        String token = store.signSandboxReceipt("sku_gold");
        String json = "{\"orderId\":\"" + token + "\",\"steamId\":\"76561198000000000\",\"productId\":\"sku_gold\"}";
        assertTrue(store.verifyReceipt(json));

        String wrong = "{\"orderId\":\"" + token + "\",\"productId\":\"other\"}";
        assertFalse(store.verifyReceipt(wrong));
    }

    @Test
    void purchaseFlowProducesJsonReceipt() {
        byte[] secret = "test-steam-secret".getBytes(StandardCharsets.UTF_8);
        SteamStorefront store = new SteamStorefront("", "", false, secret, HttpClient.newHttpClient());
        var flow = store.startPurchase("sku_pass");
        assertTrue(flow.isComplete() && flow.wasSuccessful());
        assertTrue(store.verifyReceipt(flow.receipt()));
    }

    @Test
    void serviceLoaderRegistersSteam() {
        StorefrontRegistry.clearForTests();
        assertTrue(StorefrontRegistry.isRegistered("steam"));
        assertTrue(StorefrontRegistry.isRegistered("google_play"));
        assertTrue(StorefrontRegistry.isRegistered("app_store"));
        StorefrontRegistry.clearForTests();
    }
}
