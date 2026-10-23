package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Customer;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrder;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderLine;
import com.entidaddesconocida.monte_erp.domain.model.StockIssue;
import com.entidaddesconocida.monte_erp.domain.model.User;
import com.entidaddesconocida.monte_erp.domain.service.InsufficientStockException;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MySqlSalesOrderRepository} against the in-memory
 * {@link FakeJdbc} double (RF-07 / RF-08 / RF-09 / RF-15).
 *
 * <p>They pin down the SQL, the bound parameters, the transaction handling
 * (commit/rollback and the restored {@code autoCommit}) and the row-to-entity
 * mapping, with no MySQL instance.</p>
 */
class MySqlSalesOrderRepositoryTest {

    private static final Timestamp OLD_ENTRY = Timestamp.valueOf(LocalDateTime.of(2024, 1, 1, 10, 0));
    private static final Timestamp NEW_ENTRY = Timestamp.valueOf(LocalDateTime.of(2024, 2, 1, 10, 0));

    private FakeJdbc fake;
    private MySqlSalesOrderRepository repository;

    @BeforeEach
    void setUp() {
        fake = new FakeJdbc().install();
        DatabaseConnection.configure(FakeJdbc.URL, "erp_user", "erp_pass");
        repository = new MySqlSalesOrderRepository();
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

    private static Customer customer() {
        return new Customer(3, "Acme", null, null, null);
    }

    private static Product product(int id, String name, String sku, String price) {
        Product product = new Product(name, sku, null, new BigDecimal(price), 0, null, true);
        product.setId(id);
        return product;
    }

    private static SalesOrder draftOrder(int quantity) {
        return new SalesOrder(customer(), user(), List.of(
                new SalesOrderLine(quantity, new BigDecimal("10.00"),
                        product(1, "Teclado", "SKU-1", "10.00"))));
    }

    /** A locked-lot row as the FIFO query returns it. */
    private static FakeJdbc.Row lot(int id, int available, Timestamp entryDate) {
        return FakeJdbc.row(
                "id", id,
                "initial_quantity", 10,
                "available_quantity", available,
                "purchase_price", new BigDecimal("1.00"),
                "entry_date", entryDate,
                "purchase_id", 5,
                "product_id", 1,
                "product_name", "Teclado",
                "product_sku", "SKU-1");
    }

    // --- insert: header + lines + FIFO consumption -------------------------

    @Test
    @DisplayName("insert escribe el pedido y la línea, y devuelve la clave generada")
    void insertWritesOrderAndLines() throws Exception {
        fake.withGeneratedKey(42).withRows(lot(11, 10, OLD_ENTRY));
        SalesOrder order = draftOrder(2);

        int id = repository.insert(order);

        assertEquals(42, id);
        assertEquals(42, order.getId());
        assertTrue(fake.preparedSql().get(0).contains("INSERT INTO SALES_ORDER"));
        Map<Integer, Object> orderParameters = fake.boundParameters().get(0);
        assertEquals("pending", orderParameters.get(1));
        assertEquals(0, new BigDecimal("20.00").compareTo((BigDecimal) orderParameters.get(2)));
        assertEquals(3, orderParameters.get(3));
        assertEquals(9, orderParameters.get(4));

        assertTrue(fake.preparedSql().get(1).contains("INSERT INTO SALES_ORDER_LINE"));
        Map<Integer, Object> lineParameters = fake.boundParameters().get(1);
        assertEquals(2, lineParameters.get(1));
        assertEquals(42, lineParameters.get(3));
        assertEquals(1, lineParameters.get(4));
    }

    @Test
    @DisplayName("insert lee los lotes con FOR UPDATE y en orden FIFO")
    void insertLocksLotsInFifoOrder() throws Exception {
        fake.withGeneratedKey(7).withRows(lot(11, 10, OLD_ENTRY));

        repository.insert(draftOrder(2));

        String lockSql = fake.preparedSql().stream()
                .filter(sql -> sql.contains("FROM STOCK"))
                .findFirst().orElseThrow();
        assertTrue(lockSql.contains("available_quantity > 0"));
        assertTrue(lockSql.contains("ORDER BY s.entry_date, s.id"));
        assertTrue(lockSql.contains("FOR UPDATE"));
    }

    @Test
    @DisplayName("insert reparte la línea entre lotes FIFO y descuenta cada disponible")
    void insertAllocatesFifoAcrossLots() throws Exception {
        fake.withGeneratedKey(42).withRows(lot(11, 3, OLD_ENTRY), lot(12, 10, NEW_ENTRY));

        SalesOrder order = draftOrder(5);
        repository.insert(order);

        SalesOrderLine line = order.getLines().get(0);
        assertEquals(2, line.getIssues().size());
        assertEquals(11, line.getIssues().get(0).getStock().getId());
        assertEquals(3, line.getIssues().get(0).getQuantity());
        assertEquals(12, line.getIssues().get(1).getStock().getId());
        assertEquals(2, line.getIssues().get(1).getQuantity());

        List<Map<Integer, Object>> parameters = fake.boundParameters();
        // Statement order: order, line, lots, issue(old), stock, issue(new), stock.
        assertEquals(3, parameters.get(3).get(1));
        assertEquals(11, parameters.get(3).get(3));
        assertEquals(0, parameters.get(4).get(1));
        assertEquals(11, parameters.get(4).get(2));
        assertEquals(2, parameters.get(5).get(1));
        assertEquals(12, parameters.get(5).get(3));
        assertEquals(8, parameters.get(6).get(1));
        assertEquals(12, parameters.get(6).get(2));
    }

    @Test
    @DisplayName("insert hace commit y restaura el autoCommit previo")
    void insertCommitsAndRestoresAutoCommit() throws Exception {
        fake.withGeneratedKey(7).withRows(lot(11, 10, OLD_ENTRY));

        repository.insert(draftOrder(2));

        assertEquals(List.of("setAutoCommit(false)", "commit()", "setAutoCommit(true)"),
                fake.transactionCalls());
    }

    @Test
    @DisplayName("Insuficiencia dentro de la transacción: rollback, excepción y nada guardado")
    void insertInsufficientStockRollsBack() {
        fake.withGeneratedKey(7).withRows(lot(11, 1, OLD_ENTRY));
        SalesOrder order = draftOrder(5);

        assertThrows(InsufficientStockException.class, () -> repository.insert(order));

        assertTrue(fake.transactionCalls().contains("rollback()"));
        assertFalse(fake.transactionCalls().contains("commit()"));
        assertTrue(fake.transactionCalls().contains("setAutoCommit(true)"));
        assertTrue(fake.preparedSql().stream().noneMatch(sql -> sql.contains("INSERT INTO STOCK_ISSUE")));
    }

    @Test
    @DisplayName("Un fallo al insertar una línea hace rollback y propaga la excepción")
    void insertRollsBackAndPropagatesOnFailure() {
        fake.withGeneratedKey(7).withRows(lot(11, 10, OLD_ENTRY)).failUpdateNumber(2);

        assertThrows(SQLException.class, () -> repository.insert(draftOrder(2)));

        assertTrue(fake.transactionCalls().contains("rollback()"));
        assertFalse(fake.transactionCalls().contains("commit()"));
    }

    @Test
    @DisplayName("Un fallo al insertar el consumo hace rollback total (sin descuento parcial)")
    void insertRollsBackWhenIssueInsertFails() {
        fake.withGeneratedKey(7).withRows(lot(11, 10, OLD_ENTRY)).failUpdateNumber(3);

        assertThrows(SQLException.class, () -> repository.insert(draftOrder(2)));

        assertTrue(fake.transactionCalls().contains("rollback()"));
        assertFalse(fake.transactionCalls().contains("commit()"));
    }

    // --- reads -------------------------------------------------------------

    @Test
    @DisplayName("findAll mapea pedido y cliente ligero y ordena por fecha descendente")
    void findAllMapsRowsAndOrder() throws SQLException {
        fake.withRows(FakeJdbc.row(
                "id", 5,
                "order_date", Timestamp.valueOf(LocalDateTime.of(2024, 5, 1, 10, 30)),
                "status", "pending",
                "total", new BigDecimal("36.50"),
                "customer_id", 3,
                "customer_name", "Acme"));

        List<SalesOrder> orders = repository.findAll();

        assertEquals(1, orders.size());
        SalesOrder order = orders.get(0);
        assertEquals(5, order.getId());
        assertEquals(LocalDateTime.of(2024, 5, 1, 10, 30), order.getOrderDate());
        assertEquals(SalesOrder.STATUS_PENDING, order.getStatus());
        assertEquals(0, new BigDecimal("36.50").compareTo(order.getTotal()));
        assertEquals("Acme", order.getCustomer().getName());

        String sql = fake.executedStatements().get(0);
        assertTrue(sql.contains("FROM SALES_ORDER"));
        assertTrue(sql.contains("JOIN CUSTOMER"));
        assertTrue(sql.contains("ORDER BY o.order_date DESC"));
    }

    @Test
    @DisplayName("findLines enlaza el id del pedido y mapea el producto ligero")
    void findLinesBindsIdAndMapsProduct() throws SQLException {
        fake.withRows(FakeJdbc.row(
                "id", 11,
                "quantity", 2,
                "unit_price", new BigDecimal("10.00"),
                "product_id", 1,
                "product_name", "Teclado",
                "product_sku", "SKU-1"));

        List<SalesOrderLine> lines = repository.findLines(7);

        assertEquals(1, lines.size());
        SalesOrderLine line = lines.get(0);
        assertEquals(11, line.getId());
        assertEquals(2, line.getQuantity());
        assertEquals("Teclado", line.getProduct().getName());

        String sql = fake.preparedSql().get(0);
        assertTrue(sql.contains("WHERE l.sales_order_id = ?"));
        assertEquals(7, fake.boundParameters().get(0).get(1));
    }

    @Test
    @DisplayName("RF-15: findIssues mapea el lote y el producto ligeros por pedido")
    void findIssuesMapsLotAndProduct() throws SQLException {
        fake.withRows(FakeJdbc.row(
                "id", 21,
                "quantity", 3,
                "stock_id", 11,
                "line_id", 4,
                "line_quantity", 5,
                "unit_price", new BigDecimal("10.00"),
                "product_id", 1,
                "product_name", "Teclado",
                "product_sku", "SKU-1",
                "initial_quantity", 10,
                "available_quantity", 7,
                "purchase_price", new BigDecimal("1.00"),
                "entry_date", OLD_ENTRY,
                "purchase_id", 5));

        List<StockIssue> issues = repository.findIssues(7);

        assertEquals(1, issues.size());
        StockIssue issue = issues.get(0);
        assertEquals(21, issue.getId());
        assertEquals(3, issue.getQuantity());
        assertEquals(11, issue.getStock().getId());
        assertEquals(7, issue.getStock().getAvailableQuantity());
        assertEquals(5, issue.getStock().getPurchase().getId());
        assertEquals(1, issue.getStock().getProduct().getId());
        assertEquals("Teclado", issue.getStock().getProduct().getName());
        assertEquals(4, issue.getSalesOrderLine().getId());

        String sql = fake.preparedSql().get(0);
        assertTrue(sql.contains("FROM STOCK_ISSUE"));
        assertTrue(sql.contains("WHERE l.sales_order_id = ?"));
        assertEquals(7, fake.boundParameters().get(0).get(1));
    }

    // --- RF-08: status change -------------------------------------------

    @Test
    @DisplayName("RF-08: changeStatus emite el UPDATE con guarda optimista y enlaza destino, id y origen")
    void changeStatusBindsOptimisticGuard() throws SQLException {
        fake.withUpdateCount(1);

        int updated = repository.changeStatus(7, SalesOrder.STATUS_PENDING, SalesOrder.STATUS_SHIPPED);

        assertEquals(1, updated);
        String sql = fake.preparedSql().get(0);
        assertTrue(sql.contains("UPDATE SALES_ORDER SET status = ?"));
        assertTrue(sql.contains("WHERE id = ? AND status = ?"));
        Map<Integer, Object> parameters = fake.boundParameters().get(0);
        assertEquals(SalesOrder.STATUS_SHIPPED, parameters.get(1));
        assertEquals(7, parameters.get(2));
        assertEquals(SalesOrder.STATUS_PENDING, parameters.get(3));
    }

    @Test
    @DisplayName("RF-08: changeStatus devuelve 0 filas cuando la guarda no casa (conflicto)")
    void changeStatusReturnsZeroOnConflict() throws SQLException {
        fake.withUpdateCount(0);

        int updated = repository.changeStatus(7, SalesOrder.STATUS_PENDING, SalesOrder.STATUS_SHIPPED);

        assertEquals(0, updated);
    }

    @Test
    @DisplayName("RF-08: changeStatus propaga el SQLException de la base de datos")
    void changeStatusPropagatesSqlException() {
        fake.failUpdateNumber(1);

        assertThrows(SQLException.class, () ->
                repository.changeStatus(7, SalesOrder.STATUS_PENDING, SalesOrder.STATUS_SHIPPED));
    }

    @Test
    @DisplayName("RF-09/D2: cancelar repone el disponible y borra los STOCK_ISSUE en una transacción")
    void cancelRestoresStockAndDeletesIssues() throws SQLException {
        fake.withUpdateCount(1).withRows(FakeJdbc.row("stock_id", 11, "quantity", 3));

        int updated = repository.changeStatus(7, SalesOrder.STATUS_PENDING,
                SalesOrder.STATUS_CANCELLED);

        assertEquals(1, updated);
        assertEquals(List.of("setAutoCommit(false)", "commit()", "setAutoCommit(true)"),
                fake.transactionCalls());
        assertTrue(fake.preparedSql().stream().anyMatch(sql ->
                sql.contains("UPDATE STOCK SET available_quantity = available_quantity + ?")));
        assertTrue(fake.preparedSql().stream().anyMatch(sql ->
                sql.contains("DELETE FROM STOCK_ISSUE")));
        Map<Integer, Object> restore = fake.boundParameters().stream()
                .filter(parameters -> parameters.size() == 2 && Integer.valueOf(3).equals(parameters.get(1)))
                .findFirst().orElseThrow();
        assertEquals(11, restore.get(2));
    }

    @Test
    @DisplayName("RF-09/D2: si la guarda optimista no casa, no se repone stock")
    void cancelWithZeroRowsDoesNotTouchStock() throws SQLException {
        fake.withUpdateCount(0);

        int updated = repository.changeStatus(7, SalesOrder.STATUS_PENDING,
                SalesOrder.STATUS_CANCELLED);

        assertEquals(0, updated);
        assertTrue(fake.transactionCalls().contains("rollback()"));
        assertFalse(fake.transactionCalls().contains("commit()"));
        assertTrue(fake.preparedSql().stream().noneMatch(sql ->
                sql.contains("UPDATE STOCK SET available_quantity = available_quantity + ?")));
        assertTrue(fake.preparedSql().stream().noneMatch(sql ->
                sql.contains("DELETE FROM STOCK_ISSUE")));
    }
}
