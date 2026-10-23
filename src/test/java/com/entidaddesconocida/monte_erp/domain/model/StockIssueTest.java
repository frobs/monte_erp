package com.entidaddesconocida.monte_erp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link StockIssue} (RF-15 / CU-11): the consumption of a lot by
 * a sales line and its {@code quantity > 0} invariant (mirror of the
 * {@code chk_stock_issue_quantity} CHECK).
 */
class StockIssueTest {

    private static final SalesOrderLine LINE = new SalesOrderLine(2, null, null);
    private static final Stock LOT = new Stock(1, 5, 5, null, null, null, null);

    @Test
    @DisplayName("Un consumo válido conserva cantidad, línea y lote")
    void validIssueKeepsItsData() {
        StockIssue issue = new StockIssue(3, LINE, LOT);

        assertEquals(3, issue.getQuantity());
        assertSame(LINE, issue.getSalesOrderLine());
        assertSame(LOT, issue.getStock());
    }

    @Test
    @DisplayName("quantity <= 0 se rechaza en el dominio (espejo del CHECK)")
    void nonPositiveQuantityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new StockIssue(0, LINE, LOT));
        assertThrows(IllegalArgumentException.class, () -> new StockIssue(-2, LINE, LOT));
        assertThrows(IllegalArgumentException.class, () -> new StockIssue(7, 0, LINE, LOT));
    }

    @Test
    @DisplayName("El constructor de mapeo asigna el id y setQuantity también valida")
    void mappedIssueKeepsIdAndValidatesSetter() {
        StockIssue issue = new StockIssue(7, 3, LINE, LOT);

        assertEquals(7, issue.getId());
        assertEquals(3, issue.getQuantity());
        assertThrows(IllegalArgumentException.class, () -> issue.setQuantity(0));
    }
}
