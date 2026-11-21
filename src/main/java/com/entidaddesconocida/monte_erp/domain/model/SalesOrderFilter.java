package com.entidaddesconocida.monte_erp.domain.model;

import java.time.LocalDate;

/**
 * Immutable value object holding the criteria the sales-order history is
 * filtered by (RF-07 / CU-07, mirroring the purchase filter of RF-14 / CU-10).
 *
 * <p>Every criterion is optional and {@code null} means "no bound", so
 * {@link #all()} represents the whole history. The date range is delegated to
 * {@link ReportPeriod}, which compares <strong>by day</strong> with both bounds
 * inclusive; the SQL adapter mirrors that rule with
 * {@code order_date >= from AND order_date < to + 1 day}.</p>
 *
 * <p>Unlike the in-memory filters of the CRUD screens, this filter travels to the
 * database: the adapter builds one {@code WHERE} with a {@code ? IS NULL} guard
 * per criterion instead of concatenating SQL. The factory rejects an inverted
 * range (through {@link ReportPeriod#of(LocalDate, LocalDate)}), an unknown
 * status and a non-positive customer id <em>before</em> any query is issued, so
 * the ViewModel can show a validation message without touching the database.</p>
 */
public record SalesOrderFilter(ReportPeriod period, Integer customerId, OrderStatus status) {

    private static final SalesOrderFilter ALL =
            new SalesOrderFilter(ReportPeriod.all(), null, null);

    /**
     * @throws IllegalArgumentException when the customer id is not positive
     */
    public SalesOrderFilter {
        if (customerId != null && customerId <= 0) {
            throw new IllegalArgumentException(
                    "El identificador del cliente debe ser positivo: " + customerId);
        }
    }

    /** Whole history: no date range, no customer and no status. */
    public static SalesOrderFilter all() {
        return ALL;
    }

    /**
     * Builds a filter with optional criteria.
     *
     * @param from       lower bound (inclusive) of the order date or {@code null}
     * @param to         upper bound (inclusive) of the order date or {@code null}
     * @param customerId customer to filter by or {@code null} for every customer
     * @param status     an {@link OrderStatus} or {@code null} for every status
     * @throws IllegalArgumentException when the range is inverted or the customer
     *                                  id is not positive
     */
    public static SalesOrderFilter of(LocalDate from, LocalDate to, Integer customerId,
                                      OrderStatus status) {
        ReportPeriod period = ReportPeriod.of(from, to);
        if (period.isEmpty() && customerId == null && status == null) {
            return ALL;
        }
        return new SalesOrderFilter(period, customerId, status);
    }

    /** Whether at least one criterion narrows the history. */
    public boolean isFiltered() {
        return !period.isEmpty() || customerId != null || status != null;
    }

}
