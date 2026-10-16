package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Customer;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrder;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderLine;
import com.entidaddesconocida.monte_erp.domain.model.User;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit tests for {@link MySqlSalesOrderRepository} against the in-memory
 * {@link FakeJdbc} double.
 *
 * <p>They pin down the SQL, the bound parameters, the transaction handling
 * (commit/rollback and the restored {@code autoCommit}) and the row-to-entity
 * mapping, with no MySQL instance.</p>
 */
class MySqlSalesOrderRepositoryTest {

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

    private static SalesOrder draftOrder() {
        return new SalesOrder(customer(), user(), List.of(
                new SalesOrderLine(2, new BigDecimal("10.00"), product(1, "Teclado", "SKU-1", "10.00")),
                new SalesOrderLine(3, new BigDecimal("5.50"), product(2, "Ratón", "SKU-2", "5.50"))));
    }

    @Test
    @DisplayName("insert escribe el pedido y una línea por cada línea, y devuelve la clave")
    void insertWritesOrderAndLines() throws SQLException {
        fake.withGeneratedKey(42);
        SalesOrder order = draftOrder();

        int id = repository.insert(order);

        assertEquals(42, id);
        assertEquals(42, order.getId());

        String orderSql = fake.preparedSql().get(0);
        assertTrue(orderSql.contains("INSERT INTO SALES_ORDER"));
        Map<Integer, Object> orderParameters = fake.boundParameters().get(0);
        assertEquals("pending", orderParameters.get(1));
        assertEquals(0, new BigDecimal("36.50").compareTo((BigDecimal) orderParameters.get(2)));
        assertEquals(3, orderParameters.get(3));
        assertEquals(9, orderParameters.get(4));

        assertEquals(3, fake.preparedSql().size());
        String lineSql = fake.preparedSql().get(1);
        assertTrue(lineSql.contains("INSERT INTO SALES_ORDER_LINE"));
        Map<Integer, Object> firstLine = fake.boundParameters().get(1);
        assertEquals(2, firstLine.get(1));
        assertEquals(0, new BigDecimal("10.00").compareTo((BigDecimal) firstLine.get(2)));
        assertEquals(42, firstLine.get(3));
        assertEquals(1, firstLine.get(4));
        Map<Integer, Object> secondLine = fake.boundParameters().get(2);
        assertEquals(3, secondLine.get(1));
        assertEquals(2, secondLine.get(4));
    }

    @Test
    @DisplayName("insert hace commit y restaura el autoCommit previo")
    void insertCommitsAndRestoresAutoCommit() throws SQLException {
        fake.withGeneratedKey(7);

        repository.insert(draftOrder());

        List<String> calls = fake.transactionCalls();
        assertEquals(List.of("setAutoCommit(false)", "commit()", "setAutoCommit(true)"), calls);
    }

    @Test
    @DisplayName("Un fallo al insertar una línea hace rollback y propaga la excepción")
    void insertRollsBackAndPropagatesOnFailure() {
        fake.withGeneratedKey(7).failUpdateNumber(2);
        SalesOrder order = draftOrder();

        assertThrows(SQLException.class, () -> repository.insert(order));

        assertTrue(fake.transactionCalls().contains("rollback()"));
        assertFalse(fake.transactionCalls().contains("commit()"));
        assertTrue(fake.transactionCalls().contains("setAutoCommit(true)"));
    }

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
        assertEquals(3, order.getCustomer().getId());
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
        assertEquals(0, new BigDecimal("10.00").compareTo(line.getUnitPrice()));
        assertEquals(1, line.getProduct().getId());
        assertEquals("Teclado", line.getProduct().getName());
        assertEquals("SKU-1", line.getProduct().getSku());

        String sql = fake.preparedSql().get(0);
        assertTrue(sql.contains("WHERE l.sales_order_id = ?"));
        assertEquals(7, fake.boundParameters().get(0).get(1));
    }
}
