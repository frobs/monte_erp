package com.entidaddesconocida.monte_erp.domain.service;

import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.StockReportSummary;

import java.math.BigDecimal;
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
 * <p>Like {@link FifoAllocator} it is a stateless utility: no database, no
 * JavaFX, so the valuation rule is unit tested in isolation.</p>
 */
public final class StockReportCalculator {

    private StockReportCalculator() {
        // Utility class: never instantiated.
    }

    /**
     * Totals the given lots. {@code null} or an empty list yields an empty
     * summary with zero totals.
     */
    public static StockReportSummary summarize(List<Stock> lots) {
        if (lots == null || lots.isEmpty()) {
            return StockReportSummary.empty();
        }
        List<Stock> snapshot = List.copyOf(lots);
        int totalAvailableUnits = 0;
        BigDecimal totalStockValue = BigDecimal.ZERO;
        for (Stock lot : snapshot) {
            totalAvailableUnits += lot.getAvailableQuantity();
            totalStockValue = totalStockValue.add(StockReportSummary.valueOf(lot));
        }
        return new StockReportSummary(snapshot, snapshot.size(), totalAvailableUnits,
                totalStockValue);
    }
}
