package com.entidaddesconocida.monte_erp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the minimum-stock rule of {@link Product} (RF-10, decision D8).
 */
class ProductTest {

    private static Product withMinimum(int minStock) {
        return new Product("Teclado", "SKU-1", null, new BigDecimal("10.00"), minStock,
                null, true);
    }

    @Test
    @DisplayName("isBelowMinimum es estricto: igual al mínimo no alerta")
    void isStrict() {
        Product product = withMinimum(5);

        assertTrue(product.isBelowMinimum(4));
        assertFalse(product.isBelowMinimum(5));
        assertFalse(product.isBelowMinimum(6));
    }

    @Test
    @DisplayName("Con mínimo 0 nunca alerta")
    void zeroMinimumNeverAlerts() {
        assertFalse(withMinimum(0).isBelowMinimum(0));
    }
}
