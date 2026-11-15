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
 * Unit tests for {@link SalesOrderFilter} (RF-07 / CU-07): the optional criteria,
 * the rejection of an inverted range and the label used by the diagnostics.
 */
class SalesOrderFilterTest {

    private static final LocalDate FROM = LocalDate.of(2025, 1, 1);
    private static final LocalDate TO = LocalDate.of(2025, 1, 31);

    @Test
    @DisplayName("all() no filtra, tiene el período vacío y se etiqueta como histórico completo")
    void allHasNoCriteria() {
        SalesOrderFilter filter = SalesOrderFilter.all();

        assertTrue(filter.period().isEmpty());
        assertNull(filter.customerId());
        assertNull(filter.status());
        assertFalse(filter.isFiltered());
        assertEquals("Todo el histórico", filter.label());
        assertEquals(SalesOrderFilter.all(), filter);
    }

    @Test
    @DisplayName("Un filtro con solo fechas no está vacío y conserva los dos extremos")
    void dateRangeIsKept() {
        SalesOrderFilter filter = SalesOrderFilter.of(FROM, TO, null, null);

        assertTrue(filter.isFiltered());
        assertEquals(FROM, filter.period().from());
        assertEquals(TO, filter.period().to());
        assertNull(filter.customerId());
        assertNull(filter.status());
        assertEquals("01/01/2025 – 31/01/2025", filter.label());
    }

    @Test
    @DisplayName("Un rango invertido se rechaza antes de consultar la base de datos")
    void invertedRangeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SalesOrderFilter.of(TO, FROM, null, null));
    }

    @Test
    @DisplayName("Un id de cliente no positivo se rechaza")
    void nonPositiveCustomerIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SalesOrderFilter.of(null, null, 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> SalesOrderFilter.of(null, null, -3, null));
    }

    @Test
    @DisplayName("La etiqueta compone período, cliente y estado")
    void labelComposesEveryCriterion() {
        SalesOrderFilter filter = SalesOrderFilter.of(FROM, TO, 3, OrderStatus.PENDING);

        assertEquals("01/01/2025 – 31/01/2025 · Cliente #3 · Pendiente", filter.label());
    }

    @Test
    @DisplayName("Dos filtros con los mismos criterios son iguales")
    void equalityUsesEveryCriterion() {
        SalesOrderFilter filter = SalesOrderFilter.of(FROM, null, 3, OrderStatus.SHIPPED);

        assertEquals(filter, SalesOrderFilter.of(FROM, null, 3, OrderStatus.SHIPPED));
        assertFalse(filter.equals(SalesOrderFilter.of(TO, null, 3, OrderStatus.SHIPPED)));
        assertFalse(filter.equals(SalesOrderFilter.of(FROM, null, 4, OrderStatus.SHIPPED)));
        assertFalse(filter.equals(SalesOrderFilter.of(FROM, null, 3, OrderStatus.PENDING)));
        assertEquals(filter.hashCode(),
                SalesOrderFilter.of(FROM, null, 3, OrderStatus.SHIPPED).hashCode());
    }
}
