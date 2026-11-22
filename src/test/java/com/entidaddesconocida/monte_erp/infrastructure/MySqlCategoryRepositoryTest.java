package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Category;
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
 * Unit tests for {@link MySqlCategoryRepository} against the in-memory
 * {@link FakeJdbc} double.
 *
 * <p>They pin down the SQL of every statement, the bound parameters and the
 * row-to-entity mapping (including the {@code active} flag of RF-04). No MySQL
 * instance is required.</p>
 */
class MySqlCategoryRepositoryTest {

    private FakeJdbc fake;
    private MySqlCategoryRepository repository;

    @BeforeEach
    void setUp() {
        fake = new FakeJdbc().install();
        repository = new MySqlCategoryRepository(
                new DriverManagerConnectionFactory(FakeJdbc.URL, "erp_user", "erp_pass"));
    }

    @AfterEach
    void tearDown() {
        fake.uninstall();
    }

    private static FakeJdbc.Row drinksRow() {
        return FakeJdbc.row(
                "id", 3,
                "name", "Bebidas",
                "description", "Refrescos y zumos",
                "active", true);
    }

    @Test
    @DisplayName("findAll ordena por nombre y mapea active")
    void findAllMapsRowsAndOrder() throws SQLException {
        fake.withRows(drinksRow());

        List<Category> categories = repository.findAll();

        assertEquals(1, categories.size());
        Category category = categories.get(0);
        assertEquals(3, category.getId());
        assertEquals("Bebidas", category.getName());
        assertEquals("Refrescos y zumos", category.getDescription());
        assertTrue(category.isActive());
        assertEquals("Activa", category.getStatusLabel());

        String sql = fake.executedStatements().get(0);
        assertTrue(sql.contains("FROM CATEGORY"));
        assertTrue(sql.contains("ORDER BY name"));
        assertFalse(sql.contains("WHERE"));
    }

    @Test
    @DisplayName("findActive filtra WHERE active = 1")
    void findActiveFiltersByActiveOne() throws SQLException {
        fake.withRows(drinksRow());

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
    @DisplayName("findActive(search) filtra nombre y descripción en la consulta")
    void findActiveWithSearchBindsLikePattern() throws SQLException {
        fake.withRows(drinksRow());

        repository.findActive("bebi");

        String sql = fake.preparedSql().get(0);
        assertTrue(sql.contains("WHERE active = 1"));
        assertTrue(sql.contains("name LIKE ? ESCAPE '!'"));
        assertTrue(sql.contains("description LIKE ? ESCAPE '!'"));
        assertTrue(sql.contains("ORDER BY name"));
        assertEquals("%bebi%", fake.boundParameters().get(0).get(1));
        assertEquals("%bebi%", fake.boundParameters().get(0).get(2));
    }

    @Test
    @DisplayName("findInactive(search) busca solo en las categorías dadas de baja")
    void findInactiveWithSearchUsesActiveZero() throws SQLException {
        fake.withRows();

        repository.findInactive("bebi");

        String sql = fake.preparedSql().get(0);
        assertTrue(sql.contains("WHERE active = 0"));
        assertEquals("%bebi%", fake.boundParameters().get(0).get(1));
    }

    @Test
    @DisplayName("findByName enlaza el nombre y devuelve la categoría")
    void findByNameBindsName() throws SQLException {
        fake.withRows(drinksRow());

        Category category = repository.findByName("Bebidas");

        assertNotNull(category);
        assertEquals("Bebidas", category.getName());
        String sql = fake.preparedSql().get(0);
        assertTrue(sql.contains("WHERE name = ?"));
        // A deactivated category still reserves its name (UNIQUE(name) is global),
        // so findByName must not filter by active.
        assertFalse(sql.contains("active ="), sql);
        assertEquals("Bebidas", fake.boundParameters().get(0).get(1));
    }

    @Test
    @DisplayName("findByName devuelve null cuando no existe")
    void findByNameReturnsNullWhenMissing() throws SQLException {
        fake.withRows();

        assertNull(repository.findByName("Fantasma"));
    }

    @Test
    @DisplayName("insert escribe name, description y active = 1 y devuelve la clave")
    void insertBindsColumnsAndReturnsGeneratedKey() throws SQLException {
        fake.withGeneratedKey(42);
        Category category = new Category("Bebidas", "Refrescos y zumos");

        int id = repository.insert(category);

        assertEquals(42, id);
        assertEquals(42, category.getId());
        assertTrue(fake.preparedSql().get(0).contains("INSERT INTO CATEGORY"));
        assertTrue(fake.preparedSql().get(0).contains("active"));
        Map<Integer, Object> parameters = fake.boundParameters().get(0);
        assertEquals("Bebidas", parameters.get(1));
        assertEquals("Refrescos y zumos", parameters.get(2));
    }

    @Test
    @DisplayName("insert escribe NULL en description cuando no hay descripción")
    void insertBindsNullDescription() throws SQLException {
        fake.withGeneratedKey(7);

        repository.insert(new Category("Bebidas", null));

        assertNull(fake.boundParameters().get(0).get(2));
    }

    @Test
    @DisplayName("update escribe name/description y no toca active")
    void updateWritesNameAndDescriptionOnly() throws SQLException {
        Category category = new Category(9, "Bebidas", "Refrescos", false);

        repository.update(category);

        String sql = fake.preparedSql().get(0);
        assertTrue(sql.contains("UPDATE CATEGORY"));
        assertTrue(sql.contains("name = ?"));
        assertTrue(sql.contains("description = ?"));
        assertTrue(sql.contains("WHERE id = ?"));
        assertFalse(sql.contains("active = ?"));
        Map<Integer, Object> parameters = fake.boundParameters().get(0);
        assertEquals("Bebidas", parameters.get(1));
        assertEquals("Refrescos", parameters.get(2));
        assertEquals(9, parameters.get(3));
    }

    @Test
    @DisplayName("deactivate pone active = 0 por id")
    void deactivateSetsActiveZero() throws SQLException {
        repository.deactivate(3);

        assertTrue(fake.preparedSql().get(0).contains("UPDATE CATEGORY SET active = 0"));
        assertEquals(3, fake.boundParameters().get(0).get(1));
    }

    @Test
    @DisplayName("activate pone active = 1 por id")
    void activateSetsActiveOne() throws SQLException {
        repository.activate(4);

        assertTrue(fake.preparedSql().get(0).contains("UPDATE CATEGORY SET active = 1"));
        assertEquals(4, fake.boundParameters().get(0).get(1));
    }
}
