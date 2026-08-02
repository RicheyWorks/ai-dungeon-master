package com.xai.dungeonmaster.auth;

import com.xai.dungeonmaster.store.JdbcSchema;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * JDBC {@link SessionStore} for multi-node deployments sharing a database
 * (PostgreSQL recommended; H2 works for tests). Table: {@code dm_sessions}.
 */
public final class JdbcSessionStore implements SessionStore {

    private final DataSource dataSource;

    public JdbcSessionStore(DataSource dataSource) {
        this.dataSource = dataSource;
        JdbcSchema.ensure(dataSource);
    }

    @Override
    public void save(SessionService.Session session) {
        if (session == null || session.id() == null) return;
        String sql = """
                MERGE INTO dm_sessions (id, display_name, created_at, last_seen_at)
                KEY (id)
                VALUES (?, ?, ?, ?)
                """;
        // MERGE works on H2; PostgreSQL prefers INSERT … ON CONFLICT. Try upsert dialects.
        try (Connection c = dataSource.getConnection()) {
            if (isPostgres(c)) {
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO dm_sessions (id, display_name, created_at, last_seen_at)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (id) DO UPDATE SET
                          display_name = EXCLUDED.display_name,
                          last_seen_at = EXCLUDED.last_seen_at
                        """)) {
                    bind(ps, session);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    bind(ps, session);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("JdbcSessionStore.save failed", e);
        }
    }

    @Override
    public Optional<SessionService.Session> load(String id) {
        if (id == null) return Optional.empty();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, display_name, created_at, last_seen_at FROM dm_sessions WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("JdbcSessionStore.load failed", e);
        }
    }

    @Override
    public Collection<SessionService.Session> all() {
        List<SessionService.Session> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, display_name, created_at, last_seen_at FROM dm_sessions");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("JdbcSessionStore.all failed", e);
        }
        return List.copyOf(out);
    }

    @Override
    public int size() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM dm_sessions");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException("JdbcSessionStore.size failed", e);
        }
    }

    @Override
    public void delete(String id) {
        if (id == null || id.isBlank()) return;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM dm_sessions WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("JdbcSessionStore.delete failed", e);
        }
    }

    private static void bind(PreparedStatement ps, SessionService.Session session) throws SQLException {
        ps.setString(1, session.id());
        ps.setString(2, session.displayName() == null ? "Guest" : session.displayName());
        ps.setLong(3, session.createdAtEpoch());
        ps.setLong(4, session.lastSeenEpoch());
    }

    private static SessionService.Session map(ResultSet rs) throws SQLException {
        return new SessionService.Session(
                rs.getString("id"),
                rs.getString("display_name"),
                rs.getLong("created_at"),
                rs.getLong("last_seen_at"));
    }

    private static boolean isPostgres(Connection c) throws SQLException {
        String name = c.getMetaData().getDatabaseProductName();
        return name != null && name.toLowerCase().contains("postgres");
    }
}
