package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.Supplier;
import com.entidaddesconocida.monte_erp.domain.repository.PurchaseRepository;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC implementation of {@link PurchaseRepository} for the {@code PURCHASE} and
 * {@code STOCK} tables (RF-14 / CU-10).
 *
 * <p>No business logic: it only translates between the entities and the table
 * rows. {@link #insert(Purchase)} is <strong>transactional</strong> over the
 * shared {@link DatabaseConnection} connection: the header and every lot are
 * committed together, and any failure rolls the whole purchase back (decision D5).
 * The previous {@code autoCommit} value is restored in a {@code finally} so the
 * singleton connection is left as it was found.</p>
 *
 * <p>Each lot is inserted with {@code available_quantity = initial_quantity}: a
 * lot is born available (decision D1/D6). The reads join {@code SUPPLIER} /
 * {@code PRODUCT} to build <em>light</em> entities carrying the name the table
 * shows. {@link #findLots(int)} does not filter {@code p.active}: an old purchase
 * must keep showing a product that has since been given up.</p>
 */
public class MySqlPurchaseRepository implements PurchaseRepository {

    private static final String INSERT_PURCHASE =
            "INSERT INTO PURCHASE (status, total, supplier_id, user_id) VALUES (?, ?, ?, ?)";
    private static final String INSERT_LOT =
            "INSERT INTO STOCK (initial_quantity, available_quantity, purchase_price, product_id,"
                    + " purchase_id) VALUES (?, ?, ?, ?, ?)";
    private static final String SELECT_PURCHASES = """
            SELECT p.id, p.purchase_date, p.status, p.total, p.supplier_id, s.name AS supplier_name
              FROM PURCHASE p
              JOIN SUPPLIER s ON s.id = p.supplier_id
             ORDER BY p.purchase_date DESC, p.id DESC
            """;
    private static final String SELECT_LOTS = """
            SELECT s.id, s.initial_quantity, s.available_quantity, s.purchase_price, s.entry_date,
                   s.product_id, p.name AS product_name, p.sku AS product_sku
              FROM STOCK s
              JOIN PRODUCT p ON p.id = s.product_id
             WHERE s.purchase_id = ?
             ORDER BY s.entry_date, s.id
            """;

    @Override
    public int insert(Purchase purchase) throws SQLException {
        Connection connection = DatabaseConnection.getConnection();
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int id = insertPurchase(connection, purchase);
            purchase.setId(id);
            for (Stock lot : purchase.getLots()) {
                insertLot(connection, purchase, lot);
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
    public List<Purchase> findAll() throws SQLException {
        List<Purchase> purchases = new ArrayList<>();
        try (Statement statement = DatabaseConnection.getConnection().createStatement();
             ResultSet rs = statement.executeQuery(SELECT_PURCHASES)) {
            while (rs.next()) {
                purchases.add(mapPurchase(rs));
            }
        }
        return purchases;
    }

    @Override
    public List<Stock> findLots(int purchaseId) throws SQLException {
        List<Stock> lots = new ArrayList<>();
        try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(SELECT_LOTS)) {
            ps.setInt(1, purchaseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lots.add(mapLot(rs));
                }
            }
        }
        return lots;
    }

    private int insertPurchase(Connection connection, Purchase purchase) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT_PURCHASE,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, purchase.getStatus());
            ps.setBigDecimal(2, purchase.getTotal());
            ps.setInt(3, purchase.getSupplier().getId());
            ps.setInt(4, purchase.getUser().getId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    private void insertLot(Connection connection, Purchase purchase, Stock lot) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT_LOT)) {
            ps.setInt(1, lot.getInitialQuantity());
            // A lot is born available (D1/D6): available == initial on insert.
            ps.setInt(2, lot.getInitialQuantity());
            ps.setBigDecimal(3, lot.getPurchasePrice());
            ps.setInt(4, lot.getProduct().getId());
            ps.setInt(5, purchase.getId());
            ps.executeUpdate();
            // The lot row id is not read back (one INSERT per line, like the
            // order lines): only the purchase link is known, and it is assigned
            // so the in-memory entity mirrors the stored row.
            lot.setPurchase(purchase);
            lot.setAvailableQuantity(lot.getInitialQuantity());
        }
    }

    private Purchase mapPurchase(ResultSet rs) throws SQLException {
        Supplier supplier = new Supplier(rs.getInt("supplier_id"), rs.getString("supplier_name"),
                null, null, null, true);
        return new Purchase(rs.getInt("id"),
                rs.getTimestamp("purchase_date").toLocalDateTime(),
                rs.getString("status"),
                rs.getBigDecimal("total"),
                supplier,
                null,
                new ArrayList<>());
    }

    private Stock mapLot(ResultSet rs) throws SQLException {
        Product product = new Product(rs.getString("product_name"), rs.getString("product_sku"),
                null, null, 0, null, true);
        product.setId(rs.getInt("product_id"));
        return new Stock(rs.getInt("id"), rs.getInt("initial_quantity"),
                rs.getInt("available_quantity"), rs.getBigDecimal("purchase_price"),
                rs.getTimestamp("entry_date").toLocalDateTime(), product, null);
    }
}
