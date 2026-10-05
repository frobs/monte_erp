package com.entidaddesconocida.monte_erp.infrastructure.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Single JDBC connection point for the application (singleton).
 *
 * <p>The configuration is no longer read from the classpath: it is set through
 * {@link #configure(String, String, String)} by {@link DatabaseSetupService}
 * once the user has saved the URL (properties file) and the credentials (OS
 * keyring). If the connection has been closed or has dropped, it is reopened
 * transparently.</p>
 */
public final class DatabaseConnection {

    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    private static Connection connection;
    private static String url;
    private static String user;
    private static String password;

    private DatabaseConnection() {
        // Utility class: never instantiated.
    }

    /**
     * Points the shared connection at the given settings. The previous
     * connection, if any, is closed so the next {@link #getConnection()} opens a
     * fresh one.
     */
    public static synchronized void configure(String url, String user, String password) {
        close();
        DatabaseConnection.url = url;
        DatabaseConnection.user = user;
        DatabaseConnection.password = password;
    }

    /**
     * Returns the shared connection, opening it when it does not exist yet.
     *
     * @throws SQLException when the connection cannot be established or the
     *                      configuration is missing
     */
    public static synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            loadDriver();
            if (isBlank(url) || isBlank(user) || password == null || password.isEmpty()) {
                throw new SQLException("La configuración de la base de datos no está disponible "
                        + "(url, usuario o contraseña vacíos).");
            }
            connection = DriverManager.getConnection(url, user, password);
        }
        return connection;
    }

    /**
     * Verifies that the given settings can open a JDBC connection. The shared
     * connection is left untouched.
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
     * Closes the shared connection (called when the application shuts down).
     */
    public static synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            // Shutting down: there is nothing useful to do with the error.
        } finally {
            connection = null;
        }
    }

    /**
     * Configured JDBC URL (handy to display the environment in the interface).
     */
    public static synchronized String getUrl() {
        return url;
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
