package com.entidaddesconocida.monte_erp.domain.model;

import java.time.LocalDate;

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
public record PurchaseFilter(ReportPeriod period, Integer supplierId, PurchaseStatus status) {

    private static final PurchaseFilter ALL =
            new PurchaseFilter(ReportPeriod.all(), null, null);

    /**
     * @throws IllegalArgumentException when the supplier id is not positive
     */
    public PurchaseFilter {
        if (supplierId != null && supplierId <= 0) {
            throw new IllegalArgumentException(
                    "El identificador del proveedor debe ser positivo: " + supplierId);
        }
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
     * @param status     a {@link PurchaseStatus} or {@code null} for every status
     * @throws IllegalArgumentException when the range is inverted or the supplier
     *                                  id is not positive
     */
    public static PurchaseFilter of(LocalDate from, LocalDate to, Integer supplierId,
                                    PurchaseStatus status) {
        ReportPeriod period = ReportPeriod.of(from, to);
        if (supplierId != null && supplierId <= 0) {
            throw new IllegalArgumentException(
                    "El identificador del proveedor debe ser positivo: " + supplierId);
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
    public PurchaseStatus status() {
        return status;
    }

    /** Whether at least one criterion narrows the history. */
    public boolean isFiltered() {
        return !period.isEmpty() || supplierId != null || status != null;
    }

}
