package com.entidaddesconocida.monte_erp.domain.repository;

import com.entidaddesconocida.monte_erp.domain.model.ProductSupplier;
import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.PurchaseStatus;
import com.entidaddesconocida.monte_erp.domain.model.PurchaseFilter;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.service.PurchaseStockConsumedException;

import java.sql.SQLException;
import java.util.List;

/**
 * Port over {@link Purchase} entities (RF-14 / CU-10).
 *
 * <p>The purchase screen is not a CRUD: a purchase is created and then read as
 * history, never edited or deleted. The only mutation is the status change
 * ({@link #changeStatus(int, PurchaseStatus, PurchaseStatus)}), which follows the domain state
 * machine: {@code pending → received/cancelled} and {@code received → returned}.</p>
 *
 * <p>The insert must be <strong>atomic</strong>: the purchase header and every
 * lot are written in a single transaction, so a failed lot never leaves a
 * purchase without its lots (decision D5). A fresh purchase is {@code pending},
 * so its lots are written with {@code available_quantity = 0} and only become
 * available when the purchase is received.</p>
 *
 * <p>Conscious deviation from the class diagram: the lots travel
 * <strong>embedded</strong> in the entity ({@code insert(Purchase)}, decision D7)
 * instead of a second {@code List<Stock>} parameter, replicating
 * {@code SalesOrderRepository.insert(SalesOrder)}.</p>
 */
public interface PurchaseRepository {

    /**
     * Inserts the purchase header and its lots in one transaction and returns the
     * generated purchase id (also assigned to the object and to every lot).
     */
    int insert(Purchase purchase) throws SQLException;

    /**
     * Purchases matching {@code filter}, most recent first (the read-only
     * history). The filtering happens <strong>in the query</strong>: an absent
     * criterion means "no bound" and the date range is compared by day with
     * both bounds inclusive (the adapter translates the upper bound to
     * {@code < to + 1 day}).
     */
    List<Purchase> findAll(PurchaseFilter filter) throws SQLException;

    /** Every purchase, most recent first; equivalent to {@code findAll(PurchaseFilter.all())}. */
    default List<Purchase> findAll() throws SQLException {
        return findAll(PurchaseFilter.all());
    }

    /** Lots of the given purchase, with their product, ordered by entry date and id. */
    List<Stock> findLots(int purchaseId) throws SQLException;

    /**
     * Changes the status of a purchase only if it still has {@code currentStatus}
     * (optimistic guard). Returns the rows affected: 1 on success, 0 when the
     * purchase was already moved by someone else.
     *
     * <p>The stock follows the status in the same transaction: moving to
     * {@code received} makes every lot available ({@code available_quantity =
     * initial_quantity}), and moving to {@code returned} empties them. A return is
     * only accepted while no unit has been sold; otherwise nothing is touched and
     * {@link PurchaseStockConsumedException} is thrown. Cancelling a
     * {@code pending} purchase does not need to touch the stock, because its lots
     * were never available.</p>
     */
    int changeStatus(int id, PurchaseStatus currentStatus, PurchaseStatus newStatus)
            throws SQLException, PurchaseStockConsumedException;

    /**
     * Pairs (product, supplier) of the whole purchase history: a product appears
     * with a supplier when it has at least one lot of a purchase from that
     * supplier, <strong>even if the lot is at 0</strong> (decision D3, it asks
     * "where does this product come from", not "what is available").
     *
     * <p>The product screen resolves its supplier filter directly in
     * {@code ProductRepository}'s query; this projection exposes the same history
     * for callers that need the relation explicitly.</p>
     */
    List<ProductSupplier> findProductSuppliers() throws SQLException;
}
