package com.entidaddesconocida.monte_erp.domain.service;

import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.Stock;
import com.entidaddesconocida.monte_erp.domain.model.StockReportSummary;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link StockReportCalculator} (RF-12 / CU-09): the valuation is
 * driven by the available quantity, not the initial one.
 */
class StockReportCalculatorTest {

    private static Stock lot(int id, int initial, int available, String price) {
        Product product = new Product("Teclado", "SKU-1", null, new BigDecimal("10.00"), 0, null, true);
        product.setId(1);
        return new Stock(id, initial, available, new BigDecimal(price),
                LocalDateTime.of(2025, 1, 1, 10, 0), product, null);
    }

    @Test
    @DisplayName("El valor usa el disponible: un lote consumido a 0 no suma (D9)")
    void valuationUsesAvailableQuantity() {
        StockReportSummary summary = StockReportCalculator.summarize(List.of(
                lot(1, 10, 3, "2.00"),
                lot(2, 5, 0, "9.00")));

        assertEquals(2, summary.lotCount());
        assertEquals(3, summary.totalAvailableUnits());
        assertEquals(0, new BigDecimal("6.00").compareTo(summary.totalStockValue()));
    }

    @Test
    @DisplayName("Los totales cuadran con la suma de los lotes")
    void totalsMatchLots() {
        StockReportSummary summary = StockReportCalculator.summarize(List.of(
                lot(1, 10, 4, "1.50"),
                lot(2, 8, 8, "2.25")));

        assertEquals(2, summary.lotCount());
        assertEquals(12, summary.totalAvailableUnits());
        assertEquals(0, new BigDecimal("24.00").compareTo(summary.totalStockValue()));
    }

    @Test
    @DisplayName("Sin lotes (o null) el resumen queda a cero")
    void emptyInputYieldsEmptySummary() {
        StockReportSummary fromNull = StockReportCalculator.summarize(null);
        StockReportSummary fromEmpty = StockReportCalculator.summarize(List.of());

        assertEquals(0, fromNull.lotCount());
        assertEquals(0, fromEmpty.lotCount());
        assertEquals(0, fromEmpty.totalAvailableUnits());
        assertEquals(0, BigDecimal.ZERO.compareTo(fromEmpty.totalStockValue()));
    }

    @Test
    @DisplayName("Un precio de compra ausente se valora como 0 sin romper el cuadre")
    void missingPriceCountsAsZero() {
        Stock noPrice = new Stock(9, 5, 5, null, LocalDateTime.of(2025, 1, 1, 10, 0),
                lot(1, 1, 1, "1.00").getProduct(), null);

        StockReportSummary summary = StockReportCalculator.summarize(List.of(noPrice));

        assertEquals(5, summary.totalAvailableUnits());
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalStockValue()));
    }
}
