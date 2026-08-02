package com.xai.dungeonmaster.content;

import com.xai.dungeonmaster.store.JdbcSchema;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC {@link SessionPackStore}. Table: {@code dm_session_packs}
 * ({@code session_id}, {@code pack_id}, {@code enabled}).
 */
public final class JdbcSessionPackStore implements SessionPackStore {

    private final DataSource dataSource;

    public JdbcSessionPackStore(DataSource dataSource) {
        this.dataSource = dataSource;
        JdbcSchema.ensure(dataSource);
    }

    @Override
    public Optional<Boolean> get(String sessionId, String packId) {
        if (sessionId == null || packId == null) return Optional.empty();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT enabled FROM dm_session_packs WHERE session_id = ? AND pack_id = ?")) {
            ps.setString(1, sessionId);
            ps.setString(2, packId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(rs.getInt(1) != 0);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("JdbcSessionPackStore.get failed", e);
        }
    }

    @Override
    public Map<String, Boolean> all(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return Map.of();
        Map<String, Boolean> out = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT pack_id, enabled FROM dm_session_packs WHERE session_id = ?")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString(1), rs.getInt(2) != 0);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("JdbcSessionPackStore.all failed", e);
        }
        return Map.copyOf(out);
    }

    @Override
    public void put(String sessionId, String packId, Boolean enabled) {
        if (sessionId == null || sessionId.isBlank() || packId == null || packId.isBlank()) {
            return;
        }
        try (Connection c = dataSource.getConnection()) {
            if (enabled == null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM dm_session_packs WHERE session_id = ? AND pack_id = ?")) {
                    ps.setString(1, sessionId);
                    ps.setString(2, packId);
                    ps.executeUpdate();
                }
                return;
            }
            int flag = enabled ? 1 : 0;
            if (isPostgres(c)) {
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO dm_session_packs (session_id, pack_id, enabled)
                        VALUES (?, ?, ?)
                        ON CONFLICT (session_id, pack_id) DO UPDATE SET enabled = EXCLUDED.enabled
                        """)) {
                    ps.setString(1, sessionId);
                    ps.setString(2, packId);
                    ps.setInt(3, flag);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement("""
                        MERGE INTO dm_session_packs (session_id, pack_id, enabled)
                        KEY (session_id, pack_id)
                        VALUES (?, ?, ?)
                        """)) {
                    ps.setString(1, sessionId);
                    ps.setString(2, packId);
                    ps.setInt(3, flag);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("JdbcSessionPackStore.put failed", e);
        }
    }

    private static boolean isPostgres(Connection c) throws SQLException {
        String name = c.getMetaData().getDatabaseProductName();
        return name != null && name.toLowerCase().contains("postgres");
    }
}
