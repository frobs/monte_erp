package com.entidaddesconocida.monte_erp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link Stock} (RF-14 / CU-10): the lot invariants that mirror
 * the {@code chk_stock_*} CHECK constraints of the DDL (decision D6).
 */
class StockTest {

    private static Product product() {
        Product product = new Product("Teclado", "SKU-1", null, new BigDecimal("19.99"), 0, null, true);
        product.setId(1);
        return product;
    }

    @Test
    @DisplayName("Un lote nuevo nace disponible: availableQuantity == initialQuantity")
    void newLotIsBornAvailable() {
        Stock lot = new Stock(product(), 7, new BigDecimal("10.00"));

        assertEquals(7, lot.getInitialQuantity());
        assertEquals(7, lot.getAvailableQuantity());
        assertNotNull(lot.getEntryDate());
    }

    @Test
    @DisplayName("initialQuantity <= 0 se rechaza en el dominio (espejo del CHECK)")
    void nonPositiveInitialQuantityIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Stock(product(), 0, new BigDecimal("10.00")));
        assertThrows(IllegalArgumentException.class,
                () -> new Stock(product(), -3, new BigDecimal("10.00")));
    }

    @Test
    @DisplayName("El precio de compra negativo o ausente se rechaza (espejo del CHECK)")
    void invalidPurchasePriceIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Stock(product(), 1, new BigDecimal("-0.01")));
        assertThrows(IllegalArgumentException.class,
                () -> new Stock(product(), 1, null));
    }

    @Test
    @DisplayName("subtotal = purchasePrice × initialQuantity con BigDecimal exacto")
    void subtotalMultipliesPriceByQuantity() {
        Stock lot = new Stock(product(), 3, new BigDecimal("5.50"));

        assertEquals(0, new BigDecimal("16.50").compareTo(lot.subtotal()));
    }

    @Test
    @DisplayName("subtotal con una unidad es el precio y con cero es 0")
    void subtotalHandlesOneAndZero() {
        Stock one = new Stock(product(), 1, new BigDecimal("10.00"));
        assertEquals(0, new BigDecimal("10.00").compareTo(one.subtotal()));

        // A full (rehydrated) lot can hold 0 initial quantity: the CHECK is only
        // enforced by the creation constructor, so subtotal must not blow up.
        Stock zero = new Stock(1, 0, 0, new BigDecimal("10.00"),
                LocalDateTime.now(), product(), null);
        assertEquals(0, BigDecimal.ZERO.compareTo(zero.subtotal()));
    }

    @Test
    @DisplayName("subtotal con precio ausente es 0")
    void subtotalWithoutPriceIsZero() {
        Stock lot = new Stock(1, 2, 2, null, LocalDateTime.now(), product(), null);

        assertEquals(0, BigDecimal.ZERO.compareTo(lot.subtotal()));
    }

    @Test
    @DisplayName("consume descuenta el disponible y puede agotar el lote")
    void consumeDiscountsAndCanEmptyTheLot() {
        Stock lot = new Stock(product(), 5, new BigDecimal("1.00"));

        lot.consume(2);
        assertEquals(3, lot.getAvailableQuantity());

        lot.consume(3);
        assertEquals(0, lot.getAvailableQuantity());
    }

    @Test
    @DisplayName("consume por encima del disponible o con 0/negativo se rechaza")
    void consumeBeyondAvailableIsRejected() {
        Stock lot = new Stock(product(), 5, new BigDecimal("1.00"));

        assertThrows(IllegalArgumentException.class, () -> lot.consume(6));
        assertThrows(IllegalArgumentException.class, () -> lot.consume(0));
        assertThrows(IllegalArgumentException.class, () -> lot.consume(-1));
        assertEquals(5, lot.getAvailableQuantity());
    }

    @Test
    @DisplayName("restore devuelve unidades sin superar la cantidad inicial")
    void restoreGivesUnitsBackUpToInitial() {
        Stock lot = new Stock(product(), 5, new BigDecimal("1.00"));
        lot.consume(5);

        lot.restore(5);
        assertEquals(5, lot.getAvailableQuantity());

        assertThrows(IllegalArgumentException.class, () -> lot.restore(1));
        assertThrows(IllegalArgumentException.class, () -> lot.restore(0));
    }
}
