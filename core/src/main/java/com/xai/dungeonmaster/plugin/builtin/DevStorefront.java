package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.plugin.StorefrontIntegration;

import java.nio.charset.StandardCharsets;

/**
 * A bundled developer/testing storefront ({@code id = "dev"}). It has no vendor
 * SDK — instead it mints and verifies its own HMAC-SHA256-signed receipts, so
 * the full "purchase → receipt → server-side verify → grant entitlement" loop
 * can run locally and in tests without Steam/Play/Game Center. This is the
 * storefront analogue of the offline {@code local-stub} narrator.
 *
 * Receipt format: {@code base64url(productId).base64url(HMAC_SHA256(secret, productId))}.
 * The signing secret comes from {@code STOREFRONT_DEV_SECRET} (env / system
 * property), with an insecure default for local dev.
 */
public final class DevStorefront implements StorefrontIntegration {

    /** Stable id of the dev storefront. */
    public static final String ID = "dev";

    private final byte[] secret;

    public DevStorefront() {
        String s = env("STOREFRONT_DEV_SECRET", "dev-storefront-insecure-secret-change-me");
        this.secret = s.getBytes(StandardCharsets.UTF_8);
    }

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Developer Storefront (test receipts)"; }

    /** Mint a valid receipt for a product — the dev stand-in for a real purchase. */
    public String signReceipt(String productId) {
        return HmacReceipts.sign(productId, secret);
    }

    @Override
    public boolean verifyReceipt(String receipt) {
        return HmacReceipts.verify(receipt, secret);
    }

    @Override
    public PurchaseFlow startPurchase(String productId) {
        final String receipt = signReceipt(productId);
        return new PurchaseFlow() {
            @Override public boolean isComplete() { return true; }
            @Override public boolean wasSuccessful() { return true; }
            @Override public String receipt() { return receipt; }
        };
    }

    @Override
    public Identity currentIdentity() {
        return new Identity("dev-user", "Dev Tester", true);
    }

    @Override public void unlockAchievement(String achievementId) { /* dev no-op */ }
    @Override public void submitLeaderboard(String boardId, long score) { /* dev no-op */ }

    @Override
    public CloudSaveHandle openCloudSave(String slot) {
        return new CloudSaveHandle() {
            @Override public byte[] read() { return new byte[0]; }
            @Override public void write(byte[] data) { /* dev no-op */ }
            @Override public boolean isAvailable() { return false; }
        };
    }

    private static String env(String name, String defaultValue) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) v = System.getProperty(name);
        return (v == null || v.isBlank()) ? defaultValue : v.trim();
    }
}
