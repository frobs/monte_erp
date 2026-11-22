package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.PurchaseStatus;
import com.entidaddesconocida.monte_erp.domain.model.ReportPeriod;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.StockLevel;
import com.entidaddesconocida.monte_erp.domain.repository.StockRepository;

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
 * JDBC implementation of {@link StockRepository} over {@code STOCK} and
 * {@code PRODUCT} (RF-09 / RF-10 / CU-08).
 *
 * <p>No business logic: it only translates between the entities and the table
 * rows. The aggregated level is computed with a {@code LEFT JOIN} + {@code SUM}
 * so a product with no lots still shows 0 (an inner join would drop it), and the
 * lot queries join {@code PRODUCT} to build the light product the table shows.
 * The FIFO order ({@code entry_date, id}) is the one already backed by
 * {@code idx_stock_product_date}.</p>
 *
 * <p>{@link #findLots(int)} does not filter {@code available_quantity}: a lot at
 * 0 is still part of the history. {@link #findAvailable(int)} does filter it, as
 * it feeds the allocation.</p>
 */
public class MySqlStockRepository implements StockRepository {

    private final ConnectionFactory connections;

    public MySqlStockRepository(ConnectionFactory connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    private static final String SELECT_LEVELS = """
            SELECT p.id, p.name, p.sku, p.min_stock,
                   COALESCE(SUM(s.available_quantity), 0) AS available
              FROM PRODUCT p
              LEFT JOIN STOCK s ON s.product_id = p.id
             WHERE p.active = 1
             GROUP BY p.id, p.name, p.sku, p.min_stock
             ORDER BY p.name
            """;
    /**
     * Same aggregation as {@link #SELECT_LEVELS} with the screen filters in the
     * query: the text matches name/SKU and {@code onlyBelow} keeps the products
     * whose aggregated available quantity is below their minimum (RF-10).
     */
    private static final String SELECT_LEVELS_FILTERED = """
            SELECT p.id, p.name, p.sku, p.min_stock,
                   COALESCE(SUM(s.available_quantity), 0) AS available
              FROM PRODUCT p
              LEFT JOIN STOCK s ON s.product_id = p.id
             WHERE p.active = 1
               AND (? IS NULL OR p.name LIKE ? ESCAPE '!' OR p.sku LIKE ? ESCAPE '!')
             GROUP BY p.id, p.name, p.sku, p.min_stock
            HAVING (? = 0 OR COALESCE(SUM(s.available_quantity), 0) < p.min_stock)
             ORDER BY p.name
            """;
    /** Global alert counter (RF-10): independent from the screen filters. */
    private static final String COUNT_BELOW_MINIMUM = """
            SELECT COUNT(*) AS total
              FROM (
                    SELECT p.id
                      FROM PRODUCT p
                      LEFT JOIN STOCK s ON s.product_id = p.id
                     WHERE p.active = 1
                     GROUP BY p.id, p.min_stock
                    HAVING COALESCE(SUM(s.available_quantity), 0) < p.min_stock
                   ) alerts
            """;
    private static final String SELECT_LOTS = """
            SELECT s.id, s.initial_quantity, s.available_quantity, s.purchase_price, s.entry_date,
                   s.purchase_id, pu.status AS purchase_status,
                   p.id AS product_id, p.name AS product_name, p.sku AS product_sku
              FROM STOCK s
              JOIN PRODUCT p ON p.id = s.product_id
              JOIN PURCHASE pu ON pu.id = s.purchase_id
             WHERE s.product_id = ?
             ORDER BY s.entry_date, s.id
            """;
    private static final String SELECT_AVAILABLE = """
            SELECT s.id, s.initial_quantity, s.available_quantity, s.purchase_price, s.entry_date,
                   s.purchase_id, pu.status AS purchase_status,
                   p.id AS product_id, p.name AS product_name, p.sku AS product_sku
              FROM STOCK s
              JOIN PRODUCT p ON p.id = s.product_id
              JOIN PURCHASE pu ON pu.id = s.purchase_id
             WHERE s.product_id = ? AND s.available_quantity > 0
             ORDER BY s.entry_date, s.id
            """;
    /**
     * Lots of the stock report (RF-12): the whole table joined to its product
     * and to the purchase it comes from, optionally bounded by {@code
     * entry_date}. The purchase status travels with the lot so the report can
     * tell <em>why</em> a lot shows 0 available (returned, cancelled, pending or
     * already sold). The {@code ? IS NULL} guards let the same statement serve
     * the whole history and a period without concatenating SQL; the upper bound
     * is exclusive at {@code + 1 day} so a whole "hasta" day is included without
     * a time component.
     */
    private static final String SELECT_ALL_LOTS = """
            SELECT s.id, s.initial_quantity, s.available_quantity, s.purchase_price, s.entry_date,
                   s.purchase_id, pu.status AS purchase_status,
                   p.id AS product_id, p.name AS product_name, p.sku AS product_sku
              FROM STOCK s
              JOIN PRODUCT p ON p.id = s.product_id
              JOIN PURCHASE pu ON pu.id = s.purchase_id
             WHERE (? IS NULL OR s.entry_date >= ?)
               AND (? IS NULL OR s.entry_date < DATE_ADD(?, INTERVAL 1 DAY))
             ORDER BY p.name, s.entry_date, s.id
            """;

    @Override
    public List<StockLevel> findLevels() throws SQLException {
        List<StockLevel> levels = new ArrayList<>();
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(SELECT_LEVELS)) {
            while (rs.next()) {
                levels.add(mapLevel(rs));
            }
        }
        return levels;
    }

    @Override
    public List<StockLevel> findLevels(String search, boolean onlyBelowMinimum)
            throws SQLException {
        String pattern = SqlSearch.likePattern(search);
        if (pattern == null && !onlyBelowMinimum) {
            return findLevels();
        }
        List<StockLevel> levels = new ArrayList<>();
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(SELECT_LEVELS_FILTERED)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            ps.setBoolean(4, onlyBelowMinimum);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    levels.add(mapLevel(rs));
                }
            }
        }
        return levels;
    }

    @Override
    public int countBelowMinimum() throws SQLException {
        try (Connection connection = connections.open();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(COUNT_BELOW_MINIMUM)) {
            return rs.next() ? rs.getInt("total") : 0;
        }
    }

    @Override
    public List<Stock> findLots(int productId) throws SQLException {
        return findLots(SELECT_LOTS, productId);
    }

    @Override
    public List<Stock> findAvailable(int productId) throws SQLException {
        return findLots(SELECT_AVAILABLE, productId);
    }

    @Override
    public List<Stock> findAllLots(ReportPeriod period) throws SQLException {
        List<Stock> lots = new ArrayList<>();
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(SELECT_ALL_LOTS)) {
            // Both placeholders of the lower bound (the IS NULL guard and the
            // comparison) receive the same date, and the same for the upper one.
            bindDate(ps, 1, period.from());
            bindDate(ps, 2, period.from());
            bindDate(ps, 3, period.to());
            bindDate(ps, 4, period.to());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lots.add(mapLot(rs));
                }
            }
        }
        return lots;
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

    private List<Stock> findLots(String sql, int productId) throws SQLException {
        List<Stock> lots = new ArrayList<>();
        try (Connection connection = connections.open();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lots.add(mapLot(rs));
                }
            }
        }
        return lots;
    }

    private StockLevel mapLevel(ResultSet rs) throws SQLException {
        Product product = new Product(rs.getString("name"), rs.getString("sku"),
                null, null, rs.getInt("min_stock"), null, true);
        product.setId(rs.getInt("id"));
        return new StockLevel(product, rs.getInt("available"));
    }

    private Stock mapLot(ResultSet rs) throws SQLException {
        Product product = new Product(rs.getString("product_name"), rs.getString("product_sku"),
                null, null, 0, null, true);
        product.setId(rs.getInt("product_id"));
        // Light purchase: the id to show "Compra #id" and the status that explains
        // the available quantity of the lot in the report (RF-12).
        Purchase purchase = new Purchase(rs.getInt("purchase_id"), null,
                PurchaseStatus.fromDb(rs.getString("purchase_status")), null, null, null, null);
        return new Stock(rs.getInt("id"), rs.getInt("initial_quantity"),
                rs.getInt("available_quantity"), rs.getBigDecimal("purchase_price"),
                rs.getTimestamp("entry_date").toLocalDateTime(), product, purchase);
    }
}
