package com.entidaddesconocida.monte_erp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link SalesOrderLine} (RF-07 / CU-07).
 */
class SalesOrderLineTest {

    private static Product product(String name) {
        Product product = new Product(name, "SKU-" + name, null, new BigDecimal("12.50"), 0, null, true);
        product.setId(1);
        return product;
    }

    @Test
    @DisplayName("subtotal = precio unitario × cantidad")
    void subtotalMultipliesPriceByQuantity() {
        SalesOrderLine line = new SalesOrderLine(3, new BigDecimal("12.50"), product("Teclado"));

        assertEquals(0, new BigDecimal("37.50").compareTo(line.subtotal()));
    }

    @Test
    @DisplayName("subtotal con precio nulo es 0")
    void subtotalWithNullPriceIsZero() {
        SalesOrderLine line = new SalesOrderLine(3, null, product("Teclado"));

        assertEquals(0, BigDecimal.ZERO.compareTo(line.subtotal()));
    }

    @Test
    @DisplayName("toString es legible: cantidad × producto")
    void toStringIsReadable() {
        SalesOrderLine line = new SalesOrderLine(2, new BigDecimal("9.99"), product("Ratón"));

        assertEquals("2 × Ratón", line.toString());
    }
}
