package com.entidaddesconocida.monte_erp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ProductSupplier} (RF-11): the read model of the product ↔
 * supplier projection and its id invariant.
 */
class ProductSupplierTest {

    @Test
    @DisplayName("Expone el producto y el proveedor")
    void exposesBothIds() {
        ProductSupplier pair = new ProductSupplier(3, 7);

        assertEquals(3, pair.getProductId());
        assertEquals(7, pair.getSupplierId());
    }

    @Test
    @DisplayName("Rechaza un id de producto no positivo")
    void rejectsNonPositiveProductId() {
        assertThrows(IllegalArgumentException.class, () -> new ProductSupplier(0, 7));
        assertThrows(IllegalArgumentException.class, () -> new ProductSupplier(-1, 7));
    }

    @Test
    @DisplayName("Rechaza un id de proveedor no positivo")
    void rejectsNonPositiveSupplierId() {
        assertThrows(IllegalArgumentException.class, () -> new ProductSupplier(3, 0));
        assertThrows(IllegalArgumentException.class, () -> new ProductSupplier(3, -5));
    }

    @Test
    @DisplayName("equals y hashCode se definen por el par (para usarlo en un Set)")
    void equalityIsByBothIds() {
        ProductSupplier pair = new ProductSupplier(3, 7);

        assertEquals(pair, new ProductSupplier(3, 7));
        assertEquals(pair.hashCode(), new ProductSupplier(3, 7).hashCode());
        assertNotEquals(pair, new ProductSupplier(3, 8));
        assertNotEquals(pair, new ProductSupplier(4, 7));
        assertFalse(pair.equals(null));
        assertFalse(pair.equals("3-7"));
    }

    @Test
    @DisplayName("toString es informativo")
    void toStringIsInformative() {
        assertTrue(new ProductSupplier(3, 7).toString().contains("productId=3"));
        assertTrue(new ProductSupplier(3, 7).toString().contains("supplierId=7"));
    }
}
