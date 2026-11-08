package com.entidaddesconocida.monte_erp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SalesReportSummary} (RF-12 / CU-09): the totals must
 * always square with the rows.
 */
class SalesReportSummaryTest {

    private static Product product(int id, String name) {
        Product product = new Product(name, "SKU-" + id, null, new BigDecimal("10.00"), 0, null, true);
        product.setId(id);
        return product;
    }

    @Test
    @DisplayName("Los totales cuadran con la suma de las filas")
    void totalsMatchRows() {
        SalesReportSummary summary = new SalesReportSummary(List.of(
                new SalesReportRow(product(1, "Teclado"), 3, new BigDecimal("30.00")),
                new SalesReportRow(product(2, "Ratón"), 2, new BigDecimal("9.00"))),
                2, 5, new BigDecimal("39.00"));

        assertEquals(2, summary.rows().size());
        assertEquals(2, summary.orderCount());
        assertEquals(5, summary.totalUnits());
        assertEquals(0, new BigDecimal("39.00").compareTo(summary.totalRevenue()));
    }

    @Test
    @DisplayName("Un total que no cuadra con las filas se rechaza")
    void mismatchedTotalsAreRejected() {
        List<SalesReportRow> rows = List.of(
                new SalesReportRow(product(1, "Teclado"), 3, new BigDecimal("30.00")));

        assertThrows(IllegalArgumentException.class,
                () -> new SalesReportSummary(rows, 1, 4, new BigDecimal("30.00")));
        assertThrows(IllegalArgumentException.class,
                () -> new SalesReportSummary(rows, 1, 3, new BigDecimal("31.00")));
    }

    @Test
    @DisplayName("empty() no tiene filas ni totales y su lista es inmutable")
    void emptySummaryHasNoRows() {
        SalesReportSummary summary = SalesReportSummary.empty();

        assertEquals(0, summary.rows().size());
        assertEquals(0, summary.orderCount());
        assertEquals(0, summary.totalUnits());
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalRevenue()));
        assertThrows(UnsupportedOperationException.class,
                () -> summary.rows().add(new SalesReportRow(product(1, "X"), 1, BigDecimal.ONE)));
    }

    @Test
    @DisplayName("Los pedidos y unidades canceladas viajan en el resumen sin tocar los totales")
    void cancelledFiguresTravelWithTheSummaryWithoutTouchingTheTotals() {
        SalesReportSummary summary = new SalesReportSummary(List.of(
                new SalesReportRow(product(1, "Teclado"), 2, new BigDecimal("20.00"),
                        5, new BigDecimal("50.00")),
                new SalesReportRow(product(2, "Ratón"), 0, BigDecimal.ZERO,
                        3, new BigDecimal("9.00"))),
                1, 2, 2, new BigDecimal("20.00"));

        assertEquals(1, summary.orderCount());
        assertEquals(2, summary.cancelledOrderCount());
        assertEquals(8, summary.cancelledUnits());
        assertEquals(2, summary.totalUnits());
        assertEquals(0, new BigDecimal("20.00").compareTo(summary.totalRevenue()));
        assertTrue(summary.rows().get(0).countsInSalesReport());
        assertFalse(summary.rows().get(1).countsInSalesReport());
    }

    @Test
    @DisplayName("Un número de pedidos cancelados negativo se rechaza")
    void negativeCancelledOrdersAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new SalesReportSummary(List.of(), 0, -1, 0, BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Una fila con solo cancelaciones es válida, pero no cuenta")
    void rowWithOnlyCancelledUnitsIsValidButDoesNotCount() {
        SalesReportRow row = new SalesReportRow(product(1, "X"), 0, BigDecimal.ZERO,
                4, new BigDecimal("8.00"));

        assertFalse(row.countsInSalesReport());
        assertEquals(4, row.getCancelledUnits());
        assertEquals(0, new BigDecimal("8.00").compareTo(row.getCancelledRevenue()));
        assertThrows(IllegalArgumentException.class,
                () -> new SalesReportRow(product(1, "X"), 0, BigDecimal.ZERO, 0, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new SalesReportRow(product(1, "X"), 0, BigDecimal.ZERO, -1, BigDecimal.ZERO));
    }

    @Test
    @DisplayName("La fila agregada rechaza unidades <= 0 e importe negativo")
    void rowValidatesItsInvariants() {
        assertThrows(IllegalArgumentException.class,
                () -> new SalesReportRow(product(1, "X"), 0, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new SalesReportRow(product(1, "X"), 1, new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,
                () -> new SalesReportRow(null, 1, BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Dos filas del mismo producto con las mismas cifras son iguales")
    void rowsWithSameValuesAreEqual() {
        SalesReportRow first = new SalesReportRow(product(1, "Teclado"), 3, new BigDecimal("30.00"));
        SalesReportRow second = new SalesReportRow(product(1, "Teclado"), 3, new BigDecimal("30.0"));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertTrue(first.toString().contains("Teclado"));
    }
}
