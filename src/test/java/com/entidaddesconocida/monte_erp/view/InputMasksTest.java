package com.entidaddesconocida.monte_erp.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the numeric input predicates of {@link InputMasks} (RF-03,
 * RF-07 and RF-14).
 *
 * <p>Only the pure predicates are exercised: the {@code TextFormatter} factories
 * are the View part and are not built here, so the tests need no JavaFX
 * toolkit.</p>
 */
class InputMasksTest {

    @Test
    @DisplayName("La cantidad acepta solo dígitos dentro del rango, nunca signos ni letras")
    void integerMaskAcceptsOnlyDigits() {
        assertTrue(InputMasks.acceptsPartialInteger("", 9));
        assertTrue(InputMasks.acceptsPartialInteger("0", 9));
        assertTrue(InputMasks.acceptsPartialInteger("123456789", 9));

        assertFalse(InputMasks.acceptsPartialInteger("1234567890", 9));
        assertFalse(InputMasks.acceptsPartialInteger("-1", 9));
        assertFalse(InputMasks.acceptsPartialInteger("1.5", 9));
        assertFalse(InputMasks.acceptsPartialInteger("1 2", 9));
        assertFalse(InputMasks.acceptsPartialInteger("abc", 9));
    }

    @Test
    @DisplayName("El importe acepta dígitos y un separador con como máximo dos decimales")
    void decimalMaskAcceptsMoney() {
        assertTrue(InputMasks.acceptsPartialDecimal("", 8, 2));
        assertTrue(InputMasks.acceptsPartialDecimal(",", 8, 2));
        assertTrue(InputMasks.acceptsPartialDecimal("1", 8, 2));
        assertTrue(InputMasks.acceptsPartialDecimal("1,", 8, 2));
        assertTrue(InputMasks.acceptsPartialDecimal("1.5", 8, 2));
        assertTrue(InputMasks.acceptsPartialDecimal("99999999,99", 8, 2));

        assertFalse(InputMasks.acceptsPartialDecimal("999999999", 8, 2));
        assertFalse(InputMasks.acceptsPartialDecimal("1.234", 8, 2));
        assertFalse(InputMasks.acceptsPartialDecimal("1,2,3", 8, 2));
        assertFalse(InputMasks.acceptsPartialDecimal("-1", 8, 2));
        assertFalse(InputMasks.acceptsPartialDecimal("abc", 8, 2));
    }
}
