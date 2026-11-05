package com.entidaddesconocida.monte_erp.infrastructure;

import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.ProductSupplier;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.PurchaseFilter;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.Supplier;
import com.entidaddesconocida.monte_erp.domain.repository.PurchaseRepository;
import com.entidaddesconocida.monte_erp.domain.service.InactiveEntityException;
import com.entidaddesconocida.monte_erp.domain.service.PurchaseStockConsumedException;
import com.entidaddesconocida.monte_erp.infrastructure.config.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
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
 * <p>A purchase is created {@code pending}, so each lot is inserted with
 * {@code available_quantity = 0}: the stock only counts when the purchase is
 * received. {@link #changeStatus(int, String, String)} mirrors that rule inside a
 * single transaction with an optimistic guard: {@code pending → received} makes
 * the lots available, {@code received → returned} empties them only after locking
 * them and proving that no unit was sold, and {@code pending → cancelled} touches
 * no stock. The reads join {@code SUPPLIER} / {@code PRODUCT} to build
 * <em>light</em> entities carrying the name the table shows.
 * {@link #findLots(int)} does not filter {@code p.active}: an old purchase
 * must keep showing a product that has since been given up.</p>
 *
 * <p>{@link #findAll(PurchaseFilter)} filters <strong>in the query</strong> with
 * one {@code ? IS NULL} guard per criterion (date range, supplier and status), so
 * the same statement serves the whole history and any combination of filters
 * without concatenating SQL. The upper bound is exclusive at {@code + 1 day} so a
 * whole "hasta" day is included without a time component, mirroring
 * {@code ReportPeriod.contains(LocalDateTime)}.</p>
 *
 * <p>{@link #findProductSuppliers()} projects the product ↔ supplier pairs of the
 * whole history with a {@code DISTINCT} over {@code STOCK JOIN PURCHASE}; it does
 * <strong>not</strong> filter {@code available_quantity} nor {@code PRODUCT.active}
 * (decision D3: the filter asks where the product comes from, so a lot at 0 still
 * counts and an inactive product is still listed with its suppliers).</p>
 */
public class MySqlPurchaseRepository implements PurchaseRepository {

    private static final String INSERT_PURCHASE =
            "INSERT INTO PURCHASE (status, total, supplier_id, user_id) VALUES (?, ?, ?, ?)";
    private static final String INSERT_LOT =
            "INSERT INTO STOCK (initial_quantity, available_quantity, purchase_price, product_id,"
                    + " purchase_id) VALUES (?, ?, ?, ?, ?)";
    private static final String SELECT_ACTIVE_SUPPLIER_FOR_UPDATE =
            "SELECT active FROM SUPPLIER WHERE id = ? FOR UPDATE";
    private static final String SELECT_ACTIVE_PRODUCT_FOR_UPDATE =
            "SELECT active FROM PRODUCT WHERE id = ? FOR UPDATE";
    private static final String SELECT_PURCHASES = """
            SELECT p.id, p.purchase_date, p.status, p.total, p.supplier_id, s.name AS supplier_name
              FROM PURCHASE p
              JOIN SUPPLIER s ON s.id = p.supplier_id
             WHERE (? IS NULL OR p.purchase_date >= ?)
               AND (? IS NULL OR p.purchase_date < DATE_ADD(?, INTERVAL 1 DAY))
               AND (? IS NULL OR p.supplier_id = ?)
               AND (? IS NULL OR p.status = ?)
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
    private static final String SELECT_PRODUCT_SUPPLIERS = """
            SELECT DISTINCT s.product_id, p.supplier_id
              FROM STOCK s
              JOIN PURCHASE p ON p.id = s.purchase_id
             ORDER BY s.product_id, p.supplier_id
            """;
    /**
     * Optimistic guard: the row is only updated while it still has the status the
     * caller read, so a concurrent change cannot be overwritten.
     */
    private static final String UPDATE_STATUS =
            "UPDATE PURCHASE SET status = ? WHERE id = ? AND status = ?";
    /** pending → received: the lots of the purchase become available. */
    private static final String MAKE_LOTS_AVAILABLE =
            "UPDATE STOCK SET available_quantity = initial_quantity WHERE purchase_id = ?";
    /** received → returned: the remaining units leave the stock. */
    private static final String EMPTY_LOTS =
            "UPDATE STOCK SET available_quantity = 0 WHERE purchase_id = ?";
    /**
     * Locks the lots of the purchase before a return, so a concurrent sale cannot
     * consume units between the check and the update.
     */
    private static final String SELECT_LOTS_FOR_UPDATE =
            "SELECT initial_quantity, available_quantity FROM STOCK WHERE purchase_id = ? FOR UPDATE";

    @Override
    public int insert(Purchase purchase) throws SQLException {
        Connection connection = DatabaseConnection.getConnection();
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            requireActive(connection, SELECT_ACTIVE_SUPPLIER_FOR_UPDATE,
                    purchase.getSupplier().getId(), "proveedor");
            int id = insertPurchase(connection, purchase);
            purchase.setId(id);
            for (Stock lot : purchase.getLots()) {
                requireActive(connection, SELECT_ACTIVE_PRODUCT_FOR_UPDATE,
                        lot.getProduct().getId(), "producto");
                insertLot(connection, purchase, lot);
            }
            connection.commit();
            return id;
        } catch (SQLException | RuntimeException e) {
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
    public List<Purchase> findAll(PurchaseFilter filter) throws SQLException {
        List<Purchase> purchases = new ArrayList<>();
        try (PreparedStatement ps = DatabaseConnection.getConnection()
                .prepareStatement(SELECT_PURCHASES)) {
            // Both placeholders of each guard receive the same value, so a null
            // criterion disables its comparison instead of filtering by NULL.
            bindDate(ps, 1, filter.period().from());
            bindDate(ps, 2, filter.period().from());
            bindDate(ps, 3, filter.period().to());
            bindDate(ps, 4, filter.period().to());
            bindInt(ps, 5, filter.supplierId());
            bindInt(ps, 6, filter.supplierId());
            bindString(ps, 7, filter.status());
            bindString(ps, 8, filter.status());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    purchases.add(mapPurchase(rs));
                }
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

    @Override
    public List<ProductSupplier> findProductSuppliers() throws SQLException {
        List<ProductSupplier> pairs = new ArrayList<>();
        try (Statement statement = DatabaseConnection.getConnection().createStatement();
             ResultSet rs = statement.executeQuery(SELECT_PRODUCT_SUPPLIERS)) {
            while (rs.next()) {
                pairs.add(new ProductSupplier(rs.getInt("product_id"), rs.getInt("supplier_id")));
            }
        }
        return pairs;
    }

    @Override
    public int changeStatus(int id, String currentStatus, String newStatus)
            throws SQLException, PurchaseStockConsumedException {
        Connection connection = DatabaseConnection.getConnection();
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int updated = updateStatus(connection, id, currentStatus, newStatus);
            if (updated == 0) {
                connection.rollback();
                return 0;
            }
            if (Purchase.STATUS_RECEIVED.equals(newStatus)) {
                makeLotsAvailable(connection, id);
            } else if (Purchase.STATUS_RETURNED.equals(newStatus)) {
                emptyLots(connection, id);
            }
            connection.commit();
            return updated;
        } catch (SQLException | PurchaseStockConsumedException | RuntimeException e) {
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

    private int updateStatus(Connection connection, int id, String currentStatus, String newStatus)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, newStatus);
            ps.setInt(2, id);
            ps.setString(3, currentStatus);
            return ps.executeUpdate();
        }
    }

    private void makeLotsAvailable(Connection connection, int purchaseId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(MAKE_LOTS_AVAILABLE)) {
            ps.setInt(1, purchaseId);
            ps.executeUpdate();
        }
    }

    /**
     * Empties the lots of a returned purchase, but only when every one of them
     * still has its full initial quantity. The lots are locked first so a
     * concurrent sale cannot sneak a consumption in between the check and the
     * update.
     */
    private void emptyLots(Connection connection, int purchaseId)
            throws SQLException, PurchaseStockConsumedException {
        int consumedUnits = 0;
        try (PreparedStatement ps = connection.prepareStatement(SELECT_LOTS_FOR_UPDATE)) {
            ps.setInt(1, purchaseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    consumedUnits += rs.getInt("initial_quantity") - rs.getInt("available_quantity");
                }
            }
        }
        if (consumedUnits > 0) {
            throw new PurchaseStockConsumedException(purchaseId, consumedUnits);
        }
        try (PreparedStatement ps = connection.prepareStatement(EMPTY_LOTS)) {
            ps.setInt(1, purchaseId);
            ps.executeUpdate();
        }
    }

    private void requireActive(Connection connection, String sql, int id, String entity)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !rs.getBoolean("active")) {
                    throw new InactiveEntityException("El " + entity
                            + " fue dado de baja mientras se preparaba la compra. Actualiza el formulario.");
                }
            }
        }
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
        // A pending purchase writes its lots without stock: they only count from
        // the moment the purchase is received (available == initial then).
        int availableQuantity = Purchase.STATUS_RECEIVED.equals(purchase.getStatus())
                ? lot.getInitialQuantity() : 0;
        try (PreparedStatement ps = connection.prepareStatement(INSERT_LOT)) {
            ps.setInt(1, lot.getInitialQuantity());
            ps.setInt(2, availableQuantity);
            ps.setBigDecimal(3, lot.getPurchasePrice());
            ps.setInt(4, lot.getProduct().getId());
            ps.setInt(5, purchase.getId());
            ps.executeUpdate();
            // The lot row id is not read back (one INSERT per line, like the
            // order lines): only the purchase link is known, and it is assigned
            // so the in-memory entity mirrors the stored row.
            lot.setPurchase(purchase);
            lot.setAvailableQuantity(availableQuantity);
        }
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

    /** Binds an optional integer (the supplier id), using SQL NULL when absent. */
    private static void bindInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    /** Binds an optional string (the status), using SQL NULL when absent. */
    private static void bindString(PreparedStatement ps, int index, String value)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
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
