package com.xai.dungeonmaster.store;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the multi-node auth tables if they do not exist. Dialect is vanilla
 * SQL that works on PostgreSQL and H2.
 */
public final class JdbcSchema {

    public static final String SESSIONS = """
            CREATE TABLE IF NOT EXISTS dm_sessions (
              id            VARCHAR(64)  PRIMARY KEY,
              display_name  VARCHAR(128) NOT NULL,
              created_at    BIGINT       NOT NULL,
              last_seen_at  BIGINT       NOT NULL
            )
            """;

    public static final String ENTITLEMENTS = """
            CREATE TABLE IF NOT EXISTS dm_entitlements (
              session_id  VARCHAR(64)  NOT NULL,
              product_id  VARCHAR(128) NOT NULL,
              granted_at  BIGINT       NOT NULL,
              PRIMARY KEY (session_id, product_id)
            )
            """;

    /** One-time purchase receipts (anti-replay). */
    public static final String RECEIPTS = """
            CREATE TABLE IF NOT EXISTS dm_receipts (
              fingerprint     VARCHAR(64)  PRIMARY KEY,
              session_id      VARCHAR(64)  NOT NULL,
              product_id      VARCHAR(128) NOT NULL,
              storefront      VARCHAR(64)  NOT NULL,
              redeemed_at_ms  BIGINT       NOT NULL
            )
            """;

    private JdbcSchema() {}

    public static void ensure(DataSource ds) {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute(SESSIONS);
            st.execute(ENTITLEMENTS);
            st.execute(RECEIPTS);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to ensure JDBC auth schema", e);
        }
    }
}
