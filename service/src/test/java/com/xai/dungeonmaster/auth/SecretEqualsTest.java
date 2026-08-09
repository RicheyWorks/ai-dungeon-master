package com.xai.dungeonmaster.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecretEqualsTest {

    @Test
    void matchesEqualSecrets() {
        assertTrue(SecretEquals.matches("super-secret-token", "super-secret-token"));
    }

    @Test
    void rejectsDifferentSecrets() {
        assertFalse(SecretEquals.matches("super-secret-token", "super-secret-tokeN"));
        assertFalse(SecretEquals.matches("short", "longer-value"));
        assertFalse(SecretEquals.matches("longer-value", "short"));
    }

    @Test
    void rejectsEmptyExpected() {
        assertFalse(SecretEquals.matches("", "anything"));
        assertFalse(SecretEquals.matches(null, "anything"));
        assertFalse(SecretEquals.matches("secret", null));
    }

    @Test
    void matchesEitherPrimaryOrPrevious() {
        assertTrue(SecretEquals.matchesEither("primary", "previous", "primary"));
        assertTrue(SecretEquals.matchesEither("primary", "previous", "previous"));
        assertFalse(SecretEquals.matchesEither("primary", "previous", "other"));
        // previous-only still accepted (metrics rotation edge)
        assertTrue(SecretEquals.matchesEither("", "previous", "previous"));
        assertFalse(SecretEquals.matchesEither("primary", "", "previous"));
        assertFalse(SecretEquals.matchesEither("", "", "anything"));
    }
}
