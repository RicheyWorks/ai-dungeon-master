package com.xai.dungeonmaster.entitlement;

import com.xai.dungeonmaster.plugin.StorefrontRegistry;
import com.xai.dungeonmaster.plugin.builtin.DevStorefront;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JdbcReceiptLedgerTest {

    private HikariDataSource ds;

    @BeforeEach
    void open() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:dm_receipts_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);
        StorefrontRegistry.clearForTests();
        StorefrontRegistry.register(new DevStorefront());
    }

    @AfterEach
    void close() {
        StorefrontRegistry.clearForTests();
        ds.close();
    }

    @Test
    void sharedAcrossInstances() {
        ReceiptLedger ledger = new JdbcReceiptLedger(ds, 3600);
        EntitlementService a = new EntitlementService(new InMemoryEntitlementStore(), ledger, true);
        EntitlementService b = new EntitlementService(new InMemoryEntitlementStore(), ledger, true);

        String receipt = new DevStorefront().signReceipt("sku_gold");
        assertTrue(a.verifyAndGrant("alice", "dev", "sku_gold", receipt).granted());
        var replay = b.verifyAndGrant("bob", "dev", "sku_gold", receipt);
        assertFalse(replay.granted());
        assertTrue(replay.reason().contains("already redeemed"), replay.reason());
    }

    @Test
    void expiredRowsIgnored() {
        JdbcReceiptLedger ledger = new JdbcReceiptLedger(ds, 1); // 1 second TTL
        String fp = ReceiptLedger.fingerprint("dev", "sku", "old-receipt");
        ledger.record(new ReceiptLedger.RedeemRecord(
                fp, "s1", "sku", "dev", System.currentTimeMillis() - 5_000L));
        // find should treat as missing after TTL
        assertTrue(ledger.find(fp).isEmpty());
        // can re-record
        ledger.record(new ReceiptLedger.RedeemRecord(
                fp, "s2", "sku", "dev", System.currentTimeMillis()));
        assertTrue(ledger.find(fp).isPresent());
        assertEquals("s2", ledger.find(fp).get().sessionId());
    }
}
