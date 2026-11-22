package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Customer;
import com.entidaddesconocida.monte_erp.domain.model.OrderStatus;
import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.ReportPeriod;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrder;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderFilter;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderLine;
import com.entidaddesconocida.monte_erp.domain.model.SalesReportLine;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.StockIssue;
import com.entidaddesconocida.monte_erp.domain.repository.SalesOrderRepository;
import com.entidaddesconocida.monte_erp.domain.service.FifoAllocator;
import com.entidaddesconocida.monte_erp.domain.service.InactiveEntityException;
import com.entidaddesconocida.monte_erp.domain.service.InsufficientStockException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * JDBC implementation of {@link SalesOrderRepository} for the
 * {@code SALES_ORDER}, {@code SALES_ORDER_LINE}, {@code STOCK_ISSUE} and
 * {@code STOCK} tables (RF-07 / RF-08 / RF-09 / RF-15).
 *
 * <p>No business logic: it only translates between the entities and the table
 * rows; the FIFO rule lives in {@link FifoAllocator}. {@link #insert(SalesOrder)}
 * is <strong>transactional</strong> inside a connection opened through the
 * injected {@link ConnectionFactory}: the header, every line and the stock
 * consumption are committed together. Per line it reads the available lots with
 * {@code FOR UPDATE} (row lock: two concurrent sales cannot allocate the same
 * units), asks {@code FifoAllocator} for the split, inserts one
 * {@code STOCK_ISSUE} per lot, updates {@code STOCK.available_quantity} and
 * applies {@link Stock#consume(int)} on the in-memory lot. Any failure (including
 * an {@link InsufficientStockException}) rolls the whole order back.</p>
 *
 * <p>{@link #changeStatus(int, String, String)} to {@code cancelled} is also
 * transactional (decision D2): after the optimistic guard matches, the issues of
 * the order are given back to their lots and deleted. If the guard returns 0 rows
 * the transaction is rolled back without touching the stock.</p>
 *
 * <p>The reads join {@code CUSTOMER} / {@code PRODUCT} to build <em>light</em>
 * entities carrying the name the table shows. {@link #findLines(int)} does not
 * filter {@code p.active}: an old order must keep showing a product that has
 * since been given up.</p>
 */
public class MySqlSalesOrderRepository implements SalesOrderRepository {

    private final ConnectionFactory connections;

    public MySqlSalesOrderRepository(ConnectionFactory connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    private static final String INSERT_ORDER =
            "INSERT INTO SALES_ORDER (status, total, customer_id, user_id) VALUES (?, ?, ?, ?)";
    private static final String SELECT_ACTIVE_CUSTOMER_FOR_UPDATE =
            "SELECT active FROM CUSTOMER WHERE id = ? FOR UPDATE";
    private static final String SELECT_ACTIVE_PRODUCT_FOR_UPDATE =
            "SELECT active FROM PRODUCT WHERE id = ? FOR UPDATE";
    private static final String INSERT_LINE =
            "INSERT INTO SALES_ORDER_LINE (quantity, unit_price, sales_order_id, product_id)"
                    + " VALUES (?, ?, ?, ?)";
    /**
     * Locks the available lots of the product, oldest first, so the allocation
     * this transaction computes cannot race with another sale (RF-15).
     */
    private static final String SELECT_AVAILABLE_LOTS_FOR_UPDATE = """
            SELECT s.id, s.initial_quantity, s.available_quantity, s.purchase_price, s.entry_date,
                   s.purchase_id, p.id AS product_id, p.name AS product_name, p.sku AS product_sku
              FROM STOCK s
              JOIN PRODUCT p ON p.id = s.product_id
             WHERE s.product_id = ? AND s.available_quantity > 0
             ORDER BY s.entry_date, s.id
               FOR UPDATE
            """;
    private static final String INSERT_ISSUE =
            "INSERT INTO STOCK_ISSUE (quantity, sales_order_line_id, stock_id) VALUES (?, ?, ?)";
    private static final String UPDATE_LOT_AVAILABLE =
            "UPDATE STOCK SET available_quantity = ? WHERE id = ?";
    private static final String UPDATE_LOT_RESTORE =
            "UPDATE STOCK SET available_quantity = available_quantity + ? WHERE id = ?";
    private static final String SELECT_ORDERS = """
            SELECT o.id, o.order_date, o.status, o.total, o.customer_id, c.name AS customer_name
              FROM SALES_ORDER o
              JOIN CUSTOMER c ON c.id = o.customer_id
             WHERE (? IS NULL OR o.order_date >= ?)
               AND (? IS NULL OR o.order_date < DATE_ADD(?, INTERVAL 1 DAY))
               AND (? IS NULL OR o.customer_id = ?)
               AND (? IS NULL OR o.status = ?)
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
    private static final String SELECT_ISSUES = """
            SELECT si.id, si.quantity, si.stock_id,
                   l.id AS line_id, l.quantity AS line_quantity, l.unit_price,
                   p.id AS product_id, p.name AS product_name, p.sku AS product_sku,
                   s.initial_quantity, s.available_quantity, s.purchase_price, s.entry_date,
                   s.purchase_id
              FROM STOCK_ISSUE si
              JOIN SALES_ORDER_LINE l ON l.id = si.sales_order_line_id
              JOIN STOCK s ON s.id = si.stock_id
              JOIN PRODUCT p ON p.id = s.product_id
             WHERE l.sales_order_id = ?
             ORDER BY si.id
            """;
    /** Issues of an order, to give the units back to their lots when cancelling. */
    private static final String SELECT_ISSUES_TO_REVERT = """
            SELECT si.stock_id, si.quantity
              FROM STOCK_ISSUE si
              JOIN SALES_ORDER_LINE l ON l.id = si.sales_order_line_id
             WHERE l.sales_order_id = ?
             ORDER BY si.id
            """;
    private static final String DELETE_ISSUES =
            "DELETE FROM STOCK_ISSUE WHERE sales_order_line_id IN"
                    + " (SELECT id FROM SALES_ORDER_LINE WHERE sales_order_id = ?)";
    /**
     * Flat projection of the order lines of the sales report (RF-12): one row per
     * line with its order, product, quantity and unit price. The period bounds
     * {@code order_date} with the same {@code ? IS NULL} pattern as the stock
     * report; the status is returned but not filtered (the exclusion of cancelled
     * orders is a domain invariant, decision D3).
     */
    private static final String SELECT_REPORT_LINES = """
            SELECT o.id AS order_id, o.order_date, o.status,
                   l.quantity, l.unit_price,
                   p.id AS product_id, p.name AS product_name, p.sku AS product_sku
              FROM SALES_ORDER o
              JOIN SALES_ORDER_LINE l ON l.sales_order_id = o.id
              JOIN PRODUCT p ON p.id = l.product_id
             WHERE (? IS NULL OR o.order_date >= ?)
               AND (? IS NULL OR o.order_date < DATE_ADD(?, INTERVAL 1 DAY))
             ORDER BY p.name, o.order_date, o.id
            """;
    /**
     * Optimistic guard (decision D9): the row is only updated while it still has
     * the status the caller read, so a concurrent change cannot be overwritten.
     */
    private static final String UPDATE_STATUS =
            "UPDATE SALES_ORDER SET status = ? WHERE id = ? AND status = ?";

    @Override
    public int insert(SalesOrder order) throws SQLException, InsufficientStockException {
        int id = connections.withTransaction(connection -> {
            requireActive(connection, SELECT_ACTIVE_CUSTOMER_FOR_UPDATE,
                    order.getCustomer().getId(), "cliente");
            int orderId = insertOrder(connection, order);
            for (SalesOrderLine line : order.getLines()) {
                insertLineAndConsumeStock(connection, orderId, line);
            }
            return orderId;
        });
        // Only after the commit: a rolled-back order must not keep an id.
        order.setId(id);
        return id;
    }

    @Override
    public List<SalesOrder> findAll(SalesOrderFilter filter) throws SQLException {
        List<SalesOrder> orders = new ArrayList<>();
        try (Connection connection = connections.open();
             PreparedStatement ps = connection
                     .prepareStatement(SELECT_ORDERS)) {
            // Both placeholders of each guard receive the same value, so a null
            // criterion disables its comparison instead of filtering by NULL.
            bindDate(ps, 1, filter.period().from());
            bindDate(ps, 2, filter.period().from());
            bindDate(ps, 3, filter.period().to());
            bindDate(ps, 4, filter.period().to());
            bindInt(ps, 5, filter.customerId());
            bindInt(ps, 6, filter.customerId());
            bindStatus(ps, 7, filter.status());
            bindStatus(ps, 8, filter.status());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    orders.add(mapOrder(rs));
                }
            }
        }
        return orders;
    }

    @Override
    public List<SalesOrderLine> findLines(int salesOrderId) throws SQLException {
        List<SalesOrderLine> lines = new ArrayList<>();
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(SELECT_LINES)) {
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
    public List<StockIssue> findIssues(int salesOrderId) throws SQLException {
        List<StockIssue> issues = new ArrayList<>();
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(SELECT_ISSUES)) {
            ps.setInt(1, salesOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    issues.add(mapIssue(rs));
                }
            }
        }
        return issues;
    }

    @Override
    public List<SalesReportLine> findReportLines(ReportPeriod period) throws SQLException {
        List<SalesReportLine> lines = new ArrayList<>();
        try (Connection connection = connections.open();
             PreparedStatement ps = connection
                     .prepareStatement(SELECT_REPORT_LINES)) {
            bindDate(ps, 1, period.from());
            bindDate(ps, 2, period.from());
            bindDate(ps, 3, period.to());
            bindDate(ps, 4, period.to());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(mapReportLine(rs));
                }
            }
        }
        return lines;
    }

    /** Binds an optional date, using SQL NULL (with an explicit type) when absent. */
    private static void bindDate(PreparedStatement ps, int index, LocalDate date)
            throws SQLException {
        if (date == null) {
            ps.setNull(index, Types.DATE);
        } else {
            ps.setDate(index, java.sql.Date.valueOf(date));
        }
    }

    /** Binds an optional integer (the customer id), using SQL NULL when absent. */
    private static void bindInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    /** Binds an optional status (its database value), using SQL NULL when absent. */
    private static void bindStatus(PreparedStatement ps, int index, OrderStatus value)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value.dbValue());
        }
    }

    @Override
    public int changeStatus(int id, OrderStatus currentStatus, OrderStatus newStatus)
            throws SQLException {
        // No business logic: the transition was already validated by the domain.
        if (newStatus != OrderStatus.CANCELLED) {
            try (Connection connection = connections.open();
                 PreparedStatement ps = connection.prepareStatement(UPDATE_STATUS)) {
                ps.setString(1, newStatus.dbValue());
                ps.setInt(2, id);
                ps.setString(3, currentStatus.dbValue());
                return ps.executeUpdate();
            }
        }
        return cancelAndRestoreStock(id, currentStatus);
    }

    /**
     * Cancels in one transaction: optimistic guard first, then give the units
     * back and delete the issues. A 0-row guard means someone else moved the
     * order, so nothing is touched.
     */
    private int cancelAndRestoreStock(int id, OrderStatus currentStatus) throws SQLException {
        return connections.withTransaction(connection -> {
            int updated = updateStatus(connection, id, currentStatus, OrderStatus.CANCELLED);
            if (updated == 0) {
                // Someone else moved the order: nothing to give back.
                return 0;
            }
            restoreStock(connection, id);
            deleteIssues(connection, id);
            return updated;
        });
    }

    private int insertOrder(Connection connection, SalesOrder order) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT_ORDER, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, order.getStatus().dbValue());
            ps.setBigDecimal(2, order.getTotal());
            ps.setInt(3, order.getCustomer().getId());
            ps.setInt(4, order.getUser().getId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    private void insertLineAndConsumeStock(Connection connection, int salesOrderId,
                                           SalesOrderLine line) throws SQLException, InsufficientStockException {
        requireActive(connection, SELECT_ACTIVE_PRODUCT_FOR_UPDATE,
                line.getProduct().getId(), "producto");
        int lineId = insertLine(connection, salesOrderId, line);
        line.setId(lineId);

        List<Stock> lots = findAvailableLotsForUpdate(connection, line.getProduct().getId());
        List<StockIssue> issues = FifoAllocator.allocate(line, lots);
        for (StockIssue issue : issues) {
            insertIssue(connection, lineId, issue);
            Stock lot = issue.getStock();
            // The allocator guarantees taken <= available, so consume never throws.
            lot.consume(issue.getQuantity());
            updateLotAvailable(connection, lot, lot.getAvailableQuantity());
        }
        line.setIssues(issues);
    }

    private void requireActive(Connection connection, String sql, int id, String entity)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !rs.getBoolean("active")) {
                    throw new InactiveEntityException("El " + entity
                            + " fue dado de baja mientras se preparaba el pedido. Actualiza el formulario.");
                }
            }
        }
    }

    private int insertLine(Connection connection, int salesOrderId, SalesOrderLine line)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT_LINE,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, line.getQuantity());
            ps.setBigDecimal(2, line.getUnitPrice());
            ps.setInt(3, salesOrderId);
            ps.setInt(4, line.getProduct().getId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    private List<Stock> findAvailableLotsForUpdate(Connection connection, int productId)
            throws SQLException {
        List<Stock> lots = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(SELECT_AVAILABLE_LOTS_FOR_UPDATE)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lots.add(mapLot(rs));
                }
            }
        }
        return lots;
    }

    private void insertIssue(Connection connection, int lineId, StockIssue issue) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT_ISSUE)) {
            ps.setInt(1, issue.getQuantity());
            ps.setInt(2, lineId);
            ps.setInt(3, issue.getStock().getId());
            ps.executeUpdate();
        }
    }

    private void updateLotAvailable(Connection connection, Stock lot, int available) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_LOT_AVAILABLE)) {
            ps.setInt(1, available);
            ps.setInt(2, lot.getId());
            ps.executeUpdate();
        }
    }

    private int updateStatus(Connection connection, int id, OrderStatus currentStatus,
                             OrderStatus newStatus) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, newStatus.dbValue());
            ps.setInt(2, id);
            ps.setString(3, currentStatus.dbValue());
            return ps.executeUpdate();
        }
    }

    private void restoreStock(Connection connection, int salesOrderId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(SELECT_ISSUES_TO_REVERT)) {
            select.setInt(1, salesOrderId);
            List<int[]> issued = new ArrayList<>();
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    issued.add(new int[] {rs.getInt("stock_id"), rs.getInt("quantity")});
                }
            }
            // The given-back units never exceed the initial quantity: they are
            // exactly what the sale took, and Stock.restore mirrors the CHECK.
            for (int[] row : issued) {
                try (PreparedStatement update = connection.prepareStatement(UPDATE_LOT_RESTORE)) {
                    update.setInt(1, row[1]);
                    update.setInt(2, row[0]);
                    update.executeUpdate();
                }
            }
        }
    }

    private void deleteIssues(Connection connection, int salesOrderId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(DELETE_ISSUES)) {
            ps.setInt(1, salesOrderId);
            ps.executeUpdate();
        }
    }

    private SalesOrder mapOrder(ResultSet rs) throws SQLException {
        Customer customer = new Customer(rs.getInt("customer_id"), rs.getString("customer_name"),
                null, null, null);
        return new SalesOrder(rs.getInt("id"),
                rs.getTimestamp("order_date").toLocalDateTime(),
                OrderStatus.fromDb(rs.getString("status")),
                rs.getBigDecimal("total"),
                customer,
                null,
                new ArrayList<>());
    }

    private SalesOrderLine mapLine(ResultSet rs) throws SQLException {
        Product product = lightProduct(rs, "product_id", "product_name", "product_sku");
        return new SalesOrderLine(rs.getInt("id"), rs.getInt("quantity"),
                rs.getBigDecimal("unit_price"), product);
    }

    private SalesReportLine mapReportLine(ResultSet rs) throws SQLException {
        Product product = lightProduct(rs, "product_id", "product_name", "product_sku");
        return new SalesReportLine(rs.getInt("order_id"),
                rs.getTimestamp("order_date").toLocalDateTime(),
                OrderStatus.fromDb(rs.getString("status")),
                rs.getInt("quantity"), rs.getBigDecimal("unit_price"), product);
    }

    private StockIssue mapIssue(ResultSet rs) throws SQLException {        Product product = lightProduct(rs, "product_id", "product_name", "product_sku");
        SalesOrderLine line = new SalesOrderLine(rs.getInt("line_id"),
                rs.getInt("line_quantity"), rs.getBigDecimal("unit_price"), product);
        Purchase purchase = new Purchase(rs.getInt("purchase_id"), null, null, null,
                null, null, null);
        Stock lot = new Stock(rs.getInt("stock_id"), rs.getInt("initial_quantity"),
                rs.getInt("available_quantity"), rs.getBigDecimal("purchase_price"),
                rs.getTimestamp("entry_date").toLocalDateTime(), product, purchase);
        return new StockIssue(rs.getInt("id"), rs.getInt("quantity"), line, lot);
    }

    private static Product lightProduct(ResultSet rs, String idColumn, String nameColumn,
                                        String skuColumn) throws SQLException {
        Product product = new Product(rs.getString(nameColumn), rs.getString(skuColumn),
                null, null, 0, null, true);
        product.setId(rs.getInt(idColumn));
        return product;
    }

    /** Maps a {@code STOCK} row whose id column is {@code id} (the locked lots query). */
    private static Stock mapLot(ResultSet rs) throws SQLException {
        Product product = lightProduct(rs, "product_id", "product_name", "product_sku");
        Purchase purchase = new Purchase(rs.getInt("purchase_id"), null, null, null,
                null, null, null);
        return new Stock(rs.getInt("id"), rs.getInt("initial_quantity"),
                rs.getInt("available_quantity"), rs.getBigDecimal("purchase_price"),
                rs.getTimestamp("entry_date").toLocalDateTime(), product, purchase);
    }
}
