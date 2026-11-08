package com.entidaddesconocida.monte_erp.domain.service;

import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.StockReportSummary;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure domain service that totals the stock report (RF-12 / CU-09, decision D9).
 *
 * <p>It receives the lots the adapter read and computes the number of lots, the
 * available units and the <strong>value</strong> of the stock. The value uses the
 * available quantity ({@code availableQuantity × purchasePrice}) and never the
 * initial one: a lot that has already been sold does not inflate the valuation.
 * Money is a {@link BigDecimal}, never a {@code double}.</p>
 *
 * <p>The lots whose purchase was <strong>returned or cancelled</strong> are left
 * out of the totals ({@link Stock#countsInStockReport()}): they stay in the
 * report, marked, so the 0 available has an explanation, but they are no longer
 * stock. {@link #excludedCount(List)} counts them for the footer.</p>
 *
 * <p>Like {@link FifoAllocator} it is a stateless utility: no database, no
 * JavaFX, so the valuation rule is unit tested in isolation.</p>
 */
public final class StockReportCalculator {

    private StockReportCalculator() {
        // Utility class: never instantiated.
    }

    /**
     * Totals the given lots, ignoring the ones excluded from the report. {@code
     * null} or an empty list yields an empty summary with zero totals.
     */
    public static StockReportSummary summarize(List<Stock> lots) {
        if (lots == null || lots.isEmpty()) {
            return StockReportSummary.empty();
        }
        List<Stock> counted = new ArrayList<>();
        for (Stock lot : lots) {
            if (lot != null && lot.countsInStockReport()) {
                counted.add(lot);
            }
        }
        if (counted.isEmpty()) {
            return StockReportSummary.empty();
        }
        List<Stock> snapshot = List.copyOf(counted);
        int totalAvailableUnits = 0;
        BigDecimal totalStockValue = BigDecimal.ZERO;
        for (Stock lot : snapshot) {
            totalAvailableUnits += lot.getAvailableQuantity();
            totalStockValue = totalStockValue.add(StockReportSummary.valueOf(lot));
        }
        return new StockReportSummary(snapshot, snapshot.size(), totalAvailableUnits,
                totalStockValue);
    }

    /**
     * Number of lots the report shows but does not total: those whose purchase
     * was returned or cancelled. {@code null} yields 0.
     */
    public static long excludedCount(List<Stock> lots) {
        if (lots == null) {
            return 0;
        }
        return lots.stream()
                .filter(lot -> lot == null || !lot.countsInStockReport())
                .count();
    }
}
