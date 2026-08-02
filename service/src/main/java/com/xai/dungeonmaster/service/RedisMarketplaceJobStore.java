package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.store.RedisOps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Redis-backed install jobs for multi-node progress polling.
 * Keys:
 * <ul>
 *   <li>{@code {prefix}:mkt-job:{jobId}} — hash</li>
 *   <li>{@code {prefix}:mkt-jobs} — set of job ids</li>
 * </ul>
 */
public final class RedisMarketplaceJobStore implements MarketplaceJobStore {

    private final RedisOps redis;
    private final String prefix;
    private final int ttlSeconds;

    public RedisMarketplaceJobStore(RedisOps redis, String keyPrefix, int ttlSeconds) {
        this.redis = redis;
        this.prefix = (keyPrefix == null || keyPrefix.isBlank()) ? "dm" : keyPrefix.trim();
        this.ttlSeconds = Math.max(60, ttlSeconds);
    }

    public RedisMarketplaceJobStore(RedisOps redis) {
        this(redis, "dm", 3600);
    }

    @Override
    public void save(JobRecord record) {
        if (record == null || record.jobId() == null) return;
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("packId", nullToEmpty(record.packId()));
        fields.put("phase", nullToEmpty(record.phase()));
        fields.put("bytesRead", Long.toString(record.bytesRead()));
        fields.put("bytesTotal", Long.toString(record.bytesTotal()));
        fields.put("message", nullToEmpty(record.message()));
        fields.put("cancelRequested", record.cancelRequested() ? "1" : "0");
        fields.put("error", nullToEmpty(record.error()));
        fields.put("updatedAtMs", Long.toString(record.updatedAtMs()));
        fields.put("ownerSessionId", nullToEmpty(record.ownerSessionId()));
        String key = jobKey(record.jobId());
        redis.hset(key, fields);
        redis.sadd(indexKey(), record.jobId());
        try {
            redis.expire(key, ttlSeconds);
            redis.expire(indexKey(), ttlSeconds);
        } catch (UnsupportedOperationException ignored) {
            // MemoryRedisOps may not implement expire
        }
    }

    @Override
    public Optional<JobRecord> load(String jobId) {
        if (jobId == null || jobId.isBlank()) return Optional.empty();
        Map<String, String> fields = redis.hgetAll(jobKey(jobId));
        if (fields == null || fields.isEmpty()) return Optional.empty();
        return Optional.of(fromFields(jobId, fields));
    }

    @Override
    public Collection<String> ids() {
        Set<String> members = redis.smembers(indexKey());
        if (members == null || members.isEmpty()) return List.of();
        return new ArrayList<>(members);
    }

    @Override
    public void delete(String jobId) {
        if (jobId == null) return;
        redis.del(jobKey(jobId));
        redis.srem(indexKey(), jobId);
    }

    private JobRecord fromFields(String jobId, Map<String, String> f) {
        return new JobRecord(
                jobId,
                emptyToNull(f.get("packId")),
                emptyToNull(f.get("phase")),
                parseLong(f.get("bytesRead"), 0L),
                parseLong(f.get("bytesTotal"), 0L),
                emptyToNull(f.get("message")),
                "1".equals(f.get("cancelRequested")) || "true".equalsIgnoreCase(f.get("cancelRequested")),
                emptyToNull(f.get("error")),
                parseLong(f.get("updatedAtMs"), System.currentTimeMillis()),
                emptyToNull(f.get("ownerSessionId")));
    }

    private String jobKey(String jobId) {
        return prefix + ":mkt-job:" + jobId;
    }

    private String indexKey() {
        return prefix + ":mkt-jobs";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
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
