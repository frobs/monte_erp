package com.entidaddesconocida.monte_erp.domain.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

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
public final class SalesOrderFilter {

    /** The four statuses of the domain, the only ones a filter may ask for. */
    private static final Set<String> STATUSES = Set.of(
            SalesOrder.STATUS_PENDING,
            SalesOrder.STATUS_SHIPPED,
            SalesOrder.STATUS_DELIVERED,
            SalesOrder.STATUS_CANCELLED);

    private static final SalesOrderFilter ALL =
            new SalesOrderFilter(ReportPeriod.all(), null, null);

    private final ReportPeriod period;
    private final Integer customerId;
    private final String status;

    private SalesOrderFilter(ReportPeriod period, Integer customerId, String status) {
        this.period = period;
        this.customerId = customerId;
        this.status = status;
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
     * @param status     one of the {@code SalesOrder.STATUS_*} constants or {@code null}
     * @throws IllegalArgumentException when the range is inverted, the status is
     *                                  unknown or the customer id is not positive
     */
    public static SalesOrderFilter of(LocalDate from, LocalDate to, Integer customerId,
                                      String status) {
        ReportPeriod period = ReportPeriod.of(from, to);
        if (customerId != null && customerId <= 0) {
            throw new IllegalArgumentException(
                    "El identificador del cliente debe ser positivo: " + customerId);
        }
        if (status != null && !STATUSES.contains(status)) {
            throw new IllegalArgumentException("Estado de pedido desconocido: " + status);
        }
        if (period.isEmpty() && customerId == null && status == null) {
            return ALL;
        }
        return new SalesOrderFilter(period, customerId, status);
    }

    /** Date range applied to {@code SALES_ORDER.order_date}; never {@code null}. */
    public ReportPeriod period() {
        return period;
    }

    /** Customer id or {@code null} when every customer is listed. */
    public Integer customerId() {
        return customerId;
    }

    /** Order status or {@code null} when every status is listed. */
    public String status() {
        return status;
    }

    /** Whether at least one criterion narrows the history. */
    public boolean isFiltered() {
        return !period.isEmpty() || customerId != null || status != null;
    }

    /**
     * Spanish label for the user interface or diagnostics, e.g.
     * {@code "01/01/2025 – 31/03/2025 · Cliente #3 · Pendiente"}. The customer
     * is rendered by id because the filter only carries the foreign key.
     */
    public String label() {
        if (!isFiltered()) {
            return "Todo el histórico";
        }
        StringBuilder label = new StringBuilder();
        if (!period.isEmpty()) {
            label.append(period.label());
        }
        if (customerId != null) {
            appendSeparator(label);
            label.append("Cliente #").append(customerId);
        }
        if (status != null) {
            appendSeparator(label);
            label.append(SalesOrder.labelFor(status));
        }
        return label.toString();
    }

    private static void appendSeparator(StringBuilder label) {
        if (label.length() > 0) {
            label.append(" · ");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SalesOrderFilter filter)) {
            return false;
        }
        return period.equals(filter.period)
                && Objects.equals(customerId, filter.customerId)
                && Objects.equals(status, filter.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(period, customerId, status);
    }

    @Override
    public String toString() {
        return "SalesOrderFilter{" + label() + '}';
    }
}
