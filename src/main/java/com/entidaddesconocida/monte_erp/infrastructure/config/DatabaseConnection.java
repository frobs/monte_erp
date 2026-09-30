package com.entidaddesconocida.monte_erp.infrastructure.config;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Punto único de conexión JDBC de la aplicación (patrón singleton).
 *
 * <p>La configuración (URL, usuario y contraseña) se lee de
 * {@code /application.properties} en el classpath, tal y como fija el
 * diagrama de clases. Si la conexión se ha cerrado o se ha caído, se vuelve a
 * abrir de forma transparente.</p>
 */
public final class DatabaseConnection {

    private static final String CONFIG_RESOURCE = "/application.properties";
    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    private static Connection connection;
    private static String url;
    private static String user;
    private static String password;

    private DatabaseConnection() {
        // Utilidad: no se instancia.
    }

    /**
     * Devuelve la conexión compartida, abriéndola si aún no existe.
     *
     * @throws SQLException si no se puede establecer la conexión
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
     * Cierra la conexión compartida (se invoca al terminar la aplicación).
     */
    public static synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            // Cerrando: no hay nada útil que hacer con el error.
        } finally {
            connection = null;
        }
    }

    /**
     * URL JDBC configurada (útil para mostrar el entorno en la interfaz).
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
                throw new IllegalStateException("No se encontró " + CONFIG_RESOURCE + " en el classpath");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + CONFIG_RESOURCE, e);
        }
        url = require(properties, "db.url");
        user = properties.getProperty("db.user", "").trim();
        password = properties.getProperty("db.password", "");
    }

    private static String require(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Falta la propiedad '" + key + "' en " + CONFIG_RESOURCE);
        }
        return value.trim();
    }

    /**
     * Registra el driver de MySQL. Si no estuviera disponible, se propaga el
     * error real al intentar conectar (DriverManager también lo descubre vía
     * {@code ServiceLoader}, por lo que un fallo aquí no es definitivo).
     */
    private static void loadDriver() {
        try {
            Class.forName(DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
            // Ignorado a propósito: DriverManager intentará el descubrimiento automático.
        }
    }
}
