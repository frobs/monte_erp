package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Customer;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;
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
 * Unit tests for {@link MySqlCustomerRepository} against the in-memory
 * {@link FakeJdbc} double.
 *
 * <p>They pin down the SQL of every statement, the bound parameters and the
 * row-to-entity mapping (including the {@code active} flag of RF-06). No MySQL
 * instance is required.</p>
 */
class MySqlCustomerRepositoryTest {

    private FakeJdbc fake;
    private MySqlCustomerRepository repository;

    @BeforeEach
    void setUp() {
        fake = new FakeJdbc().install();
        DatabaseConnection.configure(FakeJdbc.URL, "erp_user", "erp_pass");
        repository = new MySqlCustomerRepository();
    }

    @AfterEach
    void tearDown() {
        DatabaseConnection.configure(null, null, null);
        fake.uninstall();
    }

    private static FakeJdbc.Row acmeRow() {
        return FakeJdbc.row(
                "id", 3,
                "name", "Acme",
                "phone", "600111222",
                "email", "acme@example.com",
                "address", "Calle Mayor 1",
                "active", true);
    }

    @Test
    @DisplayName("findAll ordena por nombre y mapea active")
    void findAllMapsRowsAndOrder() throws SQLException {
        fake.withRows(acmeRow());

        List<Customer> customers = repository.findAll();

        assertEquals(1, customers.size());
        Customer customer = customers.get(0);
        assertEquals(3, customer.getId());
        assertEquals("Acme", customer.getName());
        assertEquals("600111222", customer.getPhone());
        assertEquals("acme@example.com", customer.getEmail());
        assertEquals("Calle Mayor 1", customer.getAddress());
        assertTrue(customer.isActive());
        assertEquals("Activo", customer.getStatusLabel());

        String sql = fake.executedStatements().get(0);
        assertTrue(sql.contains("FROM CUSTOMER"));
        assertTrue(sql.contains("ORDER BY name"));
        assertFalse(sql.contains("WHERE"));
    }

    @Test
    @DisplayName("findActive filtra WHERE active = 1")
    void findActiveFiltersByActiveOne() throws SQLException {
        fake.withRows(acmeRow());

        repository.findActive();

        assertTrue(fake.executedStatements().get(0).contains("WHERE active = 1"));
    }

    @Test
    @DisplayName("findInactive filtra WHERE active = 0")
    void findInactiveFiltersByActiveZero() throws SQLException {
        fake.withRows();

        repository.findInactive();

        assertTrue(fake.executedStatements().get(0).contains("WHERE active = 0"));
    }

    @Test
    @DisplayName("findActive(search) filtra nombre, teléfono y correo en la consulta")
    void findActiveWithSearchBindsLikePattern() throws SQLException {
        fake.withRows(acmeRow());

        repository.findActive("acme@");

        String sql = fake.preparedSql().get(0);
        assertTrue(sql.contains("WHERE active = 1"));
        assertTrue(sql.contains("name LIKE ? ESCAPE '!'"));
        assertTrue(sql.contains("phone LIKE ? ESCAPE '!'"));
        assertTrue(sql.contains("email LIKE ? ESCAPE '!'"));
        assertEquals("%acme@%", fake.boundParameters().get(0).get(1));
        assertEquals("%acme@%", fake.boundParameters().get(0).get(2));
        assertEquals("%acme@%", fake.boundParameters().get(0).get(3));
    }

    @Test
    @DisplayName("findInactive(search) busca solo en los clientes dados de baja")
    void findInactiveWithSearchUsesActiveZero() throws SQLException {
        fake.withRows();

        repository.findInactive("acme");

        String sql = fake.preparedSql().get(0);
        assertTrue(sql.contains("WHERE active = 0"));
        assertEquals("%acme%", fake.boundParameters().get(0).get(1));
    }

    @Test
    @DisplayName("findByName enlaza el nombre y devuelve el cliente")
    void findByNameBindsName() throws SQLException {
        fake.withRows(acmeRow());

        Customer customer = repository.findByName("Acme");

        assertNotNull(customer);
        assertEquals("Acme", customer.getName());
        String sql = fake.preparedSql().get(0);
        assertTrue(sql.contains("WHERE name = ?"));
        // A deactivated customer still reserves its name (UNIQUE(name) is global),
        // so findByName must not filter by active.
        assertFalse(sql.contains("active ="), sql);
        assertEquals("Acme", fake.boundParameters().get(0).get(1));
    }

    @Test
    @DisplayName("findByName devuelve null cuando no existe")
    void findByNameReturnsNullWhenMissing() throws SQLException {
        fake.withRows();

        assertNull(repository.findByName("Fantasma"));
    }

    @Test
    @DisplayName("insert escribe name, phone, email, address y active = 1 y devuelve la clave")
    void insertBindsColumnsAndReturnsGeneratedKey() throws SQLException {
        fake.withGeneratedKey(42);
        Customer customer = new Customer("Acme", "600111222", "acme@example.com", "Calle Mayor 1");

        int id = repository.insert(customer);

        assertEquals(42, id);
        assertEquals(42, customer.getId());
        assertTrue(fake.preparedSql().get(0).contains("INSERT INTO CUSTOMER"));
        assertTrue(fake.preparedSql().get(0).contains("active"));
        Map<Integer, Object> parameters = fake.boundParameters().get(0);
        assertEquals("Acme", parameters.get(1));
        assertEquals("600111222", parameters.get(2));
        assertEquals("acme@example.com", parameters.get(3));
        assertEquals("Calle Mayor 1", parameters.get(4));
    }

    @Test
    @DisplayName("insert escribe NULL en los campos opcionales cuando no hay contacto")
    void insertBindsNullContact() throws SQLException {
        fake.withGeneratedKey(7);

        repository.insert(new Customer("Acme", null, null, null));

        Map<Integer, Object> parameters = fake.boundParameters().get(0);
        assertNull(parameters.get(2));
        assertNull(parameters.get(3));
        assertNull(parameters.get(4));
    }

    @Test
    @DisplayName("update escribe name/phone/email/address y no toca active")
    void updateWritesContactOnly() throws SQLException {
        Customer customer =
                new Customer(9, "Acme", "600111222", "acme@example.com", "Calle Mayor 1", false);

        repository.update(customer);

        String sql = fake.preparedSql().get(0);
        assertTrue(sql.contains("UPDATE CUSTOMER"));
        assertTrue(sql.contains("name = ?"));
        assertTrue(sql.contains("phone = ?"));
        assertTrue(sql.contains("email = ?"));
        assertTrue(sql.contains("address = ?"));
        assertTrue(sql.contains("WHERE id = ?"));
        assertFalse(sql.contains("active = ?"));
        Map<Integer, Object> parameters = fake.boundParameters().get(0);
        assertEquals("Acme", parameters.get(1));
        assertEquals("600111222", parameters.get(2));
        assertEquals("acme@example.com", parameters.get(3));
        assertEquals("Calle Mayor 1", parameters.get(4));
        assertEquals(9, parameters.get(5));
    }

    @Test
    @DisplayName("deactivate pone active = 0 por id")
    void deactivateSetsActiveZero() throws SQLException {
        repository.deactivate(3);

        assertTrue(fake.preparedSql().get(0).contains("UPDATE CUSTOMER SET active = 0"));
        assertEquals(3, fake.boundParameters().get(0).get(1));
    }

    @Test
    @DisplayName("activate pone active = 1 por id")
    void activateSetsActiveOne() throws SQLException {
        repository.activate(4);

        assertTrue(fake.preparedSql().get(0).contains("UPDATE CUSTOMER SET active = 1"));
        assertEquals(4, fake.boundParameters().get(0).get(1));
    }
}
