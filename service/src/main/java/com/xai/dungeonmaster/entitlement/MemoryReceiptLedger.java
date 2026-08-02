package com.xai.dungeonmaster.entitlement;

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
}
