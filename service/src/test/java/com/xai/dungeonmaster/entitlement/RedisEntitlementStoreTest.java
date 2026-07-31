package com.xai.dungeonmaster.entitlement;

import com.xai.dungeonmaster.store.MemoryRedisOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RedisEntitlementStoreTest {

    @Test
    void grantIsIdempotentAndShared() {
        MemoryRedisOps redis = new MemoryRedisOps();
        RedisEntitlementStore a = new RedisEntitlementStore(redis, "dm");
        RedisEntitlementStore b = new RedisEntitlementStore(redis, "dm");

        a.grant("sess-1", "sku_gold");
        a.grant("sess-1", "sku_gold");
        assertTrue(a.owns("sess-1", "sku_gold"));
        assertTrue(b.owns("sess-1", "sku_gold"));
        assertEquals(1, b.products("sess-1").size());
        assertFalse(b.owns("sess-1", "sku_other"));
        assertTrue(b.products("missing").isEmpty());
    }
}
