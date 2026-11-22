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
 * Unit tests for {@link DriverManagerConnectionFactory}, the JDBC entry point.
 *
 * <p>The double {@link FakeJdbc} is registered in the global
 * {@link java.sql.DriverManager} and serves the {@code jdbc:fake:} URLs, so no
 * MySQL instance is needed. The settings belong to each factory instance (A7),
 * so the tests create their own and never touch shared state.</p>
 *
 * <p>The contract under test is "one connection per operation": no connection is
 * shared, so {@link ConnectionFactory#open()} never hands out a stale one and
 * the transactional helper always starts from a clean, committed connection.</p>
 */
class DriverManagerConnectionFactoryTest {

    private FakeJdbc fake;
    private DriverManagerConnectionFactory connections;

    @BeforeEach
    void setUp() {
        fake = new FakeJdbc().install();
        connections = new DriverManagerConnectionFactory();
    }

    @AfterEach
    void tearDown() {
        fake.uninstall();
    }

    private static DriverManagerConnectionFactory configured(String url) {
        return new DriverManagerConnectionFactory(url, "erp_user", "erp_pass");
    }

    @Test
    @DisplayName("Sin configuración, open falla con un SQLException explícito")
    void openWithoutConfigurationFails() {
        SQLException ex = assertThrows(SQLException.class, connections::open);
        assertTrue(ex.getMessage().contains("no está disponible"));
    }

    @Test
    @DisplayName("URL o usuario en blanco, o contraseña vacía, se rechazan")
    void incompleteConfigurationIsRejected() {
        assertThrows(SQLException.class, () -> configured("").open());
        assertThrows(SQLException.class, () -> configured("   ").open());
        assertThrows(SQLException.class,
                () -> new DriverManagerConnectionFactory(FakeJdbc.URL, "", "erp_pass").open());
        assertThrows(SQLException.class,
                () -> new DriverManagerConnectionFactory(FakeJdbc.URL, "erp_user", "").open());
        assertThrows(SQLException.class,
                () -> new DriverManagerConnectionFactory(FakeJdbc.URL, "erp_user", null).open());
    }

    @Test
    @DisplayName("Cada open entrega una conexión nueva, nunca una compartida")
    void openReturnsAFreshConnection() throws SQLException {
        try (Connection first = configured(FakeJdbc.URL).open();
             Connection second = configured(FakeJdbc.URL).open()) {
            assertNotSame(first, second);
        }
    }

    @Test
    @DisplayName("withConnection cierra la conexión al terminar")
    void withConnectionClosesTheConnection() throws SQLException {
        Connection[] used = new Connection[1];
        String result = configured(FakeJdbc.URL).withConnection(connection -> {
            used[0] = connection;
            return "ok";
        });

        assertEquals("ok", result);
        assertTrue(used[0].isClosed());
    }

    @Test
    @DisplayName("withConnection propaga el error y cierra la conexión")
    void withConnectionClosesOnFailure() {
        SQLException ex = assertThrows(SQLException.class, () ->
                configured(FakeJdbc.URL).withConnection(connection -> {
                    throw new SQLException("boom");
                }));

        assertEquals("boom", ex.getMessage());
    }

    @Test
    @DisplayName("withTransaction confirma y cierra la conexión")
    void withTransactionCommits() throws SQLException {
        Connection[] used = new Connection[1];
        configured(FakeJdbc.URL).withTransaction(connection -> {
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
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                configured(FakeJdbc.URL).withTransaction(connection -> {
                    throw new IllegalStateException("dominio");
                }));

        assertEquals("dominio", ex.getMessage());
        List<String> calls = fake.transactionCalls();
        assertTrue(calls.contains("rollback()"));
        assertTrue(!calls.contains("commit()"));
    }

    @Test
    @DisplayName("url devuelve la URL configurada en esa instancia")
    void urlReturnsConfiguredUrl() {
        assertEquals(FakeJdbc.URL, configured(FakeJdbc.URL).url());
    }

    @Test
    @DisplayName("testConnection abre y cierra una conexión propia")
    void testConnectionOpensAndClosesItsOwnConnection() throws SQLException {
        DriverManagerConnectionFactory.testConnection(FakeJdbc.URL, "erp_user", "erp_pass");

        assertTrue(fake.lastConnection().isClosed());
    }

    @Test
    @DisplayName("testConnection con una URL inválida propaga el SQLException")
    void testConnectionWithInvalidUrlFails() {
        assertThrows(SQLException.class,
                () -> DriverManagerConnectionFactory.testConnection("", "erp_user", "erp_pass"));
    }

    @Test
    @DisplayName("Bloquear en el trabajo no filtra la conexión a otros hilos")
    void connectionIsNotSharedAcrossThreads() throws Exception {
        DriverManagerConnectionFactory factory = configured(FakeJdbc.URL);

        AtomicBoolean distinct = new AtomicBoolean(false);
        try (Connection main = factory.open()) {
            Thread worker = Thread.ofVirtual().start(() -> {
                try (Connection other = factory.open()) {
                    distinct.set(other != main);
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            });
            worker.join();
        }
        assertTrue(distinct.get());
    }

    @Test
    @DisplayName("A7: dos fábricas mantienen configuraciones independientes")
    void twoFactoriesDoNotInterfere() throws SQLException {
        DriverManagerConnectionFactory first = configured("jdbc:fake:primera");
        DriverManagerConnectionFactory second =
                new DriverManagerConnectionFactory("jdbc:fake:segunda", "otro", "secreto");

        try (Connection ignoredFirst = first.open();
             Connection ignoredSecond = second.open()) {
            // Both opened with their own settings.
        }
        assertEquals(List.of("jdbc:fake:primera", "jdbc:fake:segunda"), fake.connectUrls());

        first.configure("jdbc:fake:tercera", "tercero", "otra");
        assertEquals("jdbc:fake:tercera", first.url());
        assertEquals("jdbc:fake:segunda", second.url());
    }
}
