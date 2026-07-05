package com.xai.dungeonmaster.auth;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for the HS256 JwtService: round-trip, tamper, wrong-secret, expiry, malformed. */
class JwtServiceTest {

    private final JwtService jwt = new JwtService("unit-test-secret-abcdefghijklmnopqrstuvwxyz", 3600);

    @Test
    void roundTripVerifies() {
        JwtService.Token token = jwt.issue("session-123", Map.of("name", "Kael"));
        Optional<Map<String, Object>> claims = jwt.verify(token.value());
        assertTrue(claims.isPresent(), "freshly issued token should verify");
        assertEquals("session-123", claims.get().get("sub"));
        assertEquals("Kael", claims.get().get("name"));
        assertTrue(token.expiresAtEpochSeconds() > System.currentTimeMillis() / 1000L);
    }

    @Test
    void tamperedPayloadRejected() {
        String[] parts = jwt.issue("s", Map.of()).value().split("\\.");
        char c = parts[1].charAt(0);
        String flipped = (c == 'A' ? 'B' : 'A') + parts[1].substring(1);
        String tampered = parts[0] + "." + flipped + "." + parts[2];
        assertTrue(jwt.verify(tampered).isEmpty(), "a tampered payload must fail verification");
    }

    @Test
    void wrongSecretRejected() {
        String token = jwt.issue("s", Map.of()).value();
        JwtService other = new JwtService("a-totally-different-secret-0000000000000", 3600);
        assertTrue(other.verify(token).isEmpty(), "token signed with another secret must not verify");
    }

    @Test
    void expiredTokenRejected() {
        String expired = jwt.issue("s", Map.of(), -60).value(); // minted 60s in the past
        assertTrue(jwt.verify(expired).isEmpty(), "expired token must be rejected");
    }

    @Test
    void malformedTokenRejected() {
        assertTrue(jwt.verify("not.a.jwt").isEmpty());
        assertTrue(jwt.verify("only-one-part").isEmpty());
        assertTrue(jwt.verify(null).isEmpty());
    }
}
