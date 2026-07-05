package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.plugin.StorefrontIntegration;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

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

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final byte[] secret;

    public DevStorefront() {
        String s = env("STOREFRONT_DEV_SECRET", "dev-storefront-insecure-secret-change-me");
        this.secret = s.getBytes(StandardCharsets.UTF_8);
    }

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Developer Storefront (test receipts)"; }

    /** Mint a valid receipt for a product — the dev stand-in for a real purchase. */
    public String signReceipt(String productId) {
        String p = (productId == null) ? "" : productId;
        String body = B64.encodeToString(p.getBytes(StandardCharsets.UTF_8));
        String sig = B64.encodeToString(hmac(p.getBytes(StandardCharsets.UTF_8)));
        return body + "." + sig;
    }

    @Override
    public boolean verifyReceipt(String receipt) {
        if (receipt == null) return false;
        String[] parts = receipt.split("\\.");
        if (parts.length != 2) return false;
        byte[] product;
        byte[] presented;
        try {
            product = B64D.decode(parts[0]);
            presented = B64D.decode(parts[1]);
        } catch (IllegalArgumentException e) {
            return false;
        }
        byte[] expected = hmac(product);
        return MessageDigest.isEqual(expected, presented);
    }

    @Override
    public PurchaseFlow startPurchase(String productId) {
        // A dev purchase completes immediately with a valid, verifiable receipt.
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

    private byte[] hmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 failed", e);
        }
    }

    private static String env(String name, String defaultValue) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) v = System.getProperty(name);
        return (v == null || v.isBlank()) ? defaultValue : v.trim();
    }
}
