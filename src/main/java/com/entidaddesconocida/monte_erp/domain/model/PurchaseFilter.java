package com.entidaddesconocida.monte_erp.domain.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable value object holding the criteria the purchase history is filtered
 * by (RF-14 / CU-10).
 *
 * <p>Every criterion is optional and {@code null} means "no bound", so
 * {@link #all()} represents the whole history. The date range is delegated to
 * {@link ReportPeriod}, which compares <strong>by day</strong> with both bounds
 * inclusive; the SQL adapter mirrors that rule with
 * {@code purchase_date >= from AND purchase_date < to + 1 day}.</p>
 *
 * <p>Unlike the in-memory filters of the CRUD screens, this filter travels to the
 * database: the adapter builds one {@code WHERE} with a {@code ? IS NULL} guard
 * per criterion instead of concatenating SQL. The factory rejects an inverted
 * range (through {@link ReportPeriod#of(LocalDate, LocalDate)}), an unknown
 * status and a non-positive supplier id <em>before</em> any query is issued, so
 * the ViewModel can show a validation message without touching the database.</p>
 */
public final class PurchaseFilter {

    /** The four statuses of the domain, the only ones a filter may ask for. */
    private static final Set<String> STATUSES = Set.of(
            Purchase.STATUS_PENDING,
            Purchase.STATUS_RECEIVED,
            Purchase.STATUS_CANCELLED,
            Purchase.STATUS_RETURNED);

    private static final PurchaseFilter ALL =
            new PurchaseFilter(ReportPeriod.all(), null, null);

    private final ReportPeriod period;
    private final Integer supplierId;
    private final String status;

    private PurchaseFilter(ReportPeriod period, Integer supplierId, String status) {
        this.period = period;
        this.supplierId = supplierId;
        this.status = status;
    }

    /** Whole history: no date range, no supplier and no status. */
    public static PurchaseFilter all() {
        return ALL;
    }

    /**
     * Builds a filter with optional criteria.
     *
     * @param from       lower bound (inclusive) of the purchase date or {@code null}
     * @param to         upper bound (inclusive) of the purchase date or {@code null}
     * @param supplierId supplier to filter by or {@code null} for every supplier
     * @param status     one of the {@code Purchase.STATUS_*} constants or {@code null}
     * @throws IllegalArgumentException when the range is inverted, the status is
     *                                  unknown or the supplier id is not positive
     */
    public static PurchaseFilter of(LocalDate from, LocalDate to, Integer supplierId,
                                    String status) {
        ReportPeriod period = ReportPeriod.of(from, to);
        if (supplierId != null && supplierId <= 0) {
            throw new IllegalArgumentException(
                    "El identificador del proveedor debe ser positivo: " + supplierId);
        }
        if (status != null && !STATUSES.contains(status)) {
            throw new IllegalArgumentException("Estado de compra desconocido: " + status);
        }
        if (period.isEmpty() && supplierId == null && status == null) {
            return ALL;
        }
        return new PurchaseFilter(period, supplierId, status);
    }

    /** Date range applied to {@code PURCHASE.purchase_date}; never {@code null}. */
    public ReportPeriod period() {
        return period;
    }

    /** Supplier id or {@code null} when every supplier is listed. */
    public Integer supplierId() {
        return supplierId;
    }

    /** Purchase status or {@code null} when every status is listed. */
    public String status() {
        return status;
    }

    /** Whether at least one criterion narrows the history. */
    public boolean isFiltered() {
        return !period.isEmpty() || supplierId != null || status != null;
    }

    /**
     * Spanish label for the user interface or diagnostics, e.g.
     * {@code "01/01/2025 – 31/03/2025 · Proveedor #3 · Pendiente"}. The supplier
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
        if (supplierId != null) {
            appendSeparator(label);
            label.append("Proveedor #").append(supplierId);
        }
        if (status != null) {
            appendSeparator(label);
            label.append(Purchase.labelFor(status));
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
        if (!(other instanceof PurchaseFilter filter)) {
            return false;
        }
        return period.equals(filter.period)
                && Objects.equals(supplierId, filter.supplierId)
                && Objects.equals(status, filter.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(period, supplierId, status);
    }

    @Override
    public String toString() {
        return "PurchaseFilter{" + label() + '}';
    }
}
