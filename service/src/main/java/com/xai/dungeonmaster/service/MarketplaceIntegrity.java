package com.xai.dungeonmaster.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Checksums and HMAC helpers for remote marketplace index + pack zips.
 */
public final class MarketplaceIntegrity {

    private MarketplaceIntegrity() {}

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Normalize expected digests: strip optional {@code sha256:} prefix, lowercase hex.
     */
    public static String normalizeSha256(String expected) {
        if (expected == null) return null;
        String s = expected.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("sha256:")) s = s.substring("sha256:".length()).trim();
        if (s.startsWith("sha-256:")) s = s.substring("sha-256:".length()).trim();
        return s.isEmpty() ? null : s;
    }

    public static boolean sha256Matches(byte[] data, String expected) {
        String want = normalizeSha256(expected);
        if (want == null) return false;
        String got = sha256Hex(data);
        return MessageDigest.isEqual(
                got.getBytes(StandardCharsets.US_ASCII),
                want.getBytes(StandardCharsets.US_ASCII));
    }

    /** HMAC-SHA256 hex of {@code body} using UTF-8 secret. */
    public static String hmacSha256Hex(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    /**
     * Verify index signature. Accepts raw hex or {@code sha256=<hex>} / {@code v1=<hex>}.
     */
    public static boolean hmacMatches(byte[] body, String secret, String provided) {
        if (secret == null || secret.isBlank() || provided == null || provided.isBlank()) {
            return false;
        }
        String sig = provided.trim();
        int eq = sig.indexOf('=');
        if (eq > 0) {
            sig = sig.substring(eq + 1).trim();
        }
        sig = sig.toLowerCase(Locale.ROOT);
        String expect = hmacSha256Hex(body, secret);
        return MessageDigest.isEqual(
                expect.getBytes(StandardCharsets.US_ASCII),
                sig.getBytes(StandardCharsets.US_ASCII));
    }
}
