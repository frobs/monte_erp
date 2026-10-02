package com.entidaddesconocida.monte_erp.infrastructure.config;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Single JDBC connection point for the application (singleton).
 *
 * <p>The configuration (URL, user and password) is read from
 * {@code /application.properties} on the classpath, as stated by the class
 * diagram. If the connection has been closed or has dropped, it is reopened
 * transparently.</p>
 */
public final class DatabaseConnection {

    private static final String CONFIG_RESOURCE = "/application.properties";
    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    private static Connection connection;
    private static String url;
    private static String user;
    private static String password;

    private DatabaseConnection() {
        // Utility class: never instantiated.
    }

    /**
     * Returns the shared connection, opening it when it does not exist yet.
     *
     * @throws SQLException when the connection cannot be established
     */
    public static synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            loadConfiguration();
            loadDriver();
            connection = DriverManager.getConnection(url, user, password);
        }
        return connection;
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
        if (url == null) {
            loadConfiguration();
        }
        return url;
    }

    private static void loadConfiguration() {
        if (url != null) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream in = DatabaseConnection.class.getResourceAsStream(CONFIG_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found on the classpath: " + CONFIG_RESOURCE);
            }
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + CONFIG_RESOURCE, e);
        }
        url = require(properties, "db.url");
        user = properties.getProperty("db.user", "").trim();
        password = properties.getProperty("db.password", "");
    }

    private static String require(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing property '" + key + "' in " + CONFIG_RESOURCE);
        }
        return value.trim();
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
}
