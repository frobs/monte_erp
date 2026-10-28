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
 */
public final class SalesReportRow {

    private final Product product;
    private final int unitsSold;
    private final BigDecimal revenue;

    /**
     * @throws IllegalArgumentException when the product is {@code null},
     *                                  {@code unitsSold <= 0} or the revenue is
     *                                  negative
     */
    public SalesReportRow(Product product, int unitsSold, BigDecimal revenue) {
        if (product == null) {
            throw new IllegalArgumentException("El producto de la fila es obligatorio");
        }
        if (unitsSold <= 0) {
            throw new IllegalArgumentException(
                    "Las unidades vendidas deben ser mayores que 0: " + unitsSold);
        }
        if (revenue == null || revenue.signum() < 0) {
            throw new IllegalArgumentException("El importe no puede ser negativo: " + revenue);
        }
        this.product = product;
        this.unitsSold = unitsSold;
        this.revenue = revenue;
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
                && revenue.compareTo(row.revenue) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(product.getId(), unitsSold, revenue.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "SalesReportRow{product=" + product.getName() + ", unitsSold=" + unitsSold
                + ", revenue=" + revenue + '}';
    }
}
