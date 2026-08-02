package com.xai.dungeonmaster.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitPropertiesTest {

    @Test
    void clampsToAtLeastOne() {
        RateLimitProperties p = RateLimitProperties.builder().sessionPerMinute(0).build();
        assertEquals(1, p.sessionPerMinute());
    }

    @Test
    void allBucketsSetsEveryLimit() {
        RateLimitProperties p = RateLimitProperties.builder().allBuckets(7).build();
        assertEquals(7, p.sessionPerMinute());
        assertEquals(7, p.actionPerMinute());
        assertEquals(7, p.savePerMinute());
        assertEquals(7, p.metricsPerMinute());
    }
}
