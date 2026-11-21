package com.entidaddesconocida.monte_erp.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        assertTrue(InputMasks.acceptsPartialInteger("", 10));
        assertTrue(InputMasks.acceptsPartialInteger("0", 10));
        assertTrue(InputMasks.acceptsPartialInteger("2147483647", 10));

        assertFalse(InputMasks.acceptsPartialInteger("12345678901", 10));
        assertFalse(InputMasks.acceptsPartialInteger("-1", 10));
        assertFalse(InputMasks.acceptsPartialInteger("1.5", 10));
        assertFalse(InputMasks.acceptsPartialInteger("1 2", 10));
        assertFalse(InputMasks.acceptsPartialInteger("abc", 10));
    }

    @Test
    @DisplayName("La cantidad con tope acepta hasta las unidades disponibles y rechaza el resto")
    void integerMaskWithStockBoundOnlyAcceptsUpToTheStock() {
        assertTrue(InputMasks.acceptsPartialIntegerWithin("", 10, 0));
        assertTrue(InputMasks.acceptsPartialIntegerWithin("", 10, 12));
        assertTrue(InputMasks.acceptsPartialIntegerWithin("12", 10, 12));
        assertTrue(InputMasks.acceptsPartialIntegerWithin("7", 10, 12));
        assertTrue(InputMasks.acceptsPartialIntegerWithin("2147483647", 10, Integer.MAX_VALUE));

        assertFalse(InputMasks.acceptsPartialIntegerWithin("13", 10, 12));
        assertFalse(InputMasks.acceptsPartialIntegerWithin("1", 10, 0));
        assertFalse(InputMasks.acceptsPartialIntegerWithin("abc", 10, 12));
        assertFalse(InputMasks.acceptsPartialIntegerWithin("2147483648", 10, Integer.MAX_VALUE));
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

    @Test
    @DisplayName("El teléfono solo admite dígitos y separadores, hasta el límite de longitud")
    void phoneMaskAcceptsOnlyDigitsAndSeparators() {
        assertTrue(InputMasks.acceptsPartialPhone("", 20));
        assertTrue(InputMasks.acceptsPartialPhone("600333444", 20));
        assertTrue(InputMasks.acceptsPartialPhone("+34 600 333 444", 20));
        assertTrue(InputMasks.acceptsPartialPhone("(+34) 600-333.444", 20));

        assertFalse(InputMasks.acceptsPartialPhone("600333444A", 20));
        assertFalse(InputMasks.acceptsPartialPhone("llámame", 20));
        assertFalse(InputMasks.acceptsPartialPhone("600_333_444", 20));
        assertFalse(InputMasks.acceptsPartialPhone("600/333/444", 20));
        assertFalse(InputMasks.acceptsPartialPhone("1".repeat(21), 20));
    }

    @Test
    @DisplayName("El correo bloquea espacios, acentos y un segundo @; respeta el límite de longitud")
    void emailMaskAcceptsOnlyEmailCharactersAndOneAt() {
        assertTrue(InputMasks.acceptsPartialEmail("", 100));
        assertTrue(InputMasks.acceptsPartialEmail("qa", 100));
        assertTrue(InputMasks.acceptsPartialEmail("qa@", 100));
        assertTrue(InputMasks.acceptsPartialEmail("first.last+tag@sub.domain.co.uk", 100));

        assertFalse(InputMasks.acceptsPartialEmail("qa @cliente.test", 100));
        assertFalse(InputMasks.acceptsPartialEmail("qa@cliente .test", 100));
        assertFalse(InputMasks.acceptsPartialEmail("qa@@cliente.test", 100));
        assertFalse(InputMasks.acceptsPartialEmail("qa@cliente.tést", 100));
        assertFalse(InputMasks.acceptsPartialEmail("a".repeat(101) + "@example.com", 100));
    }

    @Test
    @DisplayName("Los patrones dependientes del límite se compilan una sola vez por combinación")
    void digitPatternsAreCompiledOncePerLimit() {
        Pattern tenDigits = InputMasks.integerPattern(10);
        Pattern tenDigitsAgain = InputMasks.integerPattern(10);
        Pattern eightDigits = InputMasks.integerPattern(8);
        assertSame(tenDigits, tenDigitsAgain, "mismo tope, misma instancia de Pattern");
        assertNotSame(tenDigits, eightDigits, "topes distintos, patrones distintos");

        Pattern money = InputMasks.decimalPattern(8, 2);
        Pattern moneyAgain = InputMasks.decimalPattern(8, 2);
        Pattern otherMoney = InputMasks.decimalPattern(8, 3);
        assertSame(money, moneyAgain, "mismos límites, misma instancia de Pattern");
        assertNotSame(money, otherMoney, "límites distintos, patrones distintos");

        // El patrón cacheado se comporta como el literal de antes para cada tope.
        assertTrue(tenDigits.matcher("1234567890").matches());
        assertFalse(tenDigits.matcher("12345678901").matches());
        assertTrue(eightDigits.matcher("12345678").matches());
        assertFalse(eightDigits.matcher("123456789").matches());
        assertTrue(money.matcher("99999999,99").matches());
        assertFalse(money.matcher("99999999,999").matches());
        assertTrue(otherMoney.matcher("1,234").matches());
        assertFalse(otherMoney.matcher("1,2345").matches());
    }

    @Test
    @DisplayName("El resultado no cambia al pedir la misma máscara muchas veces")
    void masksAreStableAcrossRepeatedCalls() {
        for (int round = 0; round < 100; round++) {
            assertTrue(InputMasks.acceptsPartialInteger("999", 3));
            assertFalse(InputMasks.acceptsPartialInteger("9999", 3));
            assertTrue(InputMasks.acceptsPartialDecimal("12,34", 4, 2));
            assertFalse(InputMasks.acceptsPartialDecimal("12,345", 4, 2));
            assertTrue(InputMasks.acceptsPartialPhone("+34 600 333 444", 20));
            assertFalse(InputMasks.acceptsPartialPhone("600333444A", 20));
            assertTrue(InputMasks.acceptsPartialEmail("qa@cliente.test", 100));
            assertFalse(InputMasks.acceptsPartialEmail("qa@@cliente.test", 100));
        }
    }
}
