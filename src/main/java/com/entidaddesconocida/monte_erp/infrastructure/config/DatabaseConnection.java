package com.entidaddesconocida.monte_erp.infrastructure.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * JDBC entry point of the application: holds the configuration and opens a
 * <strong>connection per operation</strong>.
 *
 * <p>There is deliberately no shared {@link Connection}. A single mutable
 * connection reused by every repository forced the transactional repositories to
 * flip {@code autoCommit} on global state and restore it on {@code finally}, and
 * it only worked while all the database work happened on the JavaFX thread; two
 * concurrent operations (a background query, a virtual thread) would have
 * committed each other's work. Opening a connection per operation costs nothing
 * at this scale (a local MySQL) and removes both problems: every
 * {@link #withTransaction(SqlWork)} block is isolated, and a stale connection can
 * never be handed out, so a dropped connection (MySQL restarted, container
 * recreated) simply becomes a failed operation the caller reports, not a
 * poisoned global.</p>
 *
 * <p>The configuration is set through {@link #configure(String, String, String)}
 * once the user has saved the URL (properties file) and the credentials (OS
 * keyring).</p>
 */
public final class DatabaseConnection {

    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    private static volatile String url;
    private static volatile String user;
    private static volatile String password;

    private DatabaseConnection() {
        // Utility class: never instantiated.
    }

    /** Unit of work that receives the connection of the current operation. */
    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    /**
     * Transactional unit of work: it may throw the domain exceptions of the
     * adapter (checked), which propagate unchanged after the rollback.
     */
    @FunctionalInterface
    public interface TransactedWork<T, E extends Exception> {
        T run(Connection connection) throws SQLException, E;
    }

    /**
     * Points the connection factory at the given settings. Nothing is kept open:
     * the next operation uses the new values.
     */
    public static void configure(String url, String user, String password) {
        DatabaseConnection.url = url;
        DatabaseConnection.user = user;
        DatabaseConnection.password = password;
    }

    /**
     * Opens a new connection with the configured settings. The caller owns it and
     * must close it (prefer {@link #withConnection(SqlWork)} or
     * {@link #withTransaction(SqlWork)}).
     *
     * @throws SQLException when the connection cannot be established or the
     *                      configuration is missing
     */
    public static Connection open() throws SQLException {
        String configuredUrl = url;
        String configuredUser = user;
        String configuredPassword = password;
        if (isBlank(configuredUrl) || isBlank(configuredUser)
                || configuredPassword == null || configuredPassword.isEmpty()) {
            throw new SQLException("La configuración de la base de datos no está disponible "
                    + "(url, usuario o contraseña vacíos).");
        }
        loadDriver();
        return DriverManager.getConnection(configuredUrl, configuredUser, configuredPassword);
    }

    /**
     * Runs {@code work} with its own connection, closing it afterwards. Statement
     * errors propagate; the connection is always closed, also on failure.
     */
    public static <T> T withConnection(SqlWork<T> work) throws SQLException {
        try (Connection connection = open()) {
            return work.run(connection);
        }
    }

    /**
     * Runs {@code work} inside a transaction on its own connection: commits when
     * it returns and rolls the whole block back when it throws, so the caller sees
     * the original exception (with the rollback failure attached as suppressed when
     * there is one).
     */
    public static <T, E extends Exception> T withTransaction(TransactedWork<T, E> work)
            throws SQLException, E {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (Exception failure) {
                // Precise rethrow: the compiler infers SQLException | E | RuntimeException.
                rollback(connection, failure);
                throw failure;
            }
        }
    }

    /**
     * Verifies that the given settings can open a JDBC connection. Nothing is kept
     * open.
     *
     * @throws SQLException when the connection cannot be established
     */
    public static void testConnection(String url, String user, String password) throws SQLException {
        loadDriver();
        try (Connection ignored = DriverManager.getConnection(url, user, password)) {
            // Opened and closed successfully: the credentials are correct.
        }
    }

    /**
     * Configured JDBC URL (handy to display the environment in the interface).
     */
    public static String getUrl() {
        return url;
    }

    /**
     * Rolls the transaction back, attaching the rollback failure to the original
     * exception so the real cause is never lost.
     */
    private static void rollback(Connection connection, Exception failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    /**
     * Registers the MySQL driver. When it is not available the real error is
     * reported while connecting (DriverManager also discovers it through
     * {@code ServiceLoader}, so a failure here is not final).
     */
    private static void loadDriver() {
        try {
            Class.forName(DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
            // Deliberately ignored: DriverManager will try automatic discovery.
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
