package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.StockLevel;
import com.entidaddesconocida.monte_erp.domain.repository.StockRepository;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

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

    private static final String SELECT_LEVELS = """
            SELECT p.id, p.name, p.sku, p.min_stock,
                   COALESCE(SUM(s.available_quantity), 0) AS available
              FROM PRODUCT p
              LEFT JOIN STOCK s ON s.product_id = p.id
             WHERE p.active = 1
             GROUP BY p.id, p.name, p.sku, p.min_stock
             ORDER BY p.name
            """;
    private static final String SELECT_LOTS = """
            SELECT s.id, s.initial_quantity, s.available_quantity, s.purchase_price, s.entry_date,
                   s.purchase_id, p.id AS product_id, p.name AS product_name, p.sku AS product_sku
              FROM STOCK s
              JOIN PRODUCT p ON p.id = s.product_id
             WHERE s.product_id = ?
             ORDER BY s.entry_date, s.id
            """;
    private static final String SELECT_AVAILABLE = """
            SELECT s.id, s.initial_quantity, s.available_quantity, s.purchase_price, s.entry_date,
                   s.purchase_id, p.id AS product_id, p.name AS product_name, p.sku AS product_sku
              FROM STOCK s
              JOIN PRODUCT p ON p.id = s.product_id
             WHERE s.product_id = ? AND s.available_quantity > 0
             ORDER BY s.entry_date, s.id
            """;

    @Override
    public List<StockLevel> findLevels() throws SQLException {
        List<StockLevel> levels = new ArrayList<>();
        try (Statement statement = DatabaseConnection.getConnection().createStatement();
             ResultSet rs = statement.executeQuery(SELECT_LEVELS)) {
            while (rs.next()) {
                levels.add(mapLevel(rs));
            }
        }
        return levels;
    }

    @Override
    public List<Stock> findLots(int productId) throws SQLException {
        return findLots(SELECT_LOTS, productId);
    }

    @Override
    public List<Stock> findAvailable(int productId) throws SQLException {
        return findLots(SELECT_AVAILABLE, productId);
    }

    private List<Stock> findLots(String sql, int productId) throws SQLException {
        List<Stock> lots = new ArrayList<>();
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sql)) {
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
        // Light purchase: only its id is needed, to show "Compra #id" in the detail.
        Purchase purchase = new Purchase(rs.getInt("purchase_id"), null, null, null,
                null, null, null);
        return new Stock(rs.getInt("id"), rs.getInt("initial_quantity"),
                rs.getInt("available_quantity"), rs.getBigDecimal("purchase_price"),
                rs.getTimestamp("entry_date").toLocalDateTime(), product, purchase);
    }
}
