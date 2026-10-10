package com.entidaddesconocida.monte_erp.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ConnectionSettings#isComplete()}, the rule that decides
 * between the onboarding screen and the login screen.
 */
class ConnectionSettingsTest {

    @Test
    @DisplayName("Con URL, usuario y contraseña la configuración está completa")
    void completeWhenEveryValueIsPresent() {
        ConnectionSettings settings = new ConnectionSettings(
                "jdbc:mysql://localhost:3306/monte_erp", "erp_user", "erp_pass");
        assertTrue(settings.isComplete());
        assertEquals("jdbc:mysql://localhost:3306/monte_erp", settings.url());
        assertEquals("erp_user", settings.user());
        assertEquals("erp_pass", settings.password());
    }

    @Test
    @DisplayName("Un valor nulo en cualquiera de los tres campos deja la configuración incompleta")
    void incompleteWhenAValueIsNull() {
        assertFalse(new ConnectionSettings(null, "erp_user", "erp_pass").isComplete());
        assertFalse(new ConnectionSettings("jdbc:mysql://localhost/monte_erp", null, "erp_pass").isComplete());
        assertFalse(new ConnectionSettings("jdbc:mysql://localhost/monte_erp", "erp_user", null).isComplete());
    }

    @Test
    @DisplayName("Un valor en blanco cuenta como ausente")
    void blankCountsAsMissing() {
        assertFalse(new ConnectionSettings("   ", "erp_user", "erp_pass").isComplete());
        assertFalse(new ConnectionSettings("jdbc:mysql://localhost/monte_erp", "\t", "erp_pass").isComplete());
        assertFalse(new ConnectionSettings("jdbc:mysql://localhost/monte_erp", "erp_user", "  ").isComplete());
    }

    @Test
    @DisplayName("La igualdad es la del record (mismos valores, misma configuración)")
    void equalityComparesEveryValue() {
        ConnectionSettings one = new ConnectionSettings("jdbc:mysql://localhost/monte_erp", "erp_user", "erp_pass");
        ConnectionSettings same = new ConnectionSettings("jdbc:mysql://localhost/monte_erp", "erp_user", "erp_pass");
        ConnectionSettings other = new ConnectionSettings("jdbc:mysql://localhost/monte_erp", "erp_user", "otra");

        assertEquals(one, same);
        assertEquals(one.hashCode(), same.hashCode());
        assertFalse(one.equals(other));
    }
}
