package com.entidaddesconocida.monte_erp.viewmodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the contact rules shared by the customer and supplier forms
 * (RF-05 / RF-06).
 *
 * <p>No JavaFX and no database: the patterns are pure JDK code, so each invalid
 * email shape is asserted directly instead of going through a save.</p>
 */
class ContactValidationTest {

    @Test
    @DisplayName("Correos con formato correcto: se aceptan")
    void acceptsWellFormedEmails() {
        List<String> valid = List.of(
                "acme@example.com",
                "globex@example.com",
                "qa@cliente.test",
                "first.last+tag@sub.domain.co.uk",
                "user_name%x-y@example.io",
                "USER@EXAMPLE.COM");

        for (String email : valid) {
            assertTrue(ContactValidation.isValidEmail(email), "Debería aceptar: " + email);
        }
    }

    @Test
    @DisplayName("Correos sin arroba, sin dominio, con espacios o con puntos repetidos: se rechazan")
    void rejectsMalformedEmails() {
        List<String> invalid = List.of(
                "",
                "no-es-un-correo",
                "sin@arroba",
                "a@b",
                "a@example",
                "a@example.",
                "a@example..com",
                "a@.com",
                "@example.com",
                "a@@example.com",
                "a @example.com",
                "a@exa mple.com",
                ".a@example.com",
                "a.@example.com",
                "a..b@example.com",
                "a@-example.com",
                "a@example-.com",
                "a@example.c",
                "a@example.123");

        for (String email : invalid) {
            assertFalse(ContactValidation.isValidEmail(email), "No debería aceptar: " + email);
        }
    }

    @Test
    @DisplayName("Correo nulo: no es válido")
    void rejectsNullEmail() {
        assertFalse(ContactValidation.isValidEmail(null));
    }

    @Test
    @DisplayName("Teléfonos con dígitos y separadores: se aceptan")
    void acceptsWellFormedPhones() {
        List<String> valid = List.of(
                "",
                "600333444",
                "+34 600 333 444",
                "(+34) 600-333.444");

        for (String phone : valid) {
            assertTrue(ContactValidation.isValidPhone(phone), "Debería aceptar: " + phone);
        }
    }

    @Test
    @DisplayName("Teléfonos con letras u otros símbolos: se rechazan")
    void rejectsMalformedPhones() {
        List<String> invalid = List.of(
                "llámame",
                "600 333 444 ext. 2",
                "600_333_444",
                "600/333/444",
                "600333444#",
                "600333444?");

        for (String phone : invalid) {
            assertFalse(ContactValidation.isValidPhone(phone), "No debería aceptar: " + phone);
        }
        assertFalse(ContactValidation.isValidPhone(null));
    }
}
