package com.entidaddesconocida.monte_erp.infrastructure.config;

import com.entidaddesconocida.monte_erp.infrastructure.support.FakeJdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DatabaseConnection}, the single JDBC entry point.
 *
 * <p>The double {@link FakeJdbc} is registered in the global
 * {@link java.sql.DriverManager} and serves the {@code jdbc:fake:} URLs, so no
 * MySQL instance is needed. Because the class holds static state, every test
 * resets it.</p>
 */
class DatabaseConnectionTest {

    private FakeJdbc fake;

    @BeforeEach
    void setUp() {
        DatabaseConnection.configure(null, null, null);
        fake = new FakeJdbc().install();
    }

    @AfterEach
    void tearDown() {
        DatabaseConnection.close();
        DatabaseConnection.configure(null, null, null);
        fake.uninstall();
    }

    @Test
    @DisplayName("Sin configuración, getConnection falla con un SQLException explícito")
    void getConnectionWithoutConfigurationFails() {
        SQLException ex = assertThrows(SQLException.class, DatabaseConnection::getConnection);
        assertTrue(ex.getMessage().contains("no está disponible"));
    }

    @Test
    @DisplayName("URL o usuario en blanco, o contraseña vacía, se rechazan")
    void incompleteConfigurationIsRejected() {
        DatabaseConnection.configure("", "erp_user", "erp_pass");
        assertThrows(SQLException.class, DatabaseConnection::getConnection);

        DatabaseConnection.configure("   ", "erp_user", "erp_pass");
        assertThrows(SQLException.class, DatabaseConnection::getConnection);

        DatabaseConnection.configure(FakeJdbc.URL, "", "erp_pass");
        assertThrows(SQLException.class, DatabaseConnection::getConnection);

        DatabaseConnection.configure(FakeJdbc.URL, "erp_user", "");
        assertThrows(SQLException.class, DatabaseConnection::getConnection);

        DatabaseConnection.configure(FakeJdbc.URL, "erp_user", null);
        assertThrows(SQLException.class, DatabaseConnection::getConnection);
    }

    @Test
    @DisplayName("getConnection abre la conexión una sola vez y la reutiliza")
    void getConnectionOpensAndReuses() throws SQLException {
        DatabaseConnection.configure(FakeJdbc.URL, "erp_user", "erp_pass");

        Connection first = DatabaseConnection.getConnection();
        Connection second = DatabaseConnection.getConnection();

        assertSame(first, second);
    }

    @Test
    @DisplayName("getConnection reabre la conexión si se ha caído")
    void getConnectionReopensWhenDropped() throws SQLException {
        DatabaseConnection.configure(FakeJdbc.URL, "erp_user", "erp_pass");

        Connection first = DatabaseConnection.getConnection();
        fake.markConnectionClosed();
        Connection second = DatabaseConnection.getConnection();

        assertNotSame(first, second);
    }

    @Test
    @DisplayName("Tras close(), la siguiente llamada abre una conexión nueva")
    void closeForcesReopen() throws SQLException {
        DatabaseConnection.configure(FakeJdbc.URL, "erp_user", "erp_pass");

        Connection first = DatabaseConnection.getConnection();
        DatabaseConnection.close();
        Connection second = DatabaseConnection.getConnection();

        assertNotSame(first, second);
    }

    @Test
    @DisplayName("close() es idempotente y seguro sin conexión abierta")
    void closeIsIdempotent() {
        DatabaseConnection.close();
        DatabaseConnection.close();
    }

    @Test
    @DisplayName("getUrl devuelve la URL configurada")
    void getUrlReturnsConfiguredUrl() {
        DatabaseConnection.configure(FakeJdbc.URL, "erp_user", "erp_pass");
        assertSame(FakeJdbc.URL, DatabaseConnection.getUrl());
    }

    @Test
    @DisplayName("testConnection abre y cierra sin tocar la conexión compartida")
    void testConnectionLeavesSharedConnectionUntouched() throws SQLException {
        DatabaseConnection.configure(FakeJdbc.URL, "erp_user", "erp_pass");
        Connection shared = DatabaseConnection.getConnection();

        DatabaseConnection.testConnection(FakeJdbc.URL, "erp_user", "erp_pass");

        assertSame(shared, DatabaseConnection.getConnection());
    }

    @Test
    @DisplayName("testConnection con una URL inválida propaga el SQLException")
    void testConnectionWithInvalidUrlFails() {
        assertThrows(SQLException.class, () -> DatabaseConnection.testConnection("", "erp_user", "erp_pass"));
    }
}
