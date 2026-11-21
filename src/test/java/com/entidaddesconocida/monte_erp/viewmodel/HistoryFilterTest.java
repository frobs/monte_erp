package com.entidaddesconocida.monte_erp.viewmodel;

import javafx.beans.property.ObjectProperty;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link HistoryFilter}: every deliberate change re-queries the
 * history once, and a full reset does not query at all.
 */
class HistoryFilterTest {

    private AtomicInteger queries;
    private HistoryFilter<Integer, String> filter;

    @BeforeEach
    void setUp() {
        queries = new AtomicInteger();
        filter = new HistoryFilter<>(new String[] {"Pendiente", "Entregado"},
                queries::incrementAndGet);
    }

    @Test
    @DisplayName("Each criterion change runs the query once")
    void eachChangeRunsTheQueryOnce() {
        filter.fromDateProperty().set(LocalDate.of(2026, 1, 1));
        filter.toDateProperty().set(LocalDate.of(2026, 12, 31));
        ObjectProperty<Integer> entity = filter.entityProperty();
        entity.set(3);
        filter.statusProperty().set("Entregado");

        assertEquals(4, queries.get());
    }

    @Test
    @DisplayName("A batched reset clears every criterion without querying")
    void resetClearsWithoutQuerying() {
        filter.fromDateProperty().set(LocalDate.of(2026, 1, 1));
        filter.entityProperty().set(3);
        filter.statusProperty().set("Entregado");
        queries.set(0);

        filter.reset();

        assertEquals(0, queries.get());
        assertNull(filter.fromDateProperty().get());
        assertNull(filter.toDateProperty().get());
        assertNull(filter.entityProperty().get());
        assertNull(filter.statusProperty().get());
    }

    @Test
    @DisplayName("The status options keep the domain order")
    void statusOptionsKeepTheDomainOrder() {
        assertEquals(List.of("Pendiente", "Entregado"), filter.statusOptions());
    }
}
