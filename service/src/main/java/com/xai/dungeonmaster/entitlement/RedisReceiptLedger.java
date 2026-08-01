package com.xai.dungeonmaster.entitlement;

import com.xai.dungeonmaster.store.RedisOps;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Redis-backed receipt ledger for multi-node deploys.
 * Key: {@code {prefix}:receipt:{fingerprint}} hash.
 */
public final class RedisReceiptLedger implements ReceiptLedger {

    private final RedisOps redis;
    private final String prefix;
    private final int ttlSeconds;

    public RedisReceiptLedger(RedisOps redis, String keyPrefix, int ttlSeconds) {
        this.redis = redis;
        this.prefix = (keyPrefix == null || keyPrefix.isBlank()) ? "dm" : keyPrefix.trim();
        this.ttlSeconds = Math.max(60, ttlSeconds);
    }

    public RedisReceiptLedger(RedisOps redis) {
        this(redis, "dm", 90 * 24 * 3600);
    }

    @Override
    public Optional<RedeemRecord> find(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) return Optional.empty();
        Map<String, String> fields = redis.hgetAll(key(fingerprint));
        if (fields == null || fields.isEmpty()) return Optional.empty();
        return Optional.of(new RedeemRecord(
                fingerprint,
                fields.getOrDefault("sessionId", ""),
                fields.getOrDefault("productId", ""),
                fields.getOrDefault("storefront", ""),
                parseLong(fields.get("redeemedAt"), 0L)));
    }

    @Override
    public void record(RedeemRecord record) {
        if (record == null || record.fingerprint() == null) return;
        // Do not overwrite an existing redeem (first writer wins).
        if (find(record.fingerprint()).isPresent()) return;
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("sessionId", nullToEmpty(record.sessionId()));
        fields.put("productId", nullToEmpty(record.productId()));
        fields.put("storefront", nullToEmpty(record.storefront()));
        fields.put("redeemedAt", Long.toString(record.redeemedAtEpochMs()));
        String k = key(record.fingerprint());
        redis.hset(k, fields);
        try {
            redis.expire(k, ttlSeconds);
        } catch (UnsupportedOperationException ignored) {
            // memory ops always supports expire; keep safe
        }
    }

    private String key(String fingerprint) {
        return prefix + ":receipt:" + fingerprint;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static long parseLong(String s, long def) {
        if (s == null || s.isBlank()) return def;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
