package com.entidaddesconocida.monte_erp.domain.repository;

import com.entidaddesconocida.monte_erp.domain.model.ReportPeriod;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrder;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderFilter;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrderLine;
import com.entidaddesconocida.monte_erp.domain.model.SalesReportLine;
import com.entidaddesconocida.monte_erp.domain.model.StockIssue;
import com.entidaddesconocida.monte_erp.domain.service.InsufficientStockException;

import java.sql.SQLException;
import java.util.List;

/**
 * Port over {@link SalesOrder} entities (RF-07 / CU-07).
 *
 * <p>The order screen is not a CRUD: an order is created and then read as
 * history, never edited or deleted. RF-08 / US-08 adds the status change as the
 * only mutation, guarded so a concurrent change cannot be silently overwritten.</p>
 *
 * <p>The insert must be <strong>atomic</strong> and <strong>FIFO</strong>: the
 * order header, every line and the stock consumption ({@code STOCK_ISSUE} +
 * {@code STOCK.available_quantity}) are written in a single transaction, so a
 * failed line or an out-of-stock product never leaves an order without its lines
 * nor a partially discounted lot. The lots are re-read under row lock
 * ({@code FOR UPDATE}) so two concurrent sales cannot allocate the same units.</p>
 *
 * <p>Cancelling an order ({@code changeStatus} to {@code cancelled}) also reverts
 * the stock in that same transaction: the issues of the order are deleted and the
 * available quantity is given back to each lot, but only if the optimistic guard
 * still matches (RF-09/RF-15, decision D2).</p>
 */
public interface SalesOrderRepository {

    /**
     * Inserts the order header and its lines in one transaction, consuming the
     * stock FIFO from the oldest available lots, and returns the generated order
     * id (also assigned to the object).
     *
     * @throws InsufficientStockException when a line cannot be covered by the
     *                                    available lots; the whole transaction is
     *                                    rolled back, nothing is saved
     */
    int insert(SalesOrder order) throws SQLException, InsufficientStockException;

    /**
     * Orders matching {@code filter}, most recent first (the read-only history).
     * The filtering happens <strong>in the query</strong>: an absent criterion
     * means "no bound" and the date range is compared by day with both bounds
     * inclusive (the adapter translates the upper bound to
     * {@code < to + 1 day}).
     */
    List<SalesOrder> findAll(SalesOrderFilter filter) throws SQLException;

    /** Every order, most recent first; equivalent to {@code findAll(SalesOrderFilter.all())}. */
    default List<SalesOrder> findAll() throws SQLException {
        return findAll(SalesOrderFilter.all());
    }

    /** Lines of the given order, with their product, ordered by line id. */
    List<SalesOrderLine> findLines(int salesOrderId) throws SQLException;

    /**
     * Lots consumed by the given order, in FIFO order (RF-15, decision D7): each
     * issue carries the lot and its product so the detail can show from which lot
     * every unit left.
     */
    List<StockIssue> findIssues(int salesOrderId) throws SQLException;

    /**
     * Changes the status of an order only if it still has {@code currentStatus}
     * (optimistic guard, RF-08). Returns the rows affected: 1 on success, 0 when
     * the order was already moved by someone else.
     *
     * <p>When {@code newStatus} is {@code cancelled}, the stock consumed by the
     * order is given back and its issues are deleted in the same transaction, but
     * only after the guard matched (0 rows means no stock is touched).</p>
     */
    int changeStatus(int id, String currentStatus, String newStatus) throws SQLException;

    /**
     * Flat projection of the order lines inside the given period, used as the raw
     * input of the sales report (RF-12 / CU-09, decisions D2/D3/D8). The period
     * filters {@code SALES_ORDER.order_date} with inclusive bounds and an empty
     * period means no filter. It does <strong>not</strong> filter by status:
     * excluding the cancelled orders is a domain invariant
     * ({@code SalesReportCalculator}, decision D3), not a {@code WHERE}.
     */
    List<SalesReportLine> findReportLines(ReportPeriod period) throws SQLException;
}
