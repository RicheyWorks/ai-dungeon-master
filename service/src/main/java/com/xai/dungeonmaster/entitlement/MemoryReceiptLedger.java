package com.xai.dungeonmaster.entitlement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Process-local receipt ledger (default). */
public final class MemoryReceiptLedger implements ReceiptLedger {

    private final ConcurrentHashMap<String, RedeemRecord> byFp = new ConcurrentHashMap<>();

    @Override
    public Optional<RedeemRecord> find(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) return Optional.empty();
        return Optional.ofNullable(byFp.get(fingerprint));
    }

    @Override
    public void record(RedeemRecord record) {
        if (record == null || record.fingerprint() == null) return;
        byFp.putIfAbsent(record.fingerprint(), record);
    }

    @Override
    public List<RedeemRecord> listRecent(int limit) {
        int n = Math.max(1, Math.min(limit, 500));
        List<RedeemRecord> all = new ArrayList<>(byFp.values());
        all.sort(Comparator.comparingLong(RedeemRecord::redeemedAtEpochMs).reversed());
        if (all.size() <= n) return List.copyOf(all);
        return List.copyOf(all.subList(0, n));
    }
}
