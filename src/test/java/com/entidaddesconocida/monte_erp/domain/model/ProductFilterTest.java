package com.entidaddesconocida.monte_erp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ProductFilter} (RF-03 / RF-11): the optional criteria
 * and the ids rejected before any query is issued.
 */
class ProductFilterTest {

    @Test
    @DisplayName("all() no filtra y normaliza un texto en blanco como ausente")
    void allHasNoCriteria() {
        ProductFilter filter = ProductFilter.all();

        assertNull(filter.search());
        assertNull(filter.categoryId());
        assertNull(filter.supplierId());
        assertFalse(filter.isFiltered());
        assertEquals(ProductFilter.all(), ProductFilter.of("   ", null, null));
    }

    @Test
    @DisplayName("La búsqueda se recorta y las claves se conservan")
    void searchIsTrimmedAndIdsAreKept() {
        ProductFilter filter = ProductFilter.of("  cafe  ", 3, 7);

        assertEquals("cafe", filter.search());
        assertEquals(3, filter.categoryId());
        assertEquals(7, filter.supplierId());
        assertTrue(filter.isFiltered());
    }

    @Test
    @DisplayName("Una categoría o proveedor no positivo se rechaza antes de consultar")
    void nonPositiveIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ProductFilter.of(null, 0, null));
        assertThrows(IllegalArgumentException.class, () -> ProductFilter.of(null, null, -1));
    }

    @Test
    @DisplayName("Dos filtros con los mismos criterios son iguales")
    void equalityUsesEveryCriterion() {
        ProductFilter filter = ProductFilter.of("cafe", 3, 7);

        assertEquals(filter, ProductFilter.of("cafe", 3, 7));
        assertEquals(filter.hashCode(), ProductFilter.of("cafe", 3, 7).hashCode());
        assertFalse(filter.equals(ProductFilter.of("te", 3, 7)));
        assertFalse(filter.equals(ProductFilter.of("cafe", null, 7)));
        assertFalse(filter.equals(ProductFilter.of("cafe", 3, null)));
    }
}
