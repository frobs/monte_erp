package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Customer;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrder;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderLine;
import com.entidaddesconocida.monte_erp.domain.repository.SalesOrderRepository;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC implementation of {@link SalesOrderRepository} for the
 * {@code SALES_ORDER} and {@code SALES_ORDER_LINE} tables (RF-07 / CU-07).
 *
 * <p>No business logic: it only translates between the entities and the table
 * rows. {@link #insert(SalesOrder)} is <strong>transactional</strong> over the
 * shared {@link DatabaseConnection} connection: the header and every line are
 * committed together, and any failure rolls the whole order back. The previous
 * {@code autoCommit} value is restored in a {@code finally} so the singleton
 * connection is left as it was found.</p>
 *
 * <p>The reads join {@code CUSTOMER} / {@code PRODUCT} to build <em>light</em>
 * entities carrying the name the table shows. {@link #findLines(int)} does not
 * filter {@code p.active}: an old order must keep showing a product that has
 * since been given up.</p>
 */
public class MySqlSalesOrderRepository implements SalesOrderRepository {

    private static final String INSERT_ORDER =
            "INSERT INTO SALES_ORDER (status, total, customer_id, user_id) VALUES (?, ?, ?, ?)";
    private static final String INSERT_LINE =
            "INSERT INTO SALES_ORDER_LINE (quantity, unit_price, sales_order_id, product_id)"
                    + " VALUES (?, ?, ?, ?)";
    private static final String SELECT_ORDERS = """
            SELECT o.id, o.order_date, o.status, o.total, o.customer_id, c.name AS customer_name
              FROM SALES_ORDER o
              JOIN CUSTOMER c ON c.id = o.customer_id
             ORDER BY o.order_date DESC, o.id DESC
            """;
    private static final String SELECT_LINES = """
            SELECT l.id, l.quantity, l.unit_price, l.product_id,
                   p.name AS product_name, p.sku AS product_sku
              FROM SALES_ORDER_LINE l
              JOIN PRODUCT p ON p.id = l.product_id
             WHERE l.sales_order_id = ?
             ORDER BY l.id
            """;
    /**
     * Optimistic guard (decision D9): the row is only updated while it still has
     * the status the caller read, so a concurrent change cannot be overwritten.
     */
    private static final String UPDATE_STATUS =
            "UPDATE SALES_ORDER SET status = ? WHERE id = ? AND status = ?";

    @Override
    public int insert(SalesOrder order) throws SQLException {
        Connection connection = DatabaseConnection.getConnection();
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int id = insertOrder(connection, order);
            order.setId(id);
            for (SalesOrderLine line : order.getLines()) {
                insertLine(connection, id, line);
            }
            connection.commit();
            return id;
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            throw e;
        } finally {
            // The connection is shared (singleton): leave it as it was found.
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    @Override
    public List<SalesOrder> findAll() throws SQLException {
        List<SalesOrder> orders = new ArrayList<>();
        try (Statement statement = DatabaseConnection.getConnection().createStatement();
             ResultSet rs = statement.executeQuery(SELECT_ORDERS)) {
            while (rs.next()) {
                orders.add(mapOrder(rs));
            }
        }
        return orders;
    }

    @Override
    public List<SalesOrderLine> findLines(int salesOrderId) throws SQLException {
        List<SalesOrderLine> lines = new ArrayList<>();
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(SELECT_LINES)) {
            ps.setInt(1, salesOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(mapLine(rs));
                }
            }
        }
        return lines;
    }

    @Override
    public int changeStatus(int id, String currentStatus, String newStatus) throws SQLException {
        // No business logic: the transition was already validated by the domain.
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, newStatus);
            ps.setInt(2, id);
            ps.setString(3, currentStatus);
            return ps.executeUpdate();
        }
    }

    private int insertOrder(Connection connection, SalesOrder order) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT_ORDER, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, order.getStatus());
            ps.setBigDecimal(2, order.getTotal());
            ps.setInt(3, order.getCustomer().getId());
            ps.setInt(4, order.getUser().getId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    private void insertLine(Connection connection, int salesOrderId, SalesOrderLine line) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT_LINE)) {
            ps.setInt(1, line.getQuantity());
            ps.setBigDecimal(2, line.getUnitPrice());
            ps.setInt(3, salesOrderId);
            ps.setInt(4, line.getProduct().getId());
            ps.executeUpdate();
        }
    }

    private SalesOrder mapOrder(ResultSet rs) throws SQLException {
        Customer customer = new Customer(rs.getInt("customer_id"), rs.getString("customer_name"),
                null, null, null);
        return new SalesOrder(rs.getInt("id"),
                rs.getTimestamp("order_date").toLocalDateTime(),
                rs.getString("status"),
                rs.getBigDecimal("total"),
                customer,
                null,
                new ArrayList<>());
    }

    private SalesOrderLine mapLine(ResultSet rs) throws SQLException {
        Product product = new Product(rs.getString("product_name"), rs.getString("product_sku"),
                null, null, 0, null, true);
        product.setId(rs.getInt("product_id"));
        return new SalesOrderLine(rs.getInt("id"), rs.getInt("quantity"),
                rs.getBigDecimal("unit_price"), product);
    }
}
