package com.entidaddesconocida.monte_erp.viewmodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DraftEditor}, the list and total shared by the draft of
 * a sales order and of a purchase.
 *
 * <p>No JavaFX toolkit: plain properties and lists.</p>
 */
class DraftEditorTest {

    private static final BigDecimal MAX_TOTAL = new BigDecimal("100.00");

    private record Line(int productId, BigDecimal subtotal) {
    }

    private final DraftEditor<Line> draft =
            new DraftEditor<>(MAX_TOTAL, Line::productId, Line::subtotal);

    @Test
    @DisplayName("Adding a line publishes the running total")
    void addingALinePublishesTheTotal() {
        assertTrue(draft.add(new Line(1, new BigDecimal("10.50"))));
        assertTrue(draft.add(new Line(2, new BigDecimal("4.50"))));

        assertEquals(2, draft.lines().size());
        assertEquals(new BigDecimal("15.00"), draft.totalProperty().get());
        assertEquals(new BigDecimal("15.00"), draft.total());
        assertFalse(draft.isEmpty());
    }

    @Test
    @DisplayName("A line above the stored limit is rejected and leaves the draft untouched")
    void lineAboveTheLimitIsRejected() {
        draft.add(new Line(1, new BigDecimal("90.00")));

        assertFalse(draft.add(new Line(2, new BigDecimal("20.00"))));

        assertEquals(1, draft.lines().size());
        assertEquals(new BigDecimal("90.00"), draft.totalProperty().get());
        assertTrue(draft.totalWithinLimit());
    }

    @Test
    @DisplayName("A product can only appear once in the draft")
    void duplicateProductIsDetected() {
        draft.add(new Line(7, new BigDecimal("1.00")));

        assertTrue(draft.containsProduct(7));
        assertFalse(draft.containsProduct(8));
    }

    @Test
    @DisplayName("Removing a line recomputes the total; an unknown line is ignored")
    void removingALineRecomputesTheTotal() {
        Line first = new Line(1, new BigDecimal("10.00"));
        Line second = new Line(2, new BigDecimal("5.00"));
        draft.add(first);
        draft.add(second);

        assertTrue(draft.remove(first));
        assertEquals(new BigDecimal("5.00"), draft.totalProperty().get());
        assertTrue(draft.remove(second));
        assertFalse(draft.remove(second));
        assertFalse(draft.remove(null));
        assertTrue(draft.isEmpty());
    }

    @Test
    @DisplayName("Clearing the draft empties the list and the total")
    void clearEmptiesTheDraft() {
        draft.add(new Line(1, new BigDecimal("10.00")));

        draft.clear();

        assertTrue(draft.isEmpty());
        assertEquals(BigDecimal.ZERO, draft.totalProperty().get());
    }
}
