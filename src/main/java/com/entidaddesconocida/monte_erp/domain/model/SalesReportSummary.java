package com.entidaddesconocida.monte_erp.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of the sales report: the per-product rows plus the global totals
 * (RF-12 / CU-09, decision D2).
 *
 * <p>The totals must <strong>match</strong> the rows ({@code totalUnits} is the
 * sum of the row units and {@code totalRevenue} the sum of the row revenues);
 * the constructor throws when they do not, so a bug in the aggregation cannot
 * reach the screen or the CSV unnoticed. {@code orderCount} counts the distinct
 * non-cancelled orders that contributed to the rows (decision D3).</p>
 */
public final class SalesReportSummary {

    private final List<SalesReportRow> rows;
    private final int orderCount;
    private final int totalUnits;
    private final BigDecimal totalRevenue;

    /**
     * @throws IllegalArgumentException when {@code orderCount} is negative or
     *                                  the totals do not match the rows
     */
    public SalesReportSummary(List<SalesReportRow> rows, int orderCount, int totalUnits,
                              BigDecimal totalRevenue) {
        this.rows = List.copyOf(rows == null ? List.of() : rows);
        if (orderCount < 0) {
            throw new IllegalArgumentException(
                    "El número de pedidos no puede ser negativo: " + orderCount);
        }
        if (totalRevenue == null || totalRevenue.signum() < 0) {
            throw new IllegalArgumentException("La facturación no puede ser negativa: " + totalRevenue);
        }
        int expectedUnits = 0;
        BigDecimal expectedRevenue = BigDecimal.ZERO;
        for (SalesReportRow row : this.rows) {
            expectedUnits += row.getUnitsSold();
            expectedRevenue = expectedRevenue.add(row.getRevenue());
        }
        if (totalUnits != expectedUnits) {
            throw new IllegalArgumentException("Las unidades del resumen (" + totalUnits
                    + ") no cuadran con las filas (" + expectedUnits + ')');
        }
        if (totalRevenue.compareTo(expectedRevenue) != 0) {
            throw new IllegalArgumentException("La facturación del resumen (" + totalRevenue
                    + ") no cuadra con las filas (" + expectedRevenue + ')');
        }
        this.orderCount = orderCount;
        this.totalUnits = totalUnits;
        this.totalRevenue = totalRevenue;
    }

    /** Empty report: no rows, no orders and zero totals. */
    public static SalesReportSummary empty() {
        return new SalesReportSummary(List.of(), 0, 0, BigDecimal.ZERO);
    }

    public List<SalesReportRow> rows() {
        return rows;
    }

    public int orderCount() {
        return orderCount;
    }

    public int totalUnits() {
        return totalUnits;
    }

    public BigDecimal totalRevenue() {
        return totalRevenue;
    }

    @Override
    public String toString() {
        return "SalesReportSummary{rows=" + rows.size() + ", orderCount=" + orderCount
                + ", totalUnits=" + totalUnits + ", totalRevenue=" + totalRevenue + '}';
    }
}
