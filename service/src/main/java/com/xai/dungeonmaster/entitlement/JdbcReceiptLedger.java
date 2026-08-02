package com.xai.dungeonmaster.entitlement;

import com.xai.dungeonmaster.store.JdbcSchema;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC {@link ReceiptLedger} for multi-node deploys without Redis.
 * Table: {@code dm_receipts} (fingerprint PK).
 *
 * <p>First insert wins; concurrent double-inserts are treated as already present.
 * Optional TTL is applied lazily on {@link #find} (delete expired rows).
 */
public final class JdbcReceiptLedger implements ReceiptLedger {

    private final DataSource dataSource;
    private final long ttlMs;

    public JdbcReceiptLedger(DataSource dataSource) {
        this(dataSource, 90 * 24 * 3600);
    }

    /**
     * @param ttlSeconds retention window; rows older than this are ignored/deleted on read
     */
    public JdbcReceiptLedger(DataSource dataSource, int ttlSeconds) {
        this.dataSource = dataSource;
        this.ttlMs = Math.max(1L, ttlSeconds) * 1000L;
        JdbcSchema.ensure(dataSource);
    }

    @Override
    public Optional<RedeemRecord> find(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) return Optional.empty();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT fingerprint, session_id, product_id, storefront, redeemed_at_ms
                     FROM dm_receipts WHERE fingerprint = ?
                     """)) {
            ps.setString(1, fingerprint);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                long redeemedAt = rs.getLong("redeemed_at_ms");
                if (isExpired(redeemedAt)) {
                    delete(fingerprint);
                    return Optional.empty();
                }
                return Optional.of(new RedeemRecord(
                        rs.getString("fingerprint"),
                        rs.getString("session_id"),
                        rs.getString("product_id"),
                        rs.getString("storefront"),
                        redeemedAt));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("JdbcReceiptLedger.find failed", e);
        }
    }

    @Override
    public void record(RedeemRecord record) {
        if (record == null || record.fingerprint() == null || record.fingerprint().isBlank()) {
            return;
        }
        if (find(record.fingerprint()).isPresent()) return;
        try (Connection c = dataSource.getConnection()) {
            if (isPostgres(c)) {
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO dm_receipts
                          (fingerprint, session_id, product_id, storefront, redeemed_at_ms)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (fingerprint) DO NOTHING
                        """)) {
                    bind(ps, record);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement("""
                        MERGE INTO dm_receipts
                          (fingerprint, session_id, product_id, storefront, redeemed_at_ms)
                        KEY (fingerprint)
                        VALUES (?, ?, ?, ?, ?)
                        """)) {
                    bind(ps, record);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            // Concurrent first-writer: treat as already recorded
            if (find(record.fingerprint()).isPresent()) return;
            throw new IllegalStateException("JdbcReceiptLedger.record failed", e);
        }
    }

    @Override
    public List<RedeemRecord> listRecentUnfiltered(int limit) {
        return query(ReceiptQuery.ofLimit(limit));
    }

    @Override
    public List<RedeemRecord> list(ReceiptQuery query) {
        return query(query == null ? ReceiptQuery.ofLimit(50) : query);
    }

    private List<RedeemRecord> query(ReceiptQuery q) {
        long ttlCutoff = System.currentTimeMillis() - ttlMs;
        long since = q.sinceEpochMs() == null ? ttlCutoff : Math.max(ttlCutoff, q.sinceEpochMs());
        long until = q.untilEpochMs() == null ? Long.MAX_VALUE : q.untilEpochMs();

        StringBuilder sql = new StringBuilder("""
                SELECT fingerprint, session_id, product_id, storefront, redeemed_at_ms
                FROM dm_receipts
                WHERE redeemed_at_ms >= ? AND redeemed_at_ms <= ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(since);
        params.add(until);
        if (q.productId() != null) {
            sql.append(" AND product_id = ?");
            params.add(q.productId());
        }
        if (q.storefront() != null) {
            sql.append(" AND LOWER(storefront) = LOWER(?)");
            params.add(q.storefront());
        }
        if (q.sessionId() != null) {
            sql.append(" AND session_id = ?");
            params.add(q.sessionId());
        }
        sql.append(" ORDER BY redeemed_at_ms DESC LIMIT ?");
        params.add(q.limit());

        List<RedeemRecord> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Long l) {
                    ps.setLong(i + 1, l);
                } else if (p instanceof Integer n) {
                    ps.setInt(i + 1, n);
                } else {
                    ps.setString(i + 1, String.valueOf(p));
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new RedeemRecord(
                            rs.getString("fingerprint"),
                            rs.getString("session_id"),
                            rs.getString("product_id"),
                            rs.getString("storefront"),
                            rs.getLong("redeemed_at_ms")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("JdbcReceiptLedger.list failed", e);
        }
        return List.copyOf(out);
    }

    private void delete(String fingerprint) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM dm_receipts WHERE fingerprint = ?")) {
            ps.setString(1, fingerprint);
            ps.executeUpdate();
        } catch (SQLException ignored) {
            // best-effort TTL cleanup
        }
    }

    private boolean isExpired(long redeemedAtMs) {
        return redeemedAtMs > 0 && System.currentTimeMillis() - redeemedAtMs > ttlMs;
    }

    private static void bind(PreparedStatement ps, RedeemRecord r) throws SQLException {
        ps.setString(1, r.fingerprint());
        ps.setString(2, nullToEmpty(r.sessionId()));
        ps.setString(3, nullToEmpty(r.productId()));
        ps.setString(4, nullToEmpty(r.storefront()));
        ps.setLong(5, r.redeemedAtEpochMs());
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static boolean isPostgres(Connection c) throws SQLException {
        String name = c.getMetaData().getDatabaseProductName();
        return name != null && name.toLowerCase().contains("postgres");
    }
}
