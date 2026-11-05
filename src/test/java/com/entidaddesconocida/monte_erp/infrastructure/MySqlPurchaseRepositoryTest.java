package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.ProductSupplier;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.PurchaseFilter;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.Supplier;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.domain.service.InactiveEntityException;
import com.entidaddesconocida.monte_erp.domain.service.PurchaseStockConsumedException;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;
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
 * Unit tests for {@link MySqlPurchaseRepository} against the in-memory
 * {@link FakeJdbc} double.
 *
 * <p>They pin down the SQL, the bound parameters, the transaction handling
 * (commit/rollback and the restored {@code autoCommit}) and the row-to-entity
 * mapping, with no MySQL instance.</p>
 */
class MySqlPurchaseRepositoryTest {

    private FakeJdbc fake;
    private MySqlPurchaseRepository repository;

    @BeforeEach
    void setUp() {
        fake = new FakeJdbc().install();
        DatabaseConnection.configure(FakeJdbc.URL, "erp_user", "erp_pass");
        repository = new MySqlPurchaseRepository();
    }

    @AfterEach
    void tearDown() {
        DatabaseConnection.close();
        DatabaseConnection.configure(null, null, null);
        fake.uninstall();
    }

    private static User user() {
        User user = new User("Bea", "bea", "hash", User.ROLE_EMPLOYEE, true);
        user.setId(9);
        return user;
    }

    private static Supplier supplier() {
        return new Supplier(3, "Acme", null, null, null, true);
    }

    private static Product product(int id, String name, String sku) {
        Product product = new Product(name, sku, null, new BigDecimal("1.00"), 0, null, true);
        product.setId(id);
        return product;
    }

    private static Purchase draftPurchase() {
        return new Purchase(supplier(), user(), List.of(
                new Stock(product(1, "Teclado", "SKU-1"), 2, new BigDecimal("10.00")),
                new Stock(product(2, "Ratón", "SKU-2"), 3, new BigDecimal("5.50"))));
    }

    @Test
    @DisplayName("insert escribe la compra y un lote por cada lote, y devuelve la clave")
    void insertWritesPurchaseAndLots() throws SQLException {
        fake.withGeneratedKey(42).withRows(FakeJdbc.row("active", true));
        Purchase purchase = draftPurchase();

        int id = repository.insert(purchase);

        assertEquals(42, id);
        assertEquals(42, purchase.getId());

        String purchaseSql = fake.preparedSql().get(1);
        assertTrue(purchaseSql.contains("INSERT INTO PURCHASE"));
        Map<Integer, Object> purchaseParameters = fake.boundParameters().get(1);
        assertEquals(Purchase.STATUS_PENDING, purchaseParameters.get(1));
        assertEquals(0, new BigDecimal("36.50").compareTo((BigDecimal) purchaseParameters.get(2)));
        assertEquals(3, purchaseParameters.get(3));
        assertEquals(9, purchaseParameters.get(4));

        assertEquals(6, fake.preparedSql().size());
        String lotSql = fake.preparedSql().get(3);
        assertTrue(lotSql.contains("INSERT INTO STOCK"));
        Map<Integer, Object> firstLot = fake.boundParameters().get(3);
        assertEquals(2, firstLot.get(1));
        // A pending purchase does not feed the stock yet.
        assertEquals(0, firstLot.get(2));
        assertEquals(0, new BigDecimal("10.00").compareTo((BigDecimal) firstLot.get(3)));
        assertEquals(1, firstLot.get(4));
        assertEquals(42, firstLot.get(5));
        Map<Integer, Object> secondLot = fake.boundParameters().get(5);
        assertEquals(3, secondLot.get(1));
        assertEquals(0, secondLot.get(2));
        assertEquals(2, secondLot.get(4));
    }

    @Test
    @DisplayName("insert enlaza cada lote con la compra persistida")
    void insertLinksEachLotToThePersistedPurchase() throws SQLException {
        fake.withGeneratedKey(42).withRows(FakeJdbc.row("active", true));
        Purchase purchase = draftPurchase();

        repository.insert(purchase);

        for (Stock lot : purchase.getLots()) {
            assertTrue(lot.getPurchase() == purchase);
        }
    }

    @Test
    @DisplayName("insert hace commit y restaura el autoCommit previo")
    void insertCommitsAndRestoresAutoCommit() throws SQLException {
        fake.withGeneratedKey(7).withRows(FakeJdbc.row("active", true));

        repository.insert(draftPurchase());

        assertEquals(List.of("setAutoCommit(false)", "commit()", "setAutoCommit(true)"),
                fake.transactionCalls());
    }

    @Test
    @DisplayName("Un fallo al insertar un lote hace rollback y propaga la excepción")
    void insertRollsBackAndPropagatesOnFailure() {
        fake.withGeneratedKey(7).withRows(FakeJdbc.row("active", true)).failUpdateNumber(2);
        Purchase purchase = draftPurchase();

        assertThrows(SQLException.class, () -> repository.insert(purchase));

        assertTrue(fake.transactionCalls().contains("rollback()"));
        assertFalse(fake.transactionCalls().contains("commit()"));
        assertTrue(fake.transactionCalls().contains("setAutoCommit(true)"));
    }

    @Test
    @DisplayName("Una excepción de runtime al insertar un lote también revierte la compra")
    void insertRollsBackOnRuntimeFailure() {
        fake.withGeneratedKey(7).withRows(FakeJdbc.row("active", true));
        Purchase purchase = draftPurchase();
        purchase.getLots().add(null);

        assertThrows(NullPointerException.class, () -> repository.insert(purchase));

        assertTrue(fake.transactionCalls().contains("rollback()"));
        assertFalse(fake.transactionCalls().contains("commit()"));
        assertTrue(fake.transactionCalls().contains("setAutoCommit(true)"));
    }

    @Test
    @DisplayName("Una compra no usa un proveedor desactivado mientras el borrador estaba abierto")
    void insertRejectsSupplierDeactivatedBeforeSave() {
        fake.withNextQueryRows(FakeJdbc.row("active", false));

        assertThrows(InactiveEntityException.class, () -> repository.insert(draftPurchase()));

        assertTrue(fake.preparedSql().get(0).contains("FROM SUPPLIER")
                && fake.preparedSql().get(0).contains("FOR UPDATE"));
        assertTrue(fake.preparedSql().stream().noneMatch(sql -> sql.contains("INSERT INTO PURCHASE")));
        assertTrue(fake.transactionCalls().contains("rollback()"));
        assertFalse(fake.transactionCalls().contains("commit()"));
    }

    @Test
    @DisplayName("Una compra no incluye un producto desactivado mientras el borrador estaba abierto")
    void insertRejectsProductDeactivatedBeforeSave() {
        fake.withGeneratedKey(7)
                .withNextQueryRows(FakeJdbc.row("active", true))
                .withNextQueryRows(FakeJdbc.row("active", false));

        assertThrows(InactiveEntityException.class, () -> repository.insert(draftPurchase()));

        assertTrue(fake.preparedSql().stream().anyMatch(sql -> sql.contains("FROM PRODUCT")
                && sql.contains("FOR UPDATE")));
        assertTrue(fake.transactionCalls().contains("rollback()"));
        assertFalse(fake.transactionCalls().contains("commit()"));
    }

    @Test
    @DisplayName("findAll mapea compra y proveedor ligero y ordena por fecha descendente")
    void findAllMapsRowsAndOrder() throws SQLException {
        fake.withRows(FakeJdbc.row(
                "id", 5,
                "purchase_date", Timestamp.valueOf(LocalDateTime.of(2024, 5, 1, 10, 30)),
                "status", Purchase.STATUS_RECEIVED,
                "total", new BigDecimal("36.50"),
                "supplier_id", 3,
                "supplier_name", "Acme"));

        List<Purchase> purchases = repository.findAll();

        assertEquals(1, purchases.size());
        Purchase purchase = purchases.get(0);
        assertEquals(5, purchase.getId());
        assertEquals(LocalDateTime.of(2024, 5, 1, 10, 30), purchase.getPurchaseDate());
        assertEquals(Purchase.STATUS_RECEIVED, purchase.getStatus());
        assertEquals(0, new BigDecimal("36.50").compareTo(purchase.getTotal()));
        assertEquals(3, purchase.getSupplier().getId());
        assertEquals("Acme", purchase.getSupplier().getName());

        String sql = fake.preparedSql().get(0);
        assertTrue(sql.contains("FROM PURCHASE"));
        assertTrue(sql.contains("JOIN SUPPLIER"));
        assertTrue(sql.contains("ORDER BY p.purchase_date DESC"));

        // No filter: every guard binds NULL, so the same statement serves the
        // whole history without concatenating SQL.
        Map<Integer, Object> parameters = fake.boundParameters().get(0);
        for (int index = 1; index <= 8; index++) {
            assertNull(parameters.get(index), "parámetro " + index + " debe ser NULL");
        }
    }

    @Test
    @DisplayName("findAll enlaza el rango de fechas, el proveedor y el estado en la consulta")
    void findAllBindsEveryFilter() throws SQLException {
        PurchaseFilter filter = PurchaseFilter.of(LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 31), 3, Purchase.STATUS_PENDING);

        List<Purchase> purchases = repository.findAll(filter);

        assertTrue(purchases.isEmpty());
        String sql = fake.preparedSql().get(0);
        assertTrue(sql.contains("WHERE (? IS NULL OR p.purchase_date >= ?)"));
        assertTrue(sql.contains("(? IS NULL OR p.purchase_date < DATE_ADD(?, INTERVAL 1 DAY))"));
        assertTrue(sql.contains("(? IS NULL OR p.supplier_id = ?)"));
        assertTrue(sql.contains("(? IS NULL OR p.status = ?)"));

        Map<Integer, Object> parameters = fake.boundParameters().get(0);
        assertEquals(java.sql.Date.valueOf(LocalDate.of(2024, 1, 1)), parameters.get(1));
        assertEquals(java.sql.Date.valueOf(LocalDate.of(2024, 1, 1)), parameters.get(2));
        assertEquals(java.sql.Date.valueOf(LocalDate.of(2024, 1, 31)), parameters.get(3));
        assertEquals(java.sql.Date.valueOf(LocalDate.of(2024, 1, 31)), parameters.get(4));
        assertEquals(3, parameters.get(5));
        assertEquals(3, parameters.get(6));
        assertEquals(Purchase.STATUS_PENDING, parameters.get(7));
        assertEquals(Purchase.STATUS_PENDING, parameters.get(8));
    }

    @Test
    @DisplayName("findAll con un solo criterio deja NULL los demás")
    void findAllBindsOnlyTheGivenCriterion() throws SQLException {
        repository.findAll(PurchaseFilter.of(null, null, 7, null));

        Map<Integer, Object> parameters = fake.boundParameters().get(0);
        for (int index : List.of(1, 2, 3, 4, 7, 8)) {
            assertNull(parameters.get(index), "parámetro " + index + " debe ser NULL");
        }
        assertEquals(7, parameters.get(5));
        assertEquals(7, parameters.get(6));
    }

    @Test
    @DisplayName("findAll() sin filtro consulta el histórico completo")
    void findAllWithoutArgumentsUsesTheWholeHistory() throws SQLException {
        fake.withRows(FakeJdbc.row(
                "id", 5,
                "purchase_date", Timestamp.valueOf(LocalDateTime.of(2024, 5, 1, 10, 30)),
                "status", Purchase.STATUS_RECEIVED,
                "total", new BigDecimal("36.50"),
                "supplier_id", 3,
                "supplier_name", "Acme"));

        List<Purchase> purchases = repository.findAll();

        assertEquals(1, purchases.size());
        Map<Integer, Object> parameters = fake.boundParameters().get(0);
        for (int index = 1; index <= 8; index++) {
            assertNull(parameters.get(index), "parámetro " + index + " debe ser NULL");
        }
    }

    @Test
    @DisplayName("findLots enlaza el id de la compra y mapea el producto ligero")
    void findLotsBindsPurchaseIdAndMapsProduct() throws SQLException {
        fake.withRows(FakeJdbc.row(
                "id", 11,
                "initial_quantity", 2,
                "available_quantity", 2,
                "purchase_price", new BigDecimal("10.00"),
                "entry_date", Timestamp.valueOf(LocalDateTime.of(2024, 5, 1, 10, 30)),
                "product_id", 1,
                "product_name", "Teclado",
                "product_sku", "SKU-1"));

        List<Stock> lots = repository.findLots(7);

        assertEquals(1, lots.size());
        Stock lot = lots.get(0);
        assertEquals(11, lot.getId());
        assertEquals(2, lot.getInitialQuantity());
        assertEquals(2, lot.getAvailableQuantity());
        assertEquals(0, new BigDecimal("10.00").compareTo(lot.getPurchasePrice()));
        assertEquals(LocalDateTime.of(2024, 5, 1, 10, 30), lot.getEntryDate());
        assertEquals(1, lot.getProduct().getId());
        assertEquals("Teclado", lot.getProduct().getName());
        assertEquals("SKU-1", lot.getProduct().getSku());

        String sql = fake.preparedSql().get(0);
        assertTrue(sql.contains("WHERE s.purchase_id = ?"));
        assertTrue(sql.contains("ORDER BY s.entry_date, s.id"));
        assertEquals(7, fake.boundParameters().get(0).get(1));
    }

    @Test
    @DisplayName("insert propaga el SQLException de la base de datos")
    void insertPropagatesSqlException() {
        fake.withRows(FakeJdbc.row("active", true)).failUpdateNumber(1);

        assertThrows(SQLException.class, () -> repository.insert(draftPurchase()));
    }

    @Test
    @DisplayName("findProductSuppliers emite DISTINCT sobre STOCK JOIN PURCHASE y mapea el par")
    void findProductSuppliersProjectsDistinctPairs() throws SQLException {
        fake.withRows(
                FakeJdbc.row("product_id", 1, "supplier_id", 3),
                FakeJdbc.row("product_id", 1, "supplier_id", 4),
                FakeJdbc.row("product_id", 2, "supplier_id", 3));

        List<ProductSupplier> pairs = repository.findProductSuppliers();

        assertEquals(3, pairs.size());
        assertEquals(1, pairs.get(0).getProductId());
        assertEquals(3, pairs.get(0).getSupplierId());
        assertEquals(1, pairs.get(1).getProductId());
        assertEquals(4, pairs.get(1).getSupplierId());
        assertEquals(2, pairs.get(2).getProductId());
        assertEquals(3, pairs.get(2).getSupplierId());
    }

    @Test
    @DisplayName("findProductSuppliers no filtra available_quantity ni el producto activo (D3)")
    void findProductSuppliersKeepsTheWholeHistory() throws SQLException {
        fake.withRows(FakeJdbc.row("product_id", 1, "supplier_id", 3));

        repository.findProductSuppliers();

        String sql = fake.executedStatements().get(0);
        assertTrue(sql.contains("SELECT DISTINCT s.product_id, p.supplier_id"));
        assertTrue(sql.contains("FROM STOCK s"));
        assertTrue(sql.contains("JOIN PURCHASE p ON p.id = s.purchase_id"));
        assertTrue(sql.contains("ORDER BY s.product_id, p.supplier_id"));
        assertFalse(sql.contains("available_quantity"));
        assertFalse(sql.contains("active"));
    }

    @Test
    @DisplayName("findProductSuppliers devuelve una lista vacía sin filas")
    void findProductSuppliersReturnsEmptyList() throws SQLException {
        assertEquals(List.of(), repository.findProductSuppliers());
    }

    // --- Status machine -------------------------------------------------

    @Test
    @DisplayName("changeStatus a received actualiza con guarda optimista y libera los lotes")
    void changeStatusToReceivedMakesLotsAvailable()
            throws SQLException, PurchaseStockConsumedException {
        int updated = repository.changeStatus(7, Purchase.STATUS_PENDING, Purchase.STATUS_RECEIVED);

        assertEquals(1, updated);
        assertTrue(fake.preparedSql().get(0).contains("UPDATE PURCHASE SET status = ?"));
        Map<Integer, Object> statusParameters = fake.boundParameters().get(0);
        assertEquals(Purchase.STATUS_RECEIVED, statusParameters.get(1));
        assertEquals(7, statusParameters.get(2));
        assertEquals(Purchase.STATUS_PENDING, statusParameters.get(3));
        assertTrue(fake.preparedSql().get(1).contains(
                "UPDATE STOCK SET available_quantity = initial_quantity"));
        assertEquals(7, fake.boundParameters().get(1).get(1));
        assertEquals(List.of("setAutoCommit(false)", "commit()", "setAutoCommit(true)"),
                fake.transactionCalls());
    }

    @Test
    @DisplayName("changeStatus a returned bloquea los lotes y los vacía si no se vendió nada")
    void changeStatusToReturnedEmptiesLots()
            throws SQLException, PurchaseStockConsumedException {
        fake.withRows(FakeJdbc.row("initial_quantity", 2, "available_quantity", 2));

        int updated = repository.changeStatus(7, Purchase.STATUS_RECEIVED, Purchase.STATUS_RETURNED);

        assertEquals(1, updated);
        String lockSql = fake.preparedSql().get(1);
        assertTrue(lockSql.contains("SELECT initial_quantity, available_quantity FROM STOCK"));
        assertTrue(lockSql.contains("FOR UPDATE"));
        assertEquals(7, fake.boundParameters().get(1).get(1));
        assertTrue(fake.preparedSql().get(2).contains("UPDATE STOCK SET available_quantity = 0"));
        assertEquals(7, fake.boundParameters().get(2).get(1));
        assertTrue(fake.transactionCalls().contains("commit()"));
    }

    @Test
    @DisplayName("Devolver una compra con unidades vendidas revierte y no toca el stock")
    void changeStatusToReturnedRejectedWhenStockWasConsumed() {
        fake.withRows(FakeJdbc.row("initial_quantity", 5, "available_quantity", 3));

        PurchaseStockConsumedException error = assertThrows(PurchaseStockConsumedException.class,
                () -> repository.changeStatus(7, Purchase.STATUS_RECEIVED, Purchase.STATUS_RETURNED));

        assertEquals(7, error.getPurchaseId());
        assertEquals(2, error.getConsumedUnits());
        assertTrue(fake.transactionCalls().contains("rollback()"));
        assertFalse(fake.transactionCalls().contains("commit()"));
        assertTrue(fake.preparedSql().stream()
                .noneMatch(sql -> sql.contains("UPDATE STOCK SET available_quantity = 0")));
    }

    @Test
    @DisplayName("changeStatus con la guarda rota devuelve 0, revierte y no toca el stock")
    void changeStatusReturnsZeroOnConflict()
            throws SQLException, PurchaseStockConsumedException {
        fake.withUpdateCount(0);

        int updated = repository.changeStatus(7, Purchase.STATUS_PENDING, Purchase.STATUS_RECEIVED);

        assertEquals(0, updated);
        assertEquals(List.of("setAutoCommit(false)", "rollback()", "setAutoCommit(true)"),
                fake.transactionCalls());
        assertTrue(fake.preparedSql().stream()
                .noneMatch(sql -> sql.contains("UPDATE STOCK")));
    }
}
