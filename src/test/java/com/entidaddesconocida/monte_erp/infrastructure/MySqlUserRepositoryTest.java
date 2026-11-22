package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Role;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.infrastructure.config.DriverManagerConnectionFactory;
import com.entidaddesconocida.monte_erp.infrastructure.support.FakeJdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MySqlUserRepository} against the in-memory
 * {@link FakeJdbc} double.
 *
 * <p>They pin down the column list, the SQL of every statement, the bound
 * parameters and the row-to-entity mapping. No MySQL instance is required.</p>
 */
class MySqlUserRepositoryTest {

    private static final String HASH = "pbkdf2-sha256$210000$c2FsdA==$a2V5";

    private FakeJdbc fake;
    private MySqlUserRepository repository;

    @BeforeEach
    void setUp() {
        fake = new FakeJdbc().install();
        repository = new MySqlUserRepository(
                new DriverManagerConnectionFactory(FakeJdbc.URL, "erp_user", "erp_pass"));
    }

    @AfterEach
    void tearDown() {
        fake.uninstall();
    }

    private static FakeJdbc.Row anaRow() {
        return FakeJdbc.row(
                "id", 1,
                "name", "Ana",
                "username", "ana",
                "password_hash", HASH,
                "role", Role.ADMIN.dbValue(),
                "active", true);
    }

    @Test
    @DisplayName("findByUsername mapea la fila a un User con todas sus columnas")
    void findByUsernameMapsRow() throws SQLException {
        fake.withRows(anaRow());

        User user = repository.findByUsername("ana");

        assertNotNull(user);
        assertEquals(1, user.getId());
        assertEquals("Ana", user.getName());
        assertEquals("ana", user.getUsername());
        assertEquals(HASH, user.getPasswordHash());
        assertEquals(Role.ADMIN, user.getRole());
        assertTrue(user.isActive());

        assertEquals(1, fake.preparedSql().size());
        assertTrue(fake.preparedSql().get(0).contains("FROM USER"));
        assertTrue(fake.preparedSql().get(0).contains("WHERE username = ?"));
        assertEquals("ana", fake.boundParameters().get(0).get(1));
    }

    @Test
    @DisplayName("findByUsername devuelve null cuando no hay filas")
    void findByUsernameReturnsNullWhenMissing() throws SQLException {
        fake.withRows();
        assertNull(repository.findByUsername("nobody"));
    }

    @Test
    @DisplayName("findAll mapea todas las filas y usa ORDER BY username")
    void findAllMapsEveryRow() throws SQLException {
        fake.withRows(
                anaRow(),
                FakeJdbc.row("id", 2, "name", "Bea", "username", "bea",
                        "password_hash", HASH, "role", Role.EMPLOYEE.dbValue(), "active", false));

        List<User> users = repository.findAll();

        assertEquals(2, users.size());
        assertEquals("ana", users.get(0).getUsername());
        assertTrue(users.get(0).isActive());
        assertEquals("bea", users.get(1).getUsername());
        assertFalse(users.get(1).isActive());

        assertEquals(1, fake.executedStatements().size());
        assertTrue(fake.executedStatements().get(0).contains("FROM USER"));
        assertTrue(fake.executedStatements().get(0).contains("ORDER BY username"));
    }

    @Test
    @DisplayName("insert enlaza las cinco columnas y asigna la clave generada")
    void insertBindsColumnsAndReturnsGeneratedKey() throws SQLException {
        fake.withGeneratedKey(42);
        User user = new User("Nuevo", "nuevo", HASH, Role.EMPLOYEE, true);

        int id = repository.insert(user);

        assertEquals(42, id);
        assertEquals(42, user.getId());

        assertTrue(fake.preparedSql().get(0).contains("INSERT INTO USER"));
        Map<Integer, Object> parameters = fake.boundParameters().get(0);
        assertEquals("Nuevo", parameters.get(1));
        assertEquals("nuevo", parameters.get(2));
        assertEquals(HASH, parameters.get(3));
        assertEquals(Role.EMPLOYEE.dbValue(), parameters.get(4));
        assertEquals(true, parameters.get(5));
    }

    @Test
    @DisplayName("insert sin clave generada deja el id a 0")
    void insertWithoutGeneratedKeyLeavesZeroId() throws SQLException {
        fake.withRows();
        User user = new User("Nuevo", "nuevo", HASH, Role.ADMIN, true);

        assertEquals(0, repository.insert(user));
        assertEquals(0, user.getId());
    }

    @Test
    @DisplayName("update enlaza las cinco columnas más el id")
    void updateBindsEveryColumnAndId() throws SQLException {
        User user = new User("Ana", "ana", HASH, Role.ADMIN, true);
        user.setId(7);

        repository.update(user);

        assertTrue(fake.preparedSql().get(0).contains("UPDATE USER SET"));
        assertTrue(fake.preparedSql().get(0).contains("WHERE id = ?"));
        Map<Integer, Object> parameters = fake.boundParameters().get(0);
        assertEquals("Ana", parameters.get(1));
        assertEquals("ana", parameters.get(2));
        assertEquals(HASH, parameters.get(3));
        assertEquals(Role.ADMIN.dbValue(), parameters.get(4));
        assertEquals(true, parameters.get(5));
        assertEquals(7, parameters.get(6));
    }

    @Test
    @DisplayName("deactivate hace una baja lógica (active = 0) por id")
    void deactivateIsLogicalAndBindsId() throws SQLException {
        repository.deactivate(3);

        String sql = fake.preparedSql().get(0);
        assertTrue(sql.contains("UPDATE USER SET active = 0"));
        assertTrue(sql.contains("WHERE id = ?"));
        assertEquals(3, fake.boundParameters().get(0).get(1));
    }
}
