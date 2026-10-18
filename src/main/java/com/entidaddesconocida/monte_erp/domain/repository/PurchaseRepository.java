package com.entidaddesconocida.monte_erp.domain.repository;

import com.entidaddesconocida.monte_erp.domain.model.Purchase;
import com.entidaddesconocida.monte_erp.domain.model.Stock;

import java.sql.SQLException;
import java.util.List;

/**
 * Port over {@link Purchase} entities (RF-14 / CU-10).
 *
 * <p>The purchase screen is not a CRUD: a purchase is created and then read as
 * history, never edited or deleted. US-13 does not implement a purchase state
 * machine (decision D1) nor the FIFO stock consumption (decision D4, deferred to
 * US-09/US-14), so there is no {@code changeStatus} nor {@code StockRepository}
 * here.</p>
 *
 * <p>The insert must be <strong>atomic</strong>: the purchase header and every
 * lot are written in a single transaction, so a failed lot never leaves a
 * purchase without its lots (decision D5).</p>
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

    /** Every purchase, most recent first (the read-only history). */
    List<Purchase> findAll() throws SQLException;

    /** Lots of the given purchase, with their product, ordered by entry date and id. */
    List<Stock> findLots(int purchaseId) throws SQLException;
}
