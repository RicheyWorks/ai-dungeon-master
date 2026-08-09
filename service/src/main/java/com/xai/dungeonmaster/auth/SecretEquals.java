package com.xai.dungeonmaster.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Constant-time secret comparison that does not leak length via early return.
 * Digests both sides with SHA-256 (fixed 32-byte compare) so timing is independent
 * of token length. Empty expected secrets never match.
 */
public final class SecretEquals {

    private SecretEquals() {}

    /**
     * @return true when both non-empty and equal; false if expected is null/blank,
     *         actual is null, or values differ
     */
    public static boolean matches(String expected, String actual) {
        if (expected == null || expected.isEmpty() || actual == null) {
            // Burn a digest so empty/missing expected is not a fast-path oracle.
            digest(actual == null ? new byte[0] : actual.getBytes(StandardCharsets.UTF_8));
            return false;
        }
        byte[] a = digest(expected.getBytes(StandardCharsets.UTF_8));
        byte[] b = digest(actual.getBytes(StandardCharsets.UTF_8));
        return MessageDigest.isEqual(a, b);
    }

    /**
     * True when {@code presented} matches primary and/or previous (either may be blank).
     * Both configured sides are always evaluated (no short-circuit timing).
     */
    public static boolean matchesEither(String primary, String previous, String presented) {
        // Non-short-circuit: evaluate both sides for similar work.
        boolean okPrimary = matches(primary == null ? "" : primary, presented);
        boolean okPrevious = matches(previous == null ? "" : previous, presented);
        return okPrimary | okPrevious;
    }

    private static byte[] digest(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            // Impossible on a conforming JVM — fall back to length-padded compare.
            byte[] pad = new byte[32];
            System.arraycopy(input, 0, pad, 0, Math.min(32, input.length));
            return pad;
        }
    }
}
