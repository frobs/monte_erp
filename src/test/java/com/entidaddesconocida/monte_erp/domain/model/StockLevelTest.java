package com.entidaddesconocida.monte_erp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link StockLevel} (RF-09 / RF-10): the aggregated read model of
 * the stock screen and its minimum-stock alert.
 */
class StockLevelTest {

    private static Product product(int minStock) {
        Product product = new Product("Teclado", "SKU-1", null, new BigDecimal("10.00"),
                minStock, null, true);
        product.setId(1);
        return product;
    }

    @Test
    @DisplayName("Expone el producto, el disponible agregado y el mínimo")
    void exposesTheAggregatedLevel() {
        StockLevel level = new StockLevel(product(5), 12);

        assertEquals(1, level.getProduct().getId());
        assertEquals(12, level.getAvailableQuantity());
        assertEquals(5, level.getMinimumStock());
        assertFalse(level.isBelowMinimum());
        assertEquals("OK", level.alertLabel());
    }

    @Test
    @DisplayName("La alerta es estricta: disponible == mínimo no alerta, por debajo sí")
    void alertIsStrict() {
        assertFalse(new StockLevel(product(5), 5).isBelowMinimum());
        assertTrue(new StockLevel(product(5), 4).isBelowMinimum());
        assertEquals("Bajo mínimo", new StockLevel(product(5), 4).alertLabel());
    }

    @Test
    @DisplayName("Con mínimo 0 nunca hay alerta (el disponible no es negativo)")
    void zeroMinimumNeverAlerts() {
        assertFalse(new StockLevel(product(0), 0).isBelowMinimum());
    }
}
