package com.xai.dungeonmaster.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NarrationRateGuardTest {

    @Test
    void sessionBudgetIndependent() {
        RateLimitStore store = new MemoryRateLimitStore();
        NarrationRateGuard guard = new NarrationRateGuard(store, true, 2);
        assertTrue(guard.check("s1").allowed());
        assertTrue(guard.check("s1").allowed());
        assertFalse(guard.check("s1").allowed());
        // other session still allowed
        assertTrue(guard.check("s2").allowed());
    }

    @Test
    void disabledAlwaysAllows() {
        NarrationRateGuard guard = new NarrationRateGuard(new MemoryRateLimitStore(), false, 1);
        for (int i = 0; i < 5; i++) {
            assertTrue(guard.check("x").allowed());
        }
    }
}
