package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.PurchaseStatus;
import com.entidaddesconocida.monte_erp.domain.model.ReportPeriod;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.StockLevel;
import com.entidaddesconocida.monte_erp.infrastructure.config.DriverManagerConnectionFactory;
import com.entidaddesconocida.monte_erp.infrastructure.support.FakeJdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        repository = new MySqlStockRepository(
                new DriverManagerConnectionFactory(FakeJdbc.URL, "erp_user", "erp_pass"));
    }

    @AfterEach
    void tearDown() {
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
        return lotRow(id, initial, available, purchaseId, PurchaseStatus.RECEIVED);
    }

    private static FakeJdbc.Row lotRow(int id, int initial, int available, int purchaseId,
                                       PurchaseStatus purchaseStatus) {
        return FakeJdbc.row(
                "id", id,
                "initial_quantity", initial,
                "available_quantity", available,
                "purchase_price", new BigDecimal("1.50"),
                "entry_date", ENTRY,
                "purchase_id", purchaseId,
                "purchase_status", purchaseStatus.dbValue(),
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
    @DisplayName("findLevels(search, onlyBelow) filtra texto y mínimo en la consulta")
    void findLevelsWithFiltersBindsTheCriteria() throws SQLException {
        fake.withRows(levelRow(1, "Teclado", "SKU-1", 5, 2));

        List<StockLevel> levels = repository.findLevels("tec", true);

        assertEquals(1, levels.size());
        assertTrue(levels.get(0).isBelowMinimum());
        String sql = fake.preparedSql().get(0);
        assertTrue(sql.contains("p.name LIKE ? ESCAPE '!' OR p.sku LIKE ? ESCAPE '!'"));
        assertTrue(sql.contains("HAVING (? = 0 OR COALESCE(SUM(s.available_quantity), 0) < p.min_stock)"));
        Map<Integer, Object> parameters = fake.boundParameters().get(0);
        assertEquals("%tec%", parameters.get(1));
        assertEquals("%tec%", parameters.get(2));
        assertEquals("%tec%", parameters.get(3));
        assertEquals(true, parameters.get(4));
    }

    @Test
    @DisplayName("countBelowMinimum cuenta los productos en alerta sin los filtros de pantalla")
    void countBelowMinimumReadsTheGlobalCounter() throws SQLException {
        fake.withRows(FakeJdbc.row("total", 3));

        assertEquals(3, repository.countBelowMinimum());

        String sql = fake.executedStatements().get(0);
        assertTrue(sql.contains("COUNT(*) AS total"));
        assertTrue(sql.contains("HAVING COALESCE(SUM(s.available_quantity), 0) < p.min_stock"));
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
    @DisplayName("findAllLots uno el producto y la compra, filtra por período y ordena por nombre/fecha")
    void findAllLotsJoinsAndFiltersByPeriod() throws SQLException {
        fake.withRows(lotRow(11, 10, 7, 5));
        ReportPeriod period = ReportPeriod.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        List<Stock> lots = repository.findAllLots(period);

        assertEquals(1, lots.size());
        assertEquals("Teclado", lots.get(0).getProduct().getName());
        assertEquals(PurchaseStatus.RECEIVED, lots.get(0).getPurchase().getStatus());
        String sql = fake.preparedSql().get(0);
        assertTrue(sql.contains("JOIN PRODUCT"));
        assertTrue(sql.contains("JOIN PURCHASE"));
        assertTrue(sql.contains("purchase_status"));
        assertTrue(sql.contains("(? IS NULL OR s.entry_date >= ?)"));
        assertTrue(sql.contains("DATE_ADD(?, INTERVAL 1 DAY)"));
        assertTrue(sql.contains("ORDER BY p.name, s.entry_date, s.id"));
        Map<Integer, Object> parameters = fake.boundParameters().get(0);
        assertEquals(java.sql.Date.valueOf(LocalDate.of(2025, 1, 1)), parameters.get(1));
        assertEquals(parameters.get(1), parameters.get(2));
        assertEquals(java.sql.Date.valueOf(LocalDate.of(2025, 1, 31)), parameters.get(3));
        assertEquals(parameters.get(3), parameters.get(4));
    }

    @Test
    @DisplayName("findAllLots trae el estado de la compra: una devuelta no cuenta en los totales")
    void findAllLotsMapsThePurchaseStatus() throws SQLException {
        fake.withRows(lotRow(12, 4, 0, 8, PurchaseStatus.RETURNED));

        List<Stock> lots = repository.findAllLots(ReportPeriod.all());

        assertEquals(PurchaseStatus.RETURNED, lots.get(0).getPurchase().getStatus());
        assertFalse(lots.get(0).countsInStockReport());
    }

    @Test
    @DisplayName("findAllLots sin período liga NULL en las cuatro marcas (sin filtro)")
    void findAllLotsWithoutPeriodBindsNull() throws SQLException {
        repository.findAllLots(ReportPeriod.all());

        Map<Integer, Object> parameters = fake.boundParameters().get(0);
        assertNull(parameters.get(1));
        assertNull(parameters.get(2));
        assertNull(parameters.get(3));
        assertNull(parameters.get(4));
    }

    @Test
    @DisplayName("Un SQLException de la base de datos se propaga")
    void sqlExceptionIsPropagated() {
        MySqlStockRepository unconfigured =
                new MySqlStockRepository(new DriverManagerConnectionFactory());

        assertThrows(SQLException.class, unconfigured::findLevels);
    }
}
