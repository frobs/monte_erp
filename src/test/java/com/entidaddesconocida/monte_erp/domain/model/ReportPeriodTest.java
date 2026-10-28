package com.entidaddesconocida.monte_erp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ReportPeriod} (RF-12 / CU-09): the inclusive bounds and
 * the rejection of an inverted period (decision D7).
 */
class ReportPeriodTest {

    private static final LocalDate FROM = LocalDate.of(2025, 1, 1);
    private static final LocalDate TO = LocalDate.of(2025, 3, 31);

    @Test
    @DisplayName("all() no tiene límites y está vacío")
    void allHasNoBounds() {
        ReportPeriod period = ReportPeriod.all();

        assertNull(period.from());
        assertNull(period.to());
        assertTrue(period.isEmpty());
        assertTrue(period.contains(LocalDateTime.of(2030, 12, 31, 23, 59)));
        assertEquals("Todo el histórico", period.label());
    }

    @Test
    @DisplayName("El período contiene sus dos extremos por día, de forma inclusiva")
    void containsBoundsInclusively() {
        ReportPeriod period = ReportPeriod.of(FROM, TO);

        assertTrue(period.contains(LocalDateTime.of(2025, 1, 1, 0, 0)));
        assertTrue(period.contains(LocalDateTime.of(2025, 1, 1, 23, 59, 59)));
        assertTrue(period.contains(LocalDateTime.of(2025, 3, 31, 23, 59, 59)));
        assertFalse(period.contains(LocalDateTime.of(2024, 12, 31, 23, 59, 59)));
        assertFalse(period.contains(LocalDateTime.of(2025, 4, 1, 0, 0)));
        assertFalse(period.contains(null));
    }

    @Test
    @DisplayName("Un solo límite deja el otro extremo abierto")
    void singleBoundLeavesTheOtherOpen() {
        ReportPeriod onlyFrom = ReportPeriod.of(FROM, null);
        assertTrue(onlyFrom.contains(LocalDateTime.of(2030, 1, 1, 0, 0)));
        assertFalse(onlyFrom.contains(LocalDateTime.of(2024, 12, 31, 12, 0)));

        ReportPeriod onlyTo = ReportPeriod.of(null, TO);
        assertTrue(onlyTo.contains(LocalDateTime.of(2000, 1, 1, 0, 0)));
        assertFalse(onlyTo.contains(LocalDateTime.of(2025, 4, 1, 0, 0)));
    }

    @Test
    @DisplayName("from > to se rechaza en la factoría, antes de consultar la BD")
    void invertedPeriodIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ReportPeriod.of(TO, FROM));
    }

    @Test
    @DisplayName("El mismo día como desde y hasta es válido (un solo día)")
    void sameDayIsValid() {
        ReportPeriod period = ReportPeriod.of(FROM, FROM);

        assertTrue(period.contains(LocalDateTime.of(2025, 1, 1, 12, 0)));
        assertFalse(period.contains(LocalDateTime.of(2025, 1, 2, 0, 0)));
    }

    @Test
    @DisplayName("Dos períodos con los mismos límites son iguales (value object)")
    void equalBoundsProduceEqualPeriods() {
        assertEquals(ReportPeriod.of(FROM, TO), ReportPeriod.of(FROM, TO));
        assertEquals(ReportPeriod.of(FROM, TO).hashCode(), ReportPeriod.of(FROM, TO).hashCode());
    }
}
