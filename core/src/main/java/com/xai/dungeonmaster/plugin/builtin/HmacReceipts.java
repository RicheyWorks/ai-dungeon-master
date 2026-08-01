package com.xai.dungeonmaster.plugin.builtin;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Shared HMAC-SHA256 receipt format used by the developer storefront and by
 * sandbox modes of the Google Play / App Store plugins:
 * {@code base64url(productId).base64url(HMAC_SHA256(secret, productId))}.
 */
public final class HmacReceipts {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private HmacReceipts() {}

    public static String sign(String productId, byte[] secret) {
        String p = productId == null ? "" : productId;
        return B64.encodeToString(p.getBytes(StandardCharsets.UTF_8))
                + "."
                + B64.encodeToString(hmac(secret, p.getBytes(StandardCharsets.UTF_8)));
    }

    public static boolean verify(String receipt, byte[] secret) {
        if (receipt == null || secret == null) return false;
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
        return MessageDigest.isEqual(hmac(secret, product), presented);
    }

    /** Decode product id from a valid-looking HMAC receipt body (no verify). */
    public static String productIdFromReceipt(String receipt) {
        if (receipt == null) return null;
        String[] parts = receipt.split("\\.");
        if (parts.length < 1) return null;
        try {
            return new String(B64D.decode(parts[0]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static byte[] hmac(byte[] secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 failed", e);
        }
    }
}
