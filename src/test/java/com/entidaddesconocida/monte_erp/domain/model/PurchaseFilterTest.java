package com.entidaddesconocida.monte_erp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PurchaseFilter} (RF-14 / CU-10): the optional criteria
 * and the rejection of an inverted range.
 */
class PurchaseFilterTest {

    private static final LocalDate FROM = LocalDate.of(2025, 1, 1);
    private static final LocalDate TO = LocalDate.of(2025, 1, 31);

    @Test
    @DisplayName("all() no filtra, tiene el período vacío y se etiqueta como histórico completo")
    void allHasNoCriteria() {
        PurchaseFilter filter = PurchaseFilter.all();

        assertTrue(filter.period().isEmpty());
        assertNull(filter.supplierId());
        assertNull(filter.status());
        assertFalse(filter.isFiltered());
        assertEquals(PurchaseFilter.all(), filter);
    }

    @Test
    @DisplayName("Un filtro con solo fechas no está vacío y conserva los dos extremos")
    void dateRangeIsKept() {
        PurchaseFilter filter = PurchaseFilter.of(FROM, TO, null, null);

        assertTrue(filter.isFiltered());
        assertEquals(FROM, filter.period().from());
        assertEquals(TO, filter.period().to());
        assertNull(filter.supplierId());
        assertNull(filter.status());
    }

    @Test
    @DisplayName("Un rango invertido se rechaza antes de consultar la base de datos")
    void invertedRangeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PurchaseFilter.of(TO, FROM, null, null));
    }

    @Test
    @DisplayName("Un id de proveedor no positivo se rechaza")
    void nonPositiveSupplierIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PurchaseFilter.of(null, null, 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> PurchaseFilter.of(null, null, -3, null));
    }

    @Test
    @DisplayName("Dos filtros con los mismos criterios son iguales")
    void equalityUsesEveryCriterion() {
        PurchaseFilter filter = PurchaseFilter.of(FROM, null, 3, PurchaseStatus.RECEIVED);

        assertEquals(filter, PurchaseFilter.of(FROM, null, 3, PurchaseStatus.RECEIVED));
        assertFalse(filter.equals(PurchaseFilter.of(TO, null, 3, PurchaseStatus.RECEIVED)));
        assertFalse(filter.equals(PurchaseFilter.of(FROM, null, 4, PurchaseStatus.RECEIVED)));
        assertFalse(filter.equals(PurchaseFilter.of(FROM, null, 3, PurchaseStatus.PENDING)));
        assertEquals(filter.hashCode(),
                PurchaseFilter.of(FROM, null, 3, PurchaseStatus.RECEIVED).hashCode());
    }
}
