package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Category;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.ProductFilter;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;
import com.entidaddesconocida.monte_erp.infrastructure.support.FakeJdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MySqlProductRepository} against the in-memory
 * {@link FakeJdbc} double.
 *
 * <p>They pin down the SQL of every statement (including the {@code LEFT JOIN}
 * that carries the optional category name), the bound parameters and the
 * row-to-entity mapping. No MySQL instance is required.</p>
 */
class MySqlProductRepositoryTest {

    private FakeJdbc fake;
    private MySqlProductRepository repository;

    @BeforeEach
    void setUp() {
        fake = new FakeJdbc().install();
        DatabaseConnection.configure(FakeJdbc.URL, "erp_user", "erp_pass");
        repository = new MySqlProductRepository();
    }

    @AfterEach
    void tearDown() {
        DatabaseConnection.configure(null, null, null);
        fake.uninstall();
    }

    private static FakeJdbc.Row coffeeRow() {
        return FakeJdbc.row(
                "id", 1,
                "name", "Café molido",
                "sku", "CAF-001",
                "description", "Paquete de 250 g",
                "price", new BigDecimal("4.50"),
                "min_stock", 5,
                "active", true,
                "category_id", 3,
                "category_name", "Bebidas",
                "category_active", true);
    }

    @Test
    @DisplayName("findAll hace LEFT JOIN con categoría y mapea todas las columnas")
    void findAllMapsJoinedRow() throws SQLException {
        fake.withRows(coffeeRow());

        List<Product> products = repository.findAll();

        assertEquals(1, products.size());
        Product product = products.get(0);
        assertEquals(1, product.getId());
        assertEquals("Café molido", product.getName());
        assertEquals("CAF-001", product.getSku());
        assertEquals("Paquete de 250 g", product.getDescription());
        assertEquals(new BigDecimal("4.50"), product.getPrice());
        assertEquals(5, product.getMinStock());
        assertTrue(product.isActive());
        assertEquals(3, product.getCategory().getId());
        assertEquals("Bebidas", product.getCategory().getName());
        assertTrue(product.getCategory().isActive());

        String sql = fake.executedStatements().get(0);
        assertTrue(sql.contains("FROM PRODUCT p"));
        assertTrue(sql.contains("LEFT JOIN CATEGORY c ON p.category_id = c.id"));
        // The category's active flag is what drives the D4 rule of the form, so the
        // projection is pinned here (FakeJdbc maps by column name, so dropping the
        // column from the SELECT would otherwise go unnoticed).
        assertTrue(sql.contains("c.active AS category_active"), sql);
        assertTrue(sql.contains("WHERE p.active = 1"));
        assertTrue(sql.contains("ORDER BY p.name"));
    }

    @Test
    @DisplayName("Una categoría dada de baja en el JOIN se mapea con active = false")
    void mapsInactiveCategory() throws SQLException {
        fake.withRows(FakeJdbc.row(
                "id", 1,
                "name", "Café molido",
                "sku", "CAF-001",
                "description", null,
                "price", new BigDecimal("4.50"),
                "min_stock", 0,
                "active", true,
                "category_id", 3,
                "category_name", "Bebidas",
                "category_active", false));

        Product product = repository.findAll().get(0);

        assertFalse(product.getCategory().isActive());
    }

    @Test
    @DisplayName("Un producto sin categoría se mapea con categoría nula")
    void mapsNullCategory() throws SQLException {
        fake.withRows(FakeJdbc.row(
                "id", 2,
                "name", "Chicles",
                "sku", "CHI-001",
                "description", null,
                "price", new BigDecimal("1.20"),
                "min_stock", 0,
                "active", true,
                "category_id", null,
                "category_name", null));

        Product product = repository.findAll().get(0);

        assertNull(product.getCategory());
    }

    @Test
    @DisplayName("insert escribe NULL en category_id cuando el producto no tiene categoría")
    void insertBindsNullCategory() throws SQLException {
        fake.withGeneratedKey(7);
        Product product = new Product("Chicles", "CHI-001", null, new BigDecimal("1.20"), 0,
                null, true);

        repository.insert(product);

        assertNull(fake.boundParameters().get(0).get(6));
    }

    @Test
    @DisplayName("findInactive filtra por active = 0")
    void findInactiveFiltersByActiveZero() throws SQLException {
        fake.withRows();

        repository.findInactive();

        assertTrue(fake.executedStatements().get(0).contains("WHERE p.active = 0"));
    }

    @Test
    @DisplayName("findAll(filter) filtra texto, categoría y proveedor en la consulta")
    void findAllWithFilterBindsEveryCriterion() throws SQLException {
        fake.withRows(coffeeRow());

        List<Product> products = repository.findAll(ProductFilter.of("cafe", 3, 7));

        assertEquals(1, products.size());
        String sql = fake.preparedSql().get(0);
        assertTrue(sql.contains("p.active = ?"));
        assertTrue(sql.contains("p.name LIKE ? ESCAPE '!' OR p.sku LIKE ? ESCAPE '!'"));
        assertTrue(sql.contains("p.category_id = ?"));
        assertTrue(sql.contains("JOIN PURCHASE fp"));
        assertTrue(sql.contains("fp.supplier_id = ?"));
        assertTrue(sql.contains("ORDER BY p.name"));
        Map<Integer, Object> parameters = fake.boundParameters().get(0);
        assertEquals(true, parameters.get(1));
        assertEquals("%cafe%", parameters.get(2));
        assertEquals("%cafe%", parameters.get(3));
        assertEquals("%cafe%", parameters.get(4));
        assertEquals(3, parameters.get(5));
        assertEquals(3, parameters.get(6));
        assertEquals(7, parameters.get(7));
        assertEquals(7, parameters.get(8));
    }

    @Test
    @DisplayName("findInactive(filter) busca solo en las filas dadas de baja")
    void findInactiveWithFilterUsesActiveZero() throws SQLException {
        fake.withRows();

        repository.findInactive(ProductFilter.of(null, 3, null));

        String sql = fake.preparedSql().get(0);
        assertEquals(false, fake.boundParameters().get(0).get(1));
        assertNull(fake.boundParameters().get(0).get(2));
        assertTrue(sql.contains("p.category_id = ?"));
    }

    @Test
    @DisplayName("findBySku enlaza el SKU y devuelve null cuando no hay filas")
    void findBySkuBindsSku() throws SQLException {
        fake.withRows(coffeeRow());

        Product product = repository.findBySku("CAF-001");

        assertNotNull(product);
        assertEquals("CAF-001", product.getSku());
        assertTrue(fake.preparedSql().get(0).contains("WHERE p.sku = ?"));
        assertEquals("CAF-001", fake.boundParameters().get(0).get(1));

        fake.withRows();
        assertNull(repository.findBySku("nope"));
    }

    @Test
    @DisplayName("insert enlaza las siete columnas y asigna la clave generada")
    void insertBindsColumnsAndReturnsGeneratedKey() throws SQLException {
        fake.withGeneratedKey(42);
        Product product = new Product("Café molido", "CAF-001", null, new BigDecimal("4.50"), 0,
                new Category(3, "Bebidas", null), true);

        int id = repository.insert(product);

        assertEquals(42, id);
        assertEquals(42, product.getId());
        assertTrue(fake.preparedSql().get(0).contains("INSERT INTO PRODUCT"));
        Map<Integer, Object> parameters = fake.boundParameters().get(0);
        assertEquals("Café molido", parameters.get(1));
        assertEquals("CAF-001", parameters.get(2));
        assertNull(parameters.get(3));
        assertEquals(new BigDecimal("4.50"), parameters.get(4));
        assertEquals(0, parameters.get(5));
        assertEquals(3, parameters.get(6));
        assertEquals(true, parameters.get(7));
    }

    @Test
    @DisplayName("update escribe description y min_stock para no borrarlos al editar")
    void updatePreservesDescriptionAndMinStock() throws SQLException {
        Product product = new Product("Café molido", "CAF-001", "Paquete de 250 g",
                new BigDecimal("9.99"), 5, new Category(3, "Bebidas", null), true);
        product.setId(9);

        repository.update(product);

        String sql = fake.preparedSql().get(0);
        assertTrue(sql.contains("UPDATE PRODUCT"));
        assertTrue(sql.contains("description = ?"));
        assertTrue(sql.contains("min_stock = ?"));
        assertTrue(sql.contains("WHERE id = ?"));
        Map<Integer, Object> parameters = fake.boundParameters().get(0);
        assertEquals("Paquete de 250 g", parameters.get(3));
        assertEquals(5, parameters.get(5));
        assertEquals(true, parameters.get(7));
        assertEquals(9, parameters.get(8));
    }

    @Test
    @DisplayName("update escribe NULL en category_id cuando el producto no tiene categoría")
    void updateBindsNullCategory() throws SQLException {
        Product product = new Product("Chicles", "CHI-001", null, new BigDecimal("1.20"), 0,
                null, true);
        product.setId(9);

        repository.update(product);

        assertNull(fake.boundParameters().get(0).get(6));
    }

    @Test
    @DisplayName("deactivate y activate hacen una baja/alta lógica por id")
    void deactivateAndActivateAreLogical() throws SQLException {
        repository.deactivate(3);
        repository.activate(4);

        assertTrue(fake.preparedSql().get(0).contains("UPDATE PRODUCT SET active = 0"));
        assertEquals(3, fake.boundParameters().get(0).get(1));
        assertTrue(fake.preparedSql().get(1).contains("UPDATE PRODUCT SET active = 1"));
        assertEquals(4, fake.boundParameters().get(1).get(1));
    }

    @Test
    @DisplayName("findAll sin filas devuelve una lista vacía")
    void findAllWithoutRowsReturnsEmptyList() throws SQLException {
        fake.withRows();

        assertTrue(repository.findAll().isEmpty());
        assertFalse(repository.findAll().iterator().hasNext());
    }
}
