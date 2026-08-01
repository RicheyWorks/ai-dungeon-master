package com.xai.dungeonmaster.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Process-local job store (default when Redis is not networked). */
public final class MemoryMarketplaceJobStore implements MarketplaceJobStore {

    private final ConcurrentHashMap<String, JobRecord> byId = new ConcurrentHashMap<>();

    @Override
    public void save(JobRecord record) {
        if (record == null || record.jobId() == null) return;
        byId.put(record.jobId(), record);
    }

    @Override
    public Optional<JobRecord> load(String jobId) {
        if (jobId == null) return Optional.empty();
        return Optional.ofNullable(byId.get(jobId));
    }

    @Override
    public Collection<String> ids() {
        return new ArrayList<>(byId.keySet());
    }

    @Override
    public void delete(String jobId) {
        if (jobId != null) byId.remove(jobId);
    }
}
