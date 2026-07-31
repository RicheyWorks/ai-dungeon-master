package com.xai.dungeonmaster.store;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * Placeholder {@link DataSource} when JDBC auth stores are not selected.
 * Any real use is a configuration bug.
 */
public final class UnusedDataSource implements DataSource, AutoCloseable {

    @Override
    public Connection getConnection() throws SQLException {
        throw new SQLException("JDBC auth store is not configured (set game.auth.*.store=jdbc)");
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnection();
    }

    @Override public PrintWriter getLogWriter() { return null; }
    @Override public void setLogWriter(PrintWriter out) { /* no-op */ }
    @Override public void setLoginTimeout(int seconds) { /* no-op */ }
    @Override public int getLoginTimeout() { return 0; }
    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLException("not a wrapper");
    }
    @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    @Override public void close() { /* no-op */ }
}
