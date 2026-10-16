package com.entidaddesconocida.monte_erp.domain.repository;

import com.entidaddesconocida.monte_erp.domain.model.SalesOrder;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderLine;

import java.sql.SQLException;
import java.util.List;

/**
 * Port over {@link SalesOrder} entities (RF-07 / CU-07).
 *
 * <p>The order screen is not a CRUD: an order is created and then read as
 * history, never edited or deleted. RF-08 / US-08 adds the status change as the
 * only mutation, guarded so a concurrent change cannot be silently overwritten.</p>
 *
 * <p>The insert must be <strong>atomic</strong>: the order header and every line
 * are written in a single transaction, so a failed line never leaves an order
 * without its lines.</p>
 */
public interface SalesOrderRepository {

    /**
     * Inserts the order header and its lines in one transaction and returns the
     * generated order id (also assigned to the object).
     */
    int insert(SalesOrder order) throws SQLException;

    /** Every order, most recent first (the read-only history). */
    List<SalesOrder> findAll() throws SQLException;

    /** Lines of the given order, with their product, ordered by line id. */
    List<SalesOrderLine> findLines(int salesOrderId) throws SQLException;

    /**
     * Changes the status of an order only if it still has {@code currentStatus}
     * (optimistic guard, RF-08). Returns the rows affected: 1 on success, 0 when
     * the order was already moved by someone else.
     */
    int changeStatus(int id, String currentStatus, String newStatus) throws SQLException;
}
