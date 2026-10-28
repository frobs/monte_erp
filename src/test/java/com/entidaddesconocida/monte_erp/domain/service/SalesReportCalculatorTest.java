package com.entidaddesconocida.monte_erp.domain.service;

import com.entidaddesconocida.monte_erp.domain.model.Product;
import com.entidaddesconocida.monte_erp.domain.model.SalesOrder;
import com.entidaddesconocida.monte_erp.domain.model.SalesReportLine;
import com.entidaddesconocida.monte_erp.domain.model.SalesReportRow;
import com.entidaddesconocida.monte_erp.domain.model.SalesReportSummary;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SalesReportCalculator} (RF-12 / CU-09): cancelled orders
 * are excluded, lines of the same product are aggregated and money stays exact.
 */
class SalesReportCalculatorTest {

    private static Product product(int id, String name) {
        Product product = new Product(name, "SKU-" + id, null, new BigDecimal("10.00"), 0, null, true);
        product.setId(id);
        return product;
    }

    private static SalesReportLine line(int orderId, String status, Product product, int quantity,
                                        String price) {
        return new SalesReportLine(orderId, LocalDateTime.of(2025, 1, 15, 10, 0), status,
                quantity, new BigDecimal(price), product);
    }

    @Test
    @DisplayName("Un pedido cancelado no suma facturación ni unidades (D3)")
    void cancelledOrdersAreExcluded() {
        SalesReportSummary summary = SalesReportCalculator.summarize(List.of(
                line(1, SalesOrder.STATUS_PENDING, product(1, "Teclado"), 2, "10.00"),
                line(2, SalesOrder.STATUS_CANCELLED, product(1, "Teclado"), 50, "10.00")));

        assertEquals(1, summary.rows().size());
        assertEquals(1, summary.orderCount());
        assertEquals(2, summary.totalUnits());
        assertEquals(0, new BigDecimal("20.00").compareTo(summary.totalRevenue()));
    }

    @Test
    @DisplayName("Dos líneas del mismo producto en pedidos distintos se agregan en una fila")
    void linesOfTheSameProductAreAggregated() {
        SalesReportSummary summary = SalesReportCalculator.summarize(List.of(
                line(1, SalesOrder.STATUS_PENDING, product(1, "Teclado"), 2, "10.00"),
                line(2, SalesOrder.STATUS_DELIVERED, product(1, "Teclado"), 3, "10.00")));

        assertEquals(1, summary.rows().size());
        SalesReportRow row = summary.rows().get(0);
        assertEquals(5, row.getUnitsSold());
        assertEquals(0, new BigDecimal("50.00").compareTo(row.getRevenue()));
        assertEquals(2, summary.orderCount());
        assertEquals(5, summary.totalUnits());
        assertEquals(0, new BigDecimal("50.00").compareTo(summary.totalRevenue()));
    }

    @Test
    @DisplayName("Los totales son la suma exacta de las filas (BigDecimal, 0.10 × 3 = 0.30)")
    void totalsAreExactBigDecimals() {
        SalesReportSummary summary = SalesReportCalculator.summarize(List.of(
                line(1, SalesOrder.STATUS_PENDING, product(1, "A"), 3, "0.10"),
                line(2, SalesOrder.STATUS_SHIPPED, product(2, "B"), 1, "0.20")));

        assertEquals(0, new BigDecimal("0.30").compareTo(summary.rows().get(0).getRevenue()));
        assertEquals(0, new BigDecimal("0.50").compareTo(summary.totalRevenue()));
        assertEquals(4, summary.totalUnits());
    }

    @Test
    @DisplayName("orderCount cuenta pedidos distintos, no líneas")
    void orderCountCountsDistinctOrders() {
        SalesReportSummary summary = SalesReportCalculator.summarize(List.of(
                line(1, SalesOrder.STATUS_PENDING, product(1, "A"), 1, "1.00"),
                line(1, SalesOrder.STATUS_PENDING, product(2, "B"), 1, "1.00"),
                line(1, SalesOrder.STATUS_PENDING, product(3, "C"), 1, "1.00")));

        assertEquals(1, summary.orderCount());
        assertEquals(3, summary.rows().size());
    }

    @Test
    @DisplayName("Las filas se ordenan por importe descendente")
    void rowsAreOrderedByRevenueDescending() {
        SalesReportSummary summary = SalesReportCalculator.summarize(List.of(
                line(1, SalesOrder.STATUS_PENDING, product(1, "Barato"), 1, "1.00"),
                line(2, SalesOrder.STATUS_PENDING, product(2, "Caro"), 1, "99.00")));

        assertEquals("Caro", summary.rows().get(0).getProduct().getName());
        assertEquals("Barato", summary.rows().get(1).getProduct().getName());
    }

    @Test
    @DisplayName("Sin líneas (o null) el resumen queda a cero")
    void emptyInputYieldsEmptySummary() {
        SalesReportSummary fromNull = SalesReportCalculator.summarize(null);
        SalesReportSummary fromEmpty = SalesReportCalculator.summarize(List.of());

        assertEquals(0, fromNull.rows().size());
        assertEquals(0, fromShort(fromEmpty));
        assertEquals(0, BigDecimal.ZERO.compareTo(fromEmpty.totalRevenue()));
    }

    @Test
    @DisplayName("Todos los pedidos cancelados producen un informe vacío")
    void allCancelledYieldsEmptySummary() {
        SalesReportSummary summary = SalesReportCalculator.summarize(List.of(
                line(1, SalesOrder.STATUS_CANCELLED, product(1, "A"), 5, "10.00")));

        assertTrue(summary.rows().isEmpty());
        assertEquals(0, summary.orderCount());
        assertEquals(0, summary.totalUnits());
    }

    @Test
    @DisplayName("El resumen inmutable no expone la lista mutable del acumulador")
    void summaryRowsAreImmutable() {
        SalesReportSummary summary = SalesReportCalculator.summarize(List.of(
                line(1, SalesOrder.STATUS_PENDING, product(1, "A"), 1, "1.00")));

        assertThrows(UnsupportedOperationException.class,
                () -> summary.rows().add(new SalesReportRow(product(2, "B"), 1, BigDecimal.ONE)));
    }

    private static int fromShort(SalesReportSummary summary) {
        return summary.rows().size() + summary.orderCount() + summary.totalUnits();
    }
}
