package com.entidaddesconocida.monte_erp.infrastructure.config;

import com.entidaddesconocida.monte_erp.infrastructure.ConnectionFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * {@link ConnectionFactory} backed by {@link DriverManager}.
 *
 * <p>The settings belong to the instance, not to the class: the application
 * creates one factory in its composition root and injects it into every
 * repository, while each test can create its own with different settings and
 * they cannot interfere (A7). The factory is configured through
 * {@link #configure(String, String, String)} once the user has saved the URL
 * (properties file) and the credentials (OS keyring); nothing is kept open, so
 * the next operation uses the new values.</p>
 */
public final class DriverManagerConnectionFactory implements ConnectionFactory {

    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    private volatile String url;
    private volatile String user;
    private volatile String password;

    /** Creates an unconfigured factory: {@link #open()} fails until configured. */
    public DriverManagerConnectionFactory() {
    }

    /** Creates a factory already pointed at the given settings. */
    public DriverManagerConnectionFactory(String url, String user, String password) {
        configure(url, user, password);
    }

    /**
     * Points this factory at the given settings. Nothing is kept open: the next
     * operation uses the new values.
     */
    public void configure(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    @Override
    public Connection open() throws SQLException {
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

    @Override
    public String url() {
        return url;
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
