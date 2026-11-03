package com.entidaddesconocida.monte_erp.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseUrlTest {

    @Test
    @DisplayName("Detecta credenciales en parámetros JDBC y en la autoridad")
    void detectsEmbeddedCredentials() {
        assertTrue(DatabaseUrl.containsCredentials("jdbc:mysql://db/erp?password=secret"));
        assertTrue(DatabaseUrl.containsCredentials("jdbc:mysql://user:secret@db/erp"));
        assertFalse(DatabaseUrl.containsCredentials("jdbc:mysql://db/erp?serverTimezone=UTC"));
    }

    @Test
    @DisplayName("Redacta credenciales sin ocultar opciones JDBC inocuas")
    void redactsCredentials() {
        DatabaseUrl url = new DatabaseUrl(
                "jdbc:mysql://user:secret@db/erp?password=hidden&serverTimezone=UTC");

        assertEquals("jdbc:mysql://***@db/erp?password=***&serverTimezone=UTC", url.redacted());
    }
}
