package com.entidaddesconocida.monte_erp.infrastructure.config;

import com.entidaddesconocida.monte_erp.infrastructure.support.FakeJdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DatabaseConnection}, the JDBC entry point.
 *
 * <p>The double {@link FakeJdbc} is registered in the global
 * {@link java.sql.DriverManager} and serves the {@code jdbc:fake:} URLs, so no
 * MySQL instance is needed. Because the class holds static state, every test
 * resets it.</p>
 *
 * <p>The contract under test is "one connection per operation": no connection is
 * shared, so {@link DatabaseConnection#open()} never hands out a stale one and
 * the transactional helper always starts from a clean, committed connection.</p>
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
        DatabaseConnection.configure(null, null, null);
        fake.uninstall();
    }

    @Test
    @DisplayName("Sin configuración, open falla con un SQLException explícito")
    void openWithoutConfigurationFails() {
        SQLException ex = assertThrows(SQLException.class, DatabaseConnection::open);
        assertTrue(ex.getMessage().contains("no está disponible"));
    }

    @Test
    @DisplayName("URL o usuario en blanco, o contraseña vacía, se rechazan")
    void incompleteConfigurationIsRejected() {
        DatabaseConnection.configure("", "erp_user", "erp_pass");
        assertThrows(SQLException.class, DatabaseConnection::open);

        DatabaseConnection.configure("   ", "erp_user", "erp_pass");
        assertThrows(SQLException.class, DatabaseConnection::open);

        DatabaseConnection.configure(FakeJdbc.URL, "", "erp_pass");
        assertThrows(SQLException.class, DatabaseConnection::open);

        DatabaseConnection.configure(FakeJdbc.URL, "erp_user", "");
        assertThrows(SQLException.class, DatabaseConnection::open);

        DatabaseConnection.configure(FakeJdbc.URL, "erp_user", null);
        assertThrows(SQLException.class, DatabaseConnection::open);
    }

    @Test
    @DisplayName("Cada open entrega una conexión nueva, nunca una compartida")
    void openReturnsAFreshConnection() throws SQLException {
        DatabaseConnection.configure(FakeJdbc.URL, "erp_user", "erp_pass");

        try (Connection first = DatabaseConnection.open();
             Connection second = DatabaseConnection.open()) {
            assertNotSame(first, second);
        }
    }

    @Test
    @DisplayName("withConnection cierra la conexión al terminar")
    void withConnectionClosesTheConnection() throws SQLException {
        DatabaseConnection.configure(FakeJdbc.URL, "erp_user", "erp_pass");

        Connection[] used = new Connection[1];
        String result = DatabaseConnection.withConnection(connection -> {
            used[0] = connection;
            return "ok";
        });

        assertEquals("ok", result);
        assertTrue(used[0].isClosed());
    }

    @Test
    @DisplayName("withConnection propaga el error y cierra la conexión")
    void withConnectionClosesOnFailure() {
        DatabaseConnection.configure(FakeJdbc.URL, "erp_user", "erp_pass");

        SQLException ex = assertThrows(SQLException.class, () ->
                DatabaseConnection.withConnection(connection -> {
                    throw new SQLException("boom");
                }));

        assertEquals("boom", ex.getMessage());
    }

    @Test
    @DisplayName("withTransaction confirma y cierra la conexión")
    void withTransactionCommits() throws SQLException {
        DatabaseConnection.configure(FakeJdbc.URL, "erp_user", "erp_pass");

        Connection[] used = new Connection[1];
        DatabaseConnection.withTransaction(connection -> {
            used[0] = connection;
            return null;
        });

        List<String> calls = fake.transactionCalls();
        assertTrue(calls.contains("setAutoCommit(false)"));
        assertTrue(calls.contains("commit()"));
        assertTrue(used[0].isClosed());
    }

    @Test
    @DisplayName("withTransaction deshace y propaga el error de dominio")
    void withTransactionRollsBackOnDomainFailure() {
        DatabaseConnection.configure(FakeJdbc.URL, "erp_user", "erp_pass");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                DatabaseConnection.withTransaction(connection -> {
                    throw new IllegalStateException("dominio");
                }));

        assertEquals("dominio", ex.getMessage());
        List<String> calls = fake.transactionCalls();
        assertTrue(calls.contains("rollback()"));
        assertTrue(!calls.contains("commit()"));
    }

    @Test
    @DisplayName("getUrl devuelve la URL configurada")
    void getUrlReturnsConfiguredUrl() {
        DatabaseConnection.configure(FakeJdbc.URL, "erp_user", "erp_pass");
        assertEquals(FakeJdbc.URL, DatabaseConnection.getUrl());
    }

    @Test
    @DisplayName("testConnection abre y cierra una conexión propia")
    void testConnectionOpensAndClosesItsOwnConnection() throws SQLException {
        DatabaseConnection.testConnection(FakeJdbc.URL, "erp_user", "erp_pass");

        assertTrue(fake.lastConnection().isClosed());
    }

    @Test
    @DisplayName("testConnection con una URL inválida propaga el SQLException")
    void testConnectionWithInvalidUrlFails() {
        assertThrows(SQLException.class,
                () -> DatabaseConnection.testConnection("", "erp_user", "erp_pass"));
    }

    @Test
    @DisplayName("Bloquear en el trabajo no filtra la conexión a otros hilos")
    void connectionIsNotSharedAcrossThreads() throws Exception {
        DatabaseConnection.configure(FakeJdbc.URL, "erp_user", "erp_pass");

        AtomicBoolean distinct = new AtomicBoolean(false);
        try (Connection main = DatabaseConnection.open()) {
            Thread worker = Thread.ofVirtual().start(() -> {
                try (Connection other = DatabaseConnection.open()) {
                    distinct.set(other != main);
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
            worker.join();
        }
        assertTrue(distinct.get());
    }
}
