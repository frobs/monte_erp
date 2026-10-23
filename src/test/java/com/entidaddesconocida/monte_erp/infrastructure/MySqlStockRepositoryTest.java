package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.StockLevel;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;
import com.entidaddesconocida.monte_erp.infrastructure.support.FakeJdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MySqlStockRepository} against the in-memory
 * {@link FakeJdbc} double (RF-09 / RF-10).
 */
class MySqlStockRepositoryTest {

    private static final Timestamp ENTRY = Timestamp.valueOf(LocalDateTime.of(2024, 1, 1, 10, 0));

    private FakeJdbc fake;
    private MySqlStockRepository repository;

    @BeforeEach
    void setUp() {
        fake = new FakeJdbc().install();
        DatabaseConnection.configure(FakeJdbc.URL, "erp_user", "erp_pass");
        repository = new MySqlStockRepository();
    }

    @AfterEach
    void tearDown() {
        DatabaseConnection.close();
        DatabaseConnection.configure(null, null, null);
        fake.uninstall();
    }

    private static FakeJdbc.Row levelRow(int id, String name, String sku, int minStock,
                                         int available) {
        return FakeJdbc.row(
                "id", id,
                "name", name,
                "sku", sku,
                "min_stock", minStock,
                "available", available);
    }

    private static FakeJdbc.Row lotRow(int id, int initial, int available, int purchaseId) {
        return FakeJdbc.row(
                "id", id,
                "initial_quantity", initial,
                "available_quantity", available,
                "purchase_price", new BigDecimal("1.50"),
                "entry_date", ENTRY,
                "purchase_id", purchaseId,
                "product_id", 1,
                "product_name", "Teclado",
                "product_sku", "SKU-1");
    }

    @Test
    @DisplayName("findLevels agrega con LEFT JOIN y ordena por nombre")
    void findLevelsAggregatesWithLeftJoin() throws SQLException {
        fake.withRows(levelRow(1, "Teclado", "SKU-1", 5, 12));

        List<StockLevel> levels = repository.findLevels();

        assertEquals(1, levels.size());
        StockLevel level = levels.get(0);
        assertEquals(1, level.getProduct().getId());
        assertEquals("Teclado", level.getProduct().getName());
        assertEquals("SKU-1", level.getProduct().getSku());
        assertEquals(5, level.getMinimumStock());
        assertEquals(12, level.getAvailableQuantity());
        assertFalse(level.isBelowMinimum());

        String sql = fake.executedStatements().get(0);
        assertTrue(sql.contains("LEFT JOIN STOCK"));
        assertTrue(sql.contains("COALESCE(SUM(s.available_quantity), 0)"));
        assertTrue(sql.contains("WHERE p.active = 1"));
        assertTrue(sql.contains("ORDER BY p.name"));
    }

    @Test
    @DisplayName("findLots enlaza el producto y mapea lote y compra de origen")
    void findLotsBindsProductAndMapsPurchase() throws SQLException {
        fake.withRows(lotRow(11, 10, 7, 5));

        List<Stock> lots = repository.findLots(1);

        assertEquals(1, lots.size());
        Stock lot = lots.get(0);
        assertEquals(11, lot.getId());
        assertEquals(10, lot.getInitialQuantity());
        assertEquals(7, lot.getAvailableQuantity());
        assertEquals(0, new BigDecimal("1.50").compareTo(lot.getPurchasePrice()));
        assertEquals(ENTRY.toLocalDateTime(), lot.getEntryDate());
        assertEquals(1, lot.getProduct().getId());
        assertEquals("Teclado", lot.getProduct().getName());
        assertEquals(5, lot.getPurchase().getId());

        String sql = fake.preparedSql().get(0);
        assertTrue(sql.contains("WHERE s.product_id = ?"));
        assertTrue(sql.contains("ORDER BY s.entry_date, s.id"));
        assertEquals(1, fake.boundParameters().get(0).get(1));
    }

    @Test
    @DisplayName("findAvailable filtra disponible > 0 y ordena FIFO")
    void findAvailableFiltersAndOrders() throws SQLException {
        fake.withRows(lotRow(11, 10, 7, 5));

        repository.findAvailable(1);

        String sql = fake.preparedSql().get(0);
        assertTrue(sql.contains("available_quantity > 0"));
        assertTrue(sql.contains("ORDER BY s.entry_date, s.id"));
        assertEquals(1, fake.boundParameters().get(0).get(1));
    }

    @Test
    @DisplayName("Un SQLException de la base de datos se propaga")
    void sqlExceptionIsPropagated() {
        DatabaseConnection.configure(null, null, null);

        assertThrows(SQLException.class, () -> repository.findLevels());
    }
}
