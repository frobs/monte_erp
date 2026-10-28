package com.entidaddesconocida.monte_erp.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of the stock report: the lots plus the global totals (RF-12 / CU-09).
 *
 * <p>The value of the stock is computed with the <strong>available</strong>
 * quantity ({@code availableQuantity × purchasePrice}), never the initial one: a
 * consumed lot no longer adds value (decision D9). The constructor verifies that
 * {@code lotCount}, {@code totalAvailableUnits} and {@code totalStockValue} match
 * the given lots, so an aggregation bug cannot reach the screen.</p>
 */
public final class StockReportSummary {

    private final List<Stock> lots;
    private final int lotCount;
    private final int totalAvailableUnits;
    private final BigDecimal totalStockValue;

    /**
     * @throws IllegalArgumentException when a total does not match the lots
     */
    public StockReportSummary(List<Stock> lots, int lotCount, int totalAvailableUnits,
                              BigDecimal totalStockValue) {
        this.lots = List.copyOf(lots == null ? List.of() : lots);
        if (totalStockValue == null || totalStockValue.signum() < 0) {
            throw new IllegalArgumentException(
                    "El valor del stock no puede ser negativo: " + totalStockValue);
        }
        int expectedUnits = 0;
        BigDecimal expectedValue = BigDecimal.ZERO;
        for (Stock lot : this.lots) {
            expectedUnits += lot.getAvailableQuantity();
            expectedValue = expectedValue.add(valueOf(lot));
        }
        if (lotCount != this.lots.size()) {
            throw new IllegalArgumentException("El número de lotes del resumen (" + lotCount
                    + ") no cuadra con la lista (" + this.lots.size() + ')');
        }
        if (totalAvailableUnits != expectedUnits) {
            throw new IllegalArgumentException("Las unidades del resumen (" + totalAvailableUnits
                    + ") no cuadran con los lotes (" + expectedUnits + ')');
        }
        if (totalStockValue.compareTo(expectedValue) != 0) {
            throw new IllegalArgumentException("El valor del resumen (" + totalStockValue
                    + ") no cuadra con los lotes (" + expectedValue + ')');
        }
        this.lotCount = lotCount;
        this.totalAvailableUnits = totalAvailableUnits;
        this.totalStockValue = totalStockValue;
    }

    /** Empty report: no lots and zero totals. */
    public static StockReportSummary empty() {
        return new StockReportSummary(List.of(), 0, 0, BigDecimal.ZERO);
    }

    /**
     * Value a lot adds to the report: {@code availableQuantity × purchasePrice}
     * (0 when the price is missing). Uses the available quantity on purpose.
     */
    public static BigDecimal valueOf(Stock lot) {
        if (lot == null || lot.getPurchasePrice() == null) {
            return BigDecimal.ZERO;
        }
        return lot.getPurchasePrice().multiply(BigDecimal.valueOf(lot.getAvailableQuantity()));
    }

    public List<Stock> lots() {
        return lots;
    }

    public int lotCount() {
        return lotCount;
    }

    public int totalAvailableUnits() {
        return totalAvailableUnits;
    }

    public BigDecimal totalStockValue() {
        return totalStockValue;
    }

    @Override
    public String toString() {
        return "StockReportSummary{lotCount=" + lotCount + ", totalAvailableUnits="
                + totalAvailableUnits + ", totalStockValue=" + totalStockValue + '}';
    }
}
