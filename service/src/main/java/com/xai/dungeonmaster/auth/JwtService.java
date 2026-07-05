package com.xai.dungeonmaster.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal, dependency-free HS256 (HMAC-SHA256) JSON Web Token issuer/verifier.
 *
 * Tokens are standard three-part JWTs — {@code base64url(header).base64url(claims).base64url(sig)}
 * — so any standard JWT library can validate them given the shared secret. We
 * roll our own here to avoid pulling a JWT dependency into the service for what
 * is, in v1, single-player guest identity.
 *
 * The secret comes from {@code game.auth.jwt.secret}; if unset, an insecure
 * development secret is used and a warning is logged. Token lifetime comes from
 * {@code game.auth.jwt.ttl-seconds} (default 24h).
 */
@Component
public class JwtService {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long ttlSeconds;
    private final ObjectMapper mapper = new ObjectMapper();

    public JwtService(@Value("${game.auth.jwt.secret:}") String secret,
                      @Value("${game.auth.jwt.ttl-seconds:86400}") long ttlSeconds) {
        String s = (secret == null || secret.isBlank()) ? null : secret;
        if (s == null) {
            s = "dev-insecure-secret-change-me-please-0123456789abcdef";
            System.err.println("WARN: game.auth.jwt.secret is not set — using an insecure development "
                    + "secret. Set a strong secret before enabling auth outside local dev.");
        }
        this.secret = s.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = (ttlSeconds > 0) ? ttlSeconds : 86400;
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    /** Issue a token for {@code subject} using the configured TTL. */
    public Token issue(String subject, Map<String, Object> extraClaims) {
        return issue(subject, extraClaims, ttlSeconds);
    }

    /** Issue a token with an explicit TTL (seconds); negative TTLs mint an already-expired token (tests). */
    public Token issue(String subject, Map<String, Object> extraClaims, long ttl) {
        long now = System.currentTimeMillis() / 1000L;
        long exp = now + ttl;
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", subject);
        claims.put("iat", now);
        claims.put("exp", exp);
        if (extraClaims != null) {
            claims.putAll(extraClaims);
        }
        String signingInput = encode(header) + "." + encode(claims);
        String sig = B64.encodeToString(hmac(signingInput.getBytes(StandardCharsets.UTF_8)));
        return new Token(signingInput + "." + sig, exp);
    }

    /**
     * Verify the signature and expiry of a token. Returns the decoded claims on
     * success, or empty when the token is malformed, unsigned-correctly, or expired.
     */
    public Optional<Map<String, Object>> verify(String token) {
        if (token == null) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        String signingInput = parts[0] + "." + parts[1];
        byte[] expected = hmac(signingInput.getBytes(StandardCharsets.UTF_8));
        byte[] actual;
        try {
            actual = B64D.decode(parts[2]);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            return Optional.empty();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = mapper.readValue(B64D.decode(parts[1]), Map.class);
            Object exp = claims.get("exp");
            long expSec = (exp instanceof Number) ? ((Number) exp).longValue() : 0L;
            if (expSec <= System.currentTimeMillis() / 1000L) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String encode(Map<String, Object> obj) {
        try {
            return B64.encodeToString(mapper.writeValueAsBytes(obj));
        } catch (Exception e) {
            throw new IllegalStateException("JWT encoding failed", e);
        }
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

    /** A minted token plus its expiry (epoch seconds). */
    public record Token(String value, long expiresAtEpochSeconds) {}
}
