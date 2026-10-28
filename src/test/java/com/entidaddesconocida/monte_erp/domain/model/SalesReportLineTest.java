package com.entidaddesconocida.monte_erp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for the {@link SalesReportLine} read model (RF-12 / CU-09).
 */
class SalesReportLineTest {

    private static Product product() {
        Product product = new Product("Teclado", "SKU-1", null, new BigDecimal("10.00"), 0, null, true);
        product.setId(1);
        return product;
    }

    private static SalesReportLine line(int quantity, String price) {
        return new SalesReportLine(7, LocalDateTime.of(2025, 1, 15, 10, 0),
                SalesOrder.STATUS_PENDING, quantity, new BigDecimal(price), product());
    }

    @Test
    @DisplayName("subtotal = unitPrice × quantity con BigDecimal exacto")
    void subtotalIsExact() {
        SalesReportLine line = line(3, "0.10");

        assertEquals(0, new BigDecimal("0.30").compareTo(line.subtotal()));
        assertEquals(7, line.getOrderId());
        assertEquals("Teclado", line.getProduct().getName());
    }

    @Test
    @DisplayName("Cantidad <= 0, precio negativo o nulos se rechazan")
    void invalidValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> line(0, "1.00"));
        assertThrows(IllegalArgumentException.class, () -> line(-1, "1.00"));
        assertThrows(IllegalArgumentException.class, () -> line(1, "-0.01"));
        assertThrows(IllegalArgumentException.class, () -> new SalesReportLine(1, null,
                SalesOrder.STATUS_PENDING, 1, BigDecimal.ONE, product()));
        assertThrows(IllegalArgumentException.class, () -> new SalesReportLine(1,
                LocalDateTime.now(), null, 1, BigDecimal.ONE, product()));
        assertThrows(IllegalArgumentException.class, () -> new SalesReportLine(1,
                LocalDateTime.now(), SalesOrder.STATUS_PENDING, 1, null, product()));
        assertThrows(IllegalArgumentException.class, () -> new SalesReportLine(1,
                LocalDateTime.now(), SalesOrder.STATUS_PENDING, 1, BigDecimal.ONE, null));
    }
}
