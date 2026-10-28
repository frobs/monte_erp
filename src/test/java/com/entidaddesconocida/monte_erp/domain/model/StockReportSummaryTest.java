package com.entidaddesconocida.monte_erp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link StockReportSummary} (RF-12 / CU-09): the value of the
 * stock uses the available quantity, never the initial one.
 */
class StockReportSummaryTest {

    private static Stock lot(int initial, int available, String price) {
        Product product = new Product("Teclado", "SKU-1", null, new BigDecimal("10.00"), 0, null, true);
        product.setId(1);
        return new Stock(1, initial, available, new BigDecimal(price),
                LocalDateTime.of(2025, 1, 1, 10, 0), product, null);
    }

    @Test
    @DisplayName("El valor del stock usa el disponible, no la cantidad inicial")
    void valueUsesAvailableQuantity() {
        Stock consumed = lot(10, 3, "2.00");

        StockReportSummary summary = new StockReportSummary(List.of(consumed), 1, 3,
                new BigDecimal("6.00"));

        assertEquals(1, summary.lotCount());
        assertEquals(3, summary.totalAvailableUnits());
        assertEquals(0, new BigDecimal("6.00").compareTo(summary.totalStockValue()));
        // Sanity check: using the initial quantity (10 × 2.00 = 20.00) would be wrong.
        assertEquals(0, new BigDecimal("6.00").compareTo(StockReportSummary.valueOf(consumed)));
    }

    @Test
    @DisplayName("Totales que no cuadran con los lotes se rechazan")
    void mismatchedTotalsAreRejected() {
        Stock consumed = lot(10, 3, "2.00");

        assertThrows(IllegalArgumentException.class,
                () -> new StockReportSummary(List.of(consumed), 2, 3, new BigDecimal("6.00")));
        assertThrows(IllegalArgumentException.class,
                () -> new StockReportSummary(List.of(consumed), 1, 4, new BigDecimal("6.00")));
        assertThrows(IllegalArgumentException.class,
                () -> new StockReportSummary(List.of(consumed), 1, 3, new BigDecimal("20.00")));
    }

    @Test
    @DisplayName("empty() no tiene lotes ni totales")
    void emptySummaryHasNoLots() {
        StockReportSummary summary = StockReportSummary.empty();

        assertEquals(0, summary.lotCount());
        assertEquals(0, summary.totalAvailableUnits());
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalStockValue()));
    }

    @Test
    @DisplayName("Un lote a 0 no aporta valor aunque tuviera cantidad inicial")
    void fullyConsumedLotAddsNoValue() {
        Stock exhausted = lot(10, 0, "2.00");

        assertEquals(0, BigDecimal.ZERO.compareTo(StockReportSummary.valueOf(exhausted)));
    }
}
