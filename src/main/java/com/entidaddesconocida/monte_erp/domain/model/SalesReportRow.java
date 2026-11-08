package com.entidaddesconocida.monte_erp.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Aggregated row of the sales report: one product with the units sold and the
 * revenue it generated in the period (RF-12 / CU-09, decision D2).
 *
 * <p>It is built by
 * {@link com.entidaddesconocida.monte_erp.domain.service.SalesReportCalculator}
 * and shown in the "Ventas por producto" tab. Money is a {@link BigDecimal}: the
 * revenue is the sum of the line subtotals, never a {@code double}.</p>
 *
 * <p>The row also carries the <strong>cancelled</strong> units and amount of the
 * product. They never add to {@code unitsSold}/{@code revenue} (decision D3: a
 * cancelled order returned its stock, so charging it would inflate the report),
 * but they travel with the row so the screen and the exports can explain why a
 * product shows 0 sold instead of hiding it. A row with {@code unitsSold == 0}
 * does not count in the report ({@link #countsInSalesReport()}) and is printed
 * marked.</p>
 */
public final class SalesReportRow {

    private final Product product;
    private final int unitsSold;
    private final BigDecimal revenue;
    private final int cancelledUnits;
    private final BigDecimal cancelledRevenue;

    /** Builds a row without cancellations. */
    public SalesReportRow(Product product, int unitsSold, BigDecimal revenue) {
        this(product, unitsSold, revenue, 0, BigDecimal.ZERO);
    }

    /**
     * @throws IllegalArgumentException when the product is {@code null}, a counter
     *                                  is negative, an amount is negative or the
     *                                  row has neither sold nor cancelled units
     *                                  (it would add nothing to the report)
     */
    public SalesReportRow(Product product, int unitsSold, BigDecimal revenue,
                          int cancelledUnits, BigDecimal cancelledRevenue) {
        if (product == null) {
            throw new IllegalArgumentException("El producto de la fila es obligatorio");
        }
        if (unitsSold < 0) {
            throw new IllegalArgumentException(
                    "Las unidades vendidas no pueden ser negativas: " + unitsSold);
        }
        if (revenue == null || revenue.signum() < 0) {
            throw new IllegalArgumentException("El importe no puede ser negativo: " + revenue);
        }
        if (cancelledUnits < 0) {
            throw new IllegalArgumentException(
                    "Las unidades canceladas no pueden ser negativas: " + cancelledUnits);
        }
        if (cancelledRevenue == null || cancelledRevenue.signum() < 0) {
            throw new IllegalArgumentException(
                    "El importe cancelado no puede ser negativo: " + cancelledRevenue);
        }
        if (unitsSold == 0 && cancelledUnits == 0) {
            throw new IllegalArgumentException(
                    "La fila no aporta unidades vendidas ni canceladas: " + product.getId());
        }
        this.product = product;
        this.unitsSold = unitsSold;
        this.revenue = revenue;
        this.cancelledUnits = cancelledUnits;
        this.cancelledRevenue = cancelledRevenue;
    }

    public Product getProduct() {
        return product;
    }

    public int getUnitsSold() {
        return unitsSold;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }

    /** Units of the product whose order was cancelled: never part of the totals. */
    public int getCancelledUnits() {
        return cancelledUnits;
    }

    /** Amount of the cancelled units: never part of the totals. */
    public BigDecimal getCancelledRevenue() {
        return cancelledRevenue;
    }

    /**
     * Whether the row counts in the report totals. A row made only of cancelled
     * orders is still listed (to explain the 0 sold, as the stock report does with
     * its returned/cancelled lots) but it does not add units nor revenue.
     */
    public boolean countsInSalesReport() {
        return unitsSold > 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SalesReportRow row)) {
            return false;
        }
        // The product is keyed by id: two light copies of the same product must
        // compare equal even if they are different instances.
        return product.getId() == row.product.getId()
                && unitsSold == row.unitsSold
                && revenue.compareTo(row.revenue) == 0
                && cancelledUnits == row.cancelledUnits
                && cancelledRevenue.compareTo(row.cancelledRevenue) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(product.getId(), unitsSold, revenue.stripTrailingZeros(),
                cancelledUnits, cancelledRevenue.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "SalesReportRow{product=" + product.getName() + ", unitsSold=" + unitsSold
                + ", revenue=" + revenue + ", cancelledUnits=" + cancelledUnits
                + ", cancelledRevenue=" + cancelledRevenue + '}';
    }
}
