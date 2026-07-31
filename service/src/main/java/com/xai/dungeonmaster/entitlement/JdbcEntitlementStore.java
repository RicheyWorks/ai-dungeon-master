package com.xai.dungeonmaster.entitlement;

import com.xai.dungeonmaster.store.JdbcSchema;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * JDBC {@link EntitlementStore}. Table: {@code dm_entitlements}
 * ({@code session_id}, {@code product_id}, {@code granted_at}).
 */
public final class JdbcEntitlementStore implements EntitlementStore {

    private final DataSource dataSource;

    public JdbcEntitlementStore(DataSource dataSource) {
        this.dataSource = dataSource;
        JdbcSchema.ensure(dataSource);
    }

    @Override
    public Set<String> products(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT product_id FROM dm_entitlements WHERE session_id = ?")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("JdbcEntitlementStore.products failed", e);
        }
        return Set.copyOf(out);
    }

    @Override
    public void grant(String sessionId, String productId) {
        if (sessionId == null || sessionId.isBlank() || productId == null || productId.isBlank()) {
            return;
        }
        long now = Instant.now().getEpochSecond();
        try (Connection c = dataSource.getConnection()) {
            if (isPostgres(c)) {
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO dm_entitlements (session_id, product_id, granted_at)
                        VALUES (?, ?, ?)
                        ON CONFLICT (session_id, product_id) DO NOTHING
                        """)) {
                    ps.setString(1, sessionId);
                    ps.setString(2, productId);
                    ps.setLong(3, now);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement("""
                        MERGE INTO dm_entitlements (session_id, product_id, granted_at)
                        KEY (session_id, product_id)
                        VALUES (?, ?, ?)
                        """)) {
                    ps.setString(1, sessionId);
                    ps.setString(2, productId);
                    ps.setLong(3, now);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("JdbcEntitlementStore.grant failed", e);
        }
    }

    private static boolean isPostgres(Connection c) throws SQLException {
        String name = c.getMetaData().getDatabaseProductName();
        return name != null && name.toLowerCase().contains("postgres");
    }
}
